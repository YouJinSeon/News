package com.teddyjs.news.util

import android.content.Context
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.teddyjs.news.data.local.UserPreferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 친구 초대 보상.
 *
 * 흐름:
 *  1) 내 초대 코드 생성(고정) → 공유 링크에 ?referrer=<code> 로 실림.
 *  2) 새 사용자가 그 링크로 설치 → 첫 실행 시 Install Referrer로 초대자 코드를 읽어
 *     Firestore referrals/{초대자코드}.count 를 +1.
 *  3) 초대자는 앱을 켤 때 자기 count를 확인 → 3명마다 AI 보너스 사용권 지급.
 *
 * ⚠️ MVP: 서버 검증 없는 클라이언트 카운트(추후 Cloud Functions 검증 권장).
 *    Install Referrer는 실제 Play 설치에서만 동작(사이드로드/디버그 설치는 빈 값).
 */
@Singleton
class ReferralManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPrefs: UserPreferencesDataStore,
) {
    companion object {
        const val REWARD_THRESHOLD = 3   // 3명마다
        const val REWARD_AI_USES = 10    // AI 보너스 사용권 10회 지급
    }

    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val functions by lazy { FirebaseFunctions.getInstance("asia-northeast3") }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 앱 시작 시 호출: 설치 출처 귀속 + 보상 확인 */
    fun process() {
        scope.launch {
            val myCode = userPrefs.getOrCreateReferralCode()

            if (!userPrefs.isAttributionDone()) {
                readInstallReferrer { referrer ->
                    val inviter = extractCode(referrer)
                    if (inviter != null && inviter != myCode) {
                        // 직접 쓰기 대신 Cloud Function 호출(서버 검증: 기기당 1회만 카운트)
                        functions.getHttpsCallable("registerReferral")
                            .call(mapOf("inviterCode" to inviter, "deviceCode" to myCode))
                            .addOnSuccessListener { Timber.d("초대 등록 성공: $inviter") }
                            .addOnFailureListener { Timber.e(it, "초대 등록 실패") }
                    }
                    scope.launch { userPrefs.markAttributionDone() }
                }
            }

            checkRewards(myCode)
        }
    }

    /** 내 초대 코드 */
    suspend fun referralCode(): String = userPrefs.getOrCreateReferralCode()

    /** 내 초대 인원 수 조회 (UI용) */
    fun fetchInviteCount(onResult: (Int) -> Unit) {
        scope.launch {
            val code = userPrefs.getOrCreateReferralCode()
            firestore.collection("referrals").document(code).get()
                .addOnSuccessListener { onResult(((it.getLong("count") ?: 0L)).toInt()) }
                .addOnFailureListener { onResult(0) }
        }
    }

    private fun checkRewards(myCode: String) {
        firestore.collection("referrals").document(myCode).get()
            .addOnSuccessListener { doc ->
                val count = (doc.getLong("count") ?: 0L).toInt()
                scope.launch {
                    val milestones = count / REWARD_THRESHOLD
                    val claimed = userPrefs.getClaimedMilestones()
                    if (milestones > claimed) {
                        val newOnes = milestones - claimed
                        userPrefs.grantReferralReward(newOnes * REWARD_AI_USES)
                        userPrefs.setClaimedMilestones(milestones)
                        Timber.d("초대 보상 지급: +${newOnes * REWARD_AI_USES} AI 사용권")
                    }
                }
            }
            .addOnFailureListener { Timber.e(it, "보상 확인 실패") }
    }

    private fun readInstallReferrer(onResult: (String?) -> Unit) {
        val client = InstallReferrerClient.newBuilder(context).build()
        client.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(responseCode: Int) {
                val ref = runCatching {
                    if (responseCode == InstallReferrerClient.InstallReferrerResponse.OK) {
                        client.installReferrer.installReferrer
                    } else null
                }.getOrNull()
                onResult(ref)
                runCatching { client.endConnection() }
            }

            override fun onInstallReferrerServiceDisconnected() {
                onResult(null)
            }
        })
    }

    /** referrer 문자열에서 우리 초대 코드(u...) 추출 */
    private fun extractCode(referrer: String?): String? {
        if (referrer.isNullOrBlank()) return null
        return referrer.split("&", "?")
            .map { it.trim() }
            .map { if (it.contains("=")) it.substringAfter("=") else it }
            .firstOrNull { it.startsWith("u") && it.length in 10..16 }
    }
}
