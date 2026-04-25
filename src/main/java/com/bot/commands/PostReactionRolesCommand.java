package com.bot.commands;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import com.bot.reactions.ReactionRolePanelService;

public class PostReactionRolesCommand implements BotCommand {
    private static final String TARGET_CHANNEL_ID = "1475417253834133564";
    private static final ReactionRolePanelService PANEL_SERVICE = new ReactionRolePanelService();
    private static final Map<String, String> ROLE_LABELS = createRoleLabels();
    private static final Map<String, String> ROLE_IDS = createRoleIds();

    @Override
    public boolean matches(String commandName) {
        return "reactionroles".equalsIgnoreCase(commandName)
                || "postreactionroles".equalsIgnoreCase(commandName)
                || "rrpost".equalsIgnoreCase(commandName);
    }

    @Override
    public void execute(MessageReceivedEvent event, String commandName, String[] args) {
        if (!TARGET_CHANNEL_ID.equals(event.getChannel().getId())) {
            event.getChannel().sendMessage("Use this command in the reaction role channel.").queue();
            return;
        }

        StringBuilder roleBlock = new StringBuilder();
        for (Map.Entry<String, String> entry : ROLE_LABELS.entrySet()) {
            String emoji = entry.getKey();
            String roleId = ROLE_IDS.get(emoji);
            roleBlock.append("**")
                    .append(emoji)
                    .append("  <@&")
                    .append(roleId)
                    .append("> - ")
                    .append(entry.getValue())
                    .append("**")
                    .append("\n\n");
        }

        EmbedBuilder embedBuilder = new EmbedBuilder()
                .setTitle("Reaction Roles")
                .setColor(new Color(102, 2, 60))
                .addField("Available Roles", roleBlock.toString().trim(), false)
                .setFooter("Your reaction is removed automatically after the role is assigned.");

        event.getChannel().sendMessageEmbeds(embedBuilder.build())
                .setAllowedMentions(Collections.emptyList())
                .queue(message -> storeMessageId(event.getChannel(), message));
    }

    private void storeMessageId(MessageChannel channel, Message message) {
        PANEL_SERVICE.savePanelMessage(channel.getId(), message.getId());
        addReactions(message);
    }

    private void addReactions(Message message) {
        ROLE_LABELS.keySet().forEach(emoji -> message.addReaction(Emoji.fromUnicode(emoji)).queue());
    }

    private static Map<String, String> createRoleLabels() {
        Map<String, String> roleLabels = new LinkedHashMap<>();
        roleLabels.put("🎁", "Giveaway ping");
        roleLabels.put("✨", "Event ping");
        roleLabels.put("📊", "Poll ping");
        return roleLabels;
    }

    private static Map<String, String> createRoleIds() {
        Map<String, String> roleIds = new LinkedHashMap<>();
        roleIds.put("🎁", "1475451394915041402");
        roleIds.put("✨", "1475451341806764032");
        roleIds.put("📊", "1475451206317903892");
        return roleIds;
    }
}