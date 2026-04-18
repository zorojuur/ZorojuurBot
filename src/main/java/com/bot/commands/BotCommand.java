package com.bot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public interface BotCommand {
    boolean matches(String commandName);

    void execute(MessageReceivedEvent event, String commandName, String[] args);
}