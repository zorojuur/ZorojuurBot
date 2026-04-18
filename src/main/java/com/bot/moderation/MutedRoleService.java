package com.bot.moderation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.attribute.IPermissionContainer;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

public final class MutedRoleService {
    private static final String LEGACY_MUTED_ROLE_ID = "1494752554037547059";
    private static final String MUTED_ROLE_NAME = "Muted";
    private static final Path STATE_PATH = resolveStatePath();
    private static final MutedRoleService INSTANCE = new MutedRoleService();
    private static final Set<String> ALLOWED_CHANNEL_IDS = Set.of(
            "1475417718332194877", // ticket
            "1495005911780032522", // verify
            "1475416766367666267" // announcement
    );

    private final Map<String, Set<String>> mutedUsersByGuild = new ConcurrentHashMap<>();
    private final Map<String, Map<String, List<String>>> preMuteRoleSnapshotsByGuild = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Long>> muteUntilByGuild = new ConcurrentHashMap<>();
    private final Map<String, String> mutedRoleIdByGuild = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    private MutedRoleService() {
        loadState();
    }

    public static MutedRoleService getInstance() {
        return INSTANCE;
    }

    public String mutedRoleId() {
        return "managed-per-guild";
    }

    public String mutedRoleId(Guild guild) {
        Role mutedRole = getMutedRole(guild);
        return mutedRole == null ? "(missing)" : mutedRole.getId();
    }

    public Role getMutedRole(Guild guild) {
        String guildId = guild.getId();
        String storedRoleId = mutedRoleIdByGuild.get(guildId);
        if (storedRoleId != null && !storedRoleId.isBlank()) {
            Role stored = guild.getRoleById(storedRoleId);
            if (stored != null && !stored.getId().equals(LEGACY_MUTED_ROLE_ID)) {
                return stored;
            }
        }

        for (Role role : guild.getRoles()) {
            if (role.getName().equalsIgnoreCase(MUTED_ROLE_NAME)
                    && !role.getId().equals(LEGACY_MUTED_ROLE_ID)) {
                rememberMutedRoleId(guildId, role.getId());
                ensureMutedRoleAtBottom(role);
                return role;
            }
        }

        try {
            Role created = guild.createRole()
                    .setName(MUTED_ROLE_NAME)
                    .setMentionable(false)
                    .setHoisted(false)
                    .complete();
            rememberMutedRoleId(guildId, created.getId());
            ensureMutedRoleAtBottom(created);
            return created;
        } catch (Exception ex) {
            return null;
        }
    }

    private void ensureMutedRoleAtBottom(Role mutedRole) {
        try {
            mutedRole.getGuild().modifyRolePositions()
                    .selectPosition(mutedRole)
                    .moveTo(1)
                    .queue(success -> {
                    }, failure -> {
                    });
        } catch (Exception ignored) {
        }
    }

