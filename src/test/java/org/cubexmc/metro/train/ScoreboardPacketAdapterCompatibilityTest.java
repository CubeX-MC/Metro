package org.cubexmc.metro.train;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class ScoreboardPacketAdapterCompatibilityTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "1.18.2-R0.1-SNAPSHOT",
        "1.21.11-R0.1-SNAPSHOT",
        "26.1.2.build.74",
        "26.2-R0.1-SNAPSHOT",
        "26.2.build.32"
    })
    void bundledLibraryRecognizesSupportedServerVersions(String bukkitVersion) throws Exception {
        Server server = mock(Server.class);
        when(server.getBukkitVersion()).thenReturn(bukkitVersion);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);

            // Exercise the real runtime dependency's version selector without a live NMS server.
            // This detects the 2.7.4 regression: 26.2 was absent from its supported version list.
            Class<?> loader = Class.forName(
                "net.megavex.scoreboardlibrary.implementation.PacketAdapterLoader");
            Method selectAdapter = loader.getDeclaredMethod("findAndLoadImplementationClass");
            selectAdapter.setAccessible(true);

            assertNotNull(selectAdapter.invoke(null),
                "The bundled scoreboard library must recognize " + bukkitVersion);
        }
    }
}
