package com.bot.commands;

import java.util.concurrent.TimeUnit;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModerationDmService;
import com.bot.moderation.ModLogService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class BanCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final ModerationDmService dmService = ModerationDmService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "ban".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("ban", "+ban <userId|@mention|username> [reason]", "+ban jesterskitt spam"))
                    .queue();
            return;
        }

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                args[0],
                        member -> {
                            ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(),
                                    "banned", reason);
                            event.getGuild().ban(member, 0, TimeUnit.SECONDS)
                                .reason(reason)
                                .queue(
                                        success -> {
                                            CaseRecord record = caseService.addCase(
                                                    CaseType.BAN,
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
                                                            "Ban",
                                                            member.getUser().getName() + " banned. Case #" + record.id() + suffix))
                                                    .queue();
                                        },
                                        failure -> event.getChannel().sendMessageEmbeds(
                                                CommandTemplateEmbeds.error(
                                                        "Ban",
                                                        "I couldn't ban that member. Make sure I have the **Ban Members** permission."))
                                                .queue());
                        },
                failure -> event.getChannel().sendMessageEmbeds(
                        CommandTemplateEmbeds.error(
                                "Ban",
                                failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                        ? "I couldn't find that member in this server."
                                        : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "ban".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("ban", "/ban target:<userId|@mention|username> [reason]", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target, member -> {
                ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(), "banned",
                        reason);
                event.getGuild().ban(member, 0, TimeUnit.SECONDS).reason(reason).queue(success -> {
                    CaseRecord record = caseService.addCase(
                            CaseType.BAN,
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
                                    "Ban",
                                    member.getUser().getName() + " banned. Case #" + record.id() + suffix))
                            .setEphemeral(true)
                            .queue();
                }, failure -> event.replyEmbeds(CommandTemplateEmbeds.error("Ban", "I couldn't ban that member."))
                        .setEphemeral(true)
                        .queue());
            },
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Ban",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }
}