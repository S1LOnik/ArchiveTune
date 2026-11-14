package moe.rukamori.archivetune.ui.mascot

import moe.rukamori.archivetune.constants.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotAiCommentatorTest {
    @Test
    fun defaultFastModelsForSupportedChatProviders() {
        assertEquals("gpt-4o-mini", MascotAiCommentator.defaultFastModelFor(AiProvider.CHATGPT))
        assertEquals("gemini-2.0-flash-lite", MascotAiCommentator.defaultFastModelFor(AiProvider.GEMINI))
        assertEquals("mistral-small-latest", MascotAiCommentator.defaultFastModelFor(AiProvider.MISTRAL))
        assertEquals("google/gemini-2.0-flash-lite-001", MascotAiCommentator.defaultFastModelFor(AiProvider.OPENROUTER))
    }

    @Test
    fun unsupportedOrCustomProvidersReturnEmptyDefaultFastModel() {
        assertEquals("", MascotAiCommentator.defaultFastModelFor(AiProvider.CUSTOM))
        assertEquals("", MascotAiCommentator.defaultFastModelFor(AiProvider.NONE))
        assertEquals("", MascotAiCommentator.defaultFastModelFor(AiProvider.DEEPL))
    }

    @Test
    fun everyProviderHandledWithoutException() {
        AiProvider.entries.forEach { provider ->
            val model = MascotAiCommentator.defaultFastModelFor(provider)
            assertTrue(model.isNotEmpty() || provider in setOf(AiProvider.CUSTOM, AiProvider.NONE, AiProvider.DEEPL))
        }
    }
}
