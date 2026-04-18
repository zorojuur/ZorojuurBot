package com.bot.commands;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SlowmodeCommand implements BotCommand, SlashCommandHandler {
    @Override
    public boolean matches(String commandName) {
        return "slowmode".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length != 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "slowmode",
                    "+slowmode <seconds (0-21600)>",
                    "+slowmode 10"))
                    .queue();
            return;
        }

        int seconds;
        try {
            seconds = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Slowmode", "Slowmode must be a number."))
                    .queue();
            return;
        }

        if (seconds < 0 || seconds > 21600) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Slowmode",
                    "Slowmode must be between 0 and 21600 seconds."))
                    .queue();
            return;
        }

        if (!(event.getChannel().asGuildMessageChannel() instanceof TextChannel textChannel)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Slowmode",
                    "This channel type doesn't support slowmode."))
                    .queue();
            return;
        }

        textChannel.getManager().setSlowmode(seconds)
                .queue(
                        success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                "Slowmode",
                                "Slowmode set to " + seconds + "s."))
                                .queue(),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                "Slowmode",
                                "I couldn't update slowmode."))
                                .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "slowmode".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        int seconds = event.getOption("seconds", -1, option -> option.getAsInt());
        if (seconds < 0 || seconds > 21600) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Slowmode", "seconds must be between 0 and 21600."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (!(event.getChannel().asGuildMessageChannel() instanceof TextChannel textChannel)) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Slowmode", "This channel type doesn't support slowmode."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        textChannel.getManager().setSlowmode(seconds)
                .queue(
                        success -> event.replyEmbeds(CommandTemplateEmbeds.success(
                                "Slowmode",
                                "Slowmode set to " + seconds + "s."))
                                .setEphemeral(true)
                                .queue(),
                        failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                                "Slowmode",
                                "I couldn't update slowmode."))
                                .setEphemeral(true)
                                .queue());
    }
}

