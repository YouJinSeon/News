import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.teddyjs.news.BuildConfig
import com.teddyjs.news.domain.model.NewsCategory
import com.teddyjs.news.domain.model.UserPlan
import com.teddyjs.news.presentation.theme.*
import com.teddyjs.news.presentation.ui.settings.SettingsViewModel
import com.teddyjs.news.util.BillingManager
import com.teddyjs.news.util.ShareUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPaywallClick: () -> Unit,
    billingManager: BillingManager,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val userPlan by viewModel.userPlan.collectAsState()
    val subscribedCategories by viewModel.subscribedCategories.collectAsState()
    val breakingNotification by viewModel.breakingNotification.collectAsState()
    val dailyNotification by viewModel.dailyNotification.collectAsState()
    val darkMode by viewModel.darkMode.collectAsState()
    val context = LocalContext.current
    val activity = context as Activity
    val customKeywords by viewModel.customBreakingKeywords.collectAsState()
    var showKeywordDialog by remember { mutableStateOf(false) }
    val nightNotification by viewModel.nightNotification.collectAsState()
    val followedTopics by viewModel.followedTopics.collectAsState()
    var showTopicDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val subscribedProductId by viewModel.subscribedProductId.collectAsState()
    val referralCode by viewModel.referralCode.collectAsState()
    val inviteCount by viewModel.inviteCount.collectAsState()
    val paywallVariant by viewModel.paywallVariant.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadReferral()
        viewModel.loadPaywallVariant()
    }

    LaunchedEffect(Unit) {
        viewModel.algorithmResetDone.collect {
            scope.launch {
                snackbarHostState.showSnackbar("추천 알고리즘이 초기화되었어요 ✓")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, "뒤로")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {

            // ── 구독 상태 ──────────────────────────────────
            item {
                SettingsSection(title = "구독") {
                    if (userPlan == UserPlan.PREMIUM) {
                        SettingsItem(
                            icon = Icons.Filled.CreditCard,
                            title = "구독 관리",
                            subtitle = when (subscribedProductId) {
                                "premiummonthly" -> "월간 프리미엄 구독 중"
                                "premiumyearly"  -> "연간 프리미엄 구독 중"
                                else              -> "구독 관리"
                            },
                            onClick = {
                                val sku = subscribedProductId ?: "premiummonthly"
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://play.google.com/store/account/subscriptions?sku=$sku&package=com.teddyjs.news")
                                )
                                context.startActivity(intent)
                            },
                            trailing = {
                                Icon(Icons.Filled.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            }
                        )
                    } else {
                        SettingsItem(
                            icon = Icons.Filled.WorkspacePremium,
                            iconTint = Amber400,
                            title = "프리미엄으로 업그레이드",
                            onClick = onPaywallClick,
                            trailing = {
                                Icon(Icons.Filled.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            }
                        )
                    }
                }
            }

            // ── 관심사 카테고리 ────────────────────────────
            item {
                SettingsSection(title = "관심사 카테고리") {
                    NewsCategory.entries.forEach { category ->
                        val isSubscribed = subscribedCategories.contains(category)
                        SettingsItem(
                            icon = categoryIcon(category),
                            title = category.label,
                            subtitle = if (isSubscribed) "구독 중" else "구독 안 함",
                            trailing = {
                                Switch(
                                    checked = isSubscribed,
                                    onCheckedChange = { viewModel.toggleCategory(category) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                    )
                                )
                            }
                        )
                    }
                }
            }

            // ── 알림 설정 ──────────────────────────────────
            item {
                SettingsSection(title = "알림") {
                    SettingsItem(
                        icon = Icons.Filled.NotificationsActive,
                        iconTint = Red400,
                        title = "속보 알림",
                        subtitle = "중요한 속보를 즉시 알려드려요",
                        trailing = {
                            Switch(
                                checked = breakingNotification,
                                onCheckedChange = viewModel::toggleBreakingNotification,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                )
                            )
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Schedule,
                        iconTint = Blue400,
                        title = "정기 브리핑 알림",
                        subtitle = "아침·점심·저녁 뉴스 브리핑",
                        trailing = {
                            Switch(
                                checked = dailyNotification,
                                onCheckedChange = viewModel::toggleDailyNotification,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                )
                            )
                        }
                    )

                    SettingsItem(
                        icon = Icons.Filled.NightlightRound,
                        title = "야간 알림 (22시 ~ 8시)",
                        subtitle = "야간 시간대 알림을 받아요",
                        trailing = {
                            Switch(
                                checked = nightNotification,
                                onCheckedChange = { viewModel.setNightNotification(it) }
                            )
                        }
                    )
                }

                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    iconTint = Blue400,
                    title = "팔로우한 토픽",
                    subtitle = if (followedTopics.isEmpty()) "팔로우한 토픽이 없어요"
                    else followedTopics.joinToString(", "),
                    onClick = { showTopicDialog = true },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (followedTopics.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Blue50,
                                ) {
                                    Text(
                                        "${followedTopics.size}개",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        fontSize = 11.sp,
                                        color = Blue400,
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    }
                )

                if (showTopicDialog) {
                    FollowedTopicDialog(
                        topics = followedTopics,
                        onRemove = viewModel::removeTopic,
                        onClearAll = viewModel::clearAllTopics,
                        onDismiss = { showTopicDialog = false },
                    )
                }

                SettingsItem(
                    icon = Icons.Filled.NotificationsActive,
                    iconTint = Red400,
                    title = "속보 알림 키워드",
                    subtitle = if (customKeywords.isEmpty()) "기본 키워드 사용 중"
                    else customKeywords.joinToString(", "),
                    onClick = { showKeywordDialog = true },
                    trailing = {
                        Icon(Icons.Filled.ChevronRight, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                    }
                )

                // 키워드 다이얼로그
                if (showKeywordDialog) {
                    BreakingKeywordDialog(
                        keywords = customKeywords,
                        onAdd = viewModel::addBreakingKeyword,
                        onRemove = viewModel::removeBreakingKeyword,
                        onDismiss = { showKeywordDialog = false },
                    )
                }
            }

            // ── 화면 ──────────────────────────────────────
            item {
                SettingsSection(title = "화면") {
                    SettingsItem(
                        icon = Icons.Filled.DarkMode,
                        title = "다크모드",
                        subtitle = "어두운 테마로 변경",
                        trailing = {
                            Switch(
                                checked = darkMode,
                                onCheckedChange = viewModel::toggleDarkMode,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                )
                            )
                        }
                    )
                }
            }

            // ── 친구 초대 ──────────────────────────────────
            item {
                val remain = viewModel.rewardThreshold - (inviteCount % viewModel.rewardThreshold)
                SettingsSection(title = "친구 초대") {
                    SettingsItem(
                        icon = Icons.Filled.Share,
                        iconTint = Green400,
                        title = "친구 초대하고 AI 사용권 받기",
                        subtitle = "지금까지 ${inviteCount}명 초대 · ${viewModel.rewardThreshold}명마다 AI 사용권 10회" +
                            if (inviteCount > 0) " (다음 보상까지 ${remain}명)" else "",
                        onClick = { ShareUtils.inviteApp(context, referralCode) },
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                }
            }

            // ── 기타 ──────────────────────────────────────
            item {
                SettingsSection(title = "기타") {
                    SettingsItem(
                        icon = Icons.Filled.Refresh,
                        title = "추천 알고리즘 초기화",
                        subtitle = "검색 기록과 클릭 기록을 지워요",
                        onClick = { viewModel.clearAlgorithmData() },
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Star,
                        iconTint = Amber400,
                        title = "앱 평가하기",
                        subtitle = "Play Store에서 리뷰를 남겨주세요",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW,
                                Uri.parse("market://details?id=${context.packageName}"))
                            context.startActivity(intent)
                        },
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Mail,
                        title = "문의하기",
                        subtitle = "불편한 점이나 개선 사항을 알려주세요",
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:namch1597@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "[뉴스 브리핑] 문의")
                            }
                            context.startActivity(intent)
                        },
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.PrivacyTip,
                        title = "개인정보처리방침",
                        subtitle = "수집 및 이용 정책 확인",
                        onClick = {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://news-440a4.web.app/privacy_policy.html")
                            )
                            context.startActivity(intent)
                        },
                        trailing = {
                            Icon(Icons.Filled.ChevronRight, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                        }
                    )
                    SettingsItem(
                        icon = Icons.Filled.Info,
                        title = "앱 버전",
                        subtitle = "v${BuildConfig.VERSION_NAME}",
                    )
                }
            }
            if (BuildConfig.DEBUG) {
                item {
                    SettingsSection(title = "DEBUG") {
                        val ctx = LocalContext.current
                        SettingsItem(
                            icon = Icons.Filled.BugReport,
                            iconTint = Green400,
                            title = "알림 테스트 (개발용)",
                            subtitle = "아침 브리핑 + 속보 알림 즉시 발송",
                            onClick = { viewModel.testNotification(ctx) },
                            trailing = {
                                Icon(Icons.Filled.ChevronRight, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            }
                        )
                        SettingsItem(
                            title = "[DEBUG] 플랜 토글",
                            icon = Icons.Filled.WorkspacePremium,
                            subtitle = "현재: ${userPlan.name}",
                            onClick = {
                                viewModel.togglePlanForDebug()
                            }
                        )
                        SettingsItem(
                            title = "[실험] 페이월 변형 전환",
                            icon = Icons.Filled.Tune,
                            iconTint = Blue400,
                            subtitle = "현재: $paywallVariant " +
                                (if (paywallVariant == "B") "(🔥 긴급 배너형)" else "(기본형)") +
                                " · 눌러서 전환 후 '프리미엄 업그레이드' 열어 확인",
                            onClick = { viewModel.togglePaywallVariant() },
                            trailing = {
                                Icon(Icons.Filled.SwapHoriz, null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp, MaterialTheme.colorScheme.outline
            ),
        ) {
            Column { content() }
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        trailing?.invoke()
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 48.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    )
}

private fun categoryIcon(category: NewsCategory) = when (category) {
    NewsCategory.STOCK -> Icons.Filled.TrendingUp
    NewsCategory.POLITICS_ECONOMY -> Icons.Filled.AccountBalance
    NewsCategory.GLOBAL -> Icons.Filled.Language
    NewsCategory.SPORTS -> Icons.Filled.SportsSoccer
}

@Composable
fun BreakingKeywordDialog(
    keywords: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("속보 알림 키워드") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "키워드가 포함된 기사가 올라오면 즉시 알림을 받아요",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )

                // 기본 키워드 (삭제 불가)
                Text("기본 키워드", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(listOf("속보", "긴급", "지진", "사고", "화재", "폭락", "폭등")) { kw ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(kw, fontSize = 11.sp) },
                        )
                    }
                }

                // 내 키워드 (삭제 가능 — X 버튼)
                if (keywords.isNotEmpty()) {
                    Text("내 키워드", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        keywords.forEach { keyword ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.NotificationsActive,
                                        null,
                                        modifier = Modifier.size(14.dp),
                                        tint = Red400,
                                    )
                                    Text(keyword, fontSize = 13.sp)
                                }
                                IconButton(
                                    onClick = { onRemove(keyword) },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        "삭제",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        "추가된 키워드가 없어요",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }

                // 키워드 입력
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("키워드 추가...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (inputText.isNotBlank()) {
                            onAdd(inputText.trim())
                            inputText = ""
                        }
                    }),
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = {
                                onAdd(inputText.trim())
                                inputText = ""
                            }) {
                                Icon(Icons.Filled.Add, null, tint = Blue400)
                            }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("확인") }
        },
    )
}

@Composable
fun FollowedTopicDialog(
    topics: List<String>,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("팔로우한 토픽")
                if (topics.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("전체 삭제", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (topics.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.NotificationsOff, null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        )
                        Text(
                            "팔로우한 토픽이 없어요\n기사 상세에서 키워드를 팔로우해보세요",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 400.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(topics) { topic ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.NotificationsActive, null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Blue400,
                                )
                                Text("#$topic", fontSize = 14.sp)
                            }
                            IconButton(
                                onClick = { onRemove(topic) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Delete, "삭제",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                )
                            }
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("닫기") }
        },
    )
}
