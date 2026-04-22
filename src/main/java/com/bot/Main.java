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
import com.bot.reactions.ReactionRoleListener;

public class Main extends ListenerAdapter {
    private static final int MASS_ROLE_UPDATE_THRESHOLD = 5;
    private static final long MASS_ROLE_UPDATE_WINDOW_MS = 10_000;
    private static final int ROLE_DELETE_THRESHOLD = 3;
    private static final long ROLE_DELETE_WINDOW_MS = 60_000;

    private static final String ANTINUKE_LOG_CHANNEL_ID = "1475428173843005552";
    private static final List<String> ANTINUKE_STAFF_ROLE_IDS = List.of(
            "1496537304880255198",
            "1496542241903349821",
            "1496542172848197783",
            "1496542109619064942",
            "1496541997488672879",
            "1496542503589908603");

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

        for (String messageId : action.messageIdsToDelete()) {
            event.getChannel().deleteMessageById(messageId).queue(success -> {
            }, failure -> {
            });
        }

        if (!action.shouldTimeout()) {
            return true;
        }

        if (!event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)
                || !event.getGuild().getSelfMember().canInteract(event.getMember())) {
            return true;
        }

        event.getMember().timeoutFor(1, TimeUnit.MINUTES)
                .reason("Automatic spam timeout")
                .queue(
                        success -> {
                            CaseRecord record = caseService.addCase(
                                    CaseType.SPAM,
                                    event.getGuild().getId(),
                                    event.getChannel().getId(),
                                    event.getMember().getId(),
                                    event.getMember().getUser().getAsTag(),
                                    event.getJDA().getSelfUser().getId(),
                                    event.getJDA().getSelfUser().getAsTag(),
                                    "Automatic spam timeout (1 minute)",
                                    "Triggered by rapid message burst.");
                            modLogService.logCase(event.getGuild(), record);
                            event.getChannel().sendMessage(
                                    event.getAuthor().getAsMention() + " timed out for 1 minute due to spam.")
                                    .queue();
                        },
                        failure -> {
                        });
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

        guild.retrieveAuditLogs()
                .type(ActionType.CHANNEL_DELETE)
                .limit(1)
                .queue(entries -> {
                    if (entries.isEmpty()) {
                        return;
                    }

                    AuditLogEntry entry = entries.get(0);
                    if (entry == null || entry.getUser() == null) {
                        return;
                    }

                    Member actor = guild.getMemberById(entry.getUser().getId());
                    if (actor == null) {
                        return;
                    }

                    enforceAntiNukePenalty(
                            guild,
                            actor,
                            "deleted channel `" + event.getChannel().getName() + "`");
                }, failure -> {
                });
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        // Role-delete enforcement is handled in audit-log create events with threshold logic.
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

        Member actor = guild.getMemberById(entry.getUser().getId());
        if (actor == null) {
            return;
        }

        ActionType type = entry.getType();
        if (type == ActionType.WEBHOOK_CREATE
                || type == ActionType.WEBHOOK_UPDATE
                || type == ActionType.WEBHOOK_REMOVE) {
            enforceAntiNukePenalty(guild, actor, "modified webhooks");
            return;
        }

        if (type == ActionType.CHANNEL_DELETE) {
            enforceAntiNukePenalty(guild, actor, "deleted a channel");
            return;
        }

        if (type == ActionType.ROLE_DELETE
                && isRoleDeleteBurst(guild.getId(), actor.getId())) {
            enforceAntiNukePenalty(guild, actor, "deleted more than 2 roles quickly");
            return;
        }

        if (type == ActionType.MEMBER_ROLE_UPDATE
                && isMassRoleUpdate(guild.getId(), actor.getId())) {
            enforceAntiNukePenalty(guild, actor, "performed mass member role updates");
        }
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

    private void enforceAntiNukePenalty(Guild guild, Member actor, String reason) {
        if (actor.getId().equals(guild.getSelfMember().getId())) {
            return;
        }

        List<Role> rolesToRemove = actor.getRoles().stream()
                .filter(role -> ANTINUKE_STAFF_ROLE_IDS.contains(role.getId()))
                .toList();
        if (rolesToRemove.isEmpty()) {
            return;
        }

        guild.modifyMemberRoles(actor, List.of(), rolesToRemove).queue(
                success -> logAntiNuke(guild, "Anti-nuke: removed staff roles from "
                        + actor.getUser().getAsTag() + " after they " + reason + "."),
                failure -> logAntiNuke(guild, "Anti-nuke: failed to remove staff roles from "
                        + actor.getUser().getAsTag() + " after they " + reason + "."));
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
                                .addOption(OptionType.STRING, "action", "on, off, status", false),
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