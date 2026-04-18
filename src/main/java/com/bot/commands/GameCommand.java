package com.bot.commands;

import com.bot.game.GameChannelService;
import com.bot.game.GuessGameService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class GameCommand implements BotCommand, SlashCommandHandler {
    private final GuessGameService guessGameService = GuessGameService.getInstance();
    private final GameChannelService gameChannelService = GameChannelService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "game".equalsIgnoreCase(commandName) || "guess".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!gameChannelService.isGameChannel(event.getGuild(), event.getChannel().getId())) {
            event.getChannel().sendMessage("Use this in " + gameChannelService.getGameChannelMention(event.getGuild()) + ".")
                    .queue();
            return;
        }

        if (args.length == 0) {
            event.getChannel().sendMessage("Usage: +game <start|guess|status|stop> [number]").queue();
            return;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "start" -> event.getChannel().sendMessage(guessGameService.startGame(event.getGuild().getId())).queue();
            case "guess" -> handleGuess(event, args);
            case "status" -> event.getChannel().sendMessage(guessGameService.status(event.getGuild().getId())).queue();
            case "stop" -> event.getChannel().sendMessage(guessGameService.stop(event.getGuild().getId())).queue();
            default -> event.getChannel().sendMessage("Unknown game action. Use start, guess, status, or stop.").queue();
        }
    }

    private void handleGuess(MessageReceivedEvent event, String[] args) {
        if (args.length < 2) {
            event.getChannel().sendMessage("Usage: +game guess <number>").queue();
            return;
        }

        try {
            int guessed = Integer.parseInt(args[1]);
            event.getChannel().sendMessage(guessGameService.guess(event.getGuild().getId(), guessed)).queue();
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessage("Guess must be a number.").queue();
        }
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "game".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (!gameChannelService.isGameChannel(event.getGuild(), event.getChannel().getId())) {
            event.reply("Use this in " + gameChannelService.getGameChannelMention(event.getGuild()) + ".")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String action = event.getOption("action", "", option -> option.getAsString()).toLowerCase();
        String response;
        switch (action) {
            case "start" -> response = guessGameService.startGame(event.getGuild().getId());
            case "guess" -> {
                if (event.getOption("number") == null) {
                    event.reply("number is required for guess.").setEphemeral(true).queue();
                    return;
                }
                response = guessGameService.guess(event.getGuild().getId(), event.getOption("number").getAsInt());
            }
            case "status" -> response = guessGameService.status(event.getGuild().getId());
            case "stop" -> response = guessGameService.stop(event.getGuild().getId());
            default -> response = "Unknown game action. Use start, guess, status, or stop.";
        }

        event.reply(response).queue();
    }
}

