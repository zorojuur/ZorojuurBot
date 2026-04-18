package com.bot.commands;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModerationDmService;
import com.bot.moderation.ModLogService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class WarnCommand implements BotCommand, SlashCommandHandler {
    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final ModerationDmService dmService = ModerationDmService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "warn".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("warn", "+warn <userId|@mention|username> [reason]", "+warn jesterskitt spam"))
                    .queue();
            return;
        }

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        TargetResolver.resolveMemberAsync(event.getGuild(), args[0],
                member -> saveWarnCase(event, member, reason),
                failure -> {
                    if (failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND) {
                        event.getChannel().sendMessageEmbeds(
                                CommandTemplateEmbeds.error("Warn", "I couldn't find that member in this server."))
                                .queue();
                        return;
                    }
                    event.getChannel().sendMessageEmbeds(
                            CommandTemplateEmbeds.error("Warn", "I couldn't look up that member right now."))
                            .queue();
                });
    }

    private void saveWarnCase(MessageReceivedEvent event, Member member, String reason) {
        CaseRecord record = caseService.addCase(
                CaseType.WARN,
                event.getGuild().getId(),
                event.getChannel().getId(),
                member.getId(),
                member.getUser().getAsTag(),
                event.getAuthor().getId(),
                event.getAuthor().getAsTag(),
                reason,
                null);
        modLogService.logCase(event.getGuild(), record);
        ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(), "warned", reason);
        String suffix = dmService.deliverySuffix(dmStatus);
        event.getChannel().sendMessageEmbeds(
                CommandTemplateEmbeds.success(
                        "Warn",
                        member.getUser().getName() + " warned. Case #" + record.id() + suffix))
                .queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "warn".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Warn", "target is required.")).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        TargetResolver.resolveMemberAsync(event.getGuild(), target,
                member -> {
                    CaseRecord record = caseService.addCase(
                            CaseType.WARN,
                            event.getGuild().getId(),
                            event.getChannel().getId(),
                            member.getId(),
                            member.getUser().getAsTag(),
                            event.getUser().getId(),
                            event.getUser().getAsTag(),
                            reason,
                            null);
                    modLogService.logCase(event.getGuild(), record);
                    ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(), "warned",
                            reason);
                    String suffix = dmService.deliverySuffix(dmStatus);
                    event.replyEmbeds(
                            CommandTemplateEmbeds.success(
                                    "Warn",
                                    member.getUser().getName() + " warned. Case #" + record.id() + suffix))
                            .setEphemeral(true)
                            .queue();
                },
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Warn",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                    .setEphemeral(true)
                        .queue());
    }
}

