package com.bot.moderation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;

public final class CaseService {
    private static final Path CASE_STATE_PATH = resolveCaseStatePath();
    private static final CaseService INSTANCE = new CaseService();

    private final AtomicLong nextCaseId = new AtomicLong(1);
    private final Map<Long, CaseRecord> casesById = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    private CaseService() {
        loadState();
    }

    public static CaseService getInstance() {
        return INSTANCE;
    }

    public CaseRecord addCase(
            CaseType type,
            String guildId,
            String channelId,
            String targetId,
            String targetName,
            String moderatorId,
            String moderatorName,
            String reason,
            String extra) {
        long id = nextCaseId.getAndIncrement();
        CaseRecord record = new CaseRecord(
                id,
                type,
                guildId,
                channelId,
                targetId,
                targetName,
                moderatorId,
                moderatorName,
                reason,
                extra,
                Instant.now());
        casesById.put(id, record);
        saveState();
        return record;
    }

    public CaseRecord getCase(long caseId) {
        return casesById.get(caseId);
    }

    public CaseRecord updateCaseReason(String guildId, long caseId, String newReason) {
        CaseRecord existing = casesById.get(caseId);
        if (existing == null || !existing.guildId().equals(guildId)) {
            return null;
        }

        CaseRecord updated = new CaseRecord(
                existing.id(),
                existing.type(),
                existing.guildId(),
                existing.channelId(),
                existing.targetId(),
                existing.targetName(),
                existing.moderatorId(),
                existing.moderatorName(),
                newReason,
                existing.extra(),
                existing.createdAt());
        casesById.put(caseId, updated);
        saveState();
        return updated;
    }

    public CaseRecord deleteCase(String guildId, long caseId) {
        CaseRecord existing = casesById.get(caseId);
        if (existing == null || !existing.guildId().equals(guildId)) {
            return null;
        }

        CaseRecord removed = casesById.remove(caseId);
        if (removed != null) {
            saveState();
        }
        return removed;
    }

    public int clearWarningsForUser(String guildId, String userId, int maxToDelete) {
        List<CaseRecord> warnings = new ArrayList<>();
        for (CaseRecord record : casesById.values()) {
            if (record.type() == CaseType.WARN && record.guildId().equals(guildId)
                    && userId.equals(record.targetId())) {
                warnings.add(record);
            }
        }

        warnings.sort((left, right) -> Long.compare(right.id(), left.id()));
        int limit = maxToDelete <= 0 ? warnings.size() : Math.min(maxToDelete, warnings.size());

        int removed = 0;
        for (int index = 0; index < limit; index++) {
            CaseRecord warning = warnings.get(index);
            if (casesById.remove(warning.id()) != null) {
                removed++;
            }
        }

        if (removed > 0) {
            saveState();
        }
        return removed;
    }

    public List<CaseRecord> getCasesForUser(String guildId, String userId) {
        List<CaseRecord> records = new ArrayList<>();
        for (CaseRecord record : casesById.values()) {
            if (record.guildId().equals(guildId) && userId.equals(record.targetId())) {
                records.add(record);
            }
        }
        records.sort(Comparator.comparing(CaseRecord::id));
        return records;
    }

    public List<CaseRecord> getWarningsForUser(String guildId, String userId) {
        List<CaseRecord> warnings = new ArrayList<>();
        for (CaseRecord record : getCasesForUser(guildId, userId)) {
            if (record.type() == CaseType.WARN) {
                warnings.add(record);
            }
        }
        return warnings;
    }

    public CaseRecord findLatestCaseForTargetName(String guildId, String targetLookup) {
        String normalizedLookup = normalizeTargetLookup(targetLookup);
        if (normalizedLookup == null) {
            return null;
        }

        CaseRecord latest = null;
        for (CaseRecord record : casesById.values()) {
            if (!record.guildId().equals(guildId) || record.targetId() == null || record.targetName() == null) {
                continue;
            }

            String normalizedTarget = normalizeTargetLookup(record.targetName());
            if (normalizedTarget == null) {
                continue;
            }

            if (!normalizedTarget.equals(normalizedLookup)
                    && !normalizedTarget.startsWith(normalizedLookup + "#")) {
                continue;
            }

            if (latest == null || record.id() > latest.id()) {
                latest = record;
            }
        }

        return latest;
    }

    private String normalizeTargetLookup(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase();
        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1).trim();
        }

        while (!normalized.isEmpty() && !Character.isLetterOrDigit(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }

        return normalized.isEmpty() ? null : normalized;
    }

    private void loadState() {
        synchronized (persistenceLock) {
            if (!Files.exists(CASE_STATE_PATH)) {
                return;
            }

            try (ObjectInputStream inputStream = new ObjectInputStream(Files.newInputStream(CASE_STATE_PATH))) {
                State state = (State) inputStream.readObject();
                casesById.clear();
                casesById.putAll(state.casesById);
                nextCaseId.set(Math.max(1, state.nextCaseId));
            } catch (Exception ex) {
                System.err.println("Failed to load case state. Starting with empty case store: " + ex.getMessage());
                casesById.clear();
                nextCaseId.set(1);
            }
        }
    }

    private void saveState() {
        synchronized (persistenceLock) {
            try {
                Path parent = CASE_STATE_PATH.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Path tempPath = CASE_STATE_PATH.resolveSibling("cases-state.tmp");

                State state = new State(nextCaseId.get(), new HashMap<>(casesById));
                try (ObjectOutputStream outputStream = new ObjectOutputStream(Files.newOutputStream(tempPath))) {
                    outputStream.writeObject(state);
                }

                try {
                    Files.move(tempPath, CASE_STATE_PATH, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tempPath, CASE_STATE_PATH, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                System.err.println("Failed to persist case state: " + ex.getMessage());
            }
        }
    }

    private static Path resolveCaseStatePath() {
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            return Paths.get(userDir, "data", "cases-state.bin");
        }

        String userHome = System.getProperty("user.home");
        if (userHome != null && !userHome.isBlank()) {
            return Paths.get(userHome, ".discordbot", "cases-state.bin");
        }

        return Paths.get(System.getProperty("java.io.tmpdir"), "discordbot", "cases-state.bin");
    }

    private static class State implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private final long nextCaseId;
        private final Map<Long, CaseRecord> casesById;

        private State(long nextCaseId, Map<Long, CaseRecord> casesById) {
            this.nextCaseId = nextCaseId;
            this.casesById = casesById;
        }
    }
}
