package com.teddyjs.news.presentation.ui.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.domain.model.RewardedFeature
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.presentation.theme.Amber50
import com.teddyjs.news.presentation.theme.Amber400
import com.teddyjs.news.presentation.ui.admob.AdManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

// 주식 시세 샘플 데이터 (실제 구현 시 ViewModel 연동)
data class StockItem(val ticker: String, val price: String, val change: String, val isUp: Boolean)

val sampleStocks = listOf(
    StockItem("KOSPI", "0", "+0.0%", true),
    StockItem("나스닥", "0", "+0.0%", true),
    StockItem("원/달러", "0", "+0.0%", true),
)

suspend fun fetchStockPrice(symbol: String): StockItem? =
    withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol?interval=1d&range=1d"
            val request = Request.Builder().url(url).build()
            val body = OkHttpClient().newCall(request).execute()
                .use { it.body?.string() ?: return@withContext null }

            val json = JSONObject(body)
            val meta = json.getJSONObject("chart")
                .getJSONArray("result")
                .getJSONObject(0)
                .getJSONObject("meta")

            val price = meta.getDouble("regularMarketPrice")
            val prevClose = meta.getDouble("chartPreviousClose")
            val diff = price - prevClose
            val isUp = diff >= 0

            val ticker = when (symbol.trim()) {
                "^KS11" -> "KOSPI"
                "^IXIC" -> "나스닥"
                "KRW=X" -> "원/달러"
                else -> symbol
            }

            // 환율은 +-OO원, 주식은 +-OO포인트
            val changeStr = if (symbol == "KRW=X") {
                val diffStr = String.format("%.1f", Math.abs(diff))
                if (isUp) "+${diffStr}원" else "-${diffStr}원"
            } else {
                val diffStr = String.format("%.2f", Math.abs(diff))
                if (isUp) "+$diffStr" else "-$diffStr"
            }

            val priceStr = when (symbol) {
                "KRW=X" -> String.format("%.1f", price)
                "^KS11", "^IXIC" -> String.format("%.2f", price)
                else -> String.format("%.2f", price)
            }

            StockItem(
                ticker = ticker,
                price = priceStr,
                change = changeStr,
                isUp = isUp,
            )
        }.getOrNull()
    }

