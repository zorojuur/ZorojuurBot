package com.bot.commands;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public abstract class StaffRoleGrantCommand implements BotCommand {
    private static final String DEFAULT_STAFF_ROLE_ID = "1496542503589908603";

    private final String commandName;
    private final String roleId;
    private final String roleLabel;

    protected StaffRoleGrantCommand(String commandName, String roleId, String roleLabel) {
        this.commandName = commandName;
        this.roleId = roleId;
        this.roleLabel = roleLabel;
    }

    @Override
    public boolean matches(String incomingCommand) {
        return commandName.equalsIgnoreCase(incomingCommand);
    }

    @Override
    public void execute(MessageReceivedEvent event, String ignored, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage(commandName, "+" + commandName + " <userId|@mention|username>",
                            "+" + commandName + " jesterskitt"))
                    .queue();
            return;
        }

        Role targetRole = event.getGuild().getRoleById(roleId);
        Role defaultStaffRole = event.getGuild().getRoleById(DEFAULT_STAFF_ROLE_ID);
        if (targetRole == null || defaultStaffRole == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    roleLabel,
                    "One or more required roles were not found. Check role IDs in bot config."))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                args[0],
                member -> grantRoles(event, member, targetRole, defaultStaffRole),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        roleLabel,
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    private void grantRoles(MessageReceivedEvent event, Member member, Role targetRole, Role defaultStaffRole) {
        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.canInteract(member)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    roleLabel,
                    "I can't manage that member because they are above my highest role."))
                    .queue();
            return;
        }

        if (!selfMember.canInteract(targetRole) || !selfMember.canInteract(defaultStaffRole)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    roleLabel,
                    "I can't assign one or more roles because they are above my highest role."))
                    .queue();
            return;
        }

        List<Role> toAdd = new ArrayList<>();
        if (member.getRoles().stream().noneMatch(role -> role.getId().equals(targetRole.getId()))) {
            toAdd.add(targetRole);
        }
        if (member.getRoles().stream().noneMatch(role -> role.getId().equals(defaultStaffRole.getId()))) {
            toAdd.add(defaultStaffRole);
        }

        if (toAdd.isEmpty()) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.info(
                    roleLabel,
                    member.getUser().getAsTag() + " already has the required roles."))
                    .queue();
            return;
        }

        event.getGuild().modifyMemberRoles(member, toAdd, List.of())
                .queue(
                        success -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success(
                                roleLabel,
                                "Updated staff roles for " + member.getUser().getAsTag() + "."))
                                .queue(),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                roleLabel,
                                "I couldn't assign roles. Check role hierarchy and permissions."))
                                .queue());
    }
}

