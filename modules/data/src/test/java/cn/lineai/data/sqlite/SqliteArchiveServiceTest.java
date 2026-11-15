package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteArchiveServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase source;
    private LineCodeDatabase target;

    @Before public void create() {
        source = new LineCodeDatabase(new File(temporary.getRoot(), "source.db"));
        target = new LineCodeDatabase(new File(temporary.getRoot(), "target.db"));
    }

    @After public void close() {
        source.close();
        target.close();
    }

    @Test public void roundTripsWorkspaceFilesAsBinary() throws Exception {
        java.io.File sourceFiles = temporary.newFolder("files-source");
        java.io.File targetFiles = temporary.newFolder("files-target");
        java.io.File notes = new java.io.File(sourceFiles, "workspace/docs/notes.txt");
        notes.getParentFile().mkdirs();
        byte[] binary = new byte[] {0, 1, (byte) 0xFF, 65};
        java.nio.file.Files.write(notes.toPath(), binary);
        java.io.File skill = new java.io.File(sourceFiles, "skills/review/SKILL.md");
        skill.getParentFile().mkdirs();
        java.nio.file.Files.write(skill.toPath(), "# Review".getBytes("UTF-8"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2), sourceFiles).export(bytes);
        new SqliteArchiveService(target, Clock.fixed(3), targetFiles)
                .importFrom(new ByteArrayInputStream(bytes.toByteArray()));

        java.util.List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) names.add(entry.getName());
        }
        java.io.File restored = new java.io.File(targetFiles, "workspace/docs/notes.txt");
        assertTrue(names.toString(), restored.isFile());
        org.junit.Assert.assertArrayEquals(binary,
                java.nio.file.Files.readAllBytes(restored.toPath()));
        assertTrue(new java.io.File(targetFiles, "skills/review/SKILL.md").isFile());
    }

    @Test public void roundTripsEveryTable() {
        SqliteConversationStore conversations = new SqliteConversationStore(source, Clock.fixed(1));
        SqliteMessageStore messages = new SqliteMessageStore(source);
        String conversationId = conversations.create("Trip").id;
        messages.append(Message.builder().id("m1").conversationId(conversationId).seq(1)
                .role(Role.USER).addBlock(ContentBlock.text("hello world")).build());
        new SqliteSettingsStore(source, Clock.fixed(1)).put("ui.themeMode", "light");
        new SqliteModelStore(source).save(ModelConfig.builder().identity("model1", "Mock")
                .endpoint("custom", "mock-model", "https://example.com/v1").build());
        new SqliteErrorLogStore(source, Clock.fixed(1)).record("t", "s", "d");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        cn.lineai.data.api.ArchiveService.Summary exported =
                new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);
        assertEquals(1, exported.conversations);
        assertEquals(1, exported.models);
        assertTrue(exported.settings >= 1);

        cn.lineai.data.api.ArchiveService.Summary imported =
                new SqliteArchiveService(target, Clock.fixed(3))
                        .importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, imported.conversations);
        assertEquals(1, imported.models);

        assertEquals(1, new SqliteConversationStore(target, Clock.fixed(1)).list().size());
        assertEquals("hello world", new SqliteMessageStore(target).load(conversationId).get(0).plainText());
        assertEquals("light", new SqliteSettingsStore(target, Clock.fixed(1)).get("ui.themeMode", ""));
        assertEquals("Mock", new SqliteModelStore(target).selected().displayName());
        assertEquals(1, new SqliteErrorLogStore(target, Clock.fixed(1)).list().size());
    }

    @Test public void exportStripsAllApiKeys() throws Exception {
        new SqliteModelStore(source).save(ModelConfig.builder().identity("model1", "Mock")
                .endpoint("custom", "mock-model", "https://example.com/v1")
                .secret("sk-live-secret").flags(true, true).build());
        SqliteSettingsStore settings = new SqliteSettingsStore(source, Clock.fixed(1));
        settings.put("model.apiKey", "sk-settings-secret");
        settings.put("webSearch.apiKey", "tavily-secret");
        settings.put("webSearch.apiKeyHeader", "Authorization");
        settings.put("ssh.host", "192.168.1.10");
        settings.put("ssh.username", "admin");
        settings.put("ssh.password", "ssh-pass");
        settings.put("ssh.privateKey", "-----BEGIN OPENSSH PRIVATE KEY-----");
        settings.put("ssh.passphrase", "ssh-passphrase");
        settings.put("ui.themeMode", "light");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        cn.lineai.data.api.ArchiveService.Summary exported =
                new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);

        java.util.Map<String, String> entries = zipTexts(bytes.toByteArray());
        org.json.JSONArray models = new org.json.JSONArray(entries.get("models.json"));
        assertEquals(1, models.length());
        assertFalse(models.getJSONObject(0).has("api_key"));
        org.json.JSONArray rows = new org.json.JSONArray(entries.get("settings.json"));
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (int i = 0; i < rows.length(); i++) keys.add(rows.getJSONObject(i).optString("key"));
        assertFalse(keys.contains("model.apiKey"));
        assertFalse(keys.contains("webSearch.apiKey"));
        assertFalse(keys.contains("ssh.password"));
        assertFalse(keys.contains("ssh.privateKey"));
        assertFalse(keys.contains("ssh.passphrase"));
        assertTrue(keys.contains("ssh.host"));
        assertTrue(keys.contains("ssh.username"));
        assertTrue(keys.contains("webSearch.apiKeyHeader"));
        assertTrue(keys.contains("ui.themeMode"));
        assertEquals(rows.length(), exported.settings);

        new SqliteArchiveService(target, Clock.fixed(3))
                .importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("", new SqliteModelStore(target).selected().apiKey);
        assertEquals("", settings(target).get("model.apiKey", ""));
        assertEquals("", settings(target).get("webSearch.apiKey", ""));
        assertEquals("", settings(target).get("ssh.password", ""));
        assertEquals("", settings(target).get("ssh.privateKey", ""));
        assertEquals("", settings(target).get("ssh.passphrase", ""));
        assertEquals("192.168.1.10", settings(target).get("ssh.host", ""));
        assertEquals("Authorization", settings(target).get("webSearch.apiKeyHeader", ""));
        assertEquals("light", settings(target).get("ui.themeMode", ""));
    }

    @Test public void roundTripsProvidersWithoutApiKeys() throws Exception {
        SqliteProviderStore providers = new SqliteProviderStore(source, Clock.fixed(1));
        providers.save(cn.lineai.core.model.ProviderConfig.builder()
                .id("p1")
                .name("DeepSeek")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .endpoint("https://api.deepseek.com", "sk-provider-secret")
                .headers(java.util.Arrays.asList(
                        new cn.lineai.core.model.HttpHeader("Authorization", "Bearer x"),
                        new cn.lineai.core.model.HttpHeader("X-Team", "linecode")))
                .build());
        new SqliteModelStore(source).save(ModelConfig.builder().identity("model1", "Mock")
                .endpoint("p1", "deepseek-chat", "https://api.deepseek.com").build());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);

        java.util.Map<String, String> entries = zipTexts(bytes.toByteArray());
        org.json.JSONArray providerRows = new org.json.JSONArray(entries.get("model_providers.json"));
        assertEquals(1, providerRows.length());
        assertFalse(providerRows.getJSONObject(0).has("api_key"));
        assertTrue(providerRows.getJSONObject(0).getString("headers").contains("Authorization"));

        new SqliteArchiveService(target, Clock.fixed(3))
                .importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        java.util.List<cn.lineai.core.model.ProviderConfig> imported =
                new SqliteProviderStore(target, Clock.fixed(4)).list();
        assertEquals(1, imported.size());
        assertEquals("DeepSeek", imported.get(0).name);
        assertEquals("https://api.deepseek.com", imported.get(0).baseUrl);
        assertEquals("", imported.get(0).apiKey);
        assertEquals(2, imported.get(0).headers.size());
        assertEquals("p1", new SqliteModelStore(target).find("model1").providerId);
    }

    @Test public void importsVersion3ArchiveWithoutProviderTable() throws Exception {
        new SqliteSettingsStore(source, Clock.fixed(1)).put("k", "v");
        new SqliteProviderStore(source, Clock.fixed(1)).save(
                cn.lineai.core.model.ProviderConfig.builder().id("p1").name("DeepSeek")
                        .endpoint("https://api.deepseek.com", "sk").build());
        new SqliteModelStore(source).save(ModelConfig.builder().identity("model1", "Mock")
                .endpoint("p1", "deepseek-chat", "https://api.deepseek.com").build());
        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(exported);
        java.util.Map<String, String> texts = zipTexts(exported.toByteArray());

        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(legacy);
        addToZip(zip, "manifest.json", "{\"format\":\"linecode-archive\",\"version\":3}");
        for (java.util.Map.Entry<String, String> entry : texts.entrySet()) {
            if (entry.getKey().equals("manifest.json")
                    || entry.getKey().equals("model_providers.json")) {
                continue;
            }
            addToZip(zip, entry.getKey(), entry.getValue());
        }
        zip.finish();

        new SqliteArchiveService(target, Clock.fixed(3))
                .importFrom(new ByteArrayInputStream(legacy.toByteArray()));
        assertEquals("v", settings(target).get("k", ""));
        assertTrue(new SqliteProviderStore(target, Clock.fixed(1)).list().isEmpty());
        assertEquals("p1", new SqliteModelStore(target).find("model1").providerId);
    }

    private static SqliteSettingsStore settings(LineCodeDatabase db) {
        return new SqliteSettingsStore(db, Clock.fixed(1));
    }

    private static java.util.Map<String, String> zipTexts(byte[] archive) throws Exception {
        java.util.Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zin.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int read;
                while ((read = zin.read(buffer)) != -1) out.write(buffer, 0, read);
                entries.put(entry.getName(), new String(out.toByteArray(), "UTF-8"));
            }
        }
        return entries;
    }

    @Test public void importReplacesExistingRowsAndIsRepeatable() {
        new SqliteSettingsStore(source, Clock.fixed(1)).put("k", "v");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);
        new SqliteSettingsStore(target, Clock.fixed(1)).put("stale", "1");
        SqliteArchiveService service = new SqliteArchiveService(target, Clock.fixed(3));
        service.importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        service.importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("v", new SqliteSettingsStore(target, Clock.fixed(1)).get("k", ""));
        assertEquals("", new SqliteSettingsStore(target, Clock.fixed(1)).get("stale", ""));
    }

    @Test public void rejectsGarbageAndWrongFormat() {
        try {
            new SqliteArchiveService(target, Clock.fixed(1))
                    .importFrom(new ByteArrayInputStream(new byte[] {1, 2, 3}));
            fail("expected failure");
        } catch (DomainException expected) {
            assertNotNull(expected.code());
        }
    }

    @Test public void roundTripsToolCallsBetweenDevices() {
        SqliteConversationStore conversations = new SqliteConversationStore(source, Clock.fixed(1));
        SqliteMessageStore messages = new SqliteMessageStore(source);
        String conversationId = conversations.create("Tools").id;
        messages.append(Message.builder().id("a1").conversationId(conversationId).seq(1)
                .role(Role.ASSISTANT)
                .toolCalls(java.util.List.of(new ToolCallRecord("call-1", "read", "{\"path\":\"a.txt\"}")))
                .build());
        messages.append(Message.builder().id("t1").conversationId(conversationId).seq(2)
                .role(Role.TOOL).addBlock(ContentBlock.text("file body")).build());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);
        new SqliteArchiveService(target, Clock.fixed(3)).importFrom(
                new ByteArrayInputStream(bytes.toByteArray()));

        java.util.List<Message> restored = new SqliteMessageStore(target).load(conversationId);
        assertEquals(2, restored.size());
        assertEquals(1, restored.get(0).toolCalls.size());
        assertEquals("call-1", restored.get(0).toolCalls.get(0).id);
        assertEquals("read", restored.get(0).toolCalls.get(0).name);
        assertEquals("{\"path\":\"a.txt\"}", restored.get(0).toolCalls.get(0).arguments);
        assertEquals("file body", restored.get(1).plainText());
    }

    @Test public void unsafeArchiveLeavesExistingDataUntouched() throws Exception {
        java.io.File targetFiles = temporary.newFolder("files-unsafe");
        java.io.File live = new java.io.File(targetFiles, "workspace/keep.txt");
        live.getParentFile().mkdirs();
        java.nio.file.Files.write(live.toPath(), "keep".getBytes("UTF-8"));
        new SqliteSettingsStore(target, Clock.fixed(1)).put("ui.themeMode", "light");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes);
        addToZip(zip, "manifest.json", "{\"format\":\"linecode-archive\",\"version\":2}");
        addToZip(zip, "files/workspace/../../evil.txt", "boom");
        zip.finish();

        try {
            new SqliteArchiveService(target, Clock.fixed(3), targetFiles)
                    .importFrom(new ByteArrayInputStream(bytes.toByteArray()));
            fail("expected failure");
        } catch (DomainException expected) {
            assertNotNull(expected.code());
        }
        assertEquals("keep", new String(java.nio.file.Files.readAllBytes(live.toPath()), "UTF-8"));
        assertEquals("light", settings(target).get("ui.themeMode", ""));
        assertFalse(new java.io.File(temporary.getRoot(), "evil.txt").exists());
    }

    @Test public void importsVersion1ArchiveWithoutNewTables() throws Exception {
        new SqliteSettingsStore(source, Clock.fixed(1)).put("k", "v");
        ByteArrayOutputStream exported = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(exported);
        java.util.Set<String> v1Tables = new java.util.HashSet<>(java.util.Arrays.asList(
                "conversations", "messages", "message_blocks", "settings", "models", "error_logs"));
        java.util.Map<String, String> texts = zipTexts(exported.toByteArray());

        ByteArrayOutputStream legacy = new ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(legacy);
        addToZip(zip, "manifest.json", "{\"format\":\"linecode-archive\",\"version\":1}");
        for (java.util.Map.Entry<String, String> entry : texts.entrySet()) {
            String base = entry.getKey().replace(".json", "");
            if (v1Tables.contains(base)) addToZip(zip, entry.getKey(), entry.getValue());
        }
        zip.finish();

        new SqliteArchiveService(target, Clock.fixed(3)).importFrom(
                new ByteArrayInputStream(legacy.toByteArray()));
        assertEquals("v", new SqliteSettingsStore(target, Clock.fixed(1)).get("k", ""));
    }

    private static void addToZip(java.util.zip.ZipOutputStream zip, String name, String content)
            throws Exception {
        zip.putNextEntry(new java.util.zip.ZipEntry(name));
        zip.write(content.getBytes("UTF-8"));
        zip.closeEntry();
    }
}
