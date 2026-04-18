package com.bot.commands;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class UnlockCommand implements BotCommand, SlashCommandHandler {
    @Override
    public boolean matches(String commandName) {
        return "unlock".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!(event.getGuildChannel() instanceof IPermissionContainer channel)) {
            event.getChannel().sendMessage("This channel type cannot be unlocked.").queue();
            return;
        }

        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .clear(Permission.MESSAGE_SEND)
                .queue(
                        success -> event.getChannel().sendMessage("Channel unlocked.").queue(),
                        failure -> event.getChannel().sendMessage("I couldn't unlock this channel.").queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "unlock".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (!(event.getGuildChannel() instanceof IPermissionContainer channel)) {
            event.reply("This channel type cannot be unlocked.").setEphemeral(true).queue();
            return;
        }

        channel.upsertPermissionOverride(event.getGuild().getPublicRole())
                .clear(Permission.MESSAGE_SEND)
                .queue(
                        success -> event.reply("Channel unlocked.").setEphemeral(true).queue(),
                        failure -> event.reply("I couldn't unlock this channel.").setEphemeral(true).queue());
    }
}
