package com.hotel.converter.adapter

/**
 * Registry of available channel adapters.
 */
object AdapterRegistry {
    private val adapters: Map<String, ChannelAdapter> =
        listOf(
            ProviderAAdapter(),
            ProviderBAdapter(),
        ).associateBy { it.providerName }

    fun getAdapter(provider: String): ChannelAdapter? = adapters[provider]
}
