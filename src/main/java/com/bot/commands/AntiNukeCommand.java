package com.bot.commands;

import com.bot.moderation.AntiNukeService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class AntiNukeCommand implements BotCommand, SlashCommandHandler {
    private final AntiNukeService antiNukeService = AntiNukeService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "an".equalsIgnoreCase(commandName) || "antinuke".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        event.getChannel().sendMessage(handleAction(event.getGuild().getId(), action)).queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "an".equalsIgnoreCase(commandName) || "antinuke".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String action = event.getOption("action", "status", option -> option.getAsString()).toLowerCase();
        event.reply(handleAction(event.getGuild().getId(), action)).setEphemeral(true).queue();
    }

    private String handleAction(String guildId, String action) {
        return switch (action) {
            case "on", "enable", "enabled" -> {
                antiNukeService.setEnabled(guildId, true);
                yield "Anti-nuke is now enabled.";
            }
            case "off", "disable", "disabled" -> {
                antiNukeService.setEnabled(guildId, false);
                yield "Anti-nuke is now disabled.";
            }
            case "status" -> antiNukeService.isEnabled(guildId)
                    ? "Anti-nuke is currently enabled."
                    : "Anti-nuke is currently disabled.";
            default -> "Usage: +an <on|off|status>";
        };
    }
}


