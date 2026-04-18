package com.bot.reactions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ReactionRolePanelService {
    private static final Path STORAGE_PATH = Paths.get("reaction-role-panel-message.txt");

    public void savePanelMessage(String channelId, String messageId) {
        try {
            Files.writeString(STORAGE_PATH, channelId + System.lineSeparator() + messageId, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to save reaction role panel message ID.", ex);
        }
    }

    public PanelMessage loadPanelMessage() {
        if (!Files.exists(STORAGE_PATH)) {
            return null;
        }

        try {
            String[] lines = Files.readString(STORAGE_PATH, StandardCharsets.UTF_8).split("\\R");
            if (lines.length < 2) {
                return null;
            }

            String channelId = lines[0].trim();
            String messageId = lines[1].trim();
            if (channelId.isEmpty() || messageId.isEmpty()) {
                return null;
            }

            return new PanelMessage(channelId, messageId);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read reaction role panel message ID.", ex);
        }
    }

    public void clearMessageId() {
        try {
            Files.deleteIfExists(STORAGE_PATH);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to delete reaction role panel message ID.", ex);
        }
    }

    public record PanelMessage(String channelId, String messageId) {
    }
}