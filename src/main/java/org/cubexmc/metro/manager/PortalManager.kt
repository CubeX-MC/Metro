package org.cubexmc.metro.manager

import java.io.File
import java.util.UUID
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.logging.Level
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Minecart
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import org.cubexmc.metro.Metro
import org.cubexmc.metro.model.Portal
import org.cubexmc.metro.train.TrainMovementTask
import org.cubexmc.metro.util.MetroConstants
import org.cubexmc.metro.util.MinecartEjector
import org.cubexmc.metro.util.SchedulerUtil

/**
 * 管理矿车传送门的加载、保存、查询和传送逻辑。
 */
class PortalManager(private val plugin: Metro) {
    private val portalFile: File = File(plugin.dataFolder, "portals.yml")
    private var portalConfig: YamlConfiguration? = null
    private val portals: MutableMap<String, Portal> = HashMap()
    private val lock: ReadWriteLock = ReentrantReadWriteLock()
    @Volatile
    private var isDirty = false

    init {
        load()
    }

    fun load() {
        lock.writeLock().lock()
        try {
            portals.clear()
            if (!portalFile.exists()) {
                portalConfig = YamlConfiguration()
                return
            }
            portalConfig = YamlConfiguration.loadConfiguration(portalFile)
            val section = portalConfig?.getConfigurationSection("portals") ?: return

            for (id in section.getKeys(false)) {
                val portalSection = section.getConfigurationSection(id)
                if (portalSection != null) {
                    portals[id] = Portal.fromConfig(id, portalSection)
                }
            }
            plugin.logger.info("[Portal] Loaded ${portals.size} portals.")
        } finally {
            lock.writeLock().unlock()
        }
    }

    fun save() {
        isDirty = true
    }

    fun processAsyncSave() {
        if (!isDirty) {
            return
        }

        try {
            val yamlDataFinal = buildSnapshot()
            isDirty = false
            plugin.saveCoordinator.submitSnapshot(portalFile.toPath(), yamlDataFinal)
        } catch (exception: Exception) {
            isDirty = true
            plugin.logger.log(Level.SEVERE, "[Portal] Failed to process portals.yml save", exception)
        }
    }

    fun forceSaveSync() {
        if (!isDirty) {
            return
        }

        try {
            val yamlDataFinal = buildSnapshot()
            isDirty = false
            plugin.saveCoordinator.saveNow(portalFile.toPath(), yamlDataFinal)
        } catch (exception: Exception) {
            isDirty = true
            plugin.logger.log(Level.SEVERE, "[Portal] Failed to save portals.yml", exception)
        }
    }

    fun createPortal(id: String, entrance: Location, ownerId: UUID?): Portal {
        val portal = Portal(id)
        portal.setEntrance(entrance)
        portal.owner = ownerId
        lock.writeLock().lock()
        try {
            portals[id] = portal
        } finally {
            lock.writeLock().unlock()
        }
        save()
        return portal
    }

    fun setPortalOwner(id: String, ownerId: UUID?): Boolean {
        lock.writeLock().lock()
        try {
            val portal = portals[id] ?: return false
            portal.owner = ownerId
        } finally {
            lock.writeLock().unlock()
        }
        save()
        return true
    }

    fun addPortalAdmin(id: String, adminId: UUID): Boolean {
        val changed: Boolean
        lock.writeLock().lock()
        try {
            val portal = portals[id] ?: return false
            changed = portal.addAdmin(adminId)
        } finally {
            lock.writeLock().unlock()
        }
        if (changed) {
            save()
        }
        return changed
    }

    fun removePortalAdmin(id: String, adminId: UUID): Boolean {
        val changed: Boolean
        lock.writeLock().lock()
        try {
            val portal = portals[id] ?: return false
            changed = portal.removeAdmin(adminId)
        } finally {
            lock.writeLock().unlock()
        }
        if (changed) {
            save()
        }
        return changed
    }

    fun deletePortal(id: String): Boolean {
        val removedPortal: Boolean
        lock.writeLock().lock()
        try {
            val removed = portals.remove(id)
            removedPortal = removed != null
        } finally {
            lock.writeLock().unlock()
        }
        if (!removedPortal) {
            return false
        }
        if (plugin.lineManager != null) {
            plugin.lineManager.delPortalFromAllLines(id)
        }
        save()
        return true
    }

    fun setDestination(id: String, destination: Location): Boolean {
        lock.writeLock().lock()
        try {
            val portal = portals[id] ?: return false
            portal.setDestination(destination)
        } finally {
            lock.writeLock().unlock()
        }
        save()
        return true
    }

