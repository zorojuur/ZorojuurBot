package com.bot.commands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.CaseType;
import com.bot.moderation.ModerationDmService;
import com.bot.moderation.ModLogService;
import com.bot.moderation.MutedRoleService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class MuteCommand implements BotCommand, SlashCommandHandler {
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^(\\d+)\\s*(m|min|mins|minute|minutes|h|hr|hrs|hour|hours|d|day|days|w|week|weeks|mo|month|months)$",
            Pattern.CASE_INSENSITIVE);

    private final CaseService caseService = CaseService.getInstance();
    private final ModLogService modLogService = ModLogService.getInstance();
    private final ModerationDmService dmService = ModerationDmService.getInstance();
    private final MutedRoleService mutedRoleService = MutedRoleService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "mute".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("mute", "+mute <userId|@mention|username> [reason]", "+mute jesterskitt spam"))
                    .queue();
            return;
        }

        ParsedMuteInput parsed = parseMuteInput(args, 1);
        if (parsed.errorMessage != null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error("Mute", parsed.errorMessage)).queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), args[0],
                member -> muteMember(event, member, parsed.reason, parsed.muteUntilEpochMs),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Mute",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now. Please try again in a moment."))
                        .queue());
    }

    private void muteMember(MessageReceivedEvent event, Member member, String reason, Long muteUntilEpochMs) {
        mutedRoleService.applyMute(
                member,
                muteUntilEpochMs,
                () -> {
                    String durationSuffix = muteUntilEpochMs == null ? "" : " | Duration: timed";
                    CaseRecord record = caseService.addCase(
                            CaseType.MUTE,
                            event.getGuild().getId(),
                            event.getChannel().getId(),
                            member.getId(),
                            member.getUser().getAsTag(),
                            event.getAuthor().getId(),
                            event.getAuthor().getAsTag(),
                            reason,
                            "Muted role: " + mutedRoleService.mutedRoleId(event.getGuild()) + durationSuffix);
                    modLogService.logCase(event.getGuild(), record);
                    ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(),
                            "muted", reason);
                    String suffix = dmService.deliverySuffix(dmStatus);
                    event.getChannel().sendMessage(member.getUser().getName() + " muted. Case #" + record.id() + suffix)
                            .queue();
                },
                failureMessage -> event.getChannel().sendMessage(failureMessage).queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "mute".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        ParsedMuteInput parsed = parseMuteInput(new String[] { reason }, 0);

        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("mute", "/mute target:<userId|@mention|username> [reason]", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (parsed.errorMessage != null) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mute", parsed.errorMessage)).setEphemeral(true).queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target, member -> mutedRoleService.applyMute(member, parsed.muteUntilEpochMs, () -> {
            String durationSuffix = parsed.muteUntilEpochMs == null ? "" : " | Duration: timed";
            CaseRecord record = caseService.addCase(
                    CaseType.MUTE,
                    event.getGuild().getId(),
                    event.getChannel().getId(),
                    member.getId(),
                    member.getUser().getAsTag(),
                    event.getUser().getId(),
                    event.getUser().getAsTag(),
                    parsed.reason,
                    "Muted role: " + mutedRoleService.mutedRoleId(event.getGuild()) + durationSuffix);
            modLogService.logCase(event.getGuild(), record);
            ModerationDmService.DmDeliveryStatus dmStatus = dmService.sendActionDm(member.getUser(), "muted",
                    parsed.reason);
            String suffix = dmService.deliverySuffix(dmStatus);
            event.reply(member.getUser().getName() + " muted. Case #" + record.id() + suffix).setEphemeral(true)
                    .queue();
        }, failure -> event.reply(failure).setEphemeral(true).queue()),
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Mute",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }

    private ParsedMuteInput parseMuteInput(String[] args, int reasonStartIndex) {
        String raw = CommandTextUtil.joinArgs(args, reasonStartIndex, "No reason provided.").trim();
        if (raw.isEmpty()) {
            return new ParsedMuteInput("No reason provided.", null, null);
        }

        String[] parts = raw.split("\\s+", 2);
        Matcher matcher = DURATION_PATTERN.matcher(parts[0]);
        if (!matcher.matches()) {
            return new ParsedMuteInput(raw, null, null);
        }

        long value;
        try {
            value = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ex) {
            return new ParsedMuteInput(raw, null, "Invalid mute duration value.");
        }

        if (value <= 0) {
            return new ParsedMuteInput(raw, null, "Mute duration must be greater than 0.");
        }

        String unit = matcher.group(2).toLowerCase();
        long multiplierMs;
        if (unit.startsWith("m") && !unit.startsWith("mo")) {
            multiplierMs = 60_000L;
        } else if (unit.startsWith("h")) {
            multiplierMs = 3_600_000L;
        } else if (unit.startsWith("d")) {
            multiplierMs = 86_400_000L;
        } else if (unit.startsWith("w")) {
            multiplierMs = 604_800_000L;
        } else {
            multiplierMs = 2_592_000_000L; // 30 days
        }

        long durationMs;
        try {
            durationMs = Math.multiplyExact(value, multiplierMs);
        } catch (ArithmeticException ex) {
            return new ParsedMuteInput(raw, null, "Mute duration is too large.");
        }

        String cleanReason = parts.length > 1 ? parts[1].trim() : "No reason provided.";
        if (cleanReason.isEmpty()) {
            cleanReason = "No reason provided.";
        }

        return new ParsedMuteInput(cleanReason, System.currentTimeMillis() + durationMs, null);
    }

    private record ParsedMuteInput(String reason, Long muteUntilEpochMs, String errorMessage) {
    }
}