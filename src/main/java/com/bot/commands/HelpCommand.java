package com.bot.commands;

import java.awt.Color;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class HelpCommand implements BotCommand, SlashCommandHandler {
    private static final Color HELP_COLOR = new Color(102, 2, 60);

    @Override
    public boolean matches(String commandName) {
        return "help".equalsIgnoreCase(commandName) || "commands".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        event.getChannel().sendMessageEmbeds(buildHelpEmbed().build()).queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "help".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        event.replyEmbeds(buildHelpEmbed().build()).setEphemeral(true).queue();
    }

    private EmbedBuilder buildHelpEmbed() {
        return new EmbedBuilder()
                .setTitle("⚡ ZOROJUUR BOT COMMANDS ⚡")
                .setColor(HELP_COLOR)
                .setDescription("**Use these commands based on your role permissions.**")
                .addField(
                        "🔥 TRIAL MODERATOR+ COMMANDS",
                        "• `+help` - Show this help message\n\n"
                                + "• `+setup` - One-time setup for this server\n\n"
                                + "• `+mute <user> [reason]` - Apply muted role\n\n"
                                + "• `+unmute <user> [reason]` - Restore roles from mute snapshot\n\n"
                                + "• `+warn <user> [reason]` - Add warning case\n\n"
                                + "• `+dm <user> <message>` - Send a DM through the bot\n\n"
                                + "• `+w <user>` - Whois info for a member\n\n"
                                + "• `+modlogs <user>` - Show recent moderation logs\n\n"
                                + "• `+case <#id>` - Show case details\n\n"
                                + "• `+reason <#id> <new reason>` - Update case reason\n\n"
                                + "• `+kick <user> [reason]` - Kick a member\n\n"
                                + "• `+game <start|guess|status|stop> [number]` - Guess game\n\n"
                                + "• `+postreactionroles` / `+deletereactionroles` / `+postroleinfo` / `+postrules`",
                        false)
                .addField(
                        "🛡️ ADMIN ONLY",
                        "• `+ban <user> [reason]`\n\n"
                                + "• `+unban <user> [reason]`\n\n"
                                + "• `+dragall`\n\n"
                                + "• `+role <role> <user>`\n\n"
                                + "• `+giveall <role>`\n\n"
                                + "• `+setupverify [tester] [#channel]` / `+postverify <role>`\n\n"
                                + "• `+purge <amount> [reason]`\n\n"
                                + "• `+clean <amount>`\n\n"
                                + "• `+lock` / `+unlock`\n\n"
                                + "• `+slowmode <seconds>`\n\n"
                                + "• `+clearwarns <user> [amount]`\n\n"
                                + "• `+case delete <#id>` or `+casedelete <#id>`",
                        false);
    }
}
