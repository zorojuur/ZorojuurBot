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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.bot.moderation.AccessControlService;
import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.DeletedMessageLogService;
import com.bot.moderation.DirectMessageLogService;
import com.bot.moderation.EditedMessageLogService;
import com.bot.moderation.MessageFilterService;
import com.bot.moderation.ModLogService;
import com.bot.moderation.MutedRoleService;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import com.bot.commands.CommandRegistry;
import com.bot.commands.ModLogsCommand;
import com.bot.commands.PostVerifyPrototypeCommand;
import com.bot.game.GameChannelService;
import com.bot.reactions.ReactionRoleListener;
import com.bot.voice.VoiceChannelLayoutService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;

public class Main extends ListenerAdapter {
    private static final String TARGET_LOG_CATEGORY_ID = "1494787607782096987";
    private static final String SOURCE_LOG_CATEGORY_ID = "1494741754321305752";
    private static final String LEGACY_AUTO_GAME_CHANNEL_ID = "1495015165836394578";
    private static final String LEGACY_AUTO_GAME_CATEGORY_ID = "1495015163596640336";
    private static final String MEMBER_ROLE_ID = "1482676017028923622";
    private static final String RULES_CHANNEL_ID = "1475416785493688350";
    private static final String TICKET_CHANNEL_ID = "1475417718332194877";
    private static final String VERIFY_CHANNEL_NAME = "verify";
    private static final String VERIFY_PROTOTYPE_CHANNEL_NAME = "verify-prototype";
    private static final String VERIFY_TOPIC_MARKER = "[zoro-verify]";
    private static final String VERIFIED_ROLE_NAME = "Verified";
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
    private final GameChannelService gameChannelService = GameChannelService.getInstance();
    private final VoiceChannelLayoutService voiceChannelLayoutService = VoiceChannelLayoutService.getInstance();
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

        commandRegistry.dispatch(event, message);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        reactionRoleListener.onReactionAdd(event);
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

        if (PostVerifyPrototypeCommand.handleButton(event)) {
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
            voiceChannelLayoutService.ensureVoiceChannelLayout(guild);
            migrateChannelsToRequestedCategory(guild);
            enforceLogCategoryVisibility(guild);
            modLogService.ensureChannel(guild);
            deletedMessageLogService.ensureChannel(guild);
            editedMessageLogService.ensureChannel(guild);
            mutedRoleService.ensureMutedRoleChannelPermissions(guild);
            ensurePublicReadHistoryPermissions(guild);
            ensureRoleReadHistoryPermissions(guild, MEMBER_ROLE_ID);
            registerSlashCommands(guild);
            ensureVerifiedAccess(guild);
            cleanupLegacyAutoGameResources(guild);
        }

