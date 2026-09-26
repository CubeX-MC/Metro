# Isolated physics probe

**Use only in a disposable server directory.** This test plugin replaces blocks at x=0..399, y=63..64, z=0/4/8 in the first world, force-loads chunks, spawns occupied carts, and shuts the server down. It is not part of the deployed Metro jar.

1. Build Metro with `.\gradlew.bat :Metro:build :Metro:jarGate` from the monorepo root.
2. Prepare an isolated Paper 26.1.2 server using Java 25. Install the built Metro jar and Vault. An economy provider is not needed.
3. Before generating the test world, set `initial-enabled-packs=vanilla,minecart_improvements`, a distinct `level-name`, `pause-when-empty-seconds=-1`, and a localhost-only unused port. Set `spigot.yml` → `world-settings.default.entity-activation-range.misc: 0`. The probe supplies force-load tickets; activation is also necessary for a headless occupied-cart test.
4. Compile this source with Java 25 against the test server libraries and the built Metro jar. For example, from this directory (replace both paths):

```powershell
$probeServer = 'C:\path\to\disposable-server'
$metroJar = 'C:\path\to\plugins\Metro\build\libs\metro-1.1.9.jar'
$probeClasspath = ((Get-ChildItem "$probeServer\libraries" -Filter *.jar -Recurse | ForEach-Object FullName) -join ';') + ';' + $metroJar
New-Item -ItemType Directory -Path classes -Force | Out-Null
javac -proc:none -cp $probeClasspath -d classes PhysicsProbe.java
Copy-Item plugin.yml classes/plugin.yml
jar cf "$probeServer\plugins\PhysicsProbe.jar" -C classes .
```

5. Start the disposable server. Read `FEATURE_DETECTED` and `RESULT` in the log. Expect experimental peak displacement greater than 1.5 blocks/tick, `heldX=0.5`, both passenger counts 1, and the braking cart stopped within 0.8 blocks of x=100.5.
6. For the legacy control, use a newly generated world and `initial-enabled-packs=vanilla`. Expect `FEATURE_DETECTED=false` and peak displacement 1.5 blocks/tick. The probe invokes the experimental braking controller directly in both cases to isolate that controller; the separate task unit tests verify actual per-world dispatch.

To check migration, seed a v4 Metro config with `speed_control.cruise_control.enabled: true` before startup. Confirm v5 removes the section and creates a migration backup. Keep all generated files outside the source tree.
