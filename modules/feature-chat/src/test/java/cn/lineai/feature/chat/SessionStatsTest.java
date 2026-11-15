package cn.lineai.feature.chat;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionStatsTest {

    @Test
    public void aggregatesTokensCachePerformanceAndTools() {
        Message user = message("u1", Role.USER, "go");
        Message first = assistant("a1", 100, 20, 40, 500L, 1200L, 1);
        Message second = assistant("a2", 50, 10, 30, 0L, 2000L, 1);

        SessionStats stats = new SessionStats(Arrays.asList(user, first, second), 5000, 10000);

        assertEquals(150L, stats.inputTokens);
        assertEquals(30L, stats.outputTokens);
        assertEquals(70L, stats.cachedTokens);
        assertEquals(180L, stats.totalTokens());
        assertEquals(5000L, stats.usedTokens);
        assertEquals(10000L, stats.maxTokens);
        assertEquals(50, stats.contextPercent());
        assertEquals("47%", stats.cacheHitText());
        assertEquals(1, stats.ttftSamples);
        assertEquals(500, stats.averageTtftMs());
        assertEquals(3200L, stats.durationMs);
        assertEquals(9.375d, stats.tps(), 0.001d);
        assertEquals("9.4", stats.tpsText());
        assertEquals(2, stats.toolCalls);
        assertEquals(2, stats.assistantTurns);
        assertTrue(stats.hasTokenUsage());
    }

    @Test
    public void emptyTranscriptReportsNoUsage() {
        SessionStats stats = new SessionStats(Collections.<Message>emptyList(), 0, 0);

        assertEquals(0L, stats.totalTokens());
        assertEquals(1L, stats.maxTokens);
        assertEquals(0, stats.contextPercent());
        assertEquals("", stats.cacheHitText());
        assertEquals(0, stats.averageTtftMs());
        assertEquals(0d, stats.tps(), 0d);
        assertEquals("", stats.tpsText());
        assertEquals(0, stats.toolCalls);
        assertFalse(stats.hasTokenUsage());
    }

    @Test
    public void contextPercentIsClampedToAHundred() {
        SessionStats stats = new SessionStats(Collections.<Message>emptyList(), 50000, 1000);
        assertEquals(100, stats.contextPercent());
    }

    @Test
    public void toolCallsAreCountedAcrossNonAssistantRowsToo() {
        Message user = Message.builder().id("u1").role(Role.USER).status(Message.Status.COMPLETE)
                .seq(1).addBlock(ContentBlock.text("go"))
                .toolCalls(Collections.singletonList(
                        new ToolCallRecord("call-1", "read_file", "{}"))).build();
        SessionStats stats = new SessionStats(Collections.singletonList(user), 0, 1000);
        assertEquals(1, stats.toolCalls);
        assertFalse(stats.hasTokenUsage());
    }

    private static Message message(String id, Role role, String text) {
        return Message.builder().id(id).role(role).status(Message.Status.COMPLETE).seq(1)
                .addBlock(ContentBlock.text(text)).times(1L, 1L).build();
    }

    private static Message assistant(String id, int input, int output, int cached, long ttft,
                                     long duration, int calls) {
        Message.Builder builder = Message.builder().id(id).role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE).seq(2)
                .addBlock(ContentBlock.text("answer"))
                .tokens(input, output, cached)
                .timing(ttft, duration)
                .times(2L, 2L);
        for (int index = 0; index < calls; index++) {
            builder.toolCalls(Collections.singletonList(
                    new ToolCallRecord("call-" + id + "-" + index, "read_file", "{}")));
        }
        return builder.build();
    }
}
