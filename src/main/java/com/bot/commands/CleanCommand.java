package com.bot.commands;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class CleanCommand implements BotCommand {
    private static final int MAX_CLEAN = 100;

    @Override
    public boolean matches(String commandName) {
        return "clean".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.usage(
                    "clean",
                    "+clean <amount>",
                    "+clean 10"))
                    .queue();
            return;
        }

        int amount = parseAmount(args[0]);
        if (amount <= 0) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Clean",
                    "Amount must be a number between 1 and 100."))
                    .queue();
            return;
        }

        event.getMessage().delete().queue(success -> {
        }, failure -> {
        });

        int fetchAmount = Math.min(MAX_CLEAN, Math.max(amount * 5, amount));
        event.getChannel().getHistory().retrievePast(fetchAmount).queue(messages -> {
            List<Message> deletable = new ArrayList<>();
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(14);

            for (Message message : messages) {
                if (message.getId().equals(event.getMessageId())) {
                    continue;
                }

                if (!message.getAuthor().isBot()) {
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
                return;
            }

            event.getChannel().purgeMessages(deletable);
        }, failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                "Clean",
                "I couldn't read channel history for clean."))
                .queue());
    }

    private int parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount < 1) {
                return -1;
            }
            return Math.min(amount, MAX_CLEAN);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }
}


