package com.bot.commands;

import com.bot.moderation.CaseService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class ClearWarnsCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "clearwarns".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1 || args.length > 2) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage(
                            "clearwarns",
                            "+clearwarns <userId|@mention|username> [amount]",
                            "+clearwarns jesterskitt 2"))
                    .queue();
            return;
        }

        int amount = parseAmount(args.length == 2 ? args[1] : null);
        if (amount == Integer.MIN_VALUE) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Clear Warns", "amount must be a positive number."))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), args[0],
                member -> {
                    int removed = caseService.clearWarningsForUser(event.getGuild().getId(), member.getId(), amount);
                    event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                            "Clear Warns",
                            "Removed " + removed + " warning case(s) for " + member.getUser().getName() + "."))
                            .queue();
                },
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Clear Warns",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "clearwarns".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("clearwarns", "/clearwarns target:<userId|@mention|username> [amount]", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        int amount = event.getOption("amount", 0, option -> option.getAsInt());
        if (amount < 0) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Clear Warns", "amount must be a positive number."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target,
                member -> {
                    int removed = caseService.clearWarningsForUser(event.getGuild().getId(), member.getId(), amount);
                    event.replyEmbeds(CommandTemplateEmbeds.success(
                            "Clear Warns",
                            "Removed " + removed + " warning case(s) for " + member.getUser().getName() + "."))
                            .setEphemeral(true)
                            .queue();
                },
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Clear Warns",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }

    private int parseAmount(String raw) {
        if (raw == null) {
            return 0;
        }

        try {
            int parsed = Integer.parseInt(raw);
            if (parsed <= 0) {
                return Integer.MIN_VALUE;
            }
            return parsed;
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }

}

