package com.bot.commands;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.bot.moderation.AccessControlService;
import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class CaseCommand implements BotCommand, SlashCommandHandler {
    private static final DateTimeFormatter CASE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final CaseService caseService = CaseService.getInstance();
    private final AccessControlService accessControlService = AccessControlService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "case".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length == 2 && "delete".equalsIgnoreCase(args[0])) {
            handleDeleteCase(event, args[1]);
            return;
        }

        if (args.length != 1) {
            event.getChannel().sendMessage("Usage: +case <#id> or +case delete <#id>").queue();
            return;
        }

        long caseId = parseCaseId(args[0]);
        if (caseId <= 0) {
            event.getChannel().sendMessage("Case ID must be like +case #12").queue();
            return;
        }

        CaseRecord record = caseService.getCase(caseId);
        if (record == null || !record.guildId().equals(event.getGuild().getId())) {
            event.getChannel().sendMessage("That case was not found.").queue();
            return;
        }

        event.getChannel().sendMessage(formatCase(record)).queue();
    }

    private void handleDeleteCase(MessageReceivedEvent event, String rawCaseId) {
        if (!accessControlService.canBan(event.getMember())) {
            event.getChannel().sendMessage("Only admin+ can delete cases.").queue();
            return;
        }

        long caseId = parseCaseId(rawCaseId);
        if (caseId <= 0) {
            event.getChannel().sendMessage("Case ID must be like +case delete #12").queue();
            return;
        }

        CaseRecord removed = caseService.deleteCase(event.getGuild().getId(), caseId);
        if (removed == null) {
            event.getChannel().sendMessage("That case was not found.").queue();
            return;
        }

        event.getChannel().sendMessage("Case #" + removed.id() + " deleted.").queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "case".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        if (event.getOption("id") == null) {
            event.reply("id is required.").setEphemeral(true).queue();
            return;
        }

        long caseId = event.getOption("id").getAsLong();
        if (caseId <= 0) {
            event.reply("id is required.").setEphemeral(true).queue();
            return;
        }

        CaseRecord record = caseService.getCase(caseId);
        if (record == null || !record.guildId().equals(event.getGuild().getId())) {
            event.reply("That case was not found.").setEphemeral(true).queue();
            return;
        }

        event.reply(formatCase(record)).setEphemeral(true).queue();
    }

    private long parseCaseId(String raw) {
        String cleaned = raw.trim();
        if (cleaned.startsWith("#")) {
            cleaned = cleaned.substring(1);
        }

        try {
            return Long.parseLong(cleaned);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String formatCase(CaseRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append("Case #").append(record.id())
                .append("\nType: ").append(record.type())
                .append("\nTarget: ").append(record.targetName() == null ? "n/a" : record.targetName());

        if (record.targetId() != null) {
            builder.append(" (`").append(record.targetId()).append("`)");
        }

        builder.append("\nModerator: ").append(record.moderatorName())
                .append(" (`").append(record.moderatorId()).append("`)")
                .append("\nReason: ").append(record.reason())
                .append("\nCreated: ").append(CASE_TIME.format(record.createdAt()));

        if (record.extra() != null && !record.extra().isBlank()) {
            builder.append("\nDetails: ").append(record.extra());
        }

        return builder.toString();
    }
}


