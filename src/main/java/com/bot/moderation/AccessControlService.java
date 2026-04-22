package com.bot.moderation;

import java.util.Set;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public final class AccessControlService {
    private static final AccessControlService INSTANCE = new AccessControlService();
    private static final String BOT_OWNER_ID = System.getenv("BOT_OWNER_ID");
    private static final String DEFAULT_OWNER_ID = "1176596440160665741";

    private static final String SERVER_MANAGER_ROLE_ID = "1496537304880255198";
    private static final String HEAD_MODERATOR_ROLE_ID = "1496542241903349821";
    private static final String SENIOR_MODERATOR_ROLE_ID = "1496542172848197783";
    private static final String MODERATOR_ROLE_ID = "1496542109619064942";
    private static final String TRIAL_MODERATOR_ROLE_ID = "1496541997488672879";

    private static final Set<String> PUBLIC_COMMANDS = Set.of("game");
    private static final Set<String> TRIAL_MOD_COMMANDS = Set.of("mute", "warn");
    private static final Set<String> MODERATOR_COMMANDS = Set.of("mute", "warn", "modlogs", "logs");
    private static final Set<String> HEAD_MOD_COMMANDS = Set.of("mute", "warn", "modlogs", "logs", "kick", "ban");

    private AccessControlService() {
    }

    public static AccessControlService getInstance() {
        return INSTANCE;
    }

    public boolean isHelperPlus(Member member) {
        return isTrialModeratorOrHigher(member);
    }

    public boolean canBan(Member member) {
        if (member == null) {
            return false;
        }

        return isBotOwner(member)
                || hasRole(member, SERVER_MANAGER_ROLE_ID)
                || hasRole(member, HEAD_MODERATOR_ROLE_ID);
    }

    public boolean canUseCommand(Member member, String commandName) {
        String lowered = commandName.toLowerCase();

        if ("gamerules".equals(lowered) || "postgamerules".equals(lowered)) {
            return isBotOwner(member);
        }

        if (PUBLIC_COMMANDS.contains(lowered)) {
            return true;
        }

        if (member == null) {
            return false;
        }

        if (isOwnerOrServerManager(member)) {
            return true;
        }

        if ("an".equals(lowered) || "antinuke".equals(lowered)) {
            return false;
        }

        if (hasRole(member, HEAD_MODERATOR_ROLE_ID)) {
            return HEAD_MOD_COMMANDS.contains(lowered);
        }

        if (hasRole(member, SENIOR_MODERATOR_ROLE_ID) || hasRole(member, MODERATOR_ROLE_ID)) {
            return MODERATOR_COMMANDS.contains(lowered);
        }

        if (hasRole(member, TRIAL_MODERATOR_ROLE_ID)) {
            return TRIAL_MOD_COMMANDS.contains(lowered);
        }

        return false;
    }

    public boolean isModeratorOrHigher(Member member) {
        if (member == null) {
            return false;
        }

        return isBotOwner(member)
                || hasRole(member, SERVER_MANAGER_ROLE_ID)
                || hasRole(member, HEAD_MODERATOR_ROLE_ID)
                || hasRole(member, SENIOR_MODERATOR_ROLE_ID)
                || hasRole(member, MODERATOR_ROLE_ID);
    }

    public boolean isOwnerOrServerManager(Member member) {
        if (member == null) {
            return false;
        }

        return isBotOwner(member) || hasRole(member, SERVER_MANAGER_ROLE_ID);
    }

    private boolean isTrialModeratorOrHigher(Member member) {
        if (member == null) {
            return false;
        }

        return isModeratorOrHigher(member) || hasRole(member, TRIAL_MODERATOR_ROLE_ID);
    }

    public boolean isBotOwner(Member member) {
        if (member == null) {
            return false;
        }

        String ownerId = BOT_OWNER_ID;
        if (ownerId == null || ownerId.isBlank()) {
            ownerId = DEFAULT_OWNER_ID;
        }

        return ownerId.equals(member.getId());
    }

    public boolean hasAnyRole(Member member, Set<String> roleIds) {
        for (Role role : member.getRoles()) {
            if (roleIds.contains(role.getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasRole(Member member, String roleId) {
        for (Role role : member.getRoles()) {
            if (roleId.equals(role.getId())) {
                return true;
            }
        }
        return false;
    }
}
