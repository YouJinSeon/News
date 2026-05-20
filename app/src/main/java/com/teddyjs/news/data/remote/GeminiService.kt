package com.teddyjs.news.data.remote

import com.teddyjs.news.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val apiKey = BuildConfig.GEMINI_API_KEY

    private val baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    suspend fun summarizeArticle(title: String, content: String): GeminiResult =
        withContext(Dispatchers.IO) {
            val prompt = """
                다음 뉴스 기사를 분석해서 JSON 형식으로만 응답해줘. 다른 텍스트 없이 순수 JSON만.
                
                기사 제목: $title
                기사 내용: ${content.take(1500)}
                
                응답 형식:
                {
                  "summary": "3문장 이내 핵심 요약",
                  "investmentInsight": "투자자 관점 시사점 (없으면 null)",
                  "sentiment": "positive|negative|neutral",
                  "keywords": ["키워드1", "키워드2", "키워드3"]
                }
            """.trimIndent()

            callGemini(prompt)?.let { json ->
                val cleaned = json
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()
                    .let { raw ->
                        // { } 사이만 추출
                        val start = raw.indexOf("{")
                        val end = raw.lastIndexOf("}")
                        if (start != -1 && end != -1) raw.substring(start, end + 1) else raw
                    }

                runCatching {
                    val obj = JSONObject(cleaned)
                    GeminiResult.Success(
                        summary = obj.optString("summary", "요약을 가져올 수 없습니다."),
                        investmentInsight = obj.optString("investmentInsight")
                            .takeIf { it.isNotBlank() && it != "null" },
                        sentiment = obj.optString("sentiment", "neutral"),
                        keywords = runCatching {
                            obj.getJSONArray("keywords").let { arr ->
                                (0 until arr.length()).map { arr.getString(it) }
                            }
                        }.getOrDefault(emptyList())
                    )
                }.getOrElse {
                    Timber.e(it, "Gemini 파싱 실패: $cleaned")
                    GeminiResult.Error("파싱 실패")
                }
            } ?: GeminiResult.Error("API 호출 실패")
        }

    suspend fun analyzeTasteFeed(
        bookmarkedTitles: List<String>,
        clickedKeywords: List<String> = emptyList(),  // ← 추가
    ): String = withContext(Dispatchers.IO) {
        val titles = bookmarkedTitles.take(20).joinToString("\n- ", prefix = "- ")
        val keywords = clickedKeywords.joinToString(", ")

        val prompt = """
        다음은 사용자의 뉴스 소비 패턴이야.
        
        즐겨찾기한 기사 제목:
        $titles
        
        최근 클릭한 기사의 키워드:
        $keywords
        
        이 사람의 관심사 패턴을 3개 키워드로 추출하고,
        다음에 읽으면 좋을 뉴스 주제 2가지를 추천해줘.
        JSON으로만 응답해:
        {
          "interestKeywords": ["키워드1", "키워드2", "키워드3"],
          "recommendTopics": ["주제1", "주제2"],
          "profileSummary": "한 문장 취향 요약"
        }
    """.trimIndent()

        callGemini(prompt) ?: ""
    }

    suspend fun generateWeeklyReport(
        bookmarkedTitles: List<String>,
        categoryDistribution: Map<String, Int>,
    ): String = withContext(Dispatchers.IO) {
        // 데이터 없으면 바로 반환
        if (bookmarkedTitles.isEmpty() && categoryDistribution.isEmpty()) {
            return@withContext """
            {
              "aiInsight": "아직 분석할 데이터가 없어요. 기사를 읽고 즐겨찾기를 해보세요!",
              "topKeywords": [],
              "nextWeekWatch": []
            }
        """.trimIndent()
        }
        val titles = bookmarkedTitles.take(30).joinToString("\n- ", prefix = "- ")
        val dist = categoryDistribution.entries.joinToString(", ") { "${it.key}: ${it.value}%" }
        val prompt = """
            사용자의 이번 주 뉴스 소비 패턴을 분석해줘.
            
            카테고리 분포: $dist
            즐겨찾기 기사: $titles
            
            JSON으로만 응답해:
            {
              "aiInsight": "이번 주 취향 2~3문장 분석",
              "topKeywords": ["키워드1", "키워드2", "키워드3", "키워드4", "키워드5"],
              "nextWeekWatch": ["다음주 주목할 이슈1", "다음주 주목할 이슈2"]
            }
        """.trimIndent()

        callGemini(prompt) ?: ""
    }

    private fun callGemini(prompt: String): String? {
        return runCatching {
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", prompt) })
                        })
                    })
                })
            }.toString()

            val request = Request.Builder()
                .url("$baseUrl?key=$apiKey")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                val resBody = response.body?.string() ?: return null  // body 먼저 읽기
                if (!response.isSuccessful) {
                    Timber.e("Gemini error: ${response.code} / $resBody")  // body 출력
                    return null
                }
                Timber.d("Gemini raw: $resBody")  // 성공 시 응답 확인
                val json = JSONObject(resBody)
                json.getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .replace("```json", "").replace("```", "").trim()
            }
        }.getOrElse {
            Timber.e(it, "Gemini call failed")
            null
        }
    }

    // 순수 텍스트 반환용
    suspend fun callGeminiRaw(prompt: String): String? =
        withContext(Dispatchers.IO) {
            callGemini(prompt)
        }

    suspend fun quickSummarize(title: String, content: String): String? =
        withContext(Dispatchers.IO) {
            val prompt = """
            다음 뉴스를 2문장으로 간략하게 요약해줘. 순수 텍스트만 반환해.
            
            제목: $title
            내용: ${content.take(500)}
        """.trimIndent()
            callGemini(prompt)
        }

}

sealed class GeminiResult {
    data class Success(
        val summary: String,
        val investmentInsight: String?,
        val sentiment: String,
        val keywords: List<String>,
    ) : GeminiResult()
    data class Error(val message: String) : GeminiResult()
}