    fun getPortal(id: String?): Portal? {
        lock.readLock().lock()
        try {
            return portals[id]
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getAllPortals(): List<Portal> {
        lock.readLock().lock()
        try {
            return ArrayList(portals.values)
        } finally {
            lock.readLock().unlock()
        }
    }

    fun getPortalAt(railLocation: Location?): Portal? {
        lock.readLock().lock()
        try {
            for (portal in portals.values) {
                if (portal.matchesLocation(railLocation)) {
                    return portal
                }
            }
            return null
        } finally {
            lock.readLock().unlock()
        }
    }

    fun teleportMinecart(sourceCart: Minecart, portal: Portal) {
        val destination: Location? = portal.getDestination()
        if (destination == null || worldOf(destination) == null) {
            plugin.logger.warning("[Portal] Invalid destination for portal: ${portal.id}")
            return
        }

        var passenger: Player? = null
        if (sourceCart.passengers.isNotEmpty()) {
            val entity = sourceCart.passengers[0]
            if (entity is Player) {
                passenger = entity
            }
        }

        playEffects(sourceCart.location)
        val finalPassenger = passenger

        val oldTask = TrainMovementTask.getTaskFor(sourceCart)
        if (oldTask != null) {
            oldTask.setTeleporting(true)
        }

        if (passenger != null) {
            MinecartEjector.eject(sourceCart)
        }

        val sourcePdc = sourceCart.persistentDataContainer
        val minecartKey = MetroConstants.getMinecartKey()
        val isMetroCart = minecartKey != null && sourcePdc.has(minecartKey, PersistentDataType.BYTE)
        val teleportDelay = plugin.configFacade.getPortalTeleportDelay()

        SchedulerUtil.entityRun(
            plugin,
            sourceCart,
            Runnable {
                if (sourceCart.isValid) {
                    sourceCart.remove()
                }
            },
            teleportDelay.toLong(),
            -1L,
        )

        SchedulerUtil.regionRun(
            plugin,
            destination,
            Runnable {
                val destWorld = worldOf(destination) ?: return@Runnable
                if (!destWorld.isChunkLoaded(destination.blockX shr 4, destination.blockZ shr 4)) {
                    plugin.logger.warning("[Portal] Destination chunk is not loaded for portal: ${portal.id}")
                    return@Runnable
                }

                val newCart = destWorld.spawn(destination, Minecart::class.java)
                if (isMetroCart && minecartKey != null) {
                    newCart.persistentDataContainer.set(minecartKey, PersistentDataType.BYTE, 1.toByte())
                    newCart.customName = MetroConstants.METRO_MINECART_NAME
                    newCart.isCustomNameVisible = false
                }

                if (finalPassenger != null && finalPassenger.isOnline) {
                    plugin.bedrockCompatibility.teleportAndMountPassenger(finalPassenger, destination, newCart)
                        .thenAccept { success ->
                            SchedulerUtil.regionRun(
                                plugin,
                                destination,
                                Runnable {
                                    if (!success) {
                                        plugin.logger.warning(
                                            "[Portal] Failed to teleport or remount passenger for portal: ${portal.id}",
                                        )
                                        if (newCart.isValid) {
                                            newCart.remove()
                                        }
                                        if (oldTask != null) {
                                            oldTask.setTeleporting(false)
                                        }
                                        return@Runnable
                                    }
                                    if (finalPassenger.isOnline && newCart.isValid) {
                                        if (oldTask != null) {
                                            oldTask.transferMinecart(newCart)
                                        }
                                        applyExitVelocity(newCart, destination)
                                    }
                                },
                                2L,
                                -1L,
                            )
                        }
                } else {
                    if (oldTask != null) {
                        oldTask.transferMinecart(newCart)
                    }
                    applyExitVelocity(newCart, destination)
                }

                playEffects(destination)
            },
            teleportDelay.toLong(),
            -1L,
        )

        if (finalPassenger != null && teleportDelay > 0) {
            val args = LanguageManager.args()
            LanguageManager.put(args, "portal_id", portal.id)
            finalPassenger.sendTitle(
                plugin.languageManager.getMessage("portal.teleport_title", args),
                plugin.languageManager.getMessage("portal.teleport_subtitle", args),
                5,
                teleportDelay,
                5,
            )
        }
    }

    private fun applyExitVelocity(cart: Minecart, destination: Location) {
        val yaw = destination.yaw
        val rad = Math.toRadians(yaw.toDouble())
        val direction = Vector(-kotlin.math.sin(rad), 0.0, kotlin.math.cos(rad)).normalize()
        cart.velocity = direction.multiply(plugin.configFacade.getCartSpeed())
    }

    private fun playEffects(loc: Location?) {
        val world = worldOf(loc)
        if (loc == null || world == null) {
            return
        }

        if (plugin.configFacade.isPortalEffectParticles()) {
            world.spawnParticle(Particle.PORTAL, loc.clone().add(0.0, 1.0, 0.0), 50, 0.5, 0.5, 0.5, 0.5)
            world.spawnParticle(Particle.END_ROD, loc.clone().add(0.0, 1.5, 0.0), 20, 0.3, 0.3, 0.3, 0.05)
        }

        if (plugin.configFacade.isPortalEffectSound()) {
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
        }
    }

    private fun worldOf(location: Location?): World? {
        if (location == null) {
            return null
        }
        return try {
            location.world
        } catch (exception: IllegalArgumentException) {
            null
        }
    }

    private fun buildSnapshot(): String {
        val snapshot = YamlConfiguration()
        lock.readLock().lock()
        try {
            val portalIds = ArrayList(portals.keys)
            portalIds.sort()
            for (portalId in portalIds) {
                val portal = portals[portalId] ?: continue
                val section: ConfigurationSection = snapshot.createSection("portals.${portal.id}")
                portal.toConfig(section)
            }
            return snapshot.saveToString()
        } finally {
            lock.readLock().unlock()
        }
    }
}
