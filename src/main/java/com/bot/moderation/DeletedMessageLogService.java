package com.bot.moderation;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class DeletedMessageLogService {
    private static final DeletedMessageLogService INSTANCE = new DeletedMessageLogService();
    private static final Color LOG_COLOR = new Color(102, 2, 60);
    private static final String DEFAULT_CHANNEL_NAME = "deleted-message-log";
    private static final String DELETED_LOG_CHANNEL_ID_ENV = "DELETED_LOG_CHANNEL_ID";
    private static final String DELETED_LOG_CHANNEL_NAME_ENV = "DELETED_LOG_CHANNEL_NAME";

    private final Map<String, String> guildLogChannels = new ConcurrentHashMap<>();

    private DeletedMessageLogService() {
    }

    public static DeletedMessageLogService getInstance() {
        return INSTANCE;
    }

    public void ensureChannel(Guild guild) {
        TextChannel channel = findOrCreate(guild);
        if (channel != null) {
            guildLogChannels.put(guild.getId(), channel.getId());
        }
    }

    public void logDeletedMessage(
            Guild guild,
            String sourceChannelId,
            String authorTag,
            String authorId,
            String content,
            List<String> attachmentUrls) {
        TextChannel channel = findOrCreate(guild);
        if (channel == null) {
            return;
        }

        String safeContent = (content == null || content.isBlank()) ? "(no text content)" : content;
        StringBuilder attachmentBlock = new StringBuilder();

        if (attachmentUrls != null && !attachmentUrls.isEmpty()) {
            for (String url : attachmentUrls) {
                attachmentBlock.append("- ").append(url).append("\n");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LOG_COLOR)
                .setTitle("Deleted Message")
                .addField("Channel", "<#" + sourceChannelId + ">", false)
                .addField(
                        "Author",
                        (authorTag == null ? "unknown" : authorTag)
                                + (authorId == null ? "" : " (`" + authorId + "`)"),
                        false)
                .addField("Content", safeContent, false);

        if (!attachmentBlock.isEmpty()) {
            embed.addField("Attachments", attachmentBlock.toString().trim(), false);
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private TextChannel findOrCreate(Guild guild) {
        String existingId = guildLogChannels.get(guild.getId());
        if (existingId != null) {
            TextChannel cached = guild.getTextChannelById(existingId);
            if (cached != null) {
                return cached;
            }
        }

        String configuredId = System.getenv(DELETED_LOG_CHANNEL_ID_ENV);
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

    private String resolveChannelName() {
        String configuredName = System.getenv(DELETED_LOG_CHANNEL_NAME_ENV);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }

        return DEFAULT_CHANNEL_NAME;
    }
}
