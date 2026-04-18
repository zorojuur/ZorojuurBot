package com.bot.commands;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import com.bot.reactions.ReactionRolePanelService;
import com.bot.reactions.ReactionRolePanelService.PanelMessage;

public class DeleteReactionRolesCommand implements BotCommand {
    private static final String TARGET_CHANNEL_ID = "1475417253834133564";
    private static final ReactionRolePanelService PANEL_SERVICE = new ReactionRolePanelService();

    @Override
    public boolean matches(String commandName) {
        return "deletereactionroles".equalsIgnoreCase(commandName) || "rrdelete".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!TARGET_CHANNEL_ID.equals(event.getChannel().getId())) {
            event.getChannel().sendMessage("Use this command in the reaction role channel.").queue();
            return;
        }

        PanelMessage panelMessage = PANEL_SERVICE.loadPanelMessage();
        if (panelMessage == null) {
            event.getChannel().sendMessage("No reaction role message is stored.").queue();
            return;
        }

        if (!TARGET_CHANNEL_ID.equals(panelMessage.channelId())) {
            event.getChannel().sendMessage("Stored reaction role message is for a different channel.").queue();
            return;
        }

        event.getChannel().retrieveMessageById(panelMessage.messageId())
                .queue(
                        message -> message.delete().queue(
                                success -> {
                                    PANEL_SERVICE.clearMessageId();
                                    event.getChannel().sendMessage("Reaction role message deleted.").queue();
                                },
                                failure -> event.getChannel().sendMessage("I couldn't delete that message.").queue()),
                        failure -> {
                            PANEL_SERVICE.clearMessageId();
                            event.getChannel().sendMessage("Stored reaction role message was not found.").queue();
                        });
    }
}