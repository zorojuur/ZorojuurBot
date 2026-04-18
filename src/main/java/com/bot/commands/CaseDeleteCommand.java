package com.bot.commands;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class CaseDeleteCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "casedelete".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length != 1) {
            event.getChannel().sendMessage("Usage: +casedelete <#id>").queue();
            return;
        }

        long caseId = parseCaseId(args[0]);
        if (caseId <= 0) {
            event.getChannel().sendMessage("Case ID must be like +casedelete #12").queue();
            return;
        }

        CaseRecord removed = caseService.deleteCase(event.getGuild().getId(), caseId);
        if (removed == null) {
            event.getChannel().sendMessage("That case was not found.").queue();
            return;
        }

        event.getChannel().sendMessage("Case #" + removed.id() + " deleted.").queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "casedelete".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        long caseId = event.getOption("id", 0L, option -> option.getAsLong());
        if (caseId <= 0) {
            event.reply("id must be greater than 0.").setEphemeral(true).queue();
            return;
        }

        CaseRecord removed = caseService.deleteCase(event.getGuild().getId(), caseId);
        if (removed == null) {
            event.reply("That case was not found.").setEphemeral(true).queue();
            return;
        }

        event.reply("Case #" + removed.id() + " deleted.").setEphemeral(true).queue();
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

