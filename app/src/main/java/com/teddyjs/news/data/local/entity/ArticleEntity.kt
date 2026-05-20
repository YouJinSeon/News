package com.teddyjs.news.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "articles")
data class ArticleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val summary: String,
    val aiSummary: String? = null,
    val url: String,
    val imageUrl: String? = null,
    val source: String,
    val category: String,
    val publishedAt: Long,
    val isBookmarked: Boolean = false,
    val viewCount: Int = 0,
    val keywords: String = "", // comma-separated
    val fetchedAt: Long = System.currentTimeMillis(),
)
