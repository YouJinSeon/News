package com.teddyjs.news.presentation.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Bookmark : Screen("bookmark")
    data object Report : Screen("report")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail/{articleId}") {
        fun createRoute(articleId: String) = "detail/$articleId"
    }
    data object Paywall : Screen("paywall")
    data object TasteFeed : Screen("taste_feed")
    data object Onboarding : Screen("onboarding")
    data object Search : Screen("search")

    data object WebView : Screen("webview/{articleId}/{title}") {
        fun createRoute(articleId: String, title: String): String {
            val encodedTitle = java.net.URLEncoder.encode(title, "UTF-8")
                .replace("+", "%20")
            return "webview/$articleId/$encodedTitle"
        }
    }
}
