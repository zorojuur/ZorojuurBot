package com.bot.commands;

import java.util.Locale;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

public class PostVerifyPrototypeCommand implements BotCommand {
    private static final String VERIFY_CHANNEL_NAME = "verify";
    private static final String LEGACY_VERIFY_CHANNEL_NAME = "verify-prototype";
    private static final String VERIFY_TOPIC_MARKER = "[zoro-verify]";
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
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "postverify",
                    "+postverify <role-id|@role|role-name>",
                    "+postverify Verified"))
                    .queue();
            return;
        }

        Role targetRole = resolveRole(event.getGuild(), String.join(" ", args));
        if (targetRole == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "I couldn't find that role."))
                    .queue();
            return;
        }

        TextChannel prototypeChannel = findPrototypeChannel(event.getGuild());
        if (prototypeChannel == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "Verify channel not found. Run `+setupverify` first."))
                    .queue();
            return;
        }

        EmbedBuilder panel = new EmbedBuilder()
                .setColor(new java.awt.Color(102, 2, 60))
                .setTitle("Verification")
                .setDescription("Click the button below to verify and unlock server access.\n"
                        + "You will receive **" + targetRole.getName() + "**.");

        prototypeChannel.sendMessageEmbeds(panel.build())
                .setActionRow(Button.success(BUTTON_PREFIX + targetRole.getId(), "Verify"))
                .queue(
                        success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                "Verification",
                                "Verification panel posted in " + prototypeChannel.getAsMention() + "."))
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

        if (!(event.getChannel() instanceof TextChannel textChannel)
                || !isVerifyChannel(textChannel)) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "This button only works in the verify channel."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        String roleId;
        if (id.startsWith(BUTTON_PREFIX)) {
            roleId = id.substring(BUTTON_PREFIX.length()).trim();
        } else {
            roleId = id.substring(LEGACY_BUTTON_PREFIX.length()).trim();
        }
        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "The configured verification role no longer exists."))
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

    private TextChannel findPrototypeChannel(net.dv8tion.jda.api.entities.Guild guild) {
        for (TextChannel channel : guild.getTextChannels()) {
            if (isVerifyChannel(channel)) {
                return channel;
            }
        }
        return null;
    }

    private static boolean isVerifyChannel(TextChannel channel) {
        String topic = channel.getTopic();
        if (topic != null && topic.contains(VERIFY_TOPIC_MARKER)) {
            return true;
        }

        return channel.getName().equalsIgnoreCase(VERIFY_CHANNEL_NAME)
                || channel.getName().equalsIgnoreCase(LEGACY_VERIFY_CHANNEL_NAME);
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

