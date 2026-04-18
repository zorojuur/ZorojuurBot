package com.bot.reactions;

import java.util.HashMap;
import java.util.Map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;

public class ReactionRoleListener {
    private static final String CHANNEL_ID = "1482971656086360104";

    private final ReactionRolePanelService panelService = new ReactionRolePanelService();
    private static final Map<String, String> ROLE_BY_EMOJI = createRoleMap();

    public void onReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        if (!CHANNEL_ID.equals(event.getChannel().getId())) {
            return;
        }

        ReactionRolePanelService.PanelMessage panelMessage = panelService.loadPanelMessage();
        if (panelMessage == null || !panelMessage.messageId().equals(event.getMessageId())) {
            return;
        }

        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        String emoji = event.getReaction().getEmoji().getFormatted();
        String roleId = ROLE_BY_EMOJI.get(emoji);
        if (roleId == null) {
            return;
        }

        Role role = event.getGuild().getRoleById(roleId);
        if (role == null) {
            return;
        }

        Member member = event.getGuild().getMemberById(event.getUserId());
        if (member != null) {
            toggleRole(event, member, role);
            return;
        }

        event.getGuild().retrieveMemberById(event.getUserId()).queue(
                loadedMember -> toggleRole(event, loadedMember, role),
                failure -> {
                });
    }

    private void toggleRole(MessageReactionAddEvent event, Member member, Role role) {
        boolean hasRole = member.getRoles().stream().anyMatch(existingRole -> existingRole.getId().equals(role.getId()));

        var action = hasRole
                ? event.getGuild().removeRoleFromMember(UserSnowflake.fromId(member.getId()), role)
                : event.getGuild().addRoleToMember(UserSnowflake.fromId(member.getId()), role);

        action.queue(
                success -> event.getReaction().removeReaction(event.getUser()).queue(),
                failure -> {
                });
    }

    private static Map<String, String> createRoleMap() {
        Map<String, String> roleByEmoji = new HashMap<>();
        roleByEmoji.put("🎁", "1475451394915041402");
        roleByEmoji.put("✨", "1475451341806764032");
        roleByEmoji.put("📊", "1475451206317903892");
        return roleByEmoji;
    }
}