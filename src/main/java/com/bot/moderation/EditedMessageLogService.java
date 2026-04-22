package com.bot.moderation;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class EditedMessageLogService {
    private static final EditedMessageLogService INSTANCE = new EditedMessageLogService();
    private static final Color LOG_COLOR = new Color(102, 2, 60);
    private static final String DEFAULT_CHANNEL_NAME = "edited-message-log";
    private static final String EDITED_LOG_CHANNEL_ID_ENV = "EDITED_LOG_CHANNEL_ID";
    private static final String EDITED_LOG_CHANNEL_NAME_ENV = "EDITED_LOG_CHANNEL_NAME";

    private final Map<String, String> guildLogChannels = new ConcurrentHashMap<>();

    private EditedMessageLogService() {
    }

    public static EditedMessageLogService getInstance() {
        return INSTANCE;
    }

    public void ensureChannel(Guild guild) {
        TextChannel channel = findOrCreate(guild);
        if (channel != null) {
            guildLogChannels.put(guild.getId(), channel.getId());
        }
    }

    public void logEditedMessage(
            Guild guild,
            String sourceChannelId,
            String authorTag,
            String authorId,
            String oldContent,
            String newContent,
            List<String> newAttachmentUrls) {
        TextChannel channel = findOrCreate(guild);
        if (channel == null) {
            return;
        }

        String safeOldContent = (oldContent == null || oldContent.isBlank()) ? "(unknown/empty)" : oldContent;
        String safeNewContent = (newContent == null || newContent.isBlank()) ? "(empty)" : newContent;

        StringBuilder attachmentBlock = new StringBuilder();

        if (newAttachmentUrls != null && !newAttachmentUrls.isEmpty()) {
            for (String url : newAttachmentUrls) {
                attachmentBlock.append("- ").append(url).append("\n");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LOG_COLOR)
                .setTitle("Edited Message")
                .addField("Channel", "<#" + sourceChannelId + ">", false)
                .addField(
                        "Author",
                        (authorTag == null ? "unknown" : authorTag)
                                + (authorId == null ? "" : " (`" + authorId + "`)"),
                        false)
                .addField("Before", safeOldContent, false)
                .addField("After", safeNewContent, false);

        if (!attachmentBlock.isEmpty()) {
            embed.addField("Current Attachments", attachmentBlock.toString().trim(), false);
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

        String configuredId = System.getenv(EDITED_LOG_CHANNEL_ID_ENV);
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
        String configuredName = System.getenv(EDITED_LOG_CHANNEL_NAME_ENV);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }

        return DEFAULT_CHANNEL_NAME;
    }
}
