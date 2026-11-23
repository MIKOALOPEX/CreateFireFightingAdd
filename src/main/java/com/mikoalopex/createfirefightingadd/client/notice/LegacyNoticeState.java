package com.mikoalopex.createfirefightingadd.client.notice;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Client-side persistence for the original Firefighting Add compatibility
 * notice.
 *
 * <p>The marker lives in the client config directory, not in world or server
 * data, so it follows the installation instead of a single save. The file is
 * shared by the split projects and is written only after the player closes the
 * notice, which keeps the warning one-time across the split releases.</p>
 */
public final class LegacyNoticeState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ACKNOWLEDGED = "acknowledged";

    private final Path file;
    private boolean acknowledged;

    public LegacyNoticeState(Path file) {
        this.file = Objects.requireNonNull(file, "file");
    }

    public Path file() {
        return file;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    /**
     * Reads the marker. A missing file is a normal first run. A malformed or
     * unreadable file leaves the notice unacknowledged so the warning is not
     * lost silently.
     *
     * @return true when the file is absent or valid
     */
    public boolean load() {
        acknowledged = false;
        if (!Files.isRegularFile(file)) {
            return true;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json != null && json.has(ACKNOWLEDGED)) {
                var value = json.get(ACKNOWLEDGED);
                acknowledged = value.isJsonPrimitive()
                    && value.getAsJsonPrimitive().isBoolean()
                    && value.getAsBoolean();
            }
            return true;
        } catch (IOException | RuntimeException e) {
            acknowledged = false;
            return false;
        }
    }

    /**
     * Marks the notice acknowledged and persists the marker. The in-memory flag
     * is set even when saving fails, so the notice stays closed for this session.
     *
     * @return true when the marker was written
     */
    public boolean acknowledge() {
        acknowledged = true;
        return save();
    }

    private boolean save() {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            JsonObject json = new JsonObject();
            json.addProperty(ACKNOWLEDGED, true);
            Files.writeString(temporary, GSON.toJson(json), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
