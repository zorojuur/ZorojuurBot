package com.bot.commands;

import java.util.Set;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class GreetingCommand implements BotCommand {
    private static final Set<String> ALIASES = Set.of("ping", "hello", "hi");

    @Override
    public boolean matches(String commandName) {
        return ALIASES.contains(commandName.toLowerCase());
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if ("ping".equalsIgnoreCase(commandName)) {
            event.getChannel().sendMessage("Pong!").queue();
            return;
        }

        if ("hello".equalsIgnoreCase(commandName)) {
            event.getChannel().sendMessage("Hello!").queue();
            return;
        }

        event.getChannel().sendMessage("Hi!").queue();
    }
}