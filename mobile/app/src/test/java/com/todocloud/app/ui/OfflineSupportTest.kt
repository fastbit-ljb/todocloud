package com.todocloud.app.ui

import java.time.Instant
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class OfflineSupportTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = Instant.parse("2026-10-12T04:00:00Z") // 12:00 in Shanghai

    @Test
    fun sameDayShowsOnlyTheTime() {
        val synced = Instant.parse("2026-10-12T00:30:00Z").toEpochMilli() // 08:30
        assertEquals("离线中 · 显示 08:30 同步的内容，联网后下拉刷新", offlineNotice(synced, zone, now))
    }

    @Test
    fun earlierDayShowsTheDate() {
        val synced = Instant.parse("2026-10-10T13:05:00Z").toEpochMilli() // 10月10日 21:05
        assertEquals("离线中 · 显示 10月10日 21:05 同步的内容，联网后下拉刷新", offlineNotice(synced, zone, now))
    }
}
