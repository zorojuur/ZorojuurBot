package com.bot.commands;

import java.awt.Color;
import java.util.Locale;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

public final class CommandTemplateEmbeds {
    private static final Color TEMPLATE_COLOR = new Color(102, 2, 60);

    private CommandTemplateEmbeds() {
    }

    public static MessageEmbed info(String title, String description) {
        return new EmbedBuilder()
                .setColor(TEMPLATE_COLOR)
                .setTitle(title)
                .setDescription(description)
                .build();
    }

    public static MessageEmbed usage(String commandName, String syntax, String example) {
        StringBuilder description = new StringBuilder();
        description.append("**Usage**\n`").append(syntax).append("`");

        if (example != null && !example.isBlank()) {
            description.append("\n\n**Example**\n`").append(example).append("`");
        }

        return new EmbedBuilder()
                .setColor(TEMPLATE_COLOR)
                .setTitle("Command: +" + commandName.toLowerCase(Locale.ROOT))
                .setDescription(description.toString())
                .build();
    }

    public static MessageEmbed success(String title, String description) {
        return new EmbedBuilder()
                .setColor(TEMPLATE_COLOR)
                .setTitle(title)
                .setDescription("`SUCCESS` " + description)
                .build();
    }

    public static MessageEmbed error(String title, String description) {
        return new EmbedBuilder()
                .setColor(TEMPLATE_COLOR)
                .setTitle(title)
                .setDescription("`ERROR` " + description)
                .build();
    }
}

