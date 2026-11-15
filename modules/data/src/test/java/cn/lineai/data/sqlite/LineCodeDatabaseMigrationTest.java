package cn.lineai.data.sqlite;

import android.app.Application;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class LineCodeDatabaseMigrationTest {

    /** Columns the v15 models table carried before provider and reasoning support. */
    private static final String V15_MODELS = "CREATE TABLE models ("
            + "id TEXT PRIMARY KEY,"
            + "name TEXT NOT NULL DEFAULT '',"
            + "provider_id TEXT NOT NULL DEFAULT '',"
            + "model_id TEXT NOT NULL DEFAULT '',"
            + "base_url TEXT NOT NULL DEFAULT '',"
            + "api_key TEXT NOT NULL DEFAULT '',"
            + "protocol TEXT NOT NULL DEFAULT 'OPENAI_COMPATIBLE',"
            + "context_size INTEGER NOT NULL DEFAULT 0,"
            + "temperature REAL NOT NULL DEFAULT 0.7,"
            + "max_tokens INTEGER NOT NULL DEFAULT 4096,"
            + "reasoning_effort TEXT NOT NULL DEFAULT 'MEDIUM',"
            + "enabled INTEGER NOT NULL DEFAULT 1,"
            + "is_default INTEGER NOT NULL DEFAULT 0,"
            + "supports_vision INTEGER NOT NULL DEFAULT 0,"
            + "supports_reasoning INTEGER NOT NULL DEFAULT 1,"
            + "supports_video INTEGER NOT NULL DEFAULT 0,"
            + "supports_tools INTEGER NOT NULL DEFAULT 1,"
            + "provider_label TEXT NOT NULL DEFAULT '',"
            + "tool_call_limit INTEGER NOT NULL DEFAULT 200,"
            + "compression_enabled INTEGER NOT NULL DEFAULT 0,"
            + "compression_auto INTEGER NOT NULL DEFAULT 1,"
            + "compression_model_id TEXT NOT NULL DEFAULT '',"
            + "local_model_path TEXT NOT NULL DEFAULT '',"
            + "local_acceleration TEXT NOT NULL DEFAULT 'automatic')";

    /** Columns the v15 messages table carried before the timing columns. */
    private static final String V15_MESSAGES = "CREATE TABLE messages ("
            + "id TEXT PRIMARY KEY,"
            + "conversation_id TEXT NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,"
            + "seq INTEGER NOT NULL,"
            + "role TEXT NOT NULL,"
            + "status TEXT NOT NULL,"
            + "visibility TEXT NOT NULL DEFAULT 'VISIBLE',"
            + "model_id TEXT NOT NULL DEFAULT '',"
            + "provider_id TEXT NOT NULL DEFAULT '',"
            + "tool_call_id TEXT NOT NULL DEFAULT '',"
            + "tool_name TEXT NOT NULL DEFAULT '',"
            + "error_code TEXT NOT NULL DEFAULT '',"
            + "finish_reason TEXT NOT NULL DEFAULT '',"
            + "review_state TEXT NOT NULL DEFAULT '',"
            + "input_tokens INTEGER NOT NULL DEFAULT 0,"
            + "output_tokens INTEGER NOT NULL DEFAULT 0,"
            + "cached_tokens INTEGER NOT NULL DEFAULT 0,"
            + "created_at INTEGER NOT NULL DEFAULT 0,"
            + "updated_at INTEGER NOT NULL DEFAULT 0)";

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private final java.util.List<LineCodeDatabase> opened = new java.util.ArrayList<>();

    @After public void close() {
        for (LineCodeDatabase database : opened) database.close();
    }

    private LineCodeDatabase open(File file) {
        LineCodeDatabase database = new LineCodeDatabase(file);
        opened.add(database);
        return database;
    }

    /** Builds a database file carrying the v15 table shapes and a legacy model row. */
    private File legacyDatabase() {
        File file = new File(temporary.getRoot(), "legacy-v15.db");
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            legacy.execSQL(V15_MODELS);
            legacy.execSQL(V15_MESSAGES);
            legacy.execSQL("INSERT INTO models (id, name, provider_id, model_id, base_url,"
                    + " api_key) VALUES ('m1', 'Legacy', 'custom', 'legacy-model',"
                    + " 'https://legacy.example.com', 'sk-legacy')");
            legacy.execSQL("PRAGMA user_version=15");
        } finally {
            legacy.close();
        }
        return file;
    }

    @Test public void migratesFifteenToSixteen() {
        LineCodeDatabase database = open(legacyDatabase());
        SQLiteDatabase db = database.open();

        assertEquals(16, LineCodeDatabase.VERSION);
        assertEquals(16, userVersion(db));
        assertTrue(columnNames(db, "model_providers").containsAll(java.util.Arrays.asList(
                "id", "name", "protocol", "base_url", "api_key", "headers",
                "created_at", "updated_at")));
        assertTrue(columnNames(db, "models").containsAll(java.util.Arrays.asList(
                "reasoning_levels", "reasoning_field", "headers")));
        assertTrue(columnNames(db, "messages").containsAll(java.util.Arrays.asList(
                "ttft_ms", "duration_ms")));
        try (Cursor cursor = db.rawQuery("SELECT name FROM models WHERE id='m1'", null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals("Legacy", cursor.getString(0));
        }
    }

    @Test public void migratedSchemaMatchesFreshSchema() {
        LineCodeDatabase migrated = open(legacyDatabase());
        migrated.open();
        LineCodeDatabase fresh = open(new File(temporary.getRoot(), "fresh-v16.db"));
        fresh.open();

        for (String table : new String[] {"models", "messages", "model_providers"}) {
            assertEquals(table, columns(fresh.open(), table), columns(migrated.open(), table));
        }
    }

    @Test public void replayingTheMigrationStaysIdempotent() {
        LineCodeDatabase database = open(legacyDatabase());
        SQLiteDatabase db = database.open();
        Map<String, String> before = columns(db, "models");
        Set<String> messageColumnsBefore = columnNames(db, "messages");

        db.execSQL("PRAGMA user_version=15");
        database.close();
        db = database.open();

        assertEquals(16, userVersion(db));
        assertEquals(before, columns(db, "models"));
        assertEquals(messageColumnsBefore, columnNames(db, "messages"));
        assertEquals(0, count(db, "SELECT COUNT(*) FROM model_providers"));
    }

    /** Reads name and column signature (type, not-null, default, primary key) per column. */
    private static Map<String, String> columns(SQLiteDatabase db, String table) {
        Map<String, String> result = new TreeMap<>();
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                result.put(cursor.getString(1), cursor.getString(2) + "|" + cursor.getInt(3)
                        + "|" + cursor.getString(4) + "|" + cursor.getInt(5));
            }
        }
        return result;
    }

    private static Set<String> columnNames(SQLiteDatabase db, String table) {
        return new TreeSet<>(columns(db, table).keySet());
    }

    private static int userVersion(SQLiteDatabase db) {
        return count(db, "PRAGMA user_version");
    }

    private static int count(SQLiteDatabase db, String sql) {
        try (Cursor cursor = db.rawQuery(sql, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    @Test public void modelProvidersTableIsCreatedWithTheCurrentShape() {
        LineCodeDatabase database = open(new File(temporary.getRoot(), "fresh-shape.db"));
        SQLiteDatabase db = database.open();
        assertNotNull(columns(db, "model_providers"));
        assertEquals(8, columns(db, "model_providers").size());
    }
}
