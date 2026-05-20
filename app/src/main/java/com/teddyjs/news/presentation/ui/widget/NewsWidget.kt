package com.teddyjs.news.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.teddyjs.news.MainActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NewsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
        val count = prefs.getInt("article_count", 0)
        val articles = (0 until count).map { i ->
            Triple(
                prefs.getString("headline_$i", "") ?: "",
                prefs.getString("source_$i", "") ?: "",
                prefs.getString("article_id_$i", "") ?: "",
            )
        }
        val updatedAt = prefs.getString("updated_at", "") ?: ""

        provideContent {
            NewsWidgetContent(
                context = context,
                articles = articles,
                updatedAt = updatedAt,
            )
        }
    }

    companion object {
        fun updateWidget(
            context: Context,
            articles: List<Triple<String, String, String>>, // title, source, id
        ) {
            val prefs = context.getSharedPreferences("widget_prefs", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putInt("article_count", articles.size)
                articles.take(5).forEachIndexed { i, (title, source, id) ->
                    putString("headline_$i", title)
                    putString("source_$i", source)
                    putString("article_id_$i", id)
                }
                putString(
                    "updated_at",
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                )
                apply()
            }
        }
    }
}

@Composable
fun NewsWidgetContent(
    context: Context,
    articles: List<Triple<String, String, String>>,
    updatedAt: String,
) {
    val mainIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(Color(0xFF0D1117)))
            .padding(0.dp),
    ) {
        // ── 헤더 ──────────────────────────────────────────
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(ColorProvider(Color(0xFF161B22)))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clickable(actionStartActivity(mainIntent)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "📰 뉴스 브리핑",
                style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = ColorProvider(Color.White),
                ),
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "업데이트 $updatedAt",
                style = TextStyle(
                    fontSize = 9.sp,
                    color = ColorProvider(Color(0xFF8B949E)),
                ),
            )
        }

        // ── 기사 목록 ──────────────────────────────────────
        if (articles.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "앱을 실행해 뉴스를 불러오세요",
                    style = TextStyle(
                        fontSize = 12.sp,
                        color = ColorProvider(Color(0xFF8B949E)),
                    ),
                )
            }
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize(),
            ) {
                items(articles) { (title, source, articleId) ->
                    val articleIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("articleId", articleId)
                        putExtra("fromWidget", true)
                    }
                    val index = articles.indexOf(Triple(title, source, articleId))

                    Column(
                        modifier = GlanceModifier
                            .fillMaxWidth()
                            .clickable(actionStartActivity(articleIntent))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                        ) {
                            // 번호
                            Text(
                                text = "${index + 1}",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ColorProvider(
                                        when (index) {
                                            0 -> Color(0xFFFF8F00)
                                            1 -> Color(0xFF58A6FF)
                                            else -> Color(0xFF8B949E)
                                        }
                                    ),
                                ),
                                modifier = GlanceModifier.width(16.dp),
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Column(modifier = GlanceModifier.defaultWeight()) {
                                Text(
                                    text = title,
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ColorProvider(Color(0xFFE6EDF3)),
                                    ),
                                    maxLines = 2,
                                )
                                Spacer(modifier = GlanceModifier.height(2.dp))
                                Text(
                                    text = source,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        color = ColorProvider(Color(0xFF8B949E)),
                                    ),
                                    maxLines = 1,
                                )
                            }
                        }
                        // 구분선
                        if (index < articles.size - 1) {
                            Spacer(modifier = GlanceModifier.height(6.dp))
                            Box(
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(ColorProvider(Color(0xFF30363D))),
                            ) {}
                        }
                    }
                }
            }
        }
    }
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NewsWidget()
}