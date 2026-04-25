package com.bot.commands;

import java.util.Locale;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class PostVerifyPrototypeCommand implements BotCommand {
    private static final String VERIFIED_ROLE_ID = "1495001735402618981";
    private static final String BUTTON_PREFIX = "verify:";
    private static final String LEGACY_BUTTON_PREFIX = "verifyproto:";

    @Override
    public boolean matches(String commandName) {
        return "postverify".equalsIgnoreCase(commandName)
                || "verifypost".equalsIgnoreCase(commandName)
                || "postverifyproto".equalsIgnoreCase(commandName)
                || "verifyprotopost".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!com.bot.moderation.AccessControlService.getInstance().isOwnerOrServerManager(event.getMember())) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "Only the owner or server manager can post the verification panel."))
                    .queue();
            return;
        }

        Role targetRole = event.getGuild().getRoleById(VERIFIED_ROLE_ID);
        if (targetRole == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "I couldn't find the configured Verified role (`" + VERIFIED_ROLE_ID + "`)."))
                    .queue();
            return;
        }

        EmbedBuilder panel = new EmbedBuilder()
                .setColor(new java.awt.Color(102, 2, 60))
                .setTitle("Verification")
                .setDescription("Click the button below to verify and unlock server access.\n"
                        + "You will receive **" + targetRole.getName() + "**.");

        event.getChannel().sendMessageEmbeds(panel.build())
                .setActionRow(Button.success(BUTTON_PREFIX + targetRole.getId(), "Verify"))
                .queue(
                        success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                "Verification",
                                "Verification panel posted."))
                                .queue(),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                "Verification",
                                "I couldn't post the verification panel in that channel."))
                                .queue());
    }

    public static boolean handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(BUTTON_PREFIX) && !id.startsWith(LEGACY_BUTTON_PREFIX)) {
            return false;
        }

        String roleId;
        if (id.startsWith(BUTTON_PREFIX)) {
            roleId = id.substring(BUTTON_PREFIX.length()).trim();
        } else {
            roleId = id.substring(LEGACY_BUTTON_PREFIX.length()).trim();
        }

        if (roleId.isBlank()) {
            roleId = VERIFIED_ROLE_ID;
        }

        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "The configured verification role no longer exists (`" + roleId + "`)."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        Member member = event.getMember();
        Member selfMember = event.getGuild().getSelfMember();
        if (member == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Verification", "Member context missing."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        if (!selfMember.hasPermission(Permission.MANAGE_ROLES)
                || !selfMember.canInteract(member)
                || !selfMember.canInteract(role)) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "I can't assign that role. Check my role hierarchy and permissions."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        boolean alreadyHas = member.getRoles().stream().anyMatch(existing -> existing.getId().equals(role.getId()));
        if (alreadyHas) {
            event.replyEmbeds(CommandTemplateEmbeds.info(
                    "Verification",
                    "You already have **" + role.getName() + "**."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        event.getGuild().addRoleToMember(member, role)
                .queue(
                        success -> event.replyEmbeds(CommandTemplateEmbeds.success(
                                "Verification",
                                "Verification complete. Role **" + role.getName() + "** added."))
                                .setEphemeral(true)
                                .queue(),
                        failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                                "Verification",
                                "I couldn't add that role. Check role hierarchy and permissions."))
                                .setEphemeral(true)
                                .queue());
        return true;
    }


    private Role resolveRole(net.dv8tion.jda.api.entities.Guild guild, String input) {
        String roleId = extractRoleId(input);
        if (roleId != null) {
            return guild.getRoleById(roleId);
        }

        String loweredInput = input.trim().toLowerCase(Locale.ROOT);

        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase(Locale.ROOT).equals(loweredInput)) {
                return role;
            }
        }

        for (Role role : guild.getRoles()) {
            if (role.getName().toLowerCase(Locale.ROOT).contains(loweredInput)) {
                return role;
            }
        }

        return null;
    }

    private String extractRoleId(String rawInput) {
        if (rawInput == null) {
            return null;
        }

        String input = rawInput.trim();
        if (input.isEmpty()) {
            return null;
        }

        if (input.startsWith("<@&") && input.endsWith(">")) {
            String mention = input.substring(3, input.length() - 1);
            if (mention.matches("\\d+")) {
                return mention;
            }
        }

        if (input.matches("\\d+")) {
            return input;
        }

        return null;
    }
}

