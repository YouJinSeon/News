package com.teddyjs.news.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.teddyjs.news.data.local.dao.ArticleDao
import com.teddyjs.news.data.local.entity.ArticleEntity

@Database(
    entities = [ArticleEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE articles ADD COLUMN viewCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
