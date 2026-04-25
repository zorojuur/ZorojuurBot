package com.bot.commands;

import com.bot.moderation.AntiNukeService;
import com.bot.moderation.AccessControlService;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

public class AntiNukeCommand implements BotCommand, SlashCommandHandler {
    private final AntiNukeService antiNukeService = AntiNukeService.getInstance();
    private final AccessControlService accessControlService = AccessControlService.getInstance();

    @Override
    public boolean matches(String commandName) {
        return "an".equalsIgnoreCase(commandName)
                || "antinuke".equalsIgnoreCase(commandName)
                || "whitelist".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if ("whitelist".equalsIgnoreCase(commandName)) {
            if (!isOwner(event.getAuthor().getId())) {
                event.getChannel().sendMessage("Only the owner can manage anti-nuke whitelist.").queue();
                return;
            }
            if (args.length < 1) {
                event.getChannel().sendMessage("Usage: +whitelist <user-id|@mention>").queue();
                return;
            }

            String targetId = extractUserId(args[0]);
            if (targetId == null) {
                event.getChannel().sendMessage("I couldn't read that user. Use a user mention or user ID.").queue();
                return;
            }

            boolean added = antiNukeService.toggleWhitelisted(event.getGuild().getId(), targetId);
            String action = added ? "added to" : "removed from";
            event.getChannel().sendMessage("<@" + targetId + "> was " + action + " anti-nuke whitelist.").queue();
            return;
        }

        String action = args.length == 0 ? "status" : args[0].toLowerCase();
        if ("list".equals(action)) {
            if (!accessControlService.isOwnerOrServerManager(event.getMember())) {
                event.getChannel().sendMessage("Only the owner or server manager can view anti-nuke whitelist.").queue();
                return;
            }
            event.getChannel().sendMessage(buildWhitelistList(event.getGuild().getId())).queue();
            return;
        }

        if (isEnableOrDisable(action) && !isOwner(event.getAuthor().getId())) {
            event.getChannel().sendMessage("You do not have permission to enable or disable anti-nuke. Only the owner can do this.").queue();
            return;
        }
        event.getChannel().sendMessage(handleAction(event.getGuild().getId(), action)).queue();
    }

    @Override
    public boolean handlesSlash(String commandName) {
        return "an".equalsIgnoreCase(commandName) || "antinuke".equalsIgnoreCase(commandName);
    }

    @Override
    public void executeSlash(SlashCommandInteractionEvent event) {
        String action = event.getOption("action", "status", option -> option.getAsString()).toLowerCase();
        if ("list".equals(action)) {
            if (!accessControlService.isOwnerOrServerManager(event.getMember())) {
                event.reply("Only the owner or server manager can view anti-nuke whitelist.").setEphemeral(true).queue();
                return;
            }
            event.reply(buildWhitelistList(event.getGuild().getId())).setEphemeral(true).queue();
            return;
        }

        if (isEnableOrDisable(action) && !isOwner(event.getUser().getId())) {
            event.reply("You do not have permission to enable or disable anti-nuke. Only the owner can do this.").setEphemeral(true).queue();
            return;
        }
        event.reply(handleAction(event.getGuild().getId(), action)).setEphemeral(true).queue();
    }

    private String handleAction(String guildId, String action) {
        return switch (action) {
            case "on", "enable", "enabled" -> {
                antiNukeService.setEnabled(guildId, true);
                yield "Anti-nuke is now enabled.";
            }
            case "off", "disable", "disabled" -> {
                antiNukeService.setEnabled(guildId, false);
                yield "Anti-nuke is now disabled.";
            }
            case "status" -> antiNukeService.isEnabled(guildId)
                    ? "Anti-nuke is currently enabled."
                    : "Anti-nuke is currently disabled.";
            default -> "Usage: +an <on|off|status|list>";
        };
    }

    private boolean isEnableOrDisable(String action) {
        return action.equals("on") || action.equals("enable") || action.equals("enabled") ||
               action.equals("off") || action.equals("disable") || action.equals("disabled");
    }

    private boolean isOwner(String userId) {
        return "1176596440160665741".equals(userId);
    }

    private String buildWhitelistList(String guildId) {
        var whitelistedUsers = antiNukeService.getWhitelistedUsers(guildId);
        if (whitelistedUsers.isEmpty()) {
            return "Anti-nuke whitelist is empty.";
        }

        StringBuilder text = new StringBuilder("Anti-nuke whitelist:\n");
        whitelistedUsers.stream().sorted().forEach(userId -> text.append("- <@").append(userId).append("> (`")
                .append(userId).append("`)\n"));
        return text.toString();
    }

    private String extractUserId(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.matches("\\d+")) {
            return input;
        }
        if (input.startsWith("<@") && input.endsWith(">")) {
            String normalized = input.substring(2, input.length() - 1).replace("!", "");
            if (normalized.matches("\\d+")) {
                return normalized;
            }
        }
        return null;
    }
}
