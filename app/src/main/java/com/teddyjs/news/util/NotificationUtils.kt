package com.teddyjs.news.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas

/**
 * 알림의 '큰 아이콘'용 앱 아이콘 비트맵.
 *
 * 상태바(작은) 아이콘은 흰색 실루엣만 허용되므로(ic_notification),
 * 컬러 앱 아이콘은 setLargeIcon으로 넣는다. 적응형 아이콘도 안전하게 비트맵화.
 */
fun appLargeIcon(context: Context): Bitmap? = runCatching {
    val drawable = context.applicationInfo.loadIcon(context.packageManager)
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 108
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 108
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    bitmap
}.getOrNull()
