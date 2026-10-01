package org.cubexmc.metro.lifecycle

import java.util.Locale
import org.cubexmc.metro.Metro
import org.cubexmc.metro.integration.BlueMapIntegration
import org.cubexmc.metro.integration.DynmapIntegration
import org.cubexmc.metro.integration.MapIntegration
import org.cubexmc.metro.integration.SquaremapIntegration
import org.cubexmc.scheduler.CubexScheduler
import org.cubexmc.scheduler.CubexTask

/**
 * Owns optional web map integrations and their refresh lifecycle.
 */
class MapIntegrationLifecycle {
    private val plugin: Metro
    private val integrationFactory: IntegrationFactory
    private val refreshScheduler: RefreshScheduler
    private var activeIntegration: MapIntegration? = null
    private var activeProvider: String? = null
    private var refreshQueued = false
    private var refreshTaskId: Any? = null

    constructor(plugin: Metro) : this(
        plugin,
        IntegrationFactory { provider -> createDefaultIntegration(plugin, provider) },
        CubexRefreshScheduler(),
    )

    constructor(
        plugin: Metro,
        integrationFactory: IntegrationFactory,
        refreshScheduler: RefreshScheduler,
    ) {
        this.plugin = plugin
        this.integrationFactory = integrationFactory
        this.refreshScheduler = refreshScheduler
    }

    fun enable() {
        activateConfiguredProvider()
    }

    fun disable() {
        cancelQueuedRefresh()
        activeIntegration?.disable()
        activeIntegration = null
        activeProvider = null
    }

    fun refresh() {
        try {
            if (!activateConfiguredProvider()) {
                return
            }
            activeIntegration?.refresh()
        } catch (e: Throwable) {
            plugin.logger.warning("[Map] Failed to refresh $activeProvider integration: ${e.message}")
        }
    }

    fun requestRefresh() {
        if (plugin.configFacade == null || !plugin.configFacade.isMapIntegrationEnabled() || refreshQueued) {
            return
        }
        refreshQueued = true
        val delay = plugin.configFacade.getMapRefreshDelayTicks()
        refreshTaskId = refreshScheduler.schedule(
            plugin,
            Runnable {
                refreshQueued = false
                refreshTaskId = null
                refresh()
            },
            delay,
            -1L,
        )
    }

    private fun cancelQueuedRefresh() {
        val taskId = refreshTaskId
        if (taskId != null) {
            refreshScheduler.cancel(taskId)
            refreshTaskId = null
        }
        refreshQueued = false
    }

    private fun activateConfiguredProvider(): Boolean {
        if (plugin.configFacade == null || !plugin.configFacade.isMapIntegrationEnabled()) {
            disable()
            return false
        }

        val provider = normalizeProvider(plugin.configFacade.getMapProvider())
        if (provider == null || provider.isBlank()) {
            plugin.logger.warning("[Map] map_integration.provider is empty. Skipping map integration.")
            disable()
            return false
        }

        if (AUTO_PROVIDER == provider) {
            return activateAutoProvider()
        }

        if (activeIntegration != null && provider == activeProvider) {
            return true
        }

        disable()
        try {
            val integration = integrationFactory.create(provider)
            activeIntegration = integration
            if (integration == null) {
                plugin.logger.warning("[Map] Unknown map provider '$provider'. Expected AUTO, BLUEMAP, DYNMAP, or SQUAREMAP.")
                return false
            }
            if (!integration.isAvailable()) {
                plugin.logger.info("[Map] $provider is not available, skipping map integration.")
                activeIntegration = null
                return false
            }
            activeProvider = provider
            integration.enable()
            return true
        } catch (e: Throwable) {
            plugin.logger.info("[Map] $provider API not found, skipping map integration.")
            activeIntegration = null
            activeProvider = null
            return false
        }
    }

    private fun activateAutoProvider(): Boolean {
        val currentIntegration = activeIntegration
        if (currentIntegration != null && activeProvider != null && currentIntegration.isAvailable()) {
            return true
        }

        disable()
        for (provider in AUTO_PROVIDER_ORDER) {
            try {
                val integration = integrationFactory.create(provider)
                if (integration == null || !integration.isAvailable()) {
                    continue
                }
                activeIntegration = integration
                activeProvider = provider
                plugin.logger.info("[Map] AUTO selected $provider map provider.")
                integration.enable()
                return true
            } catch (e: Throwable) {
                plugin.logger.info("[Map] $provider API not found, skipping map integration.")
            }
        }
        plugin.logger.warning("[Map] AUTO provider could not find BlueMap, Dynmap, or Squaremap.")
        return false
    }

    private fun normalizeProvider(provider: String?): String? = provider?.trim()?.uppercase(Locale.ROOT)

    fun interface IntegrationFactory {
        fun create(provider: String): MapIntegration?
    }

    interface RefreshScheduler {
        fun schedule(plugin: Metro, task: Runnable, delay: Long, period: Long): Any?

        fun cancel(taskId: Any)
    }

    private class CubexRefreshScheduler : RefreshScheduler {
        private var scheduler: CubexScheduler? = null

        override fun schedule(plugin: Metro, task: Runnable, delay: Long, period: Long): Any {
            val current = scheduler ?: CubexScheduler.create(plugin).also { scheduler = it }
            return if (period < 0L) current.runGlobalLater(task, delay) else current.runGlobalTimer(task, delay, period)
        }

        override fun cancel(taskId: Any) {
            (taskId as CubexTask).cancel()
        }
    }

    companion object {
        private const val AUTO_PROVIDER = "AUTO"
        private val AUTO_PROVIDER_ORDER = listOf("BLUEMAP", "DYNMAP", "SQUAREMAP")

        private fun createDefaultIntegration(plugin: Metro, provider: String): MapIntegration? =
            when (provider) {
                "BLUEMAP" -> BlueMapIntegration(plugin)
                "DYNMAP" -> DynmapIntegration(plugin)
                "SQUAREMAP" -> SquaremapIntegration(plugin)
                else -> null
            }
    }
}
