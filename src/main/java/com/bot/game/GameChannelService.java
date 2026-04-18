package com.bot.game;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public final class GameChannelService {
    private static final GameChannelService INSTANCE = new GameChannelService();

    private static final String GAME_CHANNEL_ID = "1494787635884064961";
    private static final String RULES_HEADER = "Guess Game Rules";

    private final Map<String, String> channelByGuild = new ConcurrentHashMap<>();

    private GameChannelService() {
    }

    public static GameChannelService getInstance() {
        return INSTANCE;
    }

    public TextChannel ensureGameChannel(Guild guild) {
        TextChannel channel = resolveChannel(guild);
        if (channel == null) {
            return null;
        }

        ensurePinnedRules(channel);
        channelByGuild.put(guild.getId(), channel.getId());
        return channel;
    }

    public boolean isGameChannel(Guild guild, String channelId) {
        TextChannel channel = ensureGameChannel(guild);
        return channel != null && channel.getId().equals(channelId);
    }

    public String getGameChannelMention(Guild guild) {
        TextChannel channel = ensureGameChannel(guild);
        if (channel == null) {
            return "the game channel";
        }
        return channel.getAsMention();
    }

    private void ensurePinnedRules(TextChannel channel) {
        channel.retrievePinnedMessages().queue(pinnedMessages -> {
            boolean hasRules = pinnedMessages.stream()
                    .anyMatch(message -> message.getContentRaw().startsWith("**" + RULES_HEADER + "**"));
            if (hasRules) {
                return;
            }

            String rules = """
                    **Guess Game Rules**
                    - Use `+game start` to begin a game.
                    - Use `+game guess <number>` to guess from 1 to 50.
                    - Use `+game status` to check attempts.
                    - Use `+game stop` to stop the current game.
                    - One active game per server.
                    """;

            channel.sendMessage(rules)
                    .setAllowedMentions(Collections.emptyList())
                    .queue(message -> message.pin().queue());
        });
    }

    private TextChannel resolveChannel(Guild guild) {
        String cachedId = channelByGuild.get(guild.getId());
        if (cachedId != null) {
            TextChannel cached = guild.getTextChannelById(cachedId);
            if (cached != null) {
                return cached;
            }
        }

        TextChannel fixed = guild.getTextChannelById(GAME_CHANNEL_ID);
        if (fixed != null) {
            channelByGuild.put(guild.getId(), fixed.getId());
            return fixed;
        }

        for (TextChannel channel : guild.getTextChannelsByName("guess-game", true)) {
            channelByGuild.put(guild.getId(), channel.getId());
            return channel;
        }

        return null;
    }
}