        if (muteExpiryTaskStarted.compareAndSet(false, true)) {
            muteExpiryScheduler.scheduleAtFixedRate(() -> {
                for (Guild guild : event.getJDA().getGuilds()) {
                    mutedRoleService.processMuteExpirations(guild);
                    mutedRoleService.ensureMutedRoleChannelPermissions(guild);
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
    }

    private void cleanupLegacyAutoGameResources(Guild guild) {
        TextChannel legacyChannel = guild.getTextChannelById(LEGACY_AUTO_GAME_CHANNEL_ID);
        if (legacyChannel != null) {
            legacyChannel.delete().queue(success -> {
            }, failure -> {
            });
        }

        Category legacyCategory = guild.getCategoryById(LEGACY_AUTO_GAME_CATEGORY_ID);
        if (legacyCategory != null) {
            legacyCategory.delete().queue(success -> {
            }, failure -> {
            });
        }
    }

    private void ensureVerifiedAccess(Guild guild) {
        Role verifiedRole = findRoleByName(guild, VERIFIED_ROLE_NAME);
        if (verifiedRole != null) {
            enforceVerifiedRoleVisibility(guild, verifiedRole);
            return;
        }

        guild.createRole()
                .setName(VERIFIED_ROLE_NAME)
                .setMentionable(false)
                .setHoisted(false)
                .queue(
                        createdRole -> enforceVerifiedRoleVisibility(guild, createdRole),
                        failure -> System.err.println("Failed to create Verified role: " + failure.getMessage()));
    }

    private void enforceVerifiedRoleVisibility(Guild guild, Role verifiedRole) {
        Role publicRole = guild.getPublicRole();
        Role memberRole = guild.getRoleById(MEMBER_ROLE_ID);

        for (var channel : guild.getChannels()) {
            if (!(channel instanceof IPermissionContainer permissionContainer)) {
                continue;
            }

            if (!shouldGateAsNormalChannel(channel, guild, publicRole, memberRole)) {
                continue;
            }

            permissionContainer.upsertPermissionOverride(publicRole)
                    .deny(Permission.VIEW_CHANNEL)
                    .queue(success -> {
                    }, failure -> {
                    });

            permissionContainer.upsertPermissionOverride(verifiedRole)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)
                    .queue(success -> {
                    }, failure -> {
                    });

            if (memberRole != null) {
                permissionContainer.upsertPermissionOverride(memberRole)
                        .clear(Permission.VIEW_CHANNEL)
                        .queue(success -> {
                        }, failure -> {
                        });
            }

            grantStaffVisibility(permissionContainer, guild);
        }
    }

    private boolean shouldGateAsNormalChannel(
            net.dv8tion.jda.api.entities.channel.middleman.GuildChannel channel,
            Guild guild,
            Role publicRole,
            Role memberRole) {
        if (channel.getId().equals(RULES_CHANNEL_ID)) {
            return false;
        }

        if (channel.getId().equals(TICKET_CHANNEL_ID)) {
            return false;
        }

        if (channel.getName().equalsIgnoreCase(VERIFY_CHANNEL_NAME)
                || channel.getName().equalsIgnoreCase(VERIFY_PROTOTYPE_CHANNEL_NAME)) {
            return false;
        }

        if (channel instanceof TextChannel textChannelWithTopic) {
            String topic = textChannelWithTopic.getTopic();
            if (topic != null && topic.contains(VERIFY_TOPIC_MARKER)) {
                return false;
            }
        }

        if (channel instanceof TextChannel textChannel
                && TARGET_LOG_CATEGORY_ID.equals(textChannel.getParentCategoryId())) {
            return false;
        }

        if (canRoleView((IPermissionContainer) channel, publicRole)) {
            return true;
        }

        return memberRole != null && canRoleView((IPermissionContainer) channel, memberRole);
    }

    private void grantStaffVisibility(IPermissionContainer permissionContainer, Guild guild) {
        for (Role role : guild.getRoles()) {
            String lowered = role.getName().toLowerCase();
            if (lowered.contains("helper") || lowered.contains("mod") || lowered.contains("staff")
                    || lowered.contains("owner") || lowered.contains("admin")) {
                permissionContainer.upsertPermissionOverride(role)
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)
                        .queue(success -> {
                        }, failure -> {
                        });
            }
        }
    }

    private Role findRoleByName(Guild guild, String roleName) {
        for (Role role : guild.getRoles()) {
            if (role.getName().equalsIgnoreCase(roleName)) {
                return role;
            }
        }
        return null;
    }

    private void migrateChannelsToRequestedCategory(Guild guild) {
        Category source = guild.getCategoryById(SOURCE_LOG_CATEGORY_ID);
        Category target = guild.getCategoryById(TARGET_LOG_CATEGORY_ID);
        if (source == null || target == null) {
            return;
        }

        for (TextChannel channel : source.getTextChannels()) {
            channel.getManager().setParent(target).queue(success -> {
            }, failure -> {
            });
        }
    }

    private void enforceLogCategoryVisibility(Guild guild) {
        Category category = guild.getCategoryById(TARGET_LOG_CATEGORY_ID);
        if (category == null) {
            return;
        }

        long denyView = Permission.VIEW_CHANNEL.getRawValue();
        long allowView = Permission.VIEW_CHANNEL.getRawValue() | Permission.MESSAGE_HISTORY.getRawValue();

        for (var channel : category.getChannels()) {
            if (!(channel instanceof IPermissionContainer permissionContainer)) {
                continue;
            }

            permissionContainer.upsertPermissionOverride(guild.getPublicRole())
                    .deny(Permission.VIEW_CHANNEL)
                    .queue(success -> {
                    }, failure -> {
                    });

            for (Role role : guild.getRoles()) {
                String lowered = role.getName().toLowerCase();
                if (lowered.contains("helper") || lowered.contains("mod") || lowered.contains("staff")
                        || lowered.contains("owner") || lowered.contains("admin")) {
                    permissionContainer.upsertPermissionOverride(role)
                            .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_HISTORY)
                            .queue(success -> {
                            }, failure -> {
                            });
                }
            }
        }
    }

