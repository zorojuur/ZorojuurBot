package com.bot.commands;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.ModLogService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class ReasonCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "reason".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 2) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "reason",
                    "+reason <#id> <new reason>",
                    "+reason #12 spamming in vc"))
                    .queue();
            return;
        }

        long caseId = parseCaseId(args[0]);
        if (caseId <= 0) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Reason",
                    "Case ID must look like `+reason #12 spam in vc`."))
                    .queue();
            return;
        }

        String newReason = CommandTextUtil.joinArgs(args, 1, "").trim();
        if (newReason.isEmpty()) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Reason", "Please provide a new reason."))
                    .queue();
            return;
        }

        CaseRecord updated = caseService.updateCaseReason(event.getGuild().getId(), caseId, newReason);
        if (updated == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Reason",
                    "That case was not found in this server."))
                    .queue();
            return;
        }

        modLogService.updateCaseLogMessage(event.getGuild(), updated);
        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                "Reason",
                "Case #" + updated.id() + " reason updated."))
                .queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "reason".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (event.getOption("id") == null || event.getOption("reason") == null) {
            event.replyEmbeds(CommandTemplateEmbeds.usage(
                    "reason",
                    "/reason id:<case-id> reason:<new reason>",
                    null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        long caseId = event.getOption("id").getAsLong();
        String newReason = event.getOption("reason").getAsString().trim();
        if (caseId <= 0 || newReason.isEmpty()) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Reason",
                    "Use `/reason id:<case-id> reason:<new reason>` with valid values."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        CaseRecord updated = caseService.updateCaseReason(event.getGuild().getId(), caseId, newReason);
        if (updated == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Reason", "That case was not found in this server."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        modLogService.updateCaseLogMessage(event.getGuild(), updated);
        event.replyEmbeds(CommandTemplateEmbeds.success("Reason", "Case #" + updated.id() + " reason updated."))
                .setEphemeral(true)
                .queue();
    }

    private long parseCaseId(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }

        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}



