package com.teddyjs.news.di

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import com.teddyjs.news.data.local.NewsDatabase
import com.teddyjs.news.data.local.dao.ArticleDao
import com.teddyjs.news.service.NaverNewsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)  // ← 늘리기
            .readTimeout(60, TimeUnit.SECONDS)     // ← 늘리기
            .writeTimeout(30, TimeUnit.SECONDS)    // ← 추가
            .build()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NewsDatabase =
        Room.databaseBuilder(context, NewsDatabase::class.java, "news_db")
            .addMigrations(NewsDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideArticleDao(db: NewsDatabase): ArticleDao = db.articleDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideNaverNewsService(okHttpClient: OkHttpClient): NaverNewsService {
        return NaverNewsService(okHttpClient)
    }
}
