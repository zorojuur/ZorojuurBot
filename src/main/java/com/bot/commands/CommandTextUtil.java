package com.bot.commands;

public final class CommandTextUtil {
    private CommandTextUtil() {
    }

    public static String joinArgs(String[] args, int startIndex, String fallback) {
        if (args.length <= startIndex) {
            return fallback;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        String result = builder.toString().trim();
        if (result.isEmpty()) {
            return fallback;
        }
        return result;
    }
}

