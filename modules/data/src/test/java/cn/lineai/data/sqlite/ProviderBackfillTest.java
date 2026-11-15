package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.List;

import cn.lineai.core.kernel.time.Clock;
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
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ProviderBackfillTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteModelStore raw;
    private SqliteProviderStore providers;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "backfill.db"));
        raw = new SqliteModelStore(database);
        providers = new SqliteProviderStore(database, Clock.fixed(7));
    }

    @After public void close() {
        database.close();
    }

    private static ModelConfig legacyModel(String id, String providerId, String label,
                                           String baseUrl, String apiKey) {
        return ModelConfig.builder()
                .identity(id, id)
                .endpoint(providerId, id + "-model", baseUrl)
                .providerLabel(label)
                .secret(apiKey)
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .build();
    }

    @Test public void groupsModelsSharingTheLegacyEndpoint() {
        raw.save(legacyModel("a", "custom", "Example", "https://api.example.com/v1", "sk-1"));
        raw.save(legacyModel("b", "custom", "", "https://api.example.com/v1", "sk-1"));
        ProviderBackfill.run(database, providers, raw);

        List<ProviderConfig> all = providers.list();
        assertEquals(1, all.size());
        ProviderConfig created = all.get(0);
        assertEquals("custom", created.id);
        assertEquals("Example", created.name);
        assertEquals("https://api.example.com/v1", created.baseUrl);
        assertEquals("sk-1", created.apiKey);

        ModelConfig first = raw.find("a");
        ModelConfig second = raw.find("b");
        assertEquals("custom", first.providerId);
        assertEquals("Example", first.providerLabel);
        assertEquals("custom", second.providerId);
        assertEquals("Example", second.providerLabel);
    }

    @Test public void generatesAnIdentifierWhenTheModelHadNoProvider() {
        raw.save(legacyModel("a", "", "OpenAI", "https://api.openai.com/v1", "sk-1"));
        ProviderBackfill.run(database, providers, raw);

        List<ProviderConfig> all = providers.list();
        assertEquals(1, all.size());
        ProviderConfig created = all.get(0);
        assertFalse(created.id.isEmpty());
        assertEquals("OpenAI", created.name);
        assertEquals(created.id, raw.find("a").providerId);
    }

    @Test public void derivesTheNameFromTheUrlHostWhenNoLabelExists() {
        raw.save(legacyModel("a", "custom", "", "https://api.deepseek.com/v1", "sk-1"));
        ProviderBackfill.run(database, providers, raw);
        assertEquals("api.deepseek.com", providers.list().get(0).name);
        assertEquals("api.deepseek.com", raw.find("a").providerLabel);
    }

    @Test public void fallsBackToTheGenericNameWithoutLabelOrHost() {
        raw.save(legacyModel("a", "custom", "", "not a url", "sk-1"));
        ProviderBackfill.run(database, providers, raw);
        assertEquals("Provider", providers.list().get(0).name);
    }

    @Test public void skipsExistingProvidersAndLocalModels() {
        providers.save(ProviderConfig.builder().id("kept").name("Kept")
                .endpoint("https://kept.example.com", "sk").build());
        raw.save(legacyModel("a", "kept", "Kept", "https://kept.example.com", "sk"));
        raw.save(ModelConfig.builder().identity("local", "Local")
                .endpoint("", "qwen", "/models/qwen.gguf")
                .protocol(ModelConfig.Protocol.LOCAL_GGUF).build());

        ProviderBackfill.run(database, providers, raw);
        assertEquals(1, providers.list().size());
        assertEquals("", raw.find("local").providerId);
        assertTrue(raw.find("local").providerLabel.isEmpty());
    }

    @Test public void isIdempotent() {
        raw.save(legacyModel("a", "custom", "Example", "https://api.example.com/v1", "sk-1"));
        ProviderBackfill.run(database, providers, raw);
        ProviderBackfill.run(database, providers, raw);
        ProviderBackfill.run(database, providers, raw);
        assertEquals(1, providers.list().size());
        assertEquals("custom", raw.find("a").providerId);
        assertEquals("Example", raw.find("a").providerLabel);
    }

    @Test public void createsSeparateProvidersForDifferentCredentials() {
        raw.save(legacyModel("a", "", "One", "https://api.example.com/v1", "sk-1"));
        raw.save(legacyModel("b", "", "One", "https://api.example.com/v1", "sk-2"));
        ProviderBackfill.run(database, providers, raw);

        List<ProviderConfig> all = providers.list();
        assertEquals(2, all.size());
        String first = raw.find("a").providerId;
        String second = raw.find("b").providerId;
        assertFalse(first.isEmpty());
        assertFalse(second.isEmpty());
        assertFalse(first.equals(second));
    }

    @Test public void keepsTheFirstNonEmptyLabelOfTheGroup() {
        raw.save(legacyModel("a", "custom", "", "https://api.example.com/v1", "sk-1"));
        raw.save(legacyModel("b", "custom", "Second Label", "https://api.example.com/v1", "sk-1"));
        ProviderBackfill.run(database, providers, raw);
        assertEquals("Second Label", providers.list().get(0).name);
    }

    @Test public void separatesGroupsThatShareANonEmptyProviderId() {
        raw.save(legacyModel("a", "custom", "", "https://a.example.com/v1", "sk-1"));
        raw.save(legacyModel("b", "custom", "", "https://b.example.com/v1", "sk-2"));
        ProviderBackfill.run(database, providers, raw);

        assertEquals(2, providers.list().size());
        String first = raw.find("a").providerId;
        String second = raw.find("b").providerId;
        assertFalse(first.equals(second));
        assertEquals("https://a.example.com/v1", providers.find(first).baseUrl);
        assertEquals("https://b.example.com/v1", providers.find(second).baseUrl);
    }
}
