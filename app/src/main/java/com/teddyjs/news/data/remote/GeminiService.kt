package com.teddyjs.news.data.remote

import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiService @Inject constructor() {
    // 보안: API 키를 앱에 두지 않는다. Cloud Function(geminiProxy)이 서버 키로 호출하고,
    // App Check(Play Integrity)로 '진짜 우리 앱'만 함수를 부를 수 있게 막는다.
    private val functions = FirebaseFunctions.getInstance("asia-northeast3")

    companion object {
        /**
         * 브랜드 보이스(페르소나). 모든 AI 응답에 일관된 말투를 입혀
         * "정 드는" 서비스 경험을 만든다. (리텐션 차별화 요소)
         * - 친근하지만 신뢰감 있는 뉴스 친구
         * - 군더더기 없이 핵심만, 어려운 용어는 쉽게 풀어줌
         * - 과장/낚시 금지, 사실 기반
         */
        private val PERSONA = """
            너는 '뉴스 브리핑'의 AI 에디터야.
            성격: 똑똑하지만 잘난척하지 않는, 친근하고 믿음직한 뉴스 친구.
            말투 규칙:
            - 핵심만 간결하게. 군더더기·미사여구 금지.
            - 어려운 경제·시사 용어는 쉽게 풀어서.
            - 과장하거나 낚시성으로 쓰지 말 것. 철저히 사실 기반.
            - 따뜻하지만 담백한 존댓말 톤.
            아래 작업을 이 페르소나로 수행해.
        """.trimIndent()
    }

    suspend fun summarizeArticle(title: String, content: String): GeminiResult =
        withContext(Dispatchers.IO) {
            val prompt = """
                $PERSONA

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
        $PERSONA

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
            $PERSONA

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

    /** 오늘의 나를 위한 브리핑 — 헤드라인을 묶어 대화체 다이제스트 생성 */
    suspend fun generateDailyDigest(headlines: List<String>): String? =
        withContext(Dispatchers.IO) {
            if (headlines.isEmpty()) return@withContext null
            val list = headlines.take(8).joinToString("\n- ", prefix = "- ")
            val prompt = """
                $PERSONA

                아래는 오늘의 주요 뉴스 헤드라인이야. 사용자가 한눈에 훑을 수 있게
                '오늘의 핵심 3가지'를 뽑아줘.
                - 정확히 3줄. 각 줄은 '• '로 시작.
                - 각 줄 35자 이내, 핵심만. 인사말·마크다운 없이 순수 텍스트.
                - 가장 중요하고 서로 다른 주제 3개를 고를 것.

                헤드라인:
                $list
            """.trimIndent()
            callGemini(prompt)
        }

    /** 기사에 대한 사용자 질문에 답변 (대화형 뉴스) */
    suspend fun askAboutArticle(
        title: String,
        content: String,
        question: String,
    ): String? = withContext(Dispatchers.IO) {
        val prompt = """
            $PERSONA

            아래 뉴스 기사에 대한 사용자의 질문에 답해줘.
            - 기사 내용에 근거해서, 모르는 건 솔직히 모른다고 해.
            - 2~4문장으로 쉽고 간결하게. 마크다운 없이 순수 텍스트만.

            기사 제목: $title
            기사 내용: ${content.take(2000)}

            사용자 질문: $question
        """.trimIndent()
        callGemini(prompt)
    }

    /**
     * 관점 비교 — 같은 사안에 대한 여러 언론사 보도를 받아
     * 공통 사실 / 매체별 강조점 / 시각 차이를 중립적으로 비교한다.
     * (네이버·뤼튼·뉴닉이 안 하는 차별화 기능)
     */
    suspend fun comparePerspectives(
        topic: String,
        sources: List<OutletSource>,
    ): PerspectiveResult? = withContext(Dispatchers.IO) {
        if (sources.size < 2) return@withContext null
        val sourceBlock = sources.joinToString("\n\n") {
            "[${it.press}] ${it.headline}\n발췌: ${it.excerpt}"
        }
        val prompt = """
            $PERSONA

            아래는 '같은 사안'에 대한 여러 언론사의 보도야. 같은 사건을 매체마다
            어떻게 다르게 다루는지 중립적으로 비교해줘.
            - 모든 매체가 공통으로 전하는 '사실'과, 매체별 '강조점·해석'을 분리할 것.
            - 진영을 편들지 말고 '차이 자체'만 담백하게. 제공된 보도에만 근거(추측 금지).
            - 매체별 관점은 시각차가 뚜렷한 순으로 최대 4개, 각 한 문장으로 간결히.
            - 모든 매체가 거의 같게 보도해 의미 있는 차이가 없으면 divergence에 그렇게 적어.

            사안: $topic

            보도들:
            $sourceBlock

            JSON으로만 응답(다른 텍스트 없이):
            {
              "commonFacts": "공통 핵심 사실 2~3문장",
              "outletViews": [
                {"press": "언론사", "angle": "강조/해석하는 관점 한 문장"}
              ],
              "divergence": "가장 두드러진 시각 차이 1~2문장"
            }
        """.trimIndent()

        val raw = callGemini(prompt) ?: return@withContext null
        val cleaned = raw.replace("```json", "").replace("```", "").trim().let { r ->
            val s = r.indexOf("{"); val e = r.lastIndexOf("}")
            if (s != -1 && e != -1) r.substring(s, e + 1) else r
        }
        runCatching {
            val obj = JSONObject(cleaned)
            val views = obj.optJSONArray("outletViews")?.let { arr ->
                (0 until arr.length()).map { idx ->
                    val v = arr.getJSONObject(idx)
                    OutletView(
                        press = v.optString("press"),
                        angle = v.optString("angle"),
                    )
                }
            }?.filter { it.press.isNotBlank() && it.angle.isNotBlank() }
                ?.take(4) ?: emptyList()  // 카드 과다 방지 — 최대 4개 매체
            PerspectiveResult(
                commonFacts = obj.optString("commonFacts").takeIf { it.isNotBlank() }
                    ?: "공통 사실을 정리하지 못했어요.",
                outletViews = views,
                divergence = obj.optString("divergence").takeIf { it.isNotBlank() } ?: "",
            ).takeIf { it.outletViews.isNotEmpty() }
        }.getOrElse {
            Timber.e(it, "관점 비교 파싱 실패: $cleaned")
            null
        }
    }

    // 앱은 키 없이 프롬프트만 Cloud Function 으로 보내고 결과 텍스트만 받는다.
    // (Tasks.await 는 호출부가 Dispatchers.IO 에서 부르므로 블로킹 OK)
    private fun callGemini(prompt: String): String? {
        return runCatching {
            val data = hashMapOf<String, Any>("prompt" to prompt)
            val result = Tasks.await(functions.getHttpsCallable("geminiProxy").call(data))
            @Suppress("UNCHECKED_CAST")
            val map = result.getData() as? Map<String, Any?>
            (map?.get("text") as? String)
                ?.replace("```json", "")?.replace("```", "")?.trim()
        }.getOrElse {
            Timber.e(it, "geminiProxy 호출 실패")
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
            $PERSONA

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

/** 관점 비교 입력 — 한 언론사의 보도 요약 */
data class OutletSource(
    val press: String,
    val headline: String,
    val excerpt: String,
)

/** 관점 비교 결과 */
data class PerspectiveResult(
    val commonFacts: String,
    val outletViews: List<OutletView>,
    val divergence: String,
    val sourceLinks: List<SourceLink> = emptyList(), // 원문 바로가기 (Repository에서 채움)
)

data class OutletView(
    val press: String,
    val angle: String,
)

/** 관점 비교에 사용된 원문 출처 (언론사 → 기사 URL) */
data class SourceLink(
    val press: String,
    val title: String,
    val url: String,
)
