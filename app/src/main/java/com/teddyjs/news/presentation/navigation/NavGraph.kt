package com.teddyjs.news.presentation.navigation

import SettingsScreen
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.teddyjs.news.presentation.ui.bookmark.BookmarkScreen
import com.teddyjs.news.presentation.ui.detail.DetailScreen
import com.teddyjs.news.presentation.ui.detail.DetailViewModel
import com.teddyjs.news.presentation.ui.home.HomeScreen
import com.teddyjs.news.presentation.ui.onboarding.OnboardingScreen
import com.teddyjs.news.presentation.ui.paywall.PaywallScreen
import com.teddyjs.news.presentation.ui.report.ReportScreen
import com.teddyjs.news.presentation.ui.search.SearchScreen
import com.teddyjs.news.presentation.ui.webview.WebViewScreen
import com.teddyjs.news.util.BillingManager
import kotlinx.coroutines.delay

data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "홈", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Search.route, "검색", Icons.Filled.Search, Icons.Outlined.Search),
    BottomNavItem(Screen.Bookmark.route, "즐겨찾기", Icons.Filled.Star, Icons.Outlined.StarBorder),
    BottomNavItem(Screen.Report.route, "리포트", Icons.Filled.BarChart, Icons.Outlined.BarChart),
    BottomNavItem(Screen.Settings.route, "설정", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun MainScreen(
    billingManager: BillingManager,
    initialArticleId: String? = null,
    pendingArticleFlow: kotlinx.coroutines.flow.SharedFlow<String>,
) {
    val navController = rememberNavController()

    // 홈 탭 재탭 → 맨 위로 스크롤 신호
    val homeScrollToTop = remember { kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    LaunchedEffect(Unit) {
        pendingArticleFlow.collect { articleId ->
            if (articleId.isNotBlank()) {
                delay(300)
                navController.navigate(Screen.Detail.createRoute(articleId)) {
                    launchSingleTop = true
                }
            }
        }
    }

    // 첫 실행 체크
    val context = LocalContext.current
    val isFirstRun = remember {
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getBoolean("is_first_run", true)
    }

    val startDestination = if (isFirstRun) Screen.Onboarding.route else Screen.Home.route

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    val showBottomBar = currentDest?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDest?.hierarchy?.any { it.route == item.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                // 이미 홈에 있는데 홈 탭을 또 누르면 → 맨 위로 스크롤
                                if (selected && item.route == Screen.Home.route) {
                                    homeScrollToTop.tryEmit(Unit)
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(if (selected) item.selectedIcon else item.unselectedIcon, contentDescription = item.label)
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding),
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onArticleClick = { navController.navigate(Screen.Detail.createRoute(it)) },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onTasteFeedClick = { navController.navigate(Screen.TasteFeed.route) },
                    scrollToTop = homeScrollToTop,
                )
            }
            composable(Screen.Bookmark.route) {
                BookmarkScreen(
                    onArticleClick = { navController.navigate(Screen.Detail.createRoute(it)) },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                )
            }
            composable(Screen.Report.route) {
                ReportScreen(
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                )
            }
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("articleId") { type = NavType.StringType })
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                DetailScreen(
                    articleId = articleId,
                    onBack = { navController.popBackStack() },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onWebViewClick = { id, title ->
                        navController.navigate(Screen.WebView.createRoute(id, title))
                    },
                )
            }
            composable(Screen.Paywall.route) {
                PaywallScreen(
                    onBack = { navController.popBackStack() },
                    billingManager = billingManager,
                )
            }
            composable(Screen.TasteFeed.route) {
                // AI 취향 분석 피드 — HomeScreen 재활용 (취향 필터 적용)
                HomeScreen(
                    onArticleClick = { navController.navigate(Screen.Detail.createRoute(it)) },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    onTasteFeedClick = { navController.popBackStack() },
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    onArticleClick = { navController.navigate(Screen.Detail.createRoute(it)) },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                )
            }
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onDone = {
                        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("is_first_run", false).apply()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onPaywallClick = { navController.navigate(Screen.Paywall.route) },
                    billingManager = billingManager,
                )
            }

            composable(
                route = Screen.WebView.route,
                arguments = listOf(
                    navArgument("articleId") { type = NavType.StringType },
                    navArgument("title") { type = NavType.StringType },
                )
            ) { backStackEntry ->
                val articleId = backStackEntry.arguments?.getString("articleId") ?: ""
                val title = runCatching {
                    java.net.URLDecoder.decode(
                        backStackEntry.arguments?.getString("title") ?: "", "UTF-8"
                    )
                }.getOrElse {
                    backStackEntry.arguments?.getString("title") ?: ""
                }

                // articleId로 ViewModel에서 URL 가져오기
                val detailViewModel: DetailViewModel = hiltViewModel(
                    remember(backStackEntry) {
                        navController.getBackStackEntry(Screen.Detail.route)
                    }
                )
                val url by detailViewModel.webViewUrl.collectAsState()

                WebViewScreen(
                    url = url ?: "",
                    title = title,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
