package com.bot.commands;

import java.util.List;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class SetupVerifyPrototypeCommand implements BotCommand {
    private static final String VERIFY_CHANNEL_NAME = "verify";
    private static final String LEGACY_VERIFY_CHANNEL_NAME = "verify-prototype";
    private static final String LEGACY_VERIFY_CHANNEL_ID = "1495000032443633666";
    private static final String VERIFY_CATEGORY_NAME = "Verify";
    private static final String VERIFY_TOPIC_MARKER = "[zoro-verify]";
    private static final List<String> STAFF_ROLE_IDS = List.of(
            "1475422357039480963", // Trial Moderator
            "1475423830141964370", // Moderator
            "1475440354596618378", // Senior Moderator
            "1475440657702195251", // Head Moderator
            "1475424585217343640", // Admin
            "1475427475063574538" // Owner
    );

    @Override
    public boolean matches(String commandName) {
        return "setupverify".equalsIgnoreCase(commandName)
                || "verifysetup".equalsIgnoreCase(commandName)
                || "setupverifyproto".equalsIgnoreCase(commandName)
                || "verifyprotosetup".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        Guild guild = event.getGuild();
        if (args.length == 0) {
            ensureVerifyChannel(event, null, null);
            return;
        }

        if (args.length == 1) {
            TextChannel explicitChannel = resolveChannel(guild, args[0]);
            if (explicitChannel != null) {
                ensureVerifyChannel(event, null, explicitChannel);
                return;
            }

            String testerInput = args[0];
            TargetResolver.resolveMemberAsync(
                    guild,
                    testerInput,
                    member -> ensureVerifyChannel(event, member, null),
                    failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                            "Verification",
                            failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                    ? "I couldn't find that tester in this server."
                                    : "I couldn't look up that tester right now."))
                            .queue());
            return;
        }

        String testerInput = args[0];
        String channelInput = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).trim();
        TextChannel explicitChannel = resolveChannel(guild, channelInput);
        if (explicitChannel == null) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "I couldn't find that verify channel. Use a channel mention, ID, or exact name."))
                    .queue();
            return;
        }

        TargetResolver.resolveMemberAsync(
                guild,
                testerInput,
                member -> ensureVerifyChannel(event, member, explicitChannel),
                failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                        "Verification",
                        failure == TargetResolver.ResolveFailure.MEMBER_NOT_FOUND
                                ? "I couldn't find that tester in this server."
                                : "I couldn't look up that tester right now."))
                        .queue());
    }

    private void ensureVerifyChannel(MessageReceivedEvent event, Member tester, TextChannel explicitChannel) {
        Guild guild = event.getGuild();

        deleteLegacyPrototypeChannel(guild);

        TextChannel verifyChannel = explicitChannel != null ? explicitChannel : findVerifyChannel(guild);

        Category verifyCategory = findOrCreateVerifyCategory(guild, event);
        if (verifyCategory == null) {
            return;
        }

        if (verifyChannel != null) {
            moveChannelToCategoryIfNeeded(verifyChannel, verifyCategory);
            applyPermissionsAndRespond(event, verifyChannel, tester, false);
            return;
        }

        guild.createTextChannel(VERIFY_CHANNEL_NAME, verifyCategory)
                .queue(
                        created -> applyPermissionsAndRespond(event, created, tester, true),
                        failure -> event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                                "Verification",
                                "I couldn't create the verification channel."))
                                .queue());
    }

    private void deleteLegacyPrototypeChannel(Guild guild) {
        TextChannel legacy = guild.getTextChannelById(LEGACY_VERIFY_CHANNEL_ID);
        if (legacy == null) {
            return;
        }

        legacy.delete().queue(success -> {
        }, failure -> {
        });
    }

    private Category findOrCreateVerifyCategory(Guild guild, MessageReceivedEvent event) {
        for (Category category : guild.getCategories()) {
            if (category.getName().equalsIgnoreCase(VERIFY_CATEGORY_NAME)) {
                return category;
            }
        }

        try {
            return guild.createCategory(VERIFY_CATEGORY_NAME).complete();
        } catch (Exception ex) {
            event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.error(
                    "Verification",
                    "I couldn't create the Verify category."))
                    .queue();
            return null;
        }
    }

    private void moveChannelToCategoryIfNeeded(TextChannel channel, Category category) {
        if (channel.getParentCategory() != null
                && channel.getParentCategory().getId().equals(category.getId())) {
            return;
        }

        channel.getManager().setParent(category).queue(success -> {
        }, failure -> {
        });
    }

    private void applyPermissionsAndRespond(
            MessageReceivedEvent event,
            TextChannel channel,
            Member tester,
            boolean createdNow) {
        Guild guild = event.getGuild();

        channel.upsertPermissionOverride(guild.getPublicRole())
                .clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                .queue();

        for (String roleId : STAFF_ROLE_IDS) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                continue;
            }

            channel.upsertPermissionOverride(role)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                    .queue();
        }

        if (tester != null) {
            channel.upsertPermissionOverride(tester)
                    .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                    .queue();
        }

        String topic = channel.getTopic();
        if (topic == null || !topic.contains(VERIFY_TOPIC_MARKER)) {
            String newTopic = (topic == null || topic.isBlank())
                    ? VERIFY_TOPIC_MARKER
                    : topic + " " + VERIFY_TOPIC_MARKER;
            channel.getManager().setTopic(newTopic).queue(success -> {
            }, failure -> {
            });
        }

        StringBuilder description = new StringBuilder();
        description.append(createdNow ? "Created" : "Updated")
                .append(" verification channel: ")
                .append(channel.getAsMention())
                .append("\nChannel is now public for server verification");
        if (tester != null) {
            description.append(" (tester override also applied for ").append(tester.getAsMention()).append(")");
        }
        description.append(".");

        event.getChannel().sendMessageEmbeds(CommandTemplateEmbeds.success("Verification", description.toString()))
                .queue();
    }

    private TextChannel findVerifyChannel(Guild guild) {
        for (TextChannel channel : guild.getTextChannels()) {
            if (channel.getId().equals(LEGACY_VERIFY_CHANNEL_ID)) {
                continue;
            }

            String topic = channel.getTopic();
            if (topic != null && topic.contains(VERIFY_TOPIC_MARKER)) {
                return channel;
            }
        }

        for (TextChannel channel : guild.getTextChannels()) {
            if (channel.getId().equals(LEGACY_VERIFY_CHANNEL_ID)) {
                continue;
            }

            if (channel.getName().equalsIgnoreCase(VERIFY_CHANNEL_NAME)) {
                return channel;
            }
        }

        for (TextChannel channel : guild.getTextChannels()) {
            if (channel.getId().equals(LEGACY_VERIFY_CHANNEL_ID)) {
                continue;
            }

            if (channel.getName().equalsIgnoreCase(LEGACY_VERIFY_CHANNEL_NAME)) {
                return channel;
            }
        }
        return null;
    }

    private TextChannel resolveChannel(Guild guild, String input) {
        if (input == null) {
            return null;
        }

        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.startsWith("<#") && trimmed.endsWith(">")) {
            String channelId = trimmed.substring(2, trimmed.length() - 1);
            if (channelId.matches("\\d+")) {
                return guild.getTextChannelById(channelId);
            }
        }

        if (trimmed.matches("\\d+")) {
            return guild.getTextChannelById(trimmed);
        }

        for (TextChannel channel : guild.getTextChannels()) {
            if (channel.getName().equalsIgnoreCase(trimmed)) {
                return channel;
            }
        }

        return null;
    }
}

