package io.github.theminionooo.tokenmonitor.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VendorMappingTest {
    @Test
    fun modelsMapToTheVendorBehindThem() {
        assertEquals("openai", vendorOf("gpt-5.6-sol"))
        assertEquals("openai", vendorOf("GPT-5.5"))
        assertEquals("openai", vendorOf("o3-mini"))
        assertEquals("openai", vendorOf("codex-auto-review"))
        assertEquals("openai", vendorOf("codex"))
        assertEquals("claude", vendorOf("claude-fable-5-1"))
        assertEquals("claude", vendorOf("Claude Code"))
        assertEquals("deepseek", vendorOf("deepseek-v4-pro"))
        assertEquals("gemini", vendorOf("gemini-3-pro"))
        assertEquals("gemini", vendorOf("gemma-3"))
        assertEquals("xai", vendorOf("grok-4"))
        assertEquals("meta", vendorOf("llama-4-maverick"))
        assertEquals("mistral", vendorOf("codestral-latest"))
        assertEquals("qwen", vendorOf("qwen3-coder"))
        assertEquals("qwen", vendorOf("qmodel_latest"))
        assertEquals("nvidia", vendorOf("nemotron-3-super"))
        assertEquals("stepfun", vendorOf("step-3.5-flash"))
        assertEquals("kimi", vendorOf("moonshot-kimi-k2"))
        assertEquals("zai", vendorOf("glm-5.2"))
        assertEquals("cohere", vendorOf("command-r-plus"))
        assertEquals("xiaomi", vendorOf("mimo-v2"))
        assertEquals("minimax", vendorOf("abab-7"))
        assertEquals("doubao", vendorOf("doubao-seed"))
        assertEquals("hunyuan", vendorOf("hunyuan-t1"))
        assertEquals("opencode", vendorOf("big-pickle"))
        assertEquals("opencode", vendorOf("opencode"))
        assertEquals("openrouter", vendorOf("openrouter/auto"))
        assertEquals("zed", vendorOf("zed-agent"))
    }

    @Test
    fun labelsFollowTheDesktop() {
        assertEquals("Claude Code", "claude".displayName())
        assertEquals("OpenCode", "opencode".displayName())
        assertEquals("LM Studio", "lmstudio".displayName())
        assertEquals("Factory", "factory".providerLabel())
        assertEquals("NVIDIA", "nvidia".displayName())
        assertEquals("StepFun", "stepfun".displayName())
        assertEquals("gpt-5.6-sol", "gpt-5.6-sol".displayName())
        assertEquals("claude-fable-5-1", "claude-fable-5-1".displayName())
        assertEquals("stealth/ox-alpha", "stealth/ox-alpha".displayName())
        assertEquals("Studio Workstation", "studio_workstation".displayName())
        assertEquals("Claude", "claude".providerName())
    }

    @Test
    fun unknownNamesStayNeutral() {
        assertNull(vendorOf("stealth/ox-alpha"))
        assertNull(vendorOf("local-model"))
        assertNull(vendorOf(""))
    }
}
