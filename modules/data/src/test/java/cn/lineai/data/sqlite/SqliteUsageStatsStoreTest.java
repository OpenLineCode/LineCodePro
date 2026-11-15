package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.UsageStats;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteUsageStatsStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteMessageStore messages;
    private SqliteUsageStatsStore usage;
    private String conversationId;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "usage.db"));
        messages = new SqliteMessageStore(database);
        usage = new SqliteUsageStatsStore(database);
        conversationId = new SqliteConversationStore(database, Clock.fixed(1)).create("usage").id;
    }

    @After public void close() {
        database.close();
    }

    private void append(String id, long seq, Role role, String modelId, String providerId,
                        int input, int output, int cached) {
        messages.append(Message.builder()
                .id(id)
                .conversationId(conversationId)
                .seq(seq)
                .role(role)
                .model(modelId, providerId)
                .tokens(input, output, cached)
                .build());
    }

    @Test public void emptyDatabaseYieldsAnEmptySnapshot() {
        UsageStats stats = usage.load();
        assertTrue(stats.isEmpty());
        assertEquals(0L, stats.totalTokens());
        assertTrue(stats.models.isEmpty());
    }

    @Test public void aggregatesOnlyAssistantMessages() {
        append("a1", 1, Role.ASSISTANT, "deepseek-chat", "custom", 100, 50, 20);
        append("a2", 2, Role.ASSISTANT, "deepseek-chat", "custom", 200, 100, 10);
        append("a3", 3, Role.ASSISTANT, "claude", "anthropic", 500, 200, 0);
        append("u1", 4, Role.USER, "deepseek-chat", "custom", 900, 900, 900);
        append("t1", 5, Role.TOOL, "claude", "anthropic", 700, 700, 700);

        UsageStats stats = usage.load();
        assertEquals(800L, stats.inputTokens);
        assertEquals(350L, stats.outputTokens);
        assertEquals(30L, stats.cachedTokens);
        assertEquals(1150L, stats.totalTokens());
        assertEquals(2, stats.models.size());

        UsageStats.ModelUsage first = stats.models.get(0);
        assertEquals("claude", first.modelId);
        assertEquals("anthropic", first.providerId);
        assertEquals(700L, first.totalTokens());
        assertEquals(1, first.requests);

        UsageStats.ModelUsage second = stats.models.get(1);
        assertEquals("deepseek-chat", second.modelId);
        assertEquals("custom", second.providerId);
        assertEquals(450L, second.totalTokens());
        assertEquals(2, second.requests);
    }

    @Test public void ordersModelsByTotalConsumptionDescending() {
        append("a1", 1, Role.ASSISTANT, "tiny", "p", 10, 10, 0);
        append("a2", 2, Role.ASSISTANT, "huge", "p", 1000, 1000, 0);
        append("a3", 3, Role.ASSISTANT, "medium", "p", 100, 100, 0);

        UsageStats stats = usage.load();
        assertEquals(3, stats.models.size());
        assertEquals("huge", stats.models.get(0).modelId);
        assertEquals("medium", stats.models.get(1).modelId);
        assertEquals("tiny", stats.models.get(2).modelId);
    }

    @Test public void cacheRateUsesTheWeightedRatio() {
        append("a1", 1, Role.ASSISTANT, "m", "p", 100, 50, 25);
        append("a2", 2, Role.ASSISTANT, "m", "p", 300, 150, 5);

        UsageStats stats = usage.load();
        assertEquals(0.075d, stats.cacheRate(), 0.0001d);
    }
}
