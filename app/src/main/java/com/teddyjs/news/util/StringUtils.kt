package com.teddyjs.news.util

object StringUtils {
    fun cleanHtml(text: String): String {
        return text
            .replace("<b>", "").replace("</b>", "")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&apos;", "'")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                if (code != null) code.toChar().toString() else ""
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                if (code != null) code.toChar().toString() else ""
            }
            .replace(Regex("<[^>]+>"), "")  // 남은 HTML 태그 제거
            .trim()
    }
}