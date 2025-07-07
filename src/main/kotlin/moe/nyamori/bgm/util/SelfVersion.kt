package moe.nyamori.bgm.util

import java.util.*

fun getSelfVersion() = runCatching {
    object {}.javaClass.getResourceAsStream("/META-INF/maven/moe.nyamori/bgm-archive-kt/pom.properties")
        ?.use { ins -> Properties().also { it.load(ins) }.getProperty("version") }
        ?: "0.0.0"
}.getOrDefault("0.0.0")