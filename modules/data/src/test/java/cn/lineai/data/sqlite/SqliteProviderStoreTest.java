package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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
public class SqliteProviderStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteProviderStore providers;
    private long now = 10L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "providers.db"));
        providers = new SqliteProviderStore(database, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    private static ProviderConfig provider(String id, String name) {
        return ProviderConfig.builder()
                .id(id)
                .name(name)
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .endpoint("https://" + id + ".example.com/v1", "sk-" + id)
                .headers(Arrays.asList(new HttpHeader("Authorization", "Bearer " + id),
                        new HttpHeader("X-Team", "linecode")))
                .build();
    }

    @Test public void listsAlphabeticallyByName() {
        providers.save(provider("zeta", "Zeta"));
        providers.save(provider("alpha", "Alpha"));
        List<ProviderConfig> all = providers.list();
        assertEquals(2, all.size());
        assertEquals("Alpha", all.get(0).name);
        assertEquals("Zeta", all.get(1).name);
    }

    @Test public void findsExistingAndIgnoresUnknownIdentifiers() {
        providers.save(provider("p1", "Primary"));
        ProviderConfig found = providers.find("p1");
        assertNotNull(found);
        assertEquals("Primary", found.name);
        assertEquals("https://p1.example.com/v1", found.baseUrl);
        assertEquals("sk-p1", found.apiKey);
        assertEquals(ModelConfig.Protocol.OPENAI_COMPATIBLE, found.protocol);
        assertNull(providers.find("missing"));
    }

    @Test public void roundTripsHeadersAndTimestamps() {
        providers.save(provider("p1", "Primary"));
        ProviderConfig found = providers.find("p1");
        assertEquals(2, found.headers.size());
        assertEquals("Authorization", found.headers.get(0).name);
        assertEquals("Bearer p1", found.headers.get(0).value);
        assertEquals("X-Team", found.headers.get(1).name);
        assertEquals(10L, found.createdAt);
        assertEquals(10L, found.updatedAt);
    }

    @Test public void updateKeepsCreatedAtAndStampsUpdatedAt() {
        providers.save(provider("p1", "Primary"));
        long created = providers.find("p1").createdAt;
        now = 99L;
        providers.save(providers.find("p1").toBuilder().name("Renamed").build());
        ProviderConfig updated = providers.find("p1");
        assertEquals(created, updated.createdAt);
        assertEquals(99L, updated.updatedAt);
        assertEquals("Renamed", updated.name);
        assertEquals(1, providers.list().size());
    }

    @Test public void blankNameFallsBackToEndpointHost() {
        providers.save(ProviderConfig.builder().id("p1")
                .endpoint("https://api.deepseek.com/v1", "sk").build());
        assertEquals("api.deepseek.com", providers.find("p1").name);
    }

    @Test public void generatesAnIdentifierWhenMissing() {
        providers.save(ProviderConfig.builder().name("Generated")
                .endpoint("https://api.example.com", "sk").build());
        List<ProviderConfig> all = providers.list();
        assertEquals(1, all.size());
        assertFalse(all.get(0).id.isEmpty());
    }

    @Test public void deletesByIdentifier() {
        providers.save(provider("p1", "Primary"));
        providers.save(provider("p2", "Secondary"));
        providers.delete("p1");
        assertEquals(1, providers.list().size());
        assertEquals("p2", providers.list().get(0).id);
        providers.delete("never-stored");
        assertEquals(1, providers.list().size());
    }

    @Test public void rejectsNullProvider() {
        try {
            providers.save(null);
            org.junit.Assert.fail("expected failure");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("provider"));
        }
    }
}
