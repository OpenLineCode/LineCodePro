package cn.lineai.feature.models;

import cn.lineai.core.model.HttpHeader;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.ModelPreset;
import cn.lineai.core.model.ProviderConfig;
import org.junit.Test;
import static org.junit.Assert.*;

/** Validation and serialization rules of the pure provider form. */
public class ProviderFormTest {

    @Test public void requiresNameEndpointAndKey() {
        ProviderForm form = ProviderForm.blank();
        assertEquals(ProviderForm.Error.MISSING_NAME, form.validateForSave(false));
        form.name = "My provider";
        assertEquals(ProviderForm.Error.MISSING_BASE_URL, form.validateForSave(false));
        form.baseUrl = "https://api.example.com/v1";
        assertEquals(ProviderForm.Error.MISSING_KEY, form.validateForSave(false));
        form.apiKey = "secret";
        assertEquals(ProviderForm.Error.NONE, form.validateForSave(false));
        assertTrue(form.canSave(false));
    }

    @Test public void trimsFieldsAndBuildsAProvider() {
        ProviderForm form = ProviderForm.blank();
        form.name = "  My provider  ";
        form.protocol = ModelConfig.Protocol.ANTHROPIC_MESSAGES;
        form.baseUrl = " https://api.anthropic.com ";
        form.apiKey = "  key  ";
        ProviderConfig provider = form.build(1234L, () -> "generated");
        assertEquals("generated", provider.id);
        assertEquals("My provider", provider.name);
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, provider.protocol);
        assertEquals("https://api.anthropic.com", provider.baseUrl);
        assertEquals("key", provider.apiKey);
        assertEquals(1234L, provider.updatedAt);
        assertEquals(1234L, provider.createdAt);
    }

    @Test public void editingKeepsIdentityAndCreationTime() {
        ProviderConfig existing = ProviderConfig.builder()
                .id("provider_1")
                .name("Existing")
                .endpoint("https://api.example.com/v1", "old")
                .times(77L, 88L)
                .build();
        ProviderForm form = ProviderForm.from(existing);
        form.name = "Renamed";
        ProviderConfig saved = form.build(999L, () -> "unused");
        assertEquals("provider_1", saved.id);
        assertEquals("Renamed", saved.name);
        assertEquals(77L, saved.createdAt);
        assertEquals(999L, saved.updatedAt);
    }

    @Test public void presetSetsProtocolAndEndpoint() {
        ProviderForm form = ProviderForm.blank();
        form.applyPreset(ModelPreset.find("claude"));
        assertEquals(ModelConfig.Protocol.ANTHROPIC_MESSAGES, form.protocol);
        assertEquals("https://api.anthropic.com", form.baseUrl);
        assertEquals(ProviderForm.Error.MISSING_NAME, form.validateForSave(false));
    }

    @Test public void dropsBlankHeaderRowsAndTrimsNamedOnes() {
        ProviderForm form = ProviderForm.blank();
        form.name = "My provider";
        form.baseUrl = "https://api.example.com/v1";
        form.apiKey = "key";
        form.headers.add(new HttpHeader("", "orphan"));
        form.headers.add(new HttpHeader("  X-Title ", "  LineCode  "));
        ProviderConfig provider = form.build(1L, () -> "id");
        assertEquals(1, provider.headers.size());
        assertEquals("X-Title", provider.headers.get(0).name);
        assertEquals("LineCode", provider.headers.get(0).value);
    }

    @Test public void repairsAPastedEndpointAndRejectsAnUnusableOne() {
        ProviderForm form = ProviderForm.blank();
        form.name = "Kilo";
        form.apiKey = "key";
        form.baseUrl = "kilocode.ai/api/openrouter";
        assertEquals(ProviderForm.Error.NONE, form.validateForSave(false));
        assertEquals("https://kilocode.ai/api/openrouter",
                form.build(1L, () -> "id").baseUrl);

        form.baseUrl = "ftp://kilocode.ai/v1";
        assertEquals(ProviderForm.Error.INVALID_BASE_URL, form.validateForSave(false));
        form.baseUrl = "http://127.0.0.1:11434/v1";
        assertEquals(ProviderForm.Error.NONE, form.validateForSave(false));
        form.baseUrl = "http://192.168.1.10:11434/v1";
        assertEquals(ProviderForm.Error.NONE, form.validateForSave(false));
    }

    @Test public void publicCleartextFollowsTheHttpAllowance() {
        ProviderForm form = ProviderForm.blank();
        form.name = "Gateway";
        form.apiKey = "key";
        form.baseUrl = "http://api.example.com/v1";
        assertEquals(ProviderForm.Error.INVALID_BASE_URL, form.validateForSave(false));
        assertEquals(ProviderForm.Error.NONE, form.validateForSave(true));
        assertTrue(form.canSave(true));
    }

    @Test public void localProtocolIsNotSelectable() {
        ProviderForm form = ProviderForm.blank();
        assertTrue(form.protocolSelectable());
        form.protocol = ModelConfig.Protocol.LOCAL_GGUF;
        assertFalse(form.protocolSelectable());
        assertEquals(3, ProviderForm.supportedProtocols().length);
    }
}
