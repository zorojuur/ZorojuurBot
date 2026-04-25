package com.bot;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bot.moderation.AccessControlService;
import com.bot.moderation.AntiNukeService;
import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.DeletedMessageLogService;
import com.bot.moderation.DirectMessageLogService;
import com.bot.moderation.EditedMessageLogService;
import com.bot.moderation.MessageFilterService;
import com.bot.moderation.ModLogService;
import com.bot.moderation.MutedRoleService;
import com.bot.moderation.SpamDetectionService;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.guild.GuildAuditLogEntryCreateEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import com.bot.commands.CommandRegistry;
import com.bot.commands.ModLogsCommand;
import com.bot.commands.PostVerifyPrototypeCommand;
import com.bot.reactions.ReactionRoleListener;

public class Main extends ListenerAdapter {
    private static final int MASS_ROLE_UPDATE_THRESHOLD = 5;
    private static final long MASS_ROLE_UPDATE_WINDOW_MS = 10_000;
    private static final int ROLE_DELETE_THRESHOLD = 2;
    private static final long ROLE_DELETE_WINDOW_MS = 60_000;
    private static final long ANTINUKE_DEDUP_WINDOW_MS = 8_000;
    private static final List<Long> CHANNEL_DELETE_AUDIT_RETRY_DELAYS_MS = List.of(0L, 1500L, 4000L);
    private static final List<String> ANTINUKE_STAFF_ROLE_IDS = List.of(
            "1496542503589908603",
            "1496537304880255198",
            "1496542241903349821",
            "1496542172848197783",
            "1496542109619064942",
            "1496541997488672879");

    private static final String ANTINUKE_LOG_CHANNEL_ID = "1496630613996994630"; // updated anti-nuke log channel
    private static final String SPAM_LOG_CHANNEL_ID = "1496630888484835358"; // new spam log channel