@Composable
fun WeatherStockRow(
    weather: WeatherInfo?,
    stocks: List<StockItem>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(95.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 날씨 카드 — weight 더 줘서 잘림 방지
        Surface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(weather?.emoji ?: "🌡️", fontSize = 16.sp)
                    Text(
                        weather?.temp ?: "--°",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Text(
                    (weather?.city ?: "서울")
                        .replace("특별시", "")
                        .replace("광역시", "")
                        .trim(),
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        // 주식 3개
        stocks.take(3).forEach { stock ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stock.ticker,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        maxLines = 1,
                    )
                    Text(
                        stock.price,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        stock.change,
                        fontSize = 9.sp,
                        color = if (stock.isUp) Color(0xFF27500A) else Color(0xFF8A1A1A),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
fun AdRewardButton(
    feature: RewardedFeature,
    remainingUses: Int,
    userPlan: UserPlan,
    onRewarded: () -> Unit,
    onUseConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    var showAdDialog by remember { mutableStateOf(false) }

    if (userPlan == UserPlan.PREMIUM) {
        content()
        return
    }

    if (remainingUses > 0) {
        // 사용 횟수 남아있으면 바로 사용
        Box(modifier = modifier.fillMaxWidth()) {
            content()
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                shape = RoundedCornerShape(10.dp),
                color = Amber50,
            ) {
                Text(
                    "${remainingUses}회 남음",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    fontSize = 9.sp,
                    color = Amber400,
                )
            }
        }
    } else {
        // 광고 시청 버튼
        Button(
            onClick = { showAdDialog = true },
            modifier = modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Amber50),
            shape = RoundedCornerShape(10.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Amber400)
            Spacer(Modifier.width(4.dp))
            Text(
                "광고 1회 보고 ${feature.label} 이용",
                fontSize = 12.sp,
                color = Amber400,
                fontWeight = FontWeight.Medium,
            )
        }
    }

    if (showAdDialog) {
        AlertDialog(
            onDismissRequest = { showAdDialog = false },
            title = { Text("${feature.label} 열기", fontWeight = FontWeight.Medium) },
            text = { Text("광고 1회 시청 후 ${feature.label}을 이용할 수 있어요.\n(${feature.usesPerAd}회 제공)") },
            confirmButton = {
                TextButton(onClick = {
                    showAdDialog = false
                    AdManager.showRewardedAd(
                        activity = activity,
                        onRewarded = onRewarded,
                        onDismissed = {},
                        onFailed = {},
                    )
                }) { Text("광고 보기") }
            },
            dismissButton = {
                TextButton(onClick = { showAdDialog = false }) { Text("취소") }
            }
        )
    }
}

data class WeatherInfo(
    val city: String,
    val temp: String,
    val description: String,
    val emoji: String,
)

suspend fun fetchWeather(context: Context): WeatherInfo? =
    withContext(Dispatchers.IO) {
        runCatching {
            // 위치 권한이 없으면(또는 못 가져오면) null → 서울 기본값으로 진행
            val location = getLastLocation(context)
            val lat = location?.latitude ?: 37.5665
            val lon = location?.longitude ?: 126.9780

            Log.d("JSJSJS","Weather fetch: lat=$lat, lon=$lon")

            val url = "https://api.openweathermap.org/data/2.5/weather" +
                    "?lat=$lat&lon=$lon&appid=${BuildConfig.OPENWEATHER_API_KEY}" +
                    "&units=metric&lang=kr"

            val request = Request.Builder().url(url)
                .header("User-Agent", "TeddyNewsApp/1.0")
                .build()

            val response = OkHttpClient().newCall(request).execute()

            val body = response.body?.string() ?: return@runCatching null
            val json = JSONObject(body)

            val temp = json.getJSONObject("main").getDouble("temp").toInt()
            val desc = json.getJSONArray("weather")
                .getJSONObject(0).getString("description")
            val city = try {
                val nominatimUrl = "https://nominatim.openstreetmap.org/reverse" +
                        "?lat=$lat&lon=$lon&format=json&accept-language=ko"
                val nominatimResponse = OkHttpClient().newCall(
                    Request.Builder()
                        .url(nominatimUrl)
                        .header("User-Agent", "TeddyNewsApp/1.0")  // Nominatim 필수 헤더
                        .build()
                ).execute()
                val nominatimBody = nominatimResponse.body?.string()
                val nominatimJson = JSONObject(nominatimBody ?: "")
                val address = nominatimJson.getJSONObject("address")

                val city = address.optString("city")
                    .ifBlank { address.optString("county") }
                val district = address.optString("suburb")
                    .ifBlank { address.optString("borough") }
                    .ifBlank { address.optString("city_district") }

                when {
                    city.isNotBlank() && district.isNotBlank() ->
                        "$city $district"
                            .replace("특별시", "")  // 서울특별시 → 서울시
                            .replace("광역시", "")    // 부산광역시 → 부산
                            .trim()
                    city.isNotBlank() -> city
                        .replace("특별시", "")
                        .replace("광역시", "")
                        .trim()
                    district.isNotBlank() -> district
                    else -> "서울"
                }
            } catch (e: Exception) {
                json.getString("name")
            }
            val iconCode = json.getJSONArray("weather")
                .getJSONObject(0).getString("icon")

            WeatherInfo(
                city = city,
                temp = "${temp}°",
                description = desc,
                emoji = weatherEmoji(iconCode),
            )
        }.getOrElse {
            // API 실패 시 서울 기본값 반환
            WeatherInfo(
                city = "서울특별시",
                temp = "--°",
                description = "날씨 불러오는 중",
                emoji = "🌡️",
            )
        }
    }

private fun weatherEmoji(iconCode: String) = when {
    iconCode.startsWith("01") -> "☀️"
    iconCode.startsWith("02") -> "🌤️"
    iconCode.startsWith("03") -> "☁️"
    iconCode.startsWith("04") -> "☁️"
    iconCode.startsWith("09") -> "🌧️"
    iconCode.startsWith("10") -> "🌦️"
    iconCode.startsWith("11") -> "⛈️"
    iconCode.startsWith("13") -> "❄️"
    iconCode.startsWith("50") -> "🌫️"
    else -> "🌡️"
}

@SuppressLint("MissingPermission")
private suspend fun getLastLocation(context: Context): android.location.Location? =
    withContext(Dispatchers.IO) {
        runCatching {
            val manager = context.getSystemService(Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            manager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
        }.getOrNull()
    }