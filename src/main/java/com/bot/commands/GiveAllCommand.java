package com.bot.commands;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import com.bot.moderation.AccessControlService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class GiveAllCommand implements BotCommand {
    @Override
    public boolean matches(String commandName) {
        return "giveall".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!AccessControlService.getInstance().isBotOwner(event.getMember())) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "GiveAll",
                    "Only the bot owner can use this command."))
                    .queue();
            return;
        }

        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "giveall",
                    "+giveall <role-id|@role|role-name>",
                    "+giveall Giveaway Ping"))
                    .queue();
            return;
        }

        String roleInput = String.join(" ", args).trim();
        Role role = resolveRole(event.getGuild(), roleInput);
        if (role == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "GiveAll",
                    "I couldn't find that role."))
                    .queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        if (!selfMember.canInteract(role)) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "GiveAll",
                    "I can't assign that role because it is above my highest role."))
                    .queue();
            return;
        }

        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.info(
                "GiveAll",
                "Applying role **" + role.getName() + "** to guild members..."))
                .queue();

        event.getGuild().loadMembers().onSuccess(members -> applyRoleToMembers(event, role, members))
                .onError(throwable -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "GiveAll",
                        "I couldn't load guild members right now."))
                        .queue());
    }

    private void applyRoleToMembers(MessageReceivedEvent event, Role role, java.util.List<Member> members) {
        if (members.isEmpty()) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.info("GiveAll", "No members found."))
                    .queue();
            return;
        }

        Member selfMember = event.getGuild().getSelfMember();
        AtomicInteger addedCount = new AtomicInteger();
        AtomicInteger skippedCount = new AtomicInteger();
        AtomicInteger failedCount = new AtomicInteger();
        AtomicInteger finishedCount = new AtomicInteger();

        for (Member member : members) {
            if (member.getUser().isBot()
                    || member.getRoles().stream().anyMatch(existing -> existing.getId().equals(role.getId()))
                    || !selfMember.canInteract(member)) {
                skippedCount.incrementAndGet();
                if (finishedCount.incrementAndGet() == members.size()) {
                    sendSummary(event, role, addedCount.get(), skippedCount.get(), failedCount.get());
                }
                continue;
            }

            event.getGuild().addRoleToMember(member, role).queue(
                    success -> {
                        addedCount.incrementAndGet();
                        if (finishedCount.incrementAndGet() == members.size()) {
                            sendSummary(event, role, addedCount.get(), skippedCount.get(), failedCount.get());
                        }
                    },
                    failure -> {
                        failedCount.incrementAndGet();
                        if (finishedCount.incrementAndGet() == members.size()) {
                            sendSummary(event, role, addedCount.get(), skippedCount.get(), failedCount.get());
                        }
                    });
        }
    }

    private void sendSummary(MessageReceivedEvent event, Role role, int added, int skipped, int failed) {
        String description = "Finished assigning **" + role.getName() + "**.\n"
                + "Added: `" + added + "`\n"
                + "Skipped: `" + skipped + "`\n"
                + "Failed: `" + failed + "`";
        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success("GiveAll", description)).queue();
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

