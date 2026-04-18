package com.bot.commands;

import java.util.List;
import java.util.Locale;

import com.bot.moderation.CaseRecord;
import com.bot.moderation.CaseService;
import com.bot.moderation.AccessControlService;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

public class ModLogsCommand implements BotCommand, SlashCommandHandler {
    private static final String OPEN_DELETE_PREFIX = "modlogs:open:";
    private static final String SELECT_DELETE_PREFIX = "modlogs:delete:";
    private static final int MAX_MENU_OPTIONS = 25;

    private final CaseService caseService = CaseService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "modlogs".equalsIgnoreCase(commandName) || "logs".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length != 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("modlogs", "+modlogs <userId|@mention|username>", "+modlogs jesterskitt"))
                    .queue();
            return;
        }

        String target = args[0];
        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                target,
                member -> sendLogsMessage(event, member.getId(), member.getUser().getAsTag()),
                failure -> handleMissingTargetForMessage(event, target, failure));
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "modlogs".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("modlogs", "/modlogs target:<userId|@mention|username>", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(
                event.getGuild(),
                target,
                member -> replyWithLogs(event, member.getId(), member.getUser().getAsTag()),
                failure -> handleMissingTargetForSlash(event, target, failure));
    }

    private void handleMissingTargetForMessage(
            MessageReceivedEvent event,
            String target,
            TargetResolver.ResolveFailure failure) {
        CaseRecord fallback = caseService.findLatestCaseForTargetName(event.getGuild().getId(), target);
        if (fallback != null && fallback.targetId() != null) {
            String displayName = fallback.targetName() == null ? target : fallback.targetName();
            sendLogsMessage(event, fallback.targetId(), displayName);
            return;
        }

        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                "Mod Logs",
                failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                        ? "I couldn't find that member in this server."
                        : "I couldn't look up that member right now."))
                .queue();
    }

    private void handleMissingTargetForSlash(
            SlashCommandInteractionEvent event,
            String target,
            TargetResolver.ResolveFailure failure) {
        CaseRecord fallback = caseService.findLatestCaseForTargetName(event.getGuild().getId(), target);
        if (fallback != null && fallback.targetId() != null) {
            String displayName = fallback.targetName() == null ? target : fallback.targetName();
            replyWithLogs(event, fallback.targetId(), displayName);
            return;
        }

        event.replyEmbeds(CommandTemplateEmbeds.error(
                "Mod Logs",
                failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                        ? "I couldn't find that member in this server."
                        : "I couldn't look up that member right now."))
                .setEphemeral(true)
                .queue();
    }

    private void sendLogsMessage(MessageReceivedEvent event, String userId, String displayName) {
        List<CaseRecord> logs = caseService.getCasesForUser(event.getGuild().getId(), userId);
        event.getChannel().sendMessageEmbeds(buildLogsEmbed(event.getGuild().getId(), userId, displayName))
                .setActionRow(Button.secondary(OPEN_DELETE_PREFIX + userId, "Delete a modlog").withDisabled(logs.isEmpty()))
                .queue();
    }

    private void replyWithLogs(SlashCommandInteractionEvent event, String userId, String displayName) {
        List<CaseRecord> logs = caseService.getCasesForUser(event.getGuild().getId(), userId);
        event.replyEmbeds(buildLogsEmbed(event.getGuild().getId(), userId, displayName))
                .addActionRow(Button.secondary(OPEN_DELETE_PREFIX + userId, "Delete a modlog").withDisabled(logs.isEmpty()))
                .setEphemeral(true)
                .queue();
    }

    private MessageEmbed buildLogsEmbed(String guildId, String userId, String displayName) {
        List<CaseRecord> logs = caseService.getCasesForUser(guildId, userId);
        if (logs.isEmpty()) {
            return CommandTemplateEmbeds.info("Mod Logs", "No mod logs found for " + displayName + " (`" + userId + "`).");
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Mod logs for ").append(displayName)
                .append(" (`").append(userId).append("`)\nTotal: ").append(logs.size());

        int start = Math.max(0, logs.size() - 10);
        for (int i = start; i < logs.size(); i++) {
            CaseRecord log = logs.get(i);
            builder.append("\n- #").append(log.id())
                    .append(" ").append(log.type())
                    .append(" | ").append(log.reason());
        }

        return CommandTemplateEmbeds.info("Mod Logs", builder.toString());
    }

    public static boolean handleButton(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(OPEN_DELETE_PREFIX)) {
            return false;
        }

        if (!AccessControlService.getInstance().canBan(event.getMember())) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mod Logs", "Only admins can delete mod logs."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        String targetUserId = id.substring(OPEN_DELETE_PREFIX.length()).trim();
        if (targetUserId.isEmpty()) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mod Logs", "Invalid target for mod log deletion."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        List<CaseRecord> logs = CaseService.getInstance().getCasesForUser(event.getGuild().getId(), targetUserId);
        if (logs.isEmpty()) {
            event.replyEmbeds(CommandTemplateEmbeds.info("Mod Logs", "No logs available to delete for that user."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        StringSelectMenu.Builder menu = StringSelectMenu.create(SELECT_DELETE_PREFIX + targetUserId)
                .setPlaceholder("Choose a case to delete")
                .setMinValues(1)
                .setMaxValues(1);

        int start = Math.max(0, logs.size() - MAX_MENU_OPTIONS);
        for (int i = logs.size() - 1; i >= start; i--) {
            CaseRecord log = logs.get(i);
            String reason = log.reason() == null ? "No reason" : log.reason();
            if (reason.length() > 80) {
                reason = reason.substring(0, 77) + "...";
            }

            menu.addOption("#" + log.id() + " " + log.type(), String.valueOf(log.id()), reason);
        }

        event.replyEmbeds(CommandTemplateEmbeds.info("Mod Logs", "Select one case to delete."))
                .addActionRow(menu.build())
                .setEphemeral(true)
                .queue();
        return true;
    }

    public static boolean handleStringSelect(StringSelectInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(SELECT_DELETE_PREFIX)) {
            return false;
        }

        if (!AccessControlService.getInstance().canBan(event.getMember())) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mod Logs", "Only admins can delete mod logs."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        String selected = event.getValues().isEmpty() ? null : event.getValues().get(0);
        long caseId;
        try {
            caseId = Long.parseLong(selected == null ? "" : selected.trim());
        } catch (NumberFormatException ex) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mod Logs", "Invalid case ID selected."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        CaseRecord removed = CaseService.getInstance().deleteCase(event.getGuild().getId(), caseId);
        if (removed == null) {
            event.replyEmbeds(CommandTemplateEmbeds.error("Mod Logs", "That case was not found or was already deleted."))
                    .setEphemeral(true)
                    .queue();
            return true;
        }

        event.replyEmbeds(CommandTemplateEmbeds.success("Mod Logs", "Deleted case #" + removed.id() + "."))
                .setEphemeral(true)
                .queue();
        return true;
    }
}

