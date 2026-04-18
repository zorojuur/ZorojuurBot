package com.bot.moderation;

import java.util.Locale;
import java.util.regex.Pattern;

public final class MessageFilterService {
    private static final MessageFilterService INSTANCE = new MessageFilterService();

    private static final Pattern INVITE_PATTERN = Pattern.compile("(?:https?://)?(?:www\\.)?(?:discord\\.gg|discord(?:app)?\\.com/invite)/[\\w-]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    private MessageFilterService() {
    }

    public static MessageFilterService getInstance() {
        return INSTANCE;
    }

    public boolean shouldBlock(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) {
            return false;
        }

        if (containsInvite(rawContent)) {
            return true;
        }

        if (!containsUrl(rawContent)) {
            return false;
        }

        return !isGifOnlyLink(rawContent);
    }

    private boolean containsInvite(String content) {
        return INVITE_PATTERN.matcher(content).find();
    }

    private boolean containsUrl(String content) {
        return URL_PATTERN.matcher(content).find();
    }

    private boolean isGifOnlyLink(String content) {
        String lowered = content.toLowerCase(Locale.ROOT);
        if (lowered.contains("tenor.com") || lowered.contains("giphy.com")) {
            return true;
        }

        return lowered.contains(".gif");
    }
}

