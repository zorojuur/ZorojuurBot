package com.bot.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class TmodCommand implements BotCommand {
    private static final String TMOD_ROLE_ID = "1475422357039480963";

    @Override
    public boolean matches(String commandName) {
        return "tmod".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("tmod", "+tmod <userId|@mention|username>", "+tmod jesterskitt"))
                    .queue();
            return;
        }

        Role tmodRole = event.getGuild().getRoleById(TMOD_ROLE_ID);
        if (tmodRole == null) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.error("Trial Mod", "Trial Moderator role was not found."))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                args[0],
                member -> grantRole(event, member, tmodRole),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Trial Mod",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    private void grantRole(MessageReceivedEvent event, Member member, Role role) {
        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.canInteract(member)) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.error("Trial Mod",
                            "I can't manage that member because they are above my highest role."))
                    .queue();
            return;
        }

        if (!selfMember.canInteract(role)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Trial Mod",
                    "I can't assign the Trial Moderator role because it is above my highest role."))
                    .queue();
            return;
        }

        boolean alreadyHasRole = member.getRoles().stream().anyMatch(existing -> existing.getId().equals(role.getId()));
        if (alreadyHasRole) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.info("Trial Mod", member.getUser().getAsTag() + " already has Trial Moderator."))
                    .queue();
            return;
        }

        event.getGuild().addRoleToMember(member, role).queue(
                success -> event.getChannel().sendMessageEmbeds(
                        CommandTemplateEmbeds.success("Trial Mod",
                                "Added Trial Moderator to " + member.getUser().getAsTag() + "."))
                        .queue(),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Trial Mod",
                        "I couldn't assign Trial Moderator. Check role hierarchy and permissions."))
                        .queue());
    }
}

