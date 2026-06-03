package com.sudoku.game.model

/**
 * All AI provider configuration. Immutable — every mutation returns a new
 * instance (same pattern as [SessionTelemetry] / [DemoController]). Persisted as
 * JSON by `ProviderRepository`; the model itself stays Android-free so its
 * transitions are unit-testable.
 */
data class AiSettings(
    val providers: List<AiProvider> = emptyList(),
    val activeProviderId: String? = null
) {
    /** The currently selected provider, or null if none configured/selected. */
    val activeProvider: AiProvider?
        get() = providers.firstOrNull { it.id == activeProviderId }

    /** Adds a new provider, or replaces the existing one with the same id. The
     *  first provider ever added becomes active automatically. */
    fun upsert(provider: AiProvider): AiSettings {
        val next = if (providers.any { it.id == provider.id }) {
            providers.map { if (it.id == provider.id) provider else it }
        } else {
            providers + provider
        }
        return copy(providers = next, activeProviderId = activeProviderId ?: provider.id)
    }

    /** Removes a provider; if it was active, active falls back to the first
     *  remaining provider (or null when none are left). */
    fun remove(id: String): AiSettings {
        val next = providers.filterNot { it.id == id }
        val active = if (activeProviderId == id) next.firstOrNull()?.id else activeProviderId
        return copy(providers = next, activeProviderId = active)
    }

    /** Marks a provider active. No-op if the id isn't present. */
    fun setActive(id: String): AiSettings =
        if (providers.any { it.id == id }) copy(activeProviderId = id) else this

    /** Sets the selected model on one provider. No-op if the provider is absent. */
    fun setModel(providerId: String, model: String): AiSettings {
        if (providers.none { it.id == providerId }) return this
        return copy(providers = providers.map {
            if (it.id == providerId) it.copy(activeModel = model) else it
        })
    }
}
