package com.teddyjs.news.data.local.dao

import androidx.room.*
import com.teddyjs.news.data.local.entity.ArticleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles WHERE category IN (:categories) ORDER BY publishedAt DESC LIMIT :limit")
    fun getArticlesByCategories(categories: List<String>, limit: Int = 500): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles ORDER BY publishedAt DESC LIMIT :limit")
    fun getAllArticles(limit: Int = 500): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY publishedAt DESC")
    fun getBookmarkedArticles(): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getArticleById(id: String): ArticleEntity?

    @Upsert
    suspend fun upsertArticles(articles: List<ArticleEntity>)

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun setBookmark(id: String, bookmarked: Boolean)

    @Query("UPDATE articles SET aiSummary = :summary, keywords = :keywords WHERE id = :id")
    suspend fun updateAiSummary(id: String, summary: String, keywords: String)

    @Query("DELETE FROM articles WHERE fetchedAt < :cutoff AND isBookmarked = 0")
    suspend fun deleteOldArticles(cutoff: Long)

    @Query("SELECT COUNT(*) FROM articles WHERE isBookmarked = 1")
    suspend fun getBookmarkCount(): Int

    @Query("DELETE FROM articles WHERE isBookmarked = 0")
    suspend fun deleteAllExceptBookmarked()

    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND id != :currentArticleId")
    suspend fun deleteAllExceptBookmarked(currentArticleId: String = "")

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArticles(articles: List<ArticleEntity>)

    @Query("""
    UPDATE articles SET 
    title = :title,
    summary = :summary,
    fetchedAt = :fetchedAt
    WHERE id = :id
""")
    suspend fun updateArticle(id: String, title: String, summary: String, fetchedAt: Long)

    @Query("UPDATE articles SET viewCount = viewCount + 1 WHERE id = :articleId")
    suspend fun incrementViewCount(articleId: String)
}
