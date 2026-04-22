package com.bot.moderation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public final class SpamDetectionService {
    private static final SpamDetectionService INSTANCE = new SpamDetectionService();

    private static final long WINDOW_MS = 3_000;
    private static final int DELETE_THRESHOLD = 6;
    private static final int TIMEOUT_THRESHOLD = 10;

    private final Map<String, Deque<MessageWindowEntry>> messageTimesByMember = new ConcurrentHashMap<>();

    private SpamDetectionService() {
    }

    public static SpamDetectionService getInstance() {
        return INSTANCE;
    }

    public SpamAction evaluate(MessageReceivedEvent event, String message) {
        Member member = event.getMember();
        if (member == null || message == null || message.isBlank() || message.startsWith("+")) {
            return SpamAction.none();
        }

        long now = System.currentTimeMillis();
        String key = member.getGuild().getId() + ":" + member.getId();
        Deque<MessageWindowEntry> times = messageTimesByMember.computeIfAbsent(key, ignored -> new ArrayDeque<>());

        synchronized (times) {
            times.addLast(new MessageWindowEntry(now, event.getChannel().getId(), event.getMessageId()));
            while (!times.isEmpty() && now - times.peekFirst().timestamp() > WINDOW_MS) {
                times.removeFirst();
            }

            if (times.size() >= DELETE_THRESHOLD) {
                List<String> messageIds = new ArrayList<>();
                String channelId = event.getChannel().getId();
                for (MessageWindowEntry entry : times) {
                    if (channelId.equals(entry.channelId())) {
                        messageIds.add(entry.messageId());
                    }
                }

                boolean timeout = times.size() >= TIMEOUT_THRESHOLD;
                times.clear();
                return new SpamAction(true, timeout, messageIds);
            }
        }

        return SpamAction.none();
    }

    public record SpamAction(boolean shouldDeleteMessages, boolean shouldTimeout, List<String> messageIdsToDelete) {
        private static SpamAction none() {
            return new SpamAction(false, false, List.of());
        }
    }

    private record MessageWindowEntry(long timestamp, String channelId, String messageId) {
    }
}



