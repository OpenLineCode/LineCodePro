package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ProviderModelStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteModelStore raw;
    private SqliteProviderStore providers;
    private ProviderModelStore store;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "provider-models.db"));
        raw = new SqliteModelStore(database);
        providers = new SqliteProviderStore(database, Clock.fixed(5));
        store = new ProviderModelStore(raw, providers);
    }

    @After public void close() {
        database.close();
    }

    private static ModelConfig model(String id, String providerId, String modelId, String baseUrl,
                                     ModelConfig.Protocol protocol) {
        return ModelConfig.builder()
                .identity(id, id + "-name")
                .endpoint(providerId, modelId, baseUrl)
                .secret("model-key")
                .protocol(protocol)
                .context(128000, 4096)
                .build();
    }

    private static ProviderConfig provider(String id, String name, String baseUrl) {
        return ProviderConfig.builder()
                .id(id)
                .name(name)
                .protocol(ModelConfig.Protocol.ANTHROPIC_MESSAGES)
                .endpoint(baseUrl, "provider-key")
                .headers(Arrays.asList(new HttpHeader("Authorization", "Bearer provider"),
                        new HttpHeader("X-Team", "core")))
                .build();
    }

    @Test public void resolvesEndpointAndLabelFromProvider() {
        providers.save(provider("p1", "Anthropic", "https://api.anthropic.com"));
        raw.save(model("m1", "p1", "claude-sonnet-4", "https://stale.example.com",
                ModelConfig.Protocol.OPENAI_COMPATIBLE));

        ModelConfig resolved = store.find("m1");
        assertNotNull(resolved);
        assertEquals("p1", resolved.providerId);
        assertEquals("claude-sonnet-4", resolved.modelId);
        assertEquals("https://api.anthropic.com", resolved.baseUrl);
        assertEquals("Anthropic", resolved.providerLabel);
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, resolved.protocol);
        assertEquals("provider-key", resolved.apiKey);
    }

    @Test public void mergesHeadersWithModelWinningByName() {
        providers.save(provider("p1", "Anthropic", "https://api.anthropic.com"));
        raw.save(model("m1", "p1", "claude", "", ModelConfig.Protocol.ANTHROPIC_MESSAGES)
                .toBuilder()
                .advanced("", Arrays.asList(new HttpHeader("Authorization", "Bearer model"),
                        new HttpHeader("X-Model", "1")))
                .build());

        List<HttpHeader> headers = store.find("m1").headers;
        assertEquals(3, headers.size());
        assertEquals("Authorization", headers.get(0).name);
        assertEquals("Bearer model", headers.get(0).value);
        assertEquals("X-Team", headers.get(1).name);
        assertEquals("core", headers.get(1).value);
        assertEquals("X-Model", headers.get(2).name);
        assertEquals("1", headers.get(2).value);
    }

    @Test public void localModelBypassesProviderResolution() {
        providers.save(provider("p1", "Anthropic", "https://api.anthropic.com"));
        raw.save(model("local", "p1", "qwen", "/models/qwen.gguf",
                ModelConfig.Protocol.LOCAL_GGUF));

        ModelConfig resolved = store.find("local");
        assertEquals("/models/qwen.gguf", resolved.baseUrl);
        assertEquals("model-key", resolved.apiKey);
    }

    @Test public void missingProviderLeavesLegacyModelUnchanged() {
        raw.save(model("legacy", "ghost", "legacy-model", "https://legacy.example.com",
                ModelConfig.Protocol.OPENAI_COMPATIBLE));
        ModelConfig resolved = store.find("legacy");
        assertEquals("ghost", resolved.providerId);
        assertEquals("https://legacy.example.com", resolved.baseUrl);
        assertEquals("model-key", resolved.apiKey);
        assertEquals(1, store.list().size());
    }

    @Test public void saveCreatesImplicitProviderForNonLocalModel() {
        store.save(model("m1", "", "deepseek-chat", "https://api.deepseek.com/v1",
                ModelConfig.Protocol.OPENAI_COMPATIBLE));

        List<ProviderConfig> all = providers.list();
        assertEquals(1, all.size());
        ProviderConfig implicit = all.get(0);
        assertEquals("api.deepseek.com", implicit.name);
        assertEquals("https://api.deepseek.com/v1", implicit.baseUrl);
        assertEquals("model-key", implicit.apiKey);

        ModelConfig resolved = store.find("m1");
        assertEquals(implicit.id, resolved.providerId);
        assertEquals("api.deepseek.com", resolved.providerLabel);
        assertEquals("https://api.deepseek.com/v1", resolved.baseUrl);

        ModelConfig rawRow = raw.find("m1");
        assertEquals(implicit.id, rawRow.providerId);
        assertEquals("https://api.deepseek.com/v1", rawRow.baseUrl);
        assertEquals(1, providers.list().size());
    }

    @Test public void saveReusesTheExistingProvider() {
        providers.save(provider("p1", "Anthropic", "https://api.anthropic.com"));
        store.save(model("m1", "p1", "claude", "https://stale.example.com",
                ModelConfig.Protocol.OPENAI_COMPATIBLE));

        assertEquals(1, providers.list().size());
        ModelConfig rawRow = raw.find("m1");
        assertEquals("p1", rawRow.providerId);
        assertEquals("https://api.anthropic.com", rawRow.baseUrl);
        assertEquals("Anthropic", rawRow.providerLabel);
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, rawRow.protocol);
    }

    @Test public void localModelSaveCreatesNoProvider() {
        store.save(model("local", "", "qwen", "/models/qwen.gguf",
                ModelConfig.Protocol.LOCAL_GGUF));
        assertTrue(providers.list().isEmpty());
        assertEquals("/models/qwen.gguf", raw.find("local").baseUrl);
    }

    @Test public void selectionAndDeletionDelegateToTheRawStore() {
        store.save(model("m1", "", "one", "https://one.example.com", ModelConfig.Protocol.OPENAI_COMPATIBLE));
        store.save(model("m2", "", "two", "https://two.example.com", ModelConfig.Protocol.OPENAI_COMPATIBLE));
        assertEquals(2, store.list().size());
        assertNotNull(store.selected());
        store.select("m2");
        assertEquals("m2", store.selected().id);
        store.delete(List.of("m2"));
        assertEquals(1, store.list().size());
        assertEquals("m1", store.selected().id);
        assertNull(store.find("unknown"));
        assertFalse(store.list().get(0).providerId.isEmpty());
    }

    @Test public void seedFromSettingsDelegatesToTheRawStore() {
        SqliteSettingsStore settings = new SqliteSettingsStore(database, Clock.fixed(1));
        settings.put(SqliteModelStore.KEY_BASE_URL, "https://api.deepseek.com");
        settings.put(SqliteModelStore.KEY_MODEL_ID, "deepseek-chat");
        settings.put(SqliteModelStore.KEY_MODEL_NAME, "DeepSeek Chat");
        store.seedFromSettings(settings);
        assertEquals(1, store.list().size());
        assertEquals("DeepSeek Chat", store.selected().displayName());
        assertTrue(providers.list().isEmpty());
    }
}