    private static final long MESSAGE_CACHE_TTL_MS = 120_000;
    private static final int MAX_MESSAGE_CACHE_SIZE = 2_000;
    private static final Map<String, Long> PROCESSED_MESSAGE_IDS = new ConcurrentHashMap<>();
    private static final Map<String, DeletedMessageSnapshot> RECENT_MESSAGES = new ConcurrentHashMap<>();
    private static final Path SHARED_MESSAGE_CACHE_DIR = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "discordbot.processed-messages");
    private static final Path INSTANCE_LOCK_PATH = Paths.get(
            System.getProperty("java.io.tmpdir"),
            "discordbot.instance.lock");

    private static FileChannel instanceLockChannel;
    private static FileLock instanceLock;

    private final AccessControlService accessControlService = AccessControlService.getInstance();
    private final MessageFilterService messageFilterService = MessageFilterService.getInstance();
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final DeletedMessageLogService deletedMessageLogService = DeletedMessageLogService.getInstance();
    private final EditedMessageLogService editedMessageLogService = EditedMessageLogService.getInstance();
    private final DirectMessageLogService directMessageLogService = DirectMessageLogService.getInstance();
    private final MutedRoleService mutedRoleService = MutedRoleService.getInstance();
    private final SpamDetectionService spamDetectionService = SpamDetectionService.getInstance();
    private final AntiNukeService antiNukeService = AntiNukeService.getInstance();
    private final Map<String, Deque<Long>> roleUpdateEventsByActor = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> roleDeleteEventsByActor = new ConcurrentHashMap<>();
    private final Map<String, Long> recentAntiNukePenalties = new ConcurrentHashMap<>();
    private final CommandRegistry commandRegistry = new CommandRegistry();
    private final ReactionRoleListener reactionRoleListener = new ReactionRoleListener();
    private final ScheduledExecutorService muteExpiryScheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean muteExpiryTaskStarted = new AtomicBoolean(false);

    public static void main(String[] args) {
        if (!acquireInstanceLock()) {
            System.err.println("Another bot instance is already running. Stop it before starting a new one.");
            System.exit(1);
        }

        String token = System.getenv("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Missing DISCORD_TOKEN environment variable.");
            releaseInstanceLock();
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(Main::releaseInstanceLock));

        JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .enableIntents(GatewayIntent.GUILD_MODERATION)
                .enableIntents(GatewayIntent.GUILD_MESSAGE_REACTIONS)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new Main())
                .build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }

        if (!event.isFromGuild()) {
            directMessageLogService.logInboundDm(event);
            return;
        }

        if (!markMessageAsNew(event.getMessageId())) {
            return;
        }

        String message = event.getMessage().getContentRaw().trim();
        cacheMessageForDeletionLog(event);

        if (shouldAutoModerate(event, message)) {
            event.getMessage().delete().queue(
                    success -> {
                        CaseRecord record = caseService.addCase(
                                CaseType.AUTO_MOD,
                                event.getGuild().getId(),
                                event.getChannel().getId(),
                                event.getAuthor().getId(),
                                event.getAuthor().getAsTag(),
                                event.getJDA().getSelfUser().getId(),
                                event.getJDA().getSelfUser().getAsTag(),
                                "Blocked link/invite",
                                message);
                        modLogService.logCase(event.getGuild(), record);
                        event.getChannel().sendMessage(event.getAuthor().getAsMention()
                                + " links/invites are blocked here (GIF links are allowed).")
                                .queue();
                    },
                    failure -> {
                    });
            return;
        }

        if (handleSpam(event, message)) {
            return;
        }

        commandRegistry.dispatch(event, message);
    }

    private boolean handleSpam(MessageReceivedEvent event, String message) {
        if (event.getMember() == null || accessControlService.isHelperPlus(event.getMember())) {
            return false;
        }

        SpamDetectionService.SpamAction action = spamDetectionService.evaluate(event, message);
        if (!action.shouldDeleteMessages()) {
            return false;
        }

        // Log spam deletions in the spam log channel
        Guild guild = event.getGuild();
        var spamLogChannel = guild.getTextChannelById(SPAM_LOG_CHANNEL_ID);
        String spamLogMsg = "[Spam] Deleted " + action.messageIdsToDelete().size() + " message(s) from " + event.getAuthor().getAsTag() + " (" + event.getAuthor().getId() + ") in <#" + event.getChannel().getId() + "> for spam.";
        if (spamLogChannel != null && !action.messageIdsToDelete().isEmpty()) {
            spamLogChannel.sendMessage(spamLogMsg).queue();
        }
        for (String messageId : action.messageIdsToDelete()) {
            event.getChannel().deleteMessageById(messageId).queue(success -> {}, failure -> {});
        }

        CaseRecord record = caseService.addCase(
                CaseType.SPAM,
                event.getGuild().getId(),
                event.getChannel().getId(),
                event.getMember().getId(),
                event.getMember().getUser().getAsTag(),
                event.getJDA().getSelfUser().getId(),
                event.getJDA().getSelfUser().getAsTag(),
                "Automatic spam message cleanup",
                "Triggered by rapid message burst.");
        modLogService.logCase(event.getGuild(), record);
        event.getChannel().sendMessage(event.getAuthor().getAsMention() + " no spam.").queue();
        return true;
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        reactionRoleListener.onReactionAdd(event);
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        Guild guild = event.getGuild();
        if (!antiNukeService.isEnabled(guild.getId())) {
            return;
        }

        String reason = "deleted channel `" + event.getChannel().getName() + "`";
        for (Long delayMs : CHANNEL_DELETE_AUDIT_RETRY_DELAYS_MS) {
            muteExpiryScheduler.schedule(
                    () -> resolveActorForAuditAction(guild, ActionType.CHANNEL_DELETE, event.getChannel().getId(),
                            actor -> enforceAntiNukePenalty(guild, actor, reason, "channel-delete")),
                    delayMs,
                    TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        Guild guild = event.getGuild();
        if (!antiNukeService.isEnabled(guild.getId())) {
            return;
        }

        resolveActorForAuditAction(guild, ActionType.ROLE_DELETE, event.getRole().getId(), actor -> {
            if (isRoleDeleteBurst(guild.getId(), actor.getId())) {
                enforceAntiNukePenalty(guild, actor, "deleted 2 or more roles quickly", "role-delete");
            }
        });
    }

    @Override
    public void onGuildAuditLogEntryCreate(GuildAuditLogEntryCreateEvent event) {
        Guild guild = event.getGuild();
        if (!antiNukeService.isEnabled(guild.getId())) {
            return;
        }

        AuditLogEntry entry = event.getEntry();
        if (entry == null || entry.getUser() == null) {
            return;
        }

        resolveGuildMember(guild, entry.getUser().getId(), actor -> {
            ActionType type = entry.getType();
            if (type == ActionType.WEBHOOK_CREATE
                    || type == ActionType.WEBHOOK_UPDATE
                    || type == ActionType.WEBHOOK_REMOVE) {
                enforceAntiNukePenalty(guild, actor, "modified webhooks", "webhook-modify");
                return;
            }

            if (type == ActionType.CHANNEL_DELETE) {
                enforceAntiNukePenalty(guild, actor, "deleted a channel", "channel-delete");
                return;
            }

            if (type == ActionType.ROLE_DELETE
                    && isRoleDeleteBurst(guild.getId(), actor.getId())) {
                enforceAntiNukePenalty(guild, actor, "deleted 2 or more roles quickly", "role-delete");
                return;
            }

            if (type == ActionType.MEMBER_ROLE_UPDATE
                    && isMassRoleUpdate(guild.getId(), actor.getId())) {
                enforceAntiNukePenalty(guild, actor, "performed mass member role updates", "mass-role-update");
            }
        });
    }

    private void resolveActorForAuditAction(Guild guild, ActionType actionType, String targetId,
            java.util.function.Consumer<Member> onActorResolved) {
        if (!guild.getSelfMember().hasPermission(Permission.VIEW_AUDIT_LOGS)) {
            logAntiNuke(guild, "Anti-nuke: missing View Audit Log permission, cannot identify who performed "
                    + actionType.name().toLowerCase() + ".");
            return;
        }

        guild.retrieveAuditLogs()
                .type(actionType)
                .limit(20)
                .queue(entries -> {
                    AuditLogEntry fallbackEntry = null;
                    for (AuditLogEntry entry : entries) {
                        if (entry == null || entry.getUser() == null) {
                            continue;
                        }
                        if (fallbackEntry == null) {
                            fallbackEntry = entry;
                        }
                        if (targetId != null && entry.getTargetId() != null && !targetId.equals(entry.getTargetId())) {
                            continue;
                        }

                        resolveGuildMember(guild, entry.getUser().getId(), onActorResolved);
                        return;
                    }

                    if (targetId == null && fallbackEntry != null) {
                        resolveGuildMember(guild, fallbackEntry.getUser().getId(), onActorResolved);
                        return;
                    }

                    if (targetId != null) {
                        logAntiNuke(guild, "Anti-nuke: audit log entry not found yet for "
                                + actionType.name().toLowerCase() + " target `" + targetId + "`.");
                    }
                }, failure -> {
                    logAntiNuke(guild, "Anti-nuke: failed to read audit logs for " + actionType.name().toLowerCase()
                            + ". Check bot permissions.");
                });
    }

    private void resolveGuildMember(Guild guild, String userId, java.util.function.Consumer<Member> onResolved) {
        Member cached = guild.getMemberById(userId);
        if (cached != null) {
            onResolved.accept(cached);
            return;
        }

        guild.retrieveMemberById(userId).queue(onResolved, failure -> {
            logAntiNuke(guild, "Anti-nuke: couldn't resolve executor member " + userId
                    + ". They may have left the server before punishment.");
        });
    }

    private boolean isRoleDeleteBurst(String guildId, String actorId) {
        String key = guildId + ":" + actorId;
        Deque<Long> events = roleDeleteEventsByActor.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (events) {
            events.addLast(now);
            while (!events.isEmpty() && now - events.peekFirst() > ROLE_DELETE_WINDOW_MS) {
                events.removeFirst();
            }
            if (events.size() >= ROLE_DELETE_THRESHOLD) {
                events.clear();
                return true;
            }
        }
        return false;
    }

    private boolean isMassRoleUpdate(String guildId, String actorId) {
        String key = guildId + ":" + actorId;
        Deque<Long> events = roleUpdateEventsByActor.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        synchronized (events) {
            events.addLast(now);
            while (!events.isEmpty() && now - events.peekFirst() > MASS_ROLE_UPDATE_WINDOW_MS) {
                events.removeFirst();
            }
            if (events.size() >= MASS_ROLE_UPDATE_THRESHOLD) {
                events.clear();
                return true;
            }
        }
        return false;
    }

    private void enforceAntiNukePenalty(Guild guild, Member actor, String reason, String category) {
        if (actor.getId().equals(guild.getSelfMember().getId())) {
            return;
        }
        if (antiNukeService.isWhitelisted(guild.getId(), actor.getId())) {
            logAntiNuke(guild, "Anti-nuke: skipped " + actor.getUser().getAsTag() + " because they are whitelisted.");
            return;
        }
        long now = System.currentTimeMillis();
        String dedupeKey = guild.getId() + ":" + actor.getId() + ":" + category;
        Long previous = recentAntiNukePenalties.get(dedupeKey);
        if (previous != null && now - previous < ANTINUKE_DEDUP_WINDOW_MS) {
            return;
        }
        recentAntiNukePenalties.put(dedupeKey, now);
        Member selfMember = guild.getSelfMember();
        if (!selfMember.canInteract(actor)) {
            logAntiNuke(guild, "Anti-nuke: couldn't remove roles from " + actor.getUser().getAsTag()
                    + " after they " + reason + " because they are above my role hierarchy.");
            actor.getUser().openPrivateChannel().queue(
                channel -> channel.sendMessage("[Anti-nuke] I tried to remove your staff roles after: " + reason + ", but I do not have permission (my role is too low). Please contact the server owner.").queue(),
                failure -> {}
            );
            return;
        }
        boolean removeAllRoles = "channel-delete".equals(category);
        List<Role> rolesToRemove = actor.getRoles().stream()
                .filter(role -> removeAllRoles || ANTINUKE_STAFF_ROLE_IDS.contains(role.getId()))
                .filter(selfMember::canInteract)
                .toList();
        if (rolesToRemove.isEmpty()) {
            logAntiNuke(guild, "Anti-nuke: no removable " + (removeAllRoles ? "roles" : "staff roles") + " found for " + actor.getUser().getAsTag()
                    + " after they " + reason + ".");
            actor.getUser().openPrivateChannel().queue(
                channel -> channel.sendMessage("[Anti-nuke] I could not find any removable " + (removeAllRoles ? "roles" : "staff roles") + " to remove after: " + reason + ".").queue(),
                failure -> {}
            );
            return;
        }
        guild.modifyMemberRoles(actor, List.of(), rolesToRemove).queue(
                success -> {
                    logAntiNuke(guild, "Anti-nuke: removed " + (removeAllRoles ? "roles" : "staff roles") + " from "
                        + actor.getUser().getAsTag() + " after they " + reason + ".");
                    actor.getUser().openPrivateChannel().queue(
                        channel -> channel.sendMessage("[Anti-nuke] Your " + (removeAllRoles ? "roles" : "staff roles") + " were removed after: " + reason + ".").queue(),
                        failure -> {}
                    );
                },
                failure -> {
                    logAntiNuke(guild, "Anti-nuke: failed to remove " + (removeAllRoles ? "roles" : "staff roles") + " from "
                        + actor.getUser().getAsTag() + " after they " + reason + ".");
                    actor.getUser().openPrivateChannel().queue(
                        channel -> channel.sendMessage("[Anti-nuke] I tried to remove your " + (removeAllRoles ? "roles" : "staff roles") + " after: " + reason + ", but failed due to a Discord error. Please contact the server owner.").queue(),
                        f2 -> {}
                    );
                }
        );
    }

    private void logAntiNuke(Guild guild, String message) {
        var logChannel = guild.getTextChannelById(ANTINUKE_LOG_CHANNEL_ID);
        if (logChannel == null) {
            return;
        }
        logChannel.sendMessage(message).queue(success -> {
        }, failure -> {
        });
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        DeletedMessageSnapshot snapshot = RECENT_MESSAGES.remove(event.getMessageId());
        if (snapshot == null) {
            return;
        }

        Guild guild = event.getJDA().getGuildById(snapshot.guildId());
        if (guild == null) {
            return;
        }

        deletedMessageLogService.logDeletedMessage(
                guild,
                snapshot.channelId(),
                snapshot.authorTag(),
                snapshot.authorId(),
                snapshot.content(),
                snapshot.attachmentUrls());
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }

        DeletedMessageSnapshot previousSnapshot = RECENT_MESSAGES.get(event.getMessageId());
        String oldContent = previousSnapshot == null ? "(unknown/uncached)" : previousSnapshot.content();

        List<String> attachments = new ArrayList<>();
        event.getMessage().getAttachments().forEach(attachment -> attachments.add(attachment.getUrl()));
        String newContent = event.getMessage().getContentDisplay();

        editedMessageLogService.logEditedMessage(
                event.getGuild(),
                event.getChannel().getId(),
                event.getAuthor().getAsTag(),
                event.getAuthor().getId(),
                oldContent,
                newContent,
                attachments);

        RECENT_MESSAGES.put(event.getMessageId(), new DeletedMessageSnapshot(
                event.getGuild().getId(),
                event.getChannel().getId(),
                event.getAuthor().getId(),
                event.getAuthor().getAsTag(),
                newContent,
                attachments,
                Instant.now().toEpochMilli()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.reply("This command can only be used in servers.").setEphemeral(true).queue();
            return;
        }

        if (!commandRegistry.dispatchSlash(event)) {
            event.reply("Unknown command.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (PostVerifyPrototypeCommand.handleButton(event)) {
            return;
        }
        if (ModLogsCommand.handleButton(event)) {
            return;
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (ModLogsCommand.handleStringSelect(event)) {
            return;
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        event.getJDA().getPresence().setActivity(Activity.customStatus("Created by Zorojuur"));

        List<Guild> guilds = event.getJDA().getGuilds();
        directMessageLogService.ensureChannel(event.getJDA());
        for (Guild guild : guilds) {
            modLogService.ensureChannel(guild);
            deletedMessageLogService.ensureChannel(guild);
            editedMessageLogService.ensureChannel(guild);
            registerSlashCommands(guild);
        }

        if (muteExpiryTaskStarted.compareAndSet(false, true)) {
            muteExpiryScheduler.scheduleAtFixedRate(() -> {
                for (Guild guild : event.getJDA().getGuilds()) {
                    mutedRoleService.processMuteExpirations(guild);
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        mutedRoleService.enforceMutedStateOnJoin(event.getMember());
    }

    private void cacheMessageForDeletionLog(MessageReceivedEvent event) {
        List<String> attachments = new ArrayList<>();
        event.getMessage().getAttachments().forEach(attachment -> attachments.add(attachment.getUrl()));

        RECENT_MESSAGES.put(event.getMessageId(), new DeletedMessageSnapshot(
                event.getGuild().getId(),
                event.getChannel().getId(),
                event.getAuthor().getId(),
                event.getAuthor().getAsTag(),
                event.getMessage().getContentDisplay(),
                attachments,
                Instant.now().toEpochMilli()));

        cleanupDeletedMessageCache();
    }

    private void cleanupDeletedMessageCache() {
        if (RECENT_MESSAGES.size() <= MAX_MESSAGE_CACHE_SIZE) {
            return;
        }

        long now = System.currentTimeMillis();
        RECENT_MESSAGES.entrySet().removeIf(entry -> now - entry.getValue().createdAtMs() > MESSAGE_CACHE_TTL_MS);
    }

    private boolean shouldAutoModerate(MessageReceivedEvent event, String message) {
        if (message.startsWith("+")) {
            return false;
        }

        if (accessControlService.isHelperPlus(event.getMember())) {
            return false;
        }

        return messageFilterService.shouldBlock(message);
    }

    private void registerSlashCommands(Guild guild) {
        guild.updateCommands()
                .addCommands(
                        Commands.slash("help", "Show moderation bot help"),
                        Commands.slash("an", "Manage anti-nuke mode (owner/server manager only)")
                                .addOption(OptionType.STRING, "action", "on, off, status, list", false),
                        Commands.slash("mute", "Mute member by muted role")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("unmute", "Unmute member and restore previous roles")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("kick", "Kick a member")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("ban", "Ban a member (admin only)")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("unban", "Unban a user (admin only)")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("purge", "Purge recent messages")
                                .addOption(OptionType.INTEGER, "amount", "Amount up to 100", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("clearwarns", "Clear warning cases for a user (admin only)")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.INTEGER, "amount", "How many recent warns to remove", false),
                        Commands.slash("warn", "Warn a member")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true)
                                .addOption(OptionType.STRING, "reason", "Reason", false),
                        Commands.slash("case", "View case details")
                                .addOption(OptionType.INTEGER, "id", "Case ID", true),
                        Commands.slash("casedelete", "Delete a case (admin only)")
                                .addOption(OptionType.INTEGER, "id", "Case ID", true),
                        Commands.slash("reason", "Update a case reason")
                                .addOption(OptionType.INTEGER, "id", "Case ID", true)
                                .addOption(OptionType.STRING, "reason", "New reason", true),
                        Commands.slash("game", "Play the guess game")
                                .addOption(OptionType.STRING, "action", "start, guess, status, stop", true)
                                .addOption(OptionType.INTEGER, "number", "Required for guess action", false),
                        Commands.slash("role", "Add/remove a role on a user (owner/server manager)")
                                .addOption(OptionType.STRING, "role", "Role ID, mention, or name", true)
                                .addOption(OptionType.STRING, "target", "User mention or ID", true),
                        Commands.slash("w", "Whois info for a member")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true),
                        Commands.slash("modlogs", "Show recent moderation logs for a user")
                                .addOption(OptionType.STRING, "target", "User mention or ID", true))
                .queue();
    }

    private record DeletedMessageSnapshot(
            String guildId,
            String channelId,
            String authorId,
            String authorTag,
            String content,
            List<String> attachmentUrls,
            long createdAtMs) {
    }

    private boolean markMessageAsNew(String messageId) {
        long now = System.currentTimeMillis();
        Long existing = PROCESSED_MESSAGE_IDS.putIfAbsent(messageId, now);
        cleanupProcessedMessageCache(now);

        if (existing != null) {
            return false;
        }

        return markMessageAsNewAcrossProcesses(messageId, now);
    }

    private void cleanupProcessedMessageCache(long now) {
        if (PROCESSED_MESSAGE_IDS.size() <= MAX_MESSAGE_CACHE_SIZE) {
            return;
        }

        Iterator<Map.Entry<String, Long>> iterator = PROCESSED_MESSAGE_IDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (now - entry.getValue() > MESSAGE_CACHE_TTL_MS) {
                iterator.remove();
            }
        }
    }

    private boolean markMessageAsNewAcrossProcesses(String messageId, long now) {
        try {
            Files.createDirectories(SHARED_MESSAGE_CACHE_DIR);
            Files.createFile(SHARED_MESSAGE_CACHE_DIR.resolve(messageId + ".seen"));

            if (now % 67 == 0) {
                cleanupSharedMessageCache(now);
            }

            return true;
        } catch (FileAlreadyExistsException ex) {
            return false;
        } catch (IOException ex) {
            return true;
        }
    }

    private void cleanupSharedMessageCache(long now) {
        try (DirectoryStream<Path> files = Files.newDirectoryStream(SHARED_MESSAGE_CACHE_DIR, "*.seen")) {
            for (Path file : files) {
                long ageMs = now - Files.getLastModifiedTime(file).toMillis();
                if (ageMs > MESSAGE_CACHE_TTL_MS) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static boolean acquireInstanceLock() {
        try {
            instanceLockChannel = FileChannel.open(
                    INSTANCE_LOCK_PATH,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
            instanceLock = instanceLockChannel.tryLock();
            return instanceLock != null;
        } catch (OverlappingFileLockException ex) {
            return false;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to acquire bot instance lock.", ex);
        }
    }

    private static void releaseInstanceLock() {
        try {
            if (instanceLock != null) {
                instanceLock.release();
                instanceLock = null;
            }
            if (instanceLockChannel != null) {
                instanceLockChannel.close();
                instanceLockChannel = null;
            }
        } catch (IOException ex) {
            System.err.println("Failed to release bot instance lock: " + ex.getMessage());
        }
    }
}



