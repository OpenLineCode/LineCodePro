package cn.lineai.feature.models;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ProviderConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/** Provider save plan: preserved settings, inferred new rows and deletion matching. */
public class ProviderModelPlanTest {

    @Test public void existingRowKeepsCustomSettingsAndGetsNewName() {
        ProviderConfig provider = provider();
        ModelConfig existing = model("model_1", "gpt-5", "Old name");
        ProviderModelPlan plan = ProviderModelPlan.of(provider, Collections.singletonList(existing),
                Collections.singletonList(new ProviderModelPlan.Draft("gpt-5", "New name",
                        existing, false)));
        assertEquals(1, plan.models.size());
        assertTrue(plan.deletedIds.isEmpty());
        ModelConfig saved = plan.models.get(0);
        assertEquals("model_1", saved.id);
        assertEquals("New name", saved.name);
        assertEquals("provider_1", saved.providerId);
        assertEquals("gpt-5", saved.modelId);
        assertEquals(0.3f, saved.temperature, 0.0001f);
        assertEquals(200, saved.toolCallLimit);
        assertEquals(64000, saved.contextSize);
        assertTrue(saved.isDefault);
        assertEquals(provider.baseUrl, saved.baseUrl);
        assertEquals(provider.apiKey, saved.apiKey);
        assertEquals(provider.name, saved.providerLabel);
    }

    @Test public void newRowIsInferredFromTheProfile() {
        ProviderConfig provider = provider();
        ProviderModelPlan plan = ProviderModelPlan.of(provider, new ArrayList<>(),
                Collections.singletonList(new ProviderModelPlan.Draft("claude-sonnet-5-5", "",
                        null, true)));
        assertEquals(1, plan.models.size());
        ModelConfig saved = plan.models.get(0);
        assertTrue(saved.id.startsWith("model_"));
        assertEquals("Claude Sonnet 5.5", saved.displayName());
        assertEquals(1000000, saved.contextSize);
        assertTrue(saved.supportsVision);
        assertEquals(ModelConfig.Protocol.OPENAI_COMPATIBLE, saved.protocol);
    }

    @Test public void removedRowsAreReportedAsDeletions() {
        ProviderConfig provider = provider();
        ModelConfig first = model("model_1", "gpt-5", "First");
        ModelConfig second = model("model_2", "gpt-4", "Second");
        ProviderModelPlan plan = ProviderModelPlan.of(provider, Arrays.asList(first, second),
                Collections.singletonList(new ProviderModelPlan.Draft("gpt-5", "First", first,
                        false)));
        assertEquals(1, plan.models.size());
        assertEquals(Collections.singletonList("model_2"), plan.deletedIds);
    }

    @Test public void blankAndDuplicateModelIdsAreSkipped() {
        ProviderConfig provider = provider();
        ProviderModelPlan plan = ProviderModelPlan.of(provider, new ArrayList<>(), Arrays.asList(
                new ProviderModelPlan.Draft("  ", "Blank", null, false),
                new ProviderModelPlan.Draft("m1", "One", null, false),
                new ProviderModelPlan.Draft("m1", "Duplicate", null, false)));
        assertEquals(1, plan.models.size());
        assertEquals("One", plan.models.get(0).displayName());
    }

    @Test public void providerHeadersAreNotPersistedAsModelHeaders() {
        ProviderConfig provider = provider();
        ModelConfig existing = model("model_1", "gpt-5", "Model").toBuilder()
                .advanced("", Arrays.asList(new HttpHeader("X-Provider", "shared"),
                        new HttpHeader("X-Model", "own")))
                .build();
        ProviderModelPlan plan = ProviderModelPlan.of(provider, Collections.singletonList(existing),
                Collections.singletonList(new ProviderModelPlan.Draft("gpt-5", "Model", existing,
                        false)));
        List<HttpHeader> headers = plan.models.get(0).headers;
        assertEquals(1, headers.size());
        assertEquals("X-Model", headers.get(0).name);
    }

    private ProviderConfig provider() {
        return ProviderConfig.builder()
                .id("provider_1")
                .name("Example")
                .endpoint("https://api.example.com/v1", "key")
                .headers(Collections.singletonList(new HttpHeader("X-Provider", "shared")))
                .times(1L, 2L)
                .build();
    }

    private ModelConfig model(String id, String modelId, String name) {
        return ModelConfig.builder()
                .identity(id, name)
                .endpoint("provider_1", modelId, "https://api.example.com/v1")
                .providerLabel("Example")
                .toolCalls(200)
                .secret("key")
                .protocol(ModelConfig.Protocol.OPENAI_COMPATIBLE)
                .context(64000, 4096)
                .sampling(0.3f, ModelConfig.ReasoningEffort.MEDIUM)
                .flags(true, true)
                .build();
    }
}
