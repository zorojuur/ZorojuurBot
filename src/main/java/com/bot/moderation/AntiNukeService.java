package com.bot.moderation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiNukeService {
    private static final AntiNukeService INSTANCE = new AntiNukeService();

    private final Map<String, Boolean> enabledByGuild = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> whitelistByGuild = new ConcurrentHashMap<>();

    private AntiNukeService() {
    }

    public static AntiNukeService getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled(String guildId) {
        return enabledByGuild.getOrDefault(guildId, false);
    }

    public void setEnabled(String guildId, boolean enabled) {
        enabledByGuild.put(guildId, enabled);
    }

    public boolean isWhitelisted(String guildId, String userId) {
        return whitelistByGuild.getOrDefault(guildId, Set.of()).contains(userId);
    }

    public boolean toggleWhitelisted(String guildId, String userId) {
        Set<String> guildWhitelist = whitelistByGuild.computeIfAbsent(guildId, ignored -> ConcurrentHashMap.newKeySet());
        if (guildWhitelist.contains(userId)) {
            guildWhitelist.remove(userId);
            return false;
        }
        guildWhitelist.add(userId);
        return true;
    }

    public Set<String> getWhitelistedUsers(String guildId) {
        return Set.copyOf(whitelistByGuild.getOrDefault(guildId, Set.of()));
    }
}

