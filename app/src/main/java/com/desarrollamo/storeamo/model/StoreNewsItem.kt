package com.desarrollamo.storeamo.model

data class StoreNewsItem(
    val id: String,
    val appId: String,
    val appName: String,
    val type: String,
    val title: String,
    val summary: String,
    val publishedAt: String,
    val status: String,
    val sourceVisibility: String,
)
