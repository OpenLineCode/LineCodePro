package cn.lineai.feature.settings;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import cn.lineai.data.api.SettingsStore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdatePreferencesTest {

    @Test
    public void defaultsToUnmuted() {
        MapSettings settings = new MapSettings();
        assertFalse(new UpdatePreferences(settings).muted());
    }

    @Test
    public void persistsTheMuteFlagUnderTheDocumentedKey() {
        MapSettings settings = new MapSettings();
        UpdatePreferences preferences = new UpdatePreferences(settings);

        preferences.setMuted(true);
        assertTrue(preferences.muted());
        assertEquals("true", settings.values.get("update.muted"));

        preferences.setMuted(false);
        assertFalse(preferences.muted());
        assertEquals("false", settings.values.get("update.muted"));
    }

    /** Minimal in-memory settings store used by preference tests. */
    private static final class MapSettings implements SettingsStore {
        final Map<String, String> values = new HashMap<>();

        @Override public String get(String key, String fallback) {
            return values.getOrDefault(key, fallback);
        }

        @Override public int getInt(String key, int fallback) {
            return Integer.parseInt(get(key, String.valueOf(fallback)));
        }

        @Override public void put(String key, String value) {
            values.put(key, value);
        }

        @Override public void remove(String key) {
            values.remove(key);
        }

        @Override public void invalidate() {
        }

        @Override public Map<String, String> all() {
            return new HashMap<>(values);
        }
    }
}
