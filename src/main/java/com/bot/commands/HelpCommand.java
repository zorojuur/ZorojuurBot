package com.bot.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class HelpCommand implements BotCommand, SlashCommandHandler {
    @Override
    public boolean matches(String commandName) {
        return "help".equalsIgnoreCase(commandName) || "commands".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.info("Help", buildHelpText())).queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "help".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        event.replyEmbeds(CommandTemplateEmbeds.info("Help", buildHelpText())).setEphemeral(true).queue();
    }

    private String buildHelpText() {
        return "Commands:\n"
                + "+help, +commands - show this command list\n"
                + "+an <on|off|status|list> - anti-nuke toggle/status/whitelist list\n"
                + "+whitelist <user> - toggle anti-nuke whitelist (owner only)\n"
                + "+gamerules - repost guess game rules (owner only)\n"
                + "+game <start|status|stop> - manage guess game\n"
                + "+<number> - submit a guess\n"
                + "+mute <user> [reason] - mute a user\n"
                + "+unmute <user> [reason] - unmute a user\n"
                + "+warn <user> [reason] - warn a user\n"
                + "+kick <user> [reason] - kick a user\n"
                + "+ban <user> [reason] - ban a user\n"
                + "+unban <user> [reason] - unban a user\n"
                + "+modlogs <user> - show user moderation logs\n"
                + "+case <id> - view case\n"
                + "+casedelete <id> - delete case\n"
                + "+reason <id> <reason> - update case reason\n"
                + "+clearwarns <user> [amount] - clear warns\n"
                + "+purge <amount> [reason] - bulk delete messages\n"
                + "+clean <amount> - clean bot/user command messages\n"
                + "+dm <user> <message> - send DM through bot\n"
                + "+role <role> <user> - toggle a role on one user\n"
                + "+giveall <role> - give a role to all members\n"
                + "+postverify - post verification button panel\n"
                + "+w <user> - whois info\n"
                + "+postreactionroles - post reaction-role panel\n"
                + "+deletereactionroles - delete reaction-role panel\n"
                + "+roleinfo - post role info message\n"
                + "+postrules - post server rules message\n"
                + "+tmod <user> - assign Trial Moderator + Staff\n"
                + "+mod <user> - assign Moderator + Staff\n"
                + "+smod <user> - assign Senior Moderator + Staff\n"
                + "+hmod <user> - assign Head Moderator + Staff\n"
                + "+manager <user> - assign Server Manager + Staff";
    }
}
