package com.bot.commands;

import java.awt.Color;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class WarningInfoCommand implements BotCommand, SlashCommandHandler {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Color WHOIS_COLOR = new Color(102, 2, 60);

    @Override
    public boolean matches(String commandName) {
        return "w".equalsIgnoreCase(commandName) || "whois".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (args.length != 1) {
            event.getChannel().sendMessageEmbeds(
                    CommandTemplateEmbeds.usage("w", "+w <userId|@mention|username>", "+w jesterskitt"))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), args[0],
                member -> event.getChannel().sendMessageEmbeds(buildWhoisEmbed(member))
                        .setAllowedMentions(Collections.emptyList())
                        .queue(),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Whois",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .queue());
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "w".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String target = event.getOption("target", "", option -> option.getAsString());
        if (target.isBlank()) {
            event.replyEmbeds(CommandTemplateEmbeds.usage("w", "/w target:<userId|@mention|username>", null))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(event.getGuild(), target,
                member -> event.replyEmbeds(buildWhoisEmbed(member))
                        .setAllowedMentions(Collections.emptyList())
                        .setEphemeral(true)
                        .queue(),
                failure -> event.replyEmbeds(CommandTemplateEmbeds.error(
                        "Whois",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that member in this server."
                                : "I couldn't look up that member right now."))
                        .setEphemeral(true)
                        .queue());
    }

    private MessageEmbed buildWhoisEmbed(Member member) {
        String profile = "ID: `" + member.getId() + "`\n\n"
                + "Joined server: " + DATE_FORMAT.format(member.getTimeJoined()) + "\n\n"
                + "Account created: " + DATE_FORMAT.format(member.getUser().getTimeCreated()) + "\n\n"
                + "Avatar: " + member.getUser().getEffectiveAvatarUrl();

        String roles = member.getRoles().isEmpty()
                ? "none"
                : member.getRoles().stream()
                        .map(role -> "• <@&" + role.getId() + ">")
                        .collect(Collectors.joining("\n"));

        String permissions = member.getPermissions().stream()
                .limit(20)
                .map(permission -> "• " + permission.getName())
                .collect(Collectors.joining("\n"));
        if (permissions.isBlank()) {
            permissions = "none";
        }

        return new EmbedBuilder()
                .setTitle("Whois: " + member.getUser().getAsTag())
                .setColor(WHOIS_COLOR)
                .setThumbnail(member.getUser().getEffectiveAvatarUrl())
                .addField("Profile", truncateField(profile), false)
                .addField("Roles", truncateField(roles), false)
                .addField("Permissions", truncateField(permissions), false)
                .build();
    }

    private String truncateField(String value) {
        if (value == null || value.length() <= 1000) {
            return value == null ? "none" : value;
        }

        return value.substring(0, 997) + "...";
    }
}

