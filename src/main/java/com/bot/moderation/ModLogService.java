package com.bot.moderation;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class ModLogService {
    private static final ModLogService INSTANCE = new ModLogService();
    private static final Color LOG_COLOR = new Color(102, 2, 60);
    private static final String DEFAULT_MOD_LOG_CHANNEL_NAME = "mod-log";
    private static final String DEFAULT_BAN_UNBAN_LOG_CHANNEL_NAME = "ban-unban-log";
    private static final String MOD_LOG_CHANNEL_ID_ENV = "MOD_LOG_CHANNEL_ID";
    private static final String MOD_LOG_CHANNEL_NAME_ENV = "MOD_LOG_CHANNEL_NAME";

    private final Map<String, String> guildLogChannels = new ConcurrentHashMap<>();
    private final Map<String, String> guildBanUnbanLogChannels = new ConcurrentHashMap<>();
    private final Map<String, String> caseLogMessages = new ConcurrentHashMap<>();

    private ModLogService() {
    }

    public static ModLogService getInstance() {
        return INSTANCE;
    }

    public void ensureChannel(Guild guild) {
        TextChannel existing = findOrCreate(guild);
        if (existing != null) {
            guildLogChannels.put(guild.getId(), existing.getId());
        }

        TextChannel banLog = findOrCreateBanUnbanLogChannel(guild);
        if (banLog != null) {
            guildBanUnbanLogChannels.put(guild.getId(), banLog.getId());
        }
    }

    public void logCase(Guild guild, CaseRecord record) {
        TextChannel channel = isBanCase(record)
                ? findOrCreateBanUnbanLogChannel(guild)
                : findOrCreate(guild);
        if (channel == null) {
            return;
        }

        channel.sendMessageEmbeds(buildCaseEmbed(record).build())
                .queue(message -> caseLogMessages.put(caseKey(guild.getId(), record.id()), message.getId()));
    }

    public void updateCaseLogMessage(Guild guild, CaseRecord record) {
        TextChannel channel = findOrCreate(guild);
        if (channel == null) {
            return;
        }

        String caseKey = caseKey(guild.getId(), record.id());
        String messageId = caseLogMessages.get(caseKey);
        if (messageId == null) {
            channel.sendMessageEmbeds(buildCaseEmbed(record).setFooter("Reason updated").build())
                    .queue(message -> caseLogMessages.put(caseKey, message.getId()));
            return;
        }

        channel.editMessageEmbedsById(messageId, buildCaseEmbed(record).setFooter("Reason updated").build()).queue(
                success -> {
                },
                failure -> channel.sendMessageEmbeds(buildCaseEmbed(record).setFooter("Reason updated").build())
                        .queue(message -> caseLogMessages.put(caseKey, message.getId())));
    }

    private EmbedBuilder buildCaseEmbed(CaseRecord record) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LOG_COLOR)
                .setTitle("Case #" + record.id())
                .addField("Type", String.valueOf(record.type()), false)
                .addField("Moderator", record.moderatorName() + " (`" + record.moderatorId() + "`)", false);

        String target = record.targetName() == null ? "n/a" : record.targetName();
        if (record.targetId() != null) {
            target += " (`" + record.targetId() + "`)";
        }

        embed.addField("Target", target, false)
                .addField("Reason", record.reason(), false);

        if (record.extra() != null && !record.extra().isBlank()) {
            embed.addField("Details", record.extra(), false);
        }

        return embed;
    }

    private String caseKey(String guildId, long caseId) {
        return guildId + ":" + caseId;
    }

    private TextChannel findOrCreate(Guild guild) {
        String existingId = guildLogChannels.get(guild.getId());
        if (existingId != null) {
            TextChannel cached = guild.getTextChannelById(existingId);
            if (cached != null) {
                return cached;
            }
        }

        String configuredId = System.getenv(MOD_LOG_CHANNEL_ID_ENV);
        if (configuredId != null && !configuredId.isBlank()) {
            TextChannel byId = guild.getTextChannelById(configuredId.trim());
            if (byId != null) {
                guildLogChannels.put(guild.getId(), byId.getId());
                return byId;
            }
        }

        String channelName = resolveChannelName();

        List<TextChannel> channels = guild.getTextChannelsByName(channelName, true);
        if (!channels.isEmpty()) {
            TextChannel channel = channels.get(0);
            guildLogChannels.put(guild.getId(), channel.getId());
            return channel;
        }

        return null;
    }

    private TextChannel findOrCreateBanUnbanLogChannel(Guild guild) {
        String existingId = guildBanUnbanLogChannels.get(guild.getId());
        if (existingId != null) {
            TextChannel cached = guild.getTextChannelById(existingId);
            if (cached != null) {
                return cached;
            }
        }

        List<TextChannel> channels = guild.getTextChannelsByName(DEFAULT_BAN_UNBAN_LOG_CHANNEL_NAME, true);
        if (!channels.isEmpty()) {
            TextChannel channel = channels.get(0);
            guildBanUnbanLogChannels.put(guild.getId(), channel.getId());
            return channel;
        }

        return null;
    }

    private boolean isBanCase(CaseRecord record) {
        return record.type() == CaseType.BAN || record.type() == CaseType.UNBAN;
    }

    private String resolveChannelName() {
        String configuredName = System.getenv(MOD_LOG_CHANNEL_NAME_ENV);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }

        return DEFAULT_MOD_LOG_CHANNEL_NAME;
    }
}