    public void ensureMutedRoleChannelPermissions(Guild guild) {
        Role mutedRole = getMutedRole(guild);
        if (mutedRole == null) {
            return;
        }

        ensureMutedRoleAtBottom(mutedRole);

        for (GuildChannel channel : guild.getChannels()) {
            if (!(channel instanceof IPermissionContainer permissionContainer)) {
                continue;
            }

            if (isAllowedChannel(channel)) {
                permissionContainer.upsertPermissionOverride(mutedRole)
                        .clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .grant(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY)
                        .queue(success -> {
                        }, failure -> {
                            System.err.println("Failed to apply muted allow override in channel "
                                    + channel.getId() + ": " + failure.getMessage());
                        });
            } else {
                permissionContainer.upsertPermissionOverride(mutedRole)
                        .clear(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
                        .deny(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
                        .queue(success -> {
                        }, failure -> {
                            System.err.println("Failed to apply muted deny override in channel "
                                    + channel.getId() + ": " + failure.getMessage());
                        });
            }
        }
    }

    public String validateMuteCanRun(Member member) {
        Guild guild = member.getGuild();
        Role mutedRole = getMutedRole(guild);
        if (mutedRole == null) {
            return "Muted role not found and couldn't be created.";
        }

        Member selfMember = guild.getSelfMember();
        if (!selfMember.canInteract(mutedRole)) {
            return "I can't assign the muted role because it is above my highest role.";
        }

        if (!selfMember.canInteract(member)) {
            return "I can't manage that member because they are above my highest role.";
        }

        for (Role role : member.getRoles()) {
            if (role.isManaged()) {
                continue;
            }

            if (role.getId().equals(mutedRole.getId())) {
                continue;
            }

            if (!selfMember.canInteract(role)) {
                return "I can't remove all roles from that member because one or more roles are above my highest role.";
            }
        }

        return null;
    }

    public void applyMute(Member member, Runnable onSuccess, java.util.function.Consumer<String> onFailure) {
        applyMute(member, null, onSuccess, onFailure);
    }

    public void applyMute(
            Member member,
            Long muteUntilEpochMs,
            Runnable onSuccess,
            java.util.function.Consumer<String> onFailure) {
        String error = validateMuteCanRun(member);
        if (error != null) {
            onFailure.accept(error);
            return;
        }

        Guild guild = member.getGuild();
        Role mutedRole = getMutedRole(guild);
        Member selfMember = guild.getSelfMember();

        List<Role> existingRoles = new ArrayList<>(member.getRoles());
        List<String> snapshot = new ArrayList<>();
        List<Role> toRemove = new ArrayList<>();

        for (Role role : existingRoles) {
            if (role.isManaged()) {
                continue;
            }

            if (role.getId().equals(mutedRole.getId())) {
                continue;
            }

            snapshot.add(role.getId());
            if (selfMember.canInteract(role)) {
                toRemove.add(role);
            }
        }

        List<Role> toAdd = member.getRoles().stream().anyMatch(role -> role.getId().equals(mutedRole.getId()))
                ? List.of()
                : List.of(mutedRole);

        rememberMutedMember(guild.getId(), member.getId(), snapshot, muteUntilEpochMs);
        ensureMutedRoleChannelPermissions(guild);

        guild.modifyMemberRoles(member, toAdd, toRemove)
                .queue(
                        success -> onSuccess.run(),
                        failure -> {
                            clearSnapshot(guild.getId(), member.getId());
                            onFailure.accept(
                                    "I couldn't apply muted role changes. Check role hierarchy and permissions.");
                        });
    }

    public void processMuteExpirations(Guild guild) {
        long now = System.currentTimeMillis();
        List<String> expiredUserIds = new ArrayList<>();

        synchronized (persistenceLock) {
            Map<String, Long> guildDurations = muteUntilByGuild.get(guild.getId());
            if (guildDurations == null || guildDurations.isEmpty()) {
                return;
            }

            for (Map.Entry<String, Long> entry : guildDurations.entrySet()) {
                Long until = entry.getValue();
                if (until != null && until > 0 && now >= until) {
                    expiredUserIds.add(entry.getKey());
                }
            }
        }

        for (String userId : expiredUserIds) {
            Member member = guild.getMemberById(userId);
            if (member == null) {
                clearSnapshot(guild.getId(), userId);
                continue;
            }

            applyUnmute(member, () -> {
            }, failure -> {
            });
        }
    }

    public void applyUnmute(Member member, Runnable onSuccess, java.util.function.Consumer<String> onFailure) {
        Guild guild = member.getGuild();
        Role mutedRole = getMutedRole(guild);
        if (mutedRole == null) {
            onFailure.accept("Muted role not found and couldn't be created.");
            return;
        }

        Member selfMember = guild.getSelfMember();
        if (!selfMember.canInteract(mutedRole)) {
            onFailure.accept("I can't remove muted role because it is above my highest role.");
            return;
        }

        if (!selfMember.canInteract(member)) {
            onFailure.accept("I can't manage that member because they are above my highest role.");
            return;
        }

        List<String> snapshot = consumeSnapshot(guild.getId(), member.getId());
        List<Role> toAdd = new ArrayList<>();
        for (String roleId : snapshot) {
            Role role = guild.getRoleById(roleId);
            if (role == null || role.isManaged()) {
                continue;
            }
            if (selfMember.canInteract(role)) {
                toAdd.add(role);
            }
        }

        List<Role> toRemove = member.getRoles().stream()
                .filter(role -> role.getId().equals(mutedRole.getId()))
                .toList();

        guild.modifyMemberRoles(member, toAdd, toRemove)
                .queue(
                        success -> {
                            removeMutedMember(guild.getId(), member.getId());
                            onSuccess.run();
                        },
                        failure -> onFailure.accept(
                                "I couldn't restore roles for that member. Check role hierarchy and permissions."));
    }

    public void enforceMutedStateOnJoin(Member member) {
        String guildId = member.getGuild().getId();
        String userId = member.getId();
        if (!isMuted(guildId, userId)) {
            return;
        }

        String error = validateMuteCanRun(member);
        if (error != null) {
            return;
        }

        Guild guild = member.getGuild();
        Role mutedRole = getMutedRole(guild);
        Member selfMember = guild.getSelfMember();

        List<Role> toRemove = new ArrayList<>();
        for (Role role : member.getRoles()) {
            if (role.isManaged() || role.getId().equals(mutedRole.getId())) {
                continue;
            }
            if (selfMember.canInteract(role)) {
                toRemove.add(role);
            }
        }

        List<Role> toAdd = member.getRoles().stream().anyMatch(role -> role.getId().equals(mutedRole.getId()))
                ? List.of()
                : List.of(mutedRole);

        guild.modifyMemberRoles(member, toAdd, toRemove).queue(success -> {
        }, failure -> {
        });
    }

    private boolean isMuted(String guildId, String userId) {
        Set<String> mutedUsers = mutedUsersByGuild.get(guildId);
        return mutedUsers != null && mutedUsers.contains(userId);
    }

    private void rememberMutedMember(String guildId, String userId, Collection<String> previousRoles, Long muteUntilEpochMs) {
        synchronized (persistenceLock) {
            mutedUsersByGuild.computeIfAbsent(guildId, ignored -> ConcurrentHashMap.newKeySet()).add(userId);
            preMuteRoleSnapshotsByGuild
                    .computeIfAbsent(guildId, ignored -> new ConcurrentHashMap<>())
                    .put(userId, new ArrayList<>(previousRoles));

            if (muteUntilEpochMs != null && muteUntilEpochMs > 0) {
                muteUntilByGuild.computeIfAbsent(guildId, ignored -> new ConcurrentHashMap<>())
                        .put(userId, muteUntilEpochMs);
            } else {
                Map<String, Long> guildDurations = muteUntilByGuild.get(guildId);
                if (guildDurations != null) {
                    guildDurations.remove(userId);
                    if (guildDurations.isEmpty()) {
                        muteUntilByGuild.remove(guildId);
                    }
                }
            }
            saveState();
        }
    }

    private List<String> consumeSnapshot(String guildId, String userId) {
        synchronized (persistenceLock) {
            Map<String, List<String>> guildSnapshots = preMuteRoleSnapshotsByGuild.get(guildId);
            List<String> roles = guildSnapshots == null ? null : guildSnapshots.remove(userId);
            if (guildSnapshots != null && guildSnapshots.isEmpty()) {
                preMuteRoleSnapshotsByGuild.remove(guildId);
            }
            saveState();
            if (roles == null) {
                return List.of();
            }
            return roles;
        }
    }

    private void clearSnapshot(String guildId, String userId) {
        synchronized (persistenceLock) {
            Map<String, List<String>> guildSnapshots = preMuteRoleSnapshotsByGuild.get(guildId);
            if (guildSnapshots != null) {
                guildSnapshots.remove(userId);
                if (guildSnapshots.isEmpty()) {
                    preMuteRoleSnapshotsByGuild.remove(guildId);
                }
            }

            Set<String> mutedUsers = mutedUsersByGuild.get(guildId);
            if (mutedUsers != null) {
                mutedUsers.remove(userId);
                if (mutedUsers.isEmpty()) {
                    mutedUsersByGuild.remove(guildId);
                }
            }

            Map<String, Long> guildDurations = muteUntilByGuild.get(guildId);
            if (guildDurations != null) {
                guildDurations.remove(userId);
                if (guildDurations.isEmpty()) {
                    muteUntilByGuild.remove(guildId);
                }
            }
            saveState();
        }
    }

    private void removeMutedMember(String guildId, String userId) {
        synchronized (persistenceLock) {
            Set<String> mutedUsers = mutedUsersByGuild.get(guildId);
            if (mutedUsers != null) {
                mutedUsers.remove(userId);
                if (mutedUsers.isEmpty()) {
                    mutedUsersByGuild.remove(guildId);
                }
            }

            Map<String, Long> guildDurations = muteUntilByGuild.get(guildId);
            if (guildDurations != null) {
                guildDurations.remove(userId);
                if (guildDurations.isEmpty()) {
                    muteUntilByGuild.remove(guildId);
                }
            }
            saveState();
        }
    }

    private void loadState() {
        synchronized (persistenceLock) {
            if (!Files.exists(STATE_PATH)) {
                return;
            }

            try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(STATE_PATH))) {
                State state = (State) inputStream.readObject();

                mutedUsersByGuild.clear();
                for (Map.Entry<String, Set<String>> entry : state.mutedUsersByGuild.entrySet()) {
                    mutedUsersByGuild.put(entry.getKey(), ConcurrentHashMap.newKeySet());
                    mutedUsersByGuild.get(entry.getKey()).addAll(entry.getValue());
                }

                preMuteRoleSnapshotsByGuild.clear();
                for (Map.Entry<String, Map<String, List<String>>> guildEntry : state.preMuteRoleSnapshotsByGuild
                        .entrySet()) {
                    Map<String, List<String>> snapshots = new ConcurrentHashMap<>();
                    for (Map.Entry<String, List<String>> memberEntry : guildEntry.getValue().entrySet()) {
                        snapshots.put(memberEntry.getKey(), new ArrayList<>(memberEntry.getValue()));
                    }
                    preMuteRoleSnapshotsByGuild.put(guildEntry.getKey(), snapshots);
                }

                muteUntilByGuild.clear();
                if (state.muteUntilByGuild != null) {
                    for (Map.Entry<String, Map<String, Long>> entry : state.muteUntilByGuild.entrySet()) {
                        muteUntilByGuild.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                    }
                }

                mutedRoleIdByGuild.clear();
                if (state.mutedRoleIdByGuild != null) {
                    mutedRoleIdByGuild.putAll(state.mutedRoleIdByGuild);
                }
            } catch (Exception ex) {
                mutedUsersByGuild.clear();
                preMuteRoleSnapshotsByGuild.clear();
                muteUntilByGuild.clear();
                mutedRoleIdByGuild.clear();
                System.err.println("Failed to load muted role state: " + ex.getMessage());
            }
        }
    }

    private void saveState() {
        synchronized (persistenceLock) {
            try {
                Path parent = STATE_PATH.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                State state = new State(
                        copyMutedUsers(),
                        copySnapshots(),
                        copyMuteDurations(),
                        new HashMap<>(mutedRoleIdByGuild));
                Path tempPath = STATE_PATH.resolveSibling("muted-role-state.tmp");
                try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(tempPath))) {
                    outputStream.writeObject(state);
                }

                try {
                    Files.move(tempPath, STATE_PATH, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tempPath, STATE_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                System.err.println("Failed to save muted role state: " + ex.getMessage());
            }
        }
    }

    private Map<String, Set<String>> copyMutedUsers() {
        Map<String, Set<String>> copy = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : mutedUsersByGuild.entrySet()) {
            copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;
    }

    private Map<String, Map<String, List<String>>> copySnapshots() {
        Map<String, Map<String, List<String>>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> guildEntry : preMuteRoleSnapshotsByGuild.entrySet()) {
            Map<String, List<String>> snapshots = new HashMap<>();
            for (Map.Entry<String, List<String>> memberEntry : guildEntry.getValue().entrySet()) {
                snapshots.put(memberEntry.getKey(), new ArrayList<>(memberEntry.getValue()));
            }
            copy.put(guildEntry.getKey(), snapshots);
        }
        return copy;
    }

    private Map<String, Map<String, Long>> copyMuteDurations() {
        Map<String, Map<String, Long>> copy = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> guildEntry : muteUntilByGuild.entrySet()) {
            copy.put(guildEntry.getKey(), new HashMap<>(guildEntry.getValue()));
        }
        return copy;
    }

    private void rememberMutedRoleId(String guildId, String roleId) {
        synchronized (persistenceLock) {
            mutedRoleIdByGuild.put(guildId, roleId);
            saveState();
        }
    }

    private boolean isAllowedChannel(GuildChannel channel) {
        return ALLOWED_CHANNEL_IDS.contains(channel.getId());
    }

    private static Path resolveStatePath() {
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            return Paths.get(userDir, "data", "muted-role-state.bin");
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Paths.get(userHome, ".discordbot", "muted-role-state.bin");
        }

        return Paths.get(System.getProperty("java.io.tmpdir"), "discordbot", "muted-role-state.bin");
    }

    private static class State implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Map<String, Set<String>> mutedUsersByGuild;
        private final Map<String, Map<String, List<String>>> preMuteRoleSnapshotsByGuild;
        private final Map<String, Map<String, Long>> muteUntilByGuild;
        private final Map<String, String> mutedRoleIdByGuild;

        private State(Map<String, Set<String>> mutedUsersByGuild,
                Map<String, Map<String, List<String>>> preMuteRoleSnapshotsByGuild,
                Map<String, Map<String, Long>> muteUntilByGuild,
                Map<String, String> mutedRoleIdByGuild) {
            this.mutedUsersByGuild = mutedUsersByGuild;
            this.preMuteRoleSnapshotsByGuild = preMuteRoleSnapshotsByGuild;
            this.muteUntilByGuild = muteUntilByGuild;
            this.mutedRoleIdByGuild = mutedRoleIdByGuild;
        }
    }
}
