package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Set;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

public class MinecartPhysicsCompatibilityTest {
    public interface FeatureWorld extends World {
        Set<Keyed> getFeatureFlags();
    }

    @Test
    void oldApiAndMissingWorldUseLegacyPhysics() {
        assertFalse(MinecartPhysicsCompatibility.usesExperimentalMovement(null));
        assertFalse(MinecartPhysicsCompatibility.usesExperimentalMovement(mock(World.class)));
    }

    @Test
    void detectsTheFlagPerWorldWithoutCachingItsValue() {
        FeatureWorld world = mock(FeatureWorld.class);
        Keyed experiment = () -> NamespacedKey.minecraft("minecart_improvements");
        Keyed vanilla = () -> NamespacedKey.minecraft("vanilla");
        when(world.getFeatureFlags()).thenReturn(Set.of(vanilla), Set.of(vanilla, experiment), Set.of(vanilla));
        assertFalse(MinecartPhysicsCompatibility.usesExperimentalMovement(world));
        assertTrue(MinecartPhysicsCompatibility.usesExperimentalMovement(world));
        assertFalse(MinecartPhysicsCompatibility.usesExperimentalMovement(world));
    }

    @Test
    void incompatibleImplementationFallsBackToLegacy() {
        FeatureWorld world = mock(FeatureWorld.class);
        when(world.getFeatureFlags()).thenThrow(new UnsupportedOperationException());
        assertFalse(MinecartPhysicsCompatibility.usesExperimentalMovement(world));
    }
}
