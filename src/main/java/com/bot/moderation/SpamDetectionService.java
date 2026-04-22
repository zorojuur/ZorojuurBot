package com.bot.moderation;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.entities.Member;

public final class SpamDetectionService {
    private static final SpamDetectionService INSTANCE = new SpamDetectionService();

    private static final long WINDOW_MS = 4_000;
    private static final int MAX_MESSAGES_IN_WINDOW = 8;

    private final Map<String, Deque<Long>> messageTimesByMember = new ConcurrentHashMap<>();

    private SpamDetectionService() {
    }

    public static SpamDetectionService getInstance() {
        return INSTANCE;
    }

    public boolean shouldTimeout(Member member, String message) {
        if (member == null || message == null || message.isBlank() || message.startsWith("+")) {
            return false;
        }

        long now = System.currentTimeMillis();
        String key = member.getGuild().getId() + ":" + member.getId();
        Deque<Long> times = messageTimesByMember.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MS) {
                times.removeFirst();
            }

            if (times.size() > MAX_MESSAGES_IN_WINDOW) {
                times.clear();
                return true;
            }
        }

        return false;
    }
}

