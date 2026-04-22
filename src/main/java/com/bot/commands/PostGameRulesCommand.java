package com.bot.commands;

import com.bot.game.GameChannelService;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PostGameRulesCommand implements BotCommand {
    private final GameChannelService gameChannelService = GameChannelService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "gamerules".equalsIgnoreCase(commandName) || "postgamerules".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        event.getChannel().sendMessage(gameChannelService.postRulesMessage(event.getGuild(), true)).queue();
    }
}

