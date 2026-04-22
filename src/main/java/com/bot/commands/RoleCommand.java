package com.bot.commands;

import java.util.Locale;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class RoleCommand implements BotCommand, SlashCommandHandler {
    @Override
    public boolean matches(String commandName) {
        return "role".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!com.bot.moderation.AccessControlService.getInstance().isOwnerOrServerManager(event.getMember())) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                "Role",
                "Only the owner or server manager can use this command.")).queue();
            return;
        }
        if (args.length < 2) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "role",
                    "+role <role-id|@role|role-name> <user-id|@mention|username>",
                    "+role helper jesterskitt"))
                    .queue();
            return;
        }

        String roleInput = args[0];
        String userInput = args[1];

        Role role = resolveRole(event.getGuild(), roleInput);
        if (role == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Role", "I couldn't find that role."))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), userInput,
                member -> assignRole(event.getGuild().getSelfMember(), role, member,
                        removed -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                "Role",
                                (removed ? "Removed role " : "Added role ") + role.getName() + " "
                                        + (removed ? "from " : "to ") + member.getUser().getAsTag() + "."))
                                .queue(),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Role", failure)).queue()),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Role",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "role".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (!com.bot.moderation.AccessControlService.getInstance().isOwnerOrServerManager(event.getMember())) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                "Role",
                "Only the owner or server manager can use this command.")).setEphemeral(true).queue();
            return;
        }
        String roleInput = event.getOption("role", "", option -> option.getAsString());
        String userInput = event.getOption("target", "", option -> option.getAsString());

        if (roleInput.isBlank() || userInput.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage(
                    "role",
                    "/role role:<role-id|@role|role-name> target:<user-id|@mention|username>",
                    null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Role role = resolveRole(event.getGuild(), roleInput);
        if (role == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Role", "I couldn't find that role."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), userInput,
                member -> assignRole(event.getGuild().getSelfMember(), role, member,
                        removed -> event.replyEmbeds(CommandTemplateEmbeds.success(
                                "Role",
                                (removed ? "Removed role " : "Added role ") + role.getName() + " "
                                        + (removed ? "from " : "to ") + member.getUser().getAsTag() + "."))
                                .setEphemeral(true)
                                .queue(),
                        failure -> event.replyEmbeds(CommandTemplateEmbeds.error("Role", failure)).setEphemeral(true).queue()),
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Role",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }

    private void assignRole(Member selfMember, Role role, Member member, java.util.function.Consumer<Boolean> onSuccess,
            java.util.function.Consumer<String> onFailure) {
        if (!selfMember.canInteract(member)) {
            onFailure.accept("I can't manage that member because they are above my highest role.");
            return;
        }

        if (!selfMember.canInteract(role)) {
            onFailure.accept("I can't assign that role because it is above my highest role.");
            return;
        }

        boolean alreadyHasRole = member.getRoles().stream().anyMatch(existing -> existing.getId().equals(role.getId()));
        if (alreadyHasRole) {
            member.getGuild().removeRoleFromMember(member, role)
                    .queue(success -> onSuccess.accept(true),
                            failure -> onFailure
                                    .accept("I couldn't remove that role. Check role hierarchy and permissions."));
            return;
        }

        member.getGuild().addRoleToMember(member, role)
                .queue(success -> onSuccess.accept(false),
                        failure -> onFailure
                                .accept("I couldn't assign that role. Check role hierarchy and permissions."));
    }

    private Role resolveRole(net.dv8tion.jda.api.entities.Guild guild, String input) {
        String roleId = extractRoleId(input);
        if (roleId != null) {
            return guild.getRoleById(roleId);
        }

        String loweredInput = input.trim().toLowerCase(Locale.ROOT);

        for (Role role : guild.getRoles()) {
            String roleName = role.getName().toLowerCase(Locale.ROOT).trim();
            String firstWord = roleName.contains(" ") ? roleName.substring(0, roleName.indexOf(' ')) : roleName;
            if (firstWord.equals(loweredInput)) {
                return role;
            }
        }

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
