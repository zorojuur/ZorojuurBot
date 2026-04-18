package com.bot.commands;

import java.util.Locale;
import java.util.function.Consumer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

public final class TargetResolver {
    public enum ResolveFailure {
        MEMBER_NOT_FOUND,
        LOOKUP_FAILED
    }

    private TargetResolver() {
    }

    public static String extractUserId(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String input = rawInput.trim();
        if (input.isEmpty()) {
            return null;
        }

        if (input.startsWith("<@") && input.endsWith(">")) {
            String mention = input.substring(2, input.length() - 1);
            if (mention.startsWith("!")) {
                mention = mention.substring(1);
            }

            if (mention.matches("\\d+")) {
                return mention;
            }
        }

        if (input.matches("\\d+")) {
            return input;
        }

        return null;
    }

    public static Member resolveMember(Guild guild, String rawInput) {
        String userId = extractUserId(rawInput);
        if (userId != null) {
            return guild.getMemberById(userId);
        }

        if (rawInput == null) {
            return null;
        }

        String input = rawInput.trim();
        if (input.isEmpty()) {
            return null;
        }

        String loweredInput = normalizeLookup(input);
        for (Member member : guild.getMembers()) {
            if (matchesLookup(member, loweredInput)) {
                return member;
            }
        }

        return null;
    }

    public static void resolveMemberAsync(
            Guild guild,
            String rawInput,
            Consumer<Member> onSuccess,
            Consumer<ResolveFailure> onFailure) {
        if (guild == null || rawInput == null || rawInput.trim().isEmpty()) {
            onFailure.accept(ResolveFailure.MEMBER_NOT_FOUND);
            return;
        }

        String userId = extractUserId(rawInput);
        if (userId != null) {
            Member cached = guild.getMemberById(userId);
            if (cached != null) {
                onSuccess.accept(cached);
                return;
            }

            guild.retrieveMemberById(userId).queue(
                    onSuccess::accept,
                    failure -> onFailure.accept(
                            isUnknownMemberError(failure)
                                    ? ResolveFailure.MEMBER_NOT_FOUND
                                    : ResolveFailure.LOOKUP_FAILED));
            return;
        }

        String normalized = normalizeLookup(rawInput);
        if (normalized == null) {
            onFailure.accept(ResolveFailure.MEMBER_NOT_FOUND);
            return;
        }

        Member cached = resolveMember(guild, rawInput);
        if (cached != null) {
            onSuccess.accept(cached);
            return;
        }

        guild.retrieveMembersByPrefix(normalized, 25)
                .onSuccess(members -> {
                    Member match = findBestMatch(members, normalized);
                    if (match != null) {
                        onSuccess.accept(match);
                        return;
                    }
                    onFailure.accept(ResolveFailure.MEMBER_NOT_FOUND);
                })
                .onError(failure -> onFailure.accept(ResolveFailure.LOOKUP_FAILED));
    }

    private static Member findBestMatch(java.util.List<Member> members, String normalizedLookup) {
        if (members == null || members.isEmpty()) {
            return null;
        }

        for (Member member : members) {
            if (matchesLookup(member, normalizedLookup)) {
                return member;
            }
        }

        return members.get(0);
    }

    private static boolean matchesLookup(Member member, String normalizedLookup) {
        if (member == null || normalizedLookup == null || normalizedLookup.isEmpty()) {
            return false;
        }

        String username = member.getUser().getName().toLowerCase(Locale.ROOT);
        String displayName = member.getEffectiveName().toLowerCase(Locale.ROOT);
        String userTag = member.getUser().getAsTag().toLowerCase(Locale.ROOT);

        return username.equals(normalizedLookup)
                || displayName.equals(normalizedLookup)
                || userTag.equals(normalizedLookup)
                || userTag.startsWith(normalizedLookup + "#");
    }

    private static String normalizeLookup(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String normalized = rawInput.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean isUnknownMemberError(Throwable failure) {
        if (!(failure instanceof ErrorResponseException exception)) {
            return false;
        }

        ErrorResponse error = exception.getErrorResponse();
        return error == ErrorResponse.UNKNOWN_MEMBER || error == ErrorResponse.UNKNOWN_USER;
    }
}