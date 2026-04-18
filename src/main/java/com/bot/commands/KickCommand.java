package com.bot.commands;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModerationDmService;
import com.bot.moderation.ModLogService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class KickCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final ModerationDmService dmService = ModerationDmService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "kick".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("kick", "+kick <userId|@mention|username> [reason]", "+kick jesterskitt spam"))
                    .queue();
            return;
        }

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                args[0],
                        member -> {
                            ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(),
                                    "kicked", reason);
                            event.getGuild().kick(member)
                                .reason(reason)
                                .queue(
                                        success -> {
                                            CaseRecord record = caseService.addCase(
                                                    CaseType.KICK,
                                                    event.getGuild().getId(),
                                                    event.getChannel().getId(),
                                                    member.getId(),
                                                    member.getUser().getAsTag(),
                                                    event.getAuthor().getId(),
                                                    event.getAuthor().getAsTag(),
                                                    reason,
                                                    null);
                                            modLogService.logCase(event.getGuild(), record);
                                            String suffix = dmService.deliverySuffix(dmStatus);
                                            event.getChannel().sendMessageEmbeds(
                                                    CommandTemplateEmbeds.success(
                                                            "Kick",
                                                            member.getUser().getName() + " kicked. Case #" + record.id() + suffix))
                                                    .queue();
                                        },
                                        failure -> event.getChannel().sendMessageEmbeds(
                                                CommandTemplateEmbeds.error(
                                                        "Kick",
                                                        "I couldn't kick that member. Make sure I have the **Kick Members** permission."))
                                                .queue());
                        },
                failure -> event.getChannel().sendMessageEmbeds(
                        CommandTemplateEmbeds.error(
                                "Kick",
                                failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                        ? "I couldn't find that member in this server."
                                        : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "kick".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("kick", "/kick target:<userId|@mention|username> [reason]", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target, member -> {
                ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(), "kicked",
                        reason);
                event.getGuild().kick(member).reason(reason).queue(success -> {
                    CaseRecord record = caseService.addCase(
                            CaseType.KICK,
                            event.getGuild().getId(),
                            event.getChannel().getId(),
                            member.getId(),
                            member.getUser().getAsTag(),
                            event.getUser().getId(),
                            event.getUser().getAsTag(),
                            reason,
                            null);
                    modLogService.logCase(event.getGuild(), record);
                    String suffix = dmService.deliverySuffix(dmStatus);
                    event.replyEmbeds(
                            CommandTemplateEmbeds.success(
                                    "Kick",
                                    member.getUser().getName() + " kicked. Case #" + record.id() + suffix))
                            .setEphemeral(true)
                            .queue();
                }, failure -> event.replyEmbeds(CommandTemplateEmbeds.error("Kick", "I couldn't kick that member."))
                        .setEphemeral(true)
                        .queue());
            },
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Kick",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }
}