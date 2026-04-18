package com.bot.moderation;

import java.util.Locale;
import java.util.Set;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

public final class AccessControlService {
    private static final AccessControlService INSTANCE = new AccessControlService();
    private static final String BOT_OWNER_ID = System.getenv("BOT_OWNER_ID");
    private static final String DEFAULT_OWNER_ID = "1176596440160665741";

    private static final Set<String> HELPER_PLUS_ROLES = Set.of(
            "helper",
            "mod",
            "moderator",
            "staff",
            "co-owner",
            "owner",
            "admin");

    private static final Set<String> BAN_ALLOWED_ROLES = Set.of(
            "co-owner",
            "owner",
            "admin");

    private static final Set<String> PUBLIC_COMMANDS = Set.of("game");

    private AccessControlService() {
    }

    public static AccessControlService getInstance() {
        return INSTANCE;
    }

    public boolean isHelperPlus(Member member) {
        if (member == null) {
            return false;
        }

        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }

        return hasAnyRole(member, HELPER_PLUS_ROLES);
    }

    public boolean canBan(Member member) {
        if (member == null) {
            return false;
        }

        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }

        return hasAnyRole(member, BAN_ALLOWED_ROLES);
    }

    public boolean canUseCommand(Member member, String commandName) {
        String lowered = commandName.toLowerCase(Locale.ROOT);
        if ("giveall".equals(lowered)) {
            return isBotOwner(member);
        }

        if (PUBLIC_COMMANDS.contains(lowered)) {
            return true;
        }

        if (!isHelperPlus(member)) {
            return false;
        }

        if ("ban".equals(lowered)
                || "unban".equals(lowered)
                || "dm".equals(lowered)
                || "purge".equals(lowered)
                || "clean".equals(lowered)
                || "clear".equals(lowered)
                || "lock".equals(lowered)
                || "unlock".equals(lowered)
                || "slowmode".equals(lowered)
                || "clearwarns".equals(lowered)
                || "casedelete".equals(lowered)
                || "role".equals(lowered)
                || "dragall".equals(lowered)
                || "setup".equals(lowered)
                || "setupverify".equals(lowered)
                || "verifysetup".equals(lowered)
                || "setupverifyproto".equals(lowered)
                || "verifyprotosetup".equals(lowered)
                || "postverify".equals(lowered)
                || "verifypost".equals(lowered)
                || "postverifyproto".equals(lowered)
                || "verifyprotopost".equals(lowered)) {
            return canBan(member);
        }

        return true;
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

    public boolean hasAnyRole(Member member, Set<String> roleNames) {
        for (Role role : member.getRoles()) {
            if (roleNames.contains(role.getName().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
