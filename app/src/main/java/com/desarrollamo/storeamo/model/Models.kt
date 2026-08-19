package com.desarrollamo.storeamo.model

data class StoreArtifact(
    val platform: String,
    val arch: String?,
    val format: String?,
    val version: String,
    val versionCode: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long?,
    val verified: Boolean,
    val applicationId: String?,
    val verificationReport: String?,
)

data class StoreApp(
    val id: String,
    val name: String,
    val tagline: String,
    val description: String,
    val category: String,
    val featured: Boolean,
    val status: String,
    val supportedPlatforms: List<String>,
    val repository: String?,
    val artifacts: List<StoreArtifact>,
)

data class StoreCatalog(
    val schema: String,
    val version: Int,
    val apps: List<StoreApp>,
)

enum class StoreTab(val label: String, val glyph: String) {
    HOME("Inicio", "◆"),
    APPS("Apps", "▦"),
    UPDATES("Actualizaciones", "↻"),
    LIBRARY("Biblioteca", "▣"),
    SETTINGS("Ajustes", "●"),
}
