package com.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public interface SlashCommandHandler {
    boolean handlesSlash(String commandName);

    void executeSlash(SlashCommandInteractionEvent event);
}

