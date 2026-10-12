package com.todocloud.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Banner text while showing the encrypted on-device copy because the server
 * could not be reached, e.g. "离线中 · 显示 08:30 同步的内容，联网后下拉刷新".
 */
internal fun offlineNotice(
    syncedAtMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
    now: Instant = Instant.now(),
): String {
    val synced = Instant.ofEpochMilli(syncedAtMillis).atZone(zone)
    val pattern = if (synced.toLocalDate() == now.atZone(zone).toLocalDate()) "HH:mm" else "M月d日 HH:mm"
    val time = synced.format(DateTimeFormatter.ofPattern(pattern, Locale.CHINA))
    return "离线中 · 显示 $time 同步的内容，联网后下拉刷新"
}
