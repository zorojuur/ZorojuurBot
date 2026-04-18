package com.bot.commands;

import java.util.Locale;
import java.util.function.BiConsumer;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModLogService;

import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class UnbanCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "unban".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "unban",
                    "+unban <userId|@mention|username> [reason]",
                    "+unban jesterskitt appeal accepted"))
                    .queue();
            return;
        }

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        resolveBannedUserAsync(event.getGuild(), args[0], (userId, user) -> unbanForMessage(event, userId, user, reason),
                error -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Unban", error)).queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "unban".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage(
                    "unban",
                    "/unban target:<userId|@mention|username> [reason]",
                    null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        resolveBannedUserAsync(event.getGuild(), target, (userId, user) -> unbanForSlash(event, userId, user, reason),
                error -> event.replyEmbeds(CommandTemplateEmbeds.error("Unban", error)).setEphemeral(true).queue());
    }

    private void unbanForMessage(MessageReceivedEvent event, String userId, User user, String reason) {
        String targetName = user == null ? userId : user.getAsTag();

        event.getGuild().unban(UserSnowflake.fromId(userId))
                .reason(reason)
                .queue(
                        success -> {
                            CaseRecord record = caseService.addCase(
                                    CaseType.UNBAN,
                                    event.getGuild().getId(),
                                    event.getChannel().getId(),
                                    userId,
                                    targetName,
                                    event.getAuthor().getId(),
                                    event.getAuthor().getAsTag(),
                                    reason,
                                    null);
                            modLogService.logCase(event.getGuild(), record);
                            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                    "Unban",
                                    targetName + " unbanned. Case #" + record.id()))
                                    .queue();
                        },
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                "Unban",
                                "I couldn't unban that member. Make sure I have the **Ban Members** permission."))
                                .queue());
    }

    private void unbanForSlash(SlashCommandInteractionEvent event, String userId, User user, String reason) {
        String targetName = user == null ? userId : user.getAsTag();

        event.getGuild().unban(UserSnowflake.fromId(userId))
                .reason(reason)
                .queue(success -> {
                    CaseRecord record = caseService.addCase(
                            CaseType.UNBAN,
                            event.getGuild().getId(),
                            event.getChannel().getId(),
                            userId,
                            targetName,
                            event.getUser().getId(),
                            event.getUser().getAsTag(),
                            reason,
                            null);
                    modLogService.logCase(event.getGuild(), record);
                    event.replyEmbeds(CommandTemplateEmbeds.success(
                            "Unban",
                            targetName + " unbanned. Case #" + record.id()))
                            .setEphemeral(true)
                            .queue();
                }, failure -> event.replyEmbeds(CommandTemplateEmbeds.error("Unban", "I couldn't unban that member."))
                        .setEphemeral(true)
                        .queue());
    }

    private void resolveBannedUserAsync(
            net.dv8tion.jda.api.entities.Guild guild,
            String rawTarget,
            BiConsumer<String, User> onResolved,
            java.util.function.Consumer<String> onFailure) {
        String userId = TargetResolver.extractUserId(rawTarget);
        if (userId != null) {
            guild.getJDA().retrieveUserById(userId).queue(
                    user -> onResolved.accept(userId, user),
                    failure -> onResolved.accept(userId, null));
            return;
        }

        String normalizedLookup = normalizeLookup(rawTarget);
        if (normalizedLookup == null) {
            onFailure.accept("Use a user ID, mention, or username.");
            return;
        }

        guild.retrieveBanList().queue(bans -> {
            for (net.dv8tion.jda.api.entities.Guild.Ban ban : bans) {
                User user = ban.getUser();
                String username = user.getName().toLowerCase(Locale.ROOT);
                String userTag = user.getAsTag().toLowerCase(Locale.ROOT);
                if (username.equals(normalizedLookup)
                        || userTag.equals(normalizedLookup)
                        || userTag.startsWith(normalizedLookup + "#")) {
                    onResolved.accept(user.getId(), user);
                    return;
                }
            }

            onFailure.accept("I couldn't find that banned user.");
        }, failure -> onFailure.accept("I couldn't fetch the ban list right now."));
    }

    private String normalizeLookup(String rawInput) {
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

        while (!normalized.isEmpty() && !Character.isLetterOrDigit(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        return normalized.isEmpty() ? null : normalized;
    }
}