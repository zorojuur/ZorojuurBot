package com.bot.commands;

import java.awt.Color;
import java.util.Collections;
import java.util.Set;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PostRoleInfoCommand implements BotCommand {
    private static final Set<String> TARGET_CHANNEL_IDS = Set.of(
            "1475416877055479971",
            "1475426654103932989");

    @Override
    public boolean matches(String commandName) {
        return "roleinfo".equalsIgnoreCase(commandName) || "roletemplate".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!TARGET_CHANNEL_IDS.contains(event.getChannel().getId())) {
            event.getChannel().sendMessage("Use this command in the role info channel.").queue();
            return;
        }

        Guild guild = event.getGuild();

        EmbedBuilder staffEmbed = new EmbedBuilder()
                .setTitle("Role Information")
                .setColor(new Color(102, 2, 60))
                .addField("👑 Staff Roles", buildStaffRoles(guild), false);

        EmbedBuilder pingEmbed = new EmbedBuilder()
                .setColor(new Color(102, 2, 60))
                .addField("🔔 Ping Roles", buildPingRoles(guild), false);

        EmbedBuilder levelEmbed = new EmbedBuilder()
                .setColor(new Color(102, 2, 60))
                .addField("⭐ Level Roles", buildLevelRoles(guild), false);

        event.getChannel().sendMessageEmbeds(staffEmbed.build())
                .setAllowedMentions(Collections.emptyList())
                .queue();
        event.getChannel().sendMessageEmbeds(pingEmbed.build())
                .setAllowedMentions(Collections.emptyList())
                .queue();
        event.getChannel().sendMessageEmbeds(levelEmbed.build())
                .setAllowedMentions(Collections.emptyList())
                .queue();
    }

    private String buildStaffRoles(Guild guild) {
        return "**" + mentionRoleById(guild, "1496537304880255198", "Server Manager") + " -- Server Manager**\n"
                + "Full bot access and staff management permissions.\n\n"
                + "**" + mentionRoleById(guild, "1496542241903349821", "Head Moderator") + " -- Head Moderator**\n"
                + "Leads moderation and handles major moderation actions.\n\n"
                + "**" + mentionRoleById(guild, "1496542172848197783", "Senior Moderator") + " -- Senior Moderator**\n"
                + "Experienced moderation team role.\n\n"
                + "**" + mentionRoleById(guild, "1496542109619064942", "Moderator") + " -- Moderator**\n"
                + "Core moderation team role.\n\n"
                + "**" + mentionRoleById(guild, "1496541997488672879", "Trial Moderator") + " -- Trial Moderator**\n"
                + "Entry moderation role with limited commands.\n\n"
                + "**" + mentionRoleById(guild, "1496542503589908603", "Staff") + " -- Staff**\n"
                + "Default staff role added to all promoted staff members.";
    }

    private String buildPingRoles(Guild guild) {
        return "Get ping roles here: https://discord.com/channels/1475416128942772244/1475417253834133564\n\n"
                + "**" + mentionRole(guild, "Poll Ping") + " -- Poll Ping**\n"
                + "Get pinged when a new poll is posted.\n\n"
                + "**" + mentionRole(guild, "Giveaway Ping") + " -- Giveaway Ping**\n"
                + "Get pinged when a giveaway is hosted.\n\n"
                + "**" + mentionRole(guild, "Event Ping") + " -- Event Ping**\n"
                + "Get pinged when an event is hosted.";
    }

    private String buildLevelRoles(Guild guild) {
        return "**" + mentionRole(guild, "Level 1") + " -- Level 1**\n"
                + "No abilities yet.\n\n"
                + "**" + mentionRole(guild, "Level 5") + " -- Level 5**\n"
                + "No abilities yet.\n\n"
                + "**" + mentionRole(guild, "Level 10") + " -- Media Perms**\n"
                + "Unlocks the ability to send images and GIFs.\n\n"
                + "**" + mentionRole(guild, "Level 15") + " -- Level 15**\n"
                + "Access to images, GIFs, and nickname changes.\n\n"
                + "**" + mentionRole(guild, "Level 25") + " -- Level 25**\n"
                + "Access to images, GIFs, and nickname changes.\n\n"
                + "**" + mentionRole(guild, "Level 50") + " -- Level 50**\n"
                + "Access to all commands from previous levels.\n\n"
                + "**" + mentionRole(guild, "Level 100") + " -- Level 100**\n"
                + "Access to all commands from previous levels.";
    }

    private String mentionRole(Guild guild, String roleName) {
        for (Role role : guild.getRoles()) {
            if (role.getName().equalsIgnoreCase(roleName)) {
                return role.getAsMention();
            }
        }
        return "@" + roleName;
    }

    private String mentionRoleById(Guild guild, String roleId, String fallbackName) {
        Role role = guild.getRoleById(roleId);
        return role == null ? "@" + fallbackName : role.getAsMention();
    }
}