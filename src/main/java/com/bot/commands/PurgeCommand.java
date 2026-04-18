package com.bot.commands;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class PurgeCommand implements BotCommand, SlashCommandHandler {
    private static final int MAX_PURGE = 100;

    @Override
    public boolean matches(String commandName) {
        return "purge".equalsIgnoreCase(commandName) || "clear".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "purge",
                    "+purge <amount> [reason]",
                    "+purge 25 spam wave"))
                    .queue();
            return;
        }

        int amount = parseAmount(args[0]);
        if (amount <= 0) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Purge",
                    "Amount must be a number between 1 and 100."))
                    .queue();
            return;
        }

        event.getMessage().delete().queue(success -> {
        }, failure -> {
        });

        String reason = CommandTextUtil.joinArgs(args, 1, "No reason provided.");
        purgeMessages(event.getGuild().getId(), event.getChannel().getId(), event.getAuthor().getId(),
                event.getAuthor().getAsTag(), amount, reason, event);
    }

    private void purgeMessages(String guildId, String channelId, String moderatorId, String moderatorName,
            int amount, String reason, MessageReceivedEvent event) {
        int fetchAmount = Math.min(MAX_PURGE + 1, amount + 1);
        event.getChannel().getHistory().retrievePast(fetchAmount).queue(messages -> {
            List<Message> deletable = new ArrayList<>();
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
            for (Message message : messages) {
                if (message.getId().equals(event.getMessageId())) {
                    continue;
                }

                if (message.getTimeCreated().isAfter(cutoff)) {
                    deletable.add(message);
                    if (deletable.size() >= amount) {
                        break;
                    }
                }
            }

            if (deletable.isEmpty()) {
                event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.info(
                        "Purge",
                        "No deletable messages found (Discord only allows deleting newer messages)."))
                        .queue();
                return;
            }

            event.getChannel().purgeMessages(deletable);
        }, failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                "Purge",
                "I couldn't read channel history for purge."))
                .queue());
    }

    private int parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount < 1) {
                return -1;
            }
            return Math.min(amount, MAX_PURGE);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "purge".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        int amount = event.getOption("amount", 0, option -> option.getAsInt());
        if (amount <= 0) {
            event.replyEmbeds(CommandTemplateEmbeds.error(
                    "Purge",
                    "Amount must be a number between 1 and 100."))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String reason = event.getOption("reason", "No reason provided.", option -> option.getAsString());
        event.getChannel().getHistory().retrievePast(Math.min(amount, MAX_PURGE)).queue(messages -> {
            List<Message> deletable = new ArrayList<>();
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);
            for (Message message : messages) {
                if (message.getTimeCreated().isAfter(cutoff)) {
                    deletable.add(message);
                }
            }

            if (deletable.isEmpty()) {
                event.replyEmbeds(CommandTemplateEmbeds.info("Purge", "No deletable messages found."))
                        .setEphemeral(true)
                        .queue();
                return;
            }

            event.getChannel().purgeMessages(deletable);
            event.replyEmbeds(CommandTemplateEmbeds.success(
                    "Purge",
                    "Purged " + deletable.size() + " messages. Reason: " + reason))
                    .setEphemeral(true)
                    .queue();
        }, failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                "Purge",
                "I couldn't read channel history for purge."))
                .setEphemeral(true)
                .queue());
    }
}