    private void ensureRoleReadHistoryPermissions(Guild guild, String roleId) {
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            return;
        }

        for (var channel : guild.getChannels()) {
            if (!(channel instanceof IPermissionContainer permissionContainer)) {
                continue;
            }

            if (hasReadHistory(permissionContainer, role)) {
                continue;
            }

            permissionContainer.upsertPermissionOverride(role)
                    .clear(Permission.MESSAGE_HISTORY)
                    .grant(Permission.MESSAGE_HISTORY)
                    .queue(success -> {
                    }, failure -> {
                    });
        }
    }

    private void ensurePublicReadHistoryPermissions(Guild guild) {
        Role publicRole = guild.getPublicRole();
        for (var channel : guild.getChannels()) {
            if (!(channel instanceof IPermissionContainer permissionContainer)) {
                continue;
            }

            if (!canPublicRoleView(permissionContainer, publicRole)) {
                continue;
            }

            if (hasReadHistory(permissionContainer, publicRole)) {
                continue;
            }

            permissionContainer.upsertPermissionOverride(publicRole)
                    .clear(Permission.MESSAGE_HISTORY)
                    .grant(Permission.MESSAGE_HISTORY)
                    .queue(success -> {
                    }, failure -> {
                    });
        }
    }

    private boolean canPublicRoleView(IPermissionContainer channel, Role publicRole) {
        return canRoleView(channel, publicRole);
    }

    private boolean canRoleView(IPermissionContainer channel, Role role) {
        PermissionOverride override = channel.getPermissionOverride(role);
        if (override != null) {
            if (override.getDenied().contains(Permission.VIEW_CHANNEL)) {
                return false;
            }
            if (override.getAllowed().contains(Permission.VIEW_CHANNEL)) {
                return true;
            }
        }

        return role.hasPermission(Permission.VIEW_CHANNEL);
    }

    private boolean hasReadHistory(IPermissionContainer channel, Role role) {
        PermissionOverride override = channel.getPermissionOverride(role);
        if (override != null) {
            if (override.getDenied().contains(Permission.MESSAGE_HISTORY)) {
                return false;
            }
            if (override.getAllowed().contains(Permission.MESSAGE_HISTORY)) {
                return true;
            }
        }

        return role.hasPermission(Permission.MESSAGE_HISTORY);
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
                        Commands.slash("setup", "Run one-time server setup (admin only)"),
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
                        Commands.slash("dragall", "Move everyone in voice to your current voice channel (admin only)"),
                        Commands.slash("lock", "Lock current channel (admin only)"),
                        Commands.slash("unlock", "Unlock current channel (admin only)"),
                        Commands.slash("slowmode", "Set slowmode in current channel (admin only)")
                                .addOption(OptionType.INTEGER, "seconds", "0-21600", true),
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
                        Commands.slash("role", "Give a role to a member")
                                .addOption(OptionType.STRING, "role", "Role ID, mention, or name", true)
                                .addOption(OptionType.STRING, "target", "User mention, ID, or name", true),
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