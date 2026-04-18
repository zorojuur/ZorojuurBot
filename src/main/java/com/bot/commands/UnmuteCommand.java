package com.bot.commands;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModLogService;
import com.bot.moderation.MutedRoleService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class UnmuteCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final MutedRoleService mutedRoleService = MutedRoleService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "unmute".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("unmute", "+unmute <userId|@mention|username> [reason]", "+unmute jesterskitt"))
                    .queue();
            return;
        }

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        TargetResolver.resolveMemberAsync(event.getGuild(), args[0],
                member -> mutedRoleService.applyUnmute(member, () -> {
                    CaseRecord record = caseService.addCase(
                            CaseType.UNMUTE,
                            event.getGuild().getId(),
                            event.getChannel().getId(),
                            member.getId(),
                            member.getUser().getAsTag(),
                            event.getAuthor().getId(),
                            event.getAuthor().getAsTag(),
                            reason,
                            "Muted role: " + mutedRoleService.mutedRoleId(event.getGuild()));
                    modLogService.logCase(event.getGuild(), record);
                    event.getChannel()
                            .sendMessage(member.getUser().getName() + " unmuted. Case #" + record.id()).queue();
                }, failure -> event.getChannel().sendMessage(failure).queue()),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Unmute",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "unmute".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("unmute", "/unmute target:<userId|@mention|username> [reason]", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target,
                        member -> mutedRoleService.applyUnmute(member, () -> {
                            CaseRecord record = caseService.addCase(
                                    CaseType.UNMUTE,
                                    event.getGuild().getId(),
                                    event.getChannel().getId(),
                                    member.getId(),
                                    member.getUser().getAsTag(),
                                    event.getUser().getId(),
                                    event.getUser().getAsTag(),
                                    reason,
                                    "Muted role: " + mutedRoleService.mutedRoleId(event.getGuild()));
                            modLogService.logCase(event.getGuild(), record);
                            event.reply(member.getUser().getName() + " unmuted. Case #" + record.id())
                                    .setEphemeral(true).queue();
                        }, failure -> event.reply(failure).setEphemeral(true).queue()),
                        failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                                "Unmute",
                                failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                        ? "I couldn't find that member in this server."
                                        : "I couldn't look up that member right now."))
                                .setEphemeral(true)
                                .queue());
    }
}