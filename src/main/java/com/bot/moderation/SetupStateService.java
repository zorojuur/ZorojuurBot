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
import java.util.HashSet;
import java.util.Set;

public final class SetupStateService {
    private static final Path STATE_PATH = resolveStatePath();
    private static final SetupStateService INSTANCE = new SetupStateService();

    private final Set<String> configuredGuildIds = new HashSet<>();
    private final Object persistenceLock = new Object();

    private SetupStateService() {
        loadState();
    }

    public static SetupStateService getInstance() {
        return INSTANCE;
    }

    public boolean isSetupComplete(String guildId) {
        synchronized (persistenceLock) {
            return configuredGuildIds.contains(guildId);
        }
    }

    public void markSetupComplete(String guildId) {
        synchronized (persistenceLock) {
            configuredGuildIds.add(guildId);
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
                configuredGuildIds.clear();
                configuredGuildIds.addAll(state.configuredGuildIds);
            } catch (Exception ex) {
                configuredGuildIds.clear();
                System.err.println("Failed to load setup state: " + ex.getMessage());
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

                State state = new State(new HashSet<>(configuredGuildIds));
                Path tempPath = STATE_PATH.resolveSibling("setup-state.tmp");
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
                System.err.println("Failed to save setup state: " + ex.getMessage());
            }
        }
    }

    private static Path resolveStatePath() {
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            return Paths.get(userDir, "data", "setup-state.bin");
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Paths.get(userHome, ".discordbot", "setup-state.bin");
        }

        return Paths.get(System.getProperty("java.io.tmpdir"), "discordbot", "setup-state.bin");
    }

    private static class State implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final Set<String> configuredGuildIds;

        private State(Set<String> configuredGuildIds) {
            this.configuredGuildIds = configuredGuildIds;
        }
    }
}
