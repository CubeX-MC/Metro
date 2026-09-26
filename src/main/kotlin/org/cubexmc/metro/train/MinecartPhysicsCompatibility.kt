package org.cubexmc.metro.train

import java.lang.reflect.Method
import org.bukkit.Keyed
import org.bukkit.World

/** Reads the opt-in without linking the 1.18 API baseline to newer Bukkit classes. */
object MinecartPhysicsCompatibility {
    private val featureMethods = object : ClassValue<Method?>() {
        override fun computeValue(type: Class<*>): Method? = try {
            type.getMethod("getFeatureFlags")
        } catch (_: NoSuchMethodException) {
            null
        }
    }

    @JvmStatic
    fun usesExperimentalMovement(world: World?): Boolean {
        if (world == null) return false
        return try {
            val flags = featureMethods.get(world.javaClass)?.invoke(world) as? Iterable<*> ?: return false
            flags.any { it is Keyed && it.key.toString() == "minecraft:minecart_improvements" }
        } catch (_: ReflectiveOperationException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }
}