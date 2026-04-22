package com.bot.moderation;

import java.awt.Color;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public final class DirectMessageLogService {
    private static final DirectMessageLogService INSTANCE = new DirectMessageLogService();
    private static final Color LOG_COLOR = new Color(102, 2, 60);

    private static final String DEFAULT_CHANNEL_NAME = "bot-dm-log";

    private static final String DM_LOG_GUILD_ID_ENV = "DM_LOG_GUILD_ID";
    private static final String DM_LOG_CHANNEL_ID_ENV = "DM_LOG_CHANNEL_ID";
    private static final String DM_LOG_CHANNEL_NAME_ENV = "DM_LOG_CHANNEL_NAME";

    private final Map<String, String> guildLogChannels = new ConcurrentHashMap<>();

    private DirectMessageLogService() {
    }

    public static DirectMessageLogService getInstance() {
        return INSTANCE;
    }

    public void ensureChannel(JDA jda) {
        Guild guild = resolveTargetGuild(jda);
        if (guild == null) {
            return;
        }

        TextChannel channel = findOrCreate(guild);
        if (channel != null) {
            guildLogChannels.put(guild.getId(), channel.getId());
        }
    }

    public void logInboundDm(MessageReceivedEvent event) {
        Guild guild = resolveTargetGuild(event.getJDA());
        if (guild == null) {
            return;
        }

        TextChannel channel = findOrCreate(guild);
        if (channel == null) {
            return;
        }

        long timestamp = event.getMessage().getTimeCreated().toEpochSecond();
        String content = event.getMessage().getContentDisplay();
        boolean hasText = content != null && !content.isBlank();
        boolean hasAttachments = !event.getMessage().getAttachments().isEmpty();
        boolean hasEmbeds = !event.getMessage().getEmbeds().isEmpty();
        boolean hasStickers = !event.getMessage().getStickers().isEmpty();

        StringBuilder details = new StringBuilder();

        if (hasAttachments) {
            details.append("Attachments:\n");
            event.getMessage().getAttachments().forEach(attachment ->
                    details.append("- ").append(attachment.getFileName())
                            .append(" | ").append(attachment.getUrl()));
            details.append("\n\n");
        }

        if (hasEmbeds) {
            details.append("Embeds:\n");
            event.getMessage().getEmbeds().forEach(embed -> {
                String name = embed.getTitle() != null ? embed.getTitle() : "(embed)";
                details.append("- ").append(name);
                if (embed.getUrl() != null) {
                    details.append(" | ").append(embed.getUrl());
                }
                details.append("\n");
            });
            details.append("\n");
        }

        if (hasStickers) {
            details.append("Stickers:\n");
            event.getMessage().getStickers().forEach(sticker ->
                    details.append("- ").append(sticker.getName()).append("\n"));
            details.append("\n");
        }

        if (!hasText && !hasAttachments && !hasEmbeds && !hasStickers) {
            details.append("(unknown/empty payload)");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(LOG_COLOR)
                .setTitle("New DM Received")
                .addField("From", event.getAuthor().getAsTag() + " (`" + event.getAuthor().getId() + "`)", false)
                .addField("Time", "<t:" + timestamp + ":F> (<t:" + timestamp + ":R>)", false)
                .addField("Content", hasText ? content : "(no text content)", false);

        if (!details.isEmpty()) {
            embed.addField("Details", details.toString().trim(), false);
        }

        channel.sendMessageEmbeds(embed.build())
                .setAllowedMentions(Collections.emptyList())
                .queue();
    }

    private TextChannel findOrCreate(Guild guild) {
        String existingId = guildLogChannels.get(guild.getId());
        if (existingId != null) {
            TextChannel cached = guild.getTextChannelById(existingId);
            if (cached != null) {
                return cached;
            }
        }

        String configuredChannelId = System.getenv(DM_LOG_CHANNEL_ID_ENV);
        if (configuredChannelId != null && !configuredChannelId.isBlank()) {
            TextChannel byId = guild.getTextChannelById(configuredChannelId.trim());
            if (byId != null) {
                guildLogChannels.put(guild.getId(), byId.getId());
                return byId;
            }
        }

        String channelName = resolveChannelName();
        List<TextChannel> byName = guild.getTextChannelsByName(channelName, true);
        if (!byName.isEmpty()) {
            TextChannel channel = byName.get(0);
            guildLogChannels.put(guild.getId(), channel.getId());
            return channel;
        }

        return null;
    }

    private Guild resolveTargetGuild(JDA jda) {
        String configuredGuildId = System.getenv(DM_LOG_GUILD_ID_ENV);
        if (configuredGuildId != null && !configuredGuildId.isBlank()) {
            Guild configured = jda.getGuildById(configuredGuildId.trim());
            if (configured != null) {
                return configured;
            }
        }

        List<Guild> guilds = jda.getGuilds();
        if (guilds.isEmpty()) {
            return null;
        }

        if (guilds.size() == 1) {
            return guilds.get(0);
        }

        return guilds.get(0);
    }

    private String resolveChannelName() {
        String configuredName = System.getenv(DM_LOG_CHANNEL_NAME_ENV);
        if (configuredName != null && !configuredName.isBlank()) {
            return configuredName.trim();
        }

        return DEFAULT_CHANNEL_NAME;
    }

}


