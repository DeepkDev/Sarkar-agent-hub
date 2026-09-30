package com.example.providers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderRegistry {
    val mockProvider = MockProvider()
    val geminiProvider = GeminiProvider()
    val localProvider = LocalProviderStub()

    private val providers = listOf(mockProvider, geminiProvider, localProvider)

    private val _activeProvider = MutableStateFlow<AIProvider>(mockProvider)
    val activeProvider: StateFlow<AIProvider> = _activeProvider.asStateFlow()

    fun getAllProviders(): List<AIProvider> = providers

    fun setActiveProvider(providerId: String): Boolean {
        val target = providers.find { it.id == providerId } ?: return false
        _activeProvider.value = target
        return true
    }
}
