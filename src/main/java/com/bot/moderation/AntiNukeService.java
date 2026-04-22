package com.bot.moderation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AntiNukeService {
    private static final AntiNukeService INSTANCE = new AntiNukeService();

    private final Map<String, Boolean> enabledByGuild = new ConcurrentHashMap<>();

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
}

