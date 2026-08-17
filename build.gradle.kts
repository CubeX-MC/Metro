import com.github.jengelman.gradle.plugins.shadow.transformers.ServiceFileTransformer

plugins { id("cubex-kotlin-plugin") }

version = "1.1.9"
description = "metro"

dependencies {
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly(CubexDeps.spigotApi("1.18.2-R0.1-SNAPSHOT"))

    testImplementation(CubexDeps.junitJupiter)
    testImplementation("org.mockito:mockito-core:5.18.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.18.0")

    implementation("net.megavex:scoreboard-library-api:2.7.4")
    runtimeOnly("net.megavex:scoreboard-library-implementation:2.7.4")

    implementation(platform("net.kyori:adventure-bom:4.25.0"))
    implementation("net.kyori:adventure-api")
    implementation("net.kyori:adventure-text-serializer-gson")
    implementation("net.kyori:adventure-text-serializer-json")
    implementation("net.kyori:adventure-text-serializer-commons")
    implementation("net.kyori:adventure-text-serializer-legacy")
    implementation("net.kyori:adventure-text-serializer-plain")
    implementation("net.kyori:adventure-text-minimessage")
    implementation("net.kyori:option:1.1.0")
    implementation("com.google.auto.service:auto-service-annotations:1.1.1")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("org.apiguardian:apiguardian-api:1.1.2")
    implementation("org.checkerframework:checker-qual:3.50.0")
    implementation("org.jetbrains:annotations:26.0.2-1")
    implementation("org.jspecify:jspecify:1.0.0")

    implementation("org.incendo:cloud-paper:2.0.0-beta.14")
    implementation("org.incendo:cloud-annotations:2.0.0")
    implementation("org.incendo:cloud-minecraft-extras") {
        version { strictly("2.0.0-beta.10") }
    }
    implementation(project(":modules:cubex-core"))
    implementation(project(":modules:cubex-config"))
    implementation(project(":modules:cubex-i18n"))
    implementation(project(":modules:cubex-scheduler"))
    implementation(project(":modules:cubex-spatial"))
    implementation(project(":modules:cubex-gui"))

    compileOnly("de.bluecolored.bluemap:BlueMapAPI:2.7.2")
    compileOnly("us.dynmap:DynmapCoreAPI:3.7-beta-6")
    compileOnly("xyz.jpenilla:squaremap-api:1.3.12")
}

tasks.shadowJar {
    archiveBaseName.set("metro")
    transformers.removeIf { it is ServiceFileTransformer }
    relocate("com.tcoded.folialib", "org.cubexmc.metro.libs.folialib")
    relocate("net.kyori", "org.cubexmc.metro.libs.kyori")
    relocate("net.megavex.scoreboardlibrary", "org.cubexmc.metro.lib.scoreboardlibrary")
    relocate("org.incendo.cloud", "org.cubexmc.metro.lib.cloud")
    relocate("io.leangen.geantyref", "org.cubexmc.metro.lib.geantyref")
}
