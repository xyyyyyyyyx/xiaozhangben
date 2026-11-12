package com.xiaozhangben.ledger.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 时间一律以 epoch 毫秒（UTC）存储，显示时换算到本机时区。 */
object DateUtils {

    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA)
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)

    /** 导入 CSV 时容忍的多种日期写法，按顺序尝试。 */
    private val importFormatters = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/M/d HH:mm:ss",
        "yyyy/M/d HH:mm",
        "yyyy-MM-dd",
        "yyyy/M/d",
        "yyyy.M.d",
        "yyyyMMdd",
        "yyyy年M月d日 HH:mm",
        "yyyy年M月d日",
    ).map { DateTimeFormatter.ofPattern(it, Locale.CHINA) }

    fun toLocalDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun toLocalDateTime(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

    fun toMillis(date: LocalDate, time: LocalTime = LocalTime.MIDNIGHT): Long =
        date.atTime(time).atZone(zone).toInstant().toEpochMilli()

    fun startOfDay(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfMonth(month: YearMonth): Long =
        month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun endOfMonthExclusive(month: YearMonth): Long =
        month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun startOfMonth(date: LocalDate): Long = startOfMonth(YearMonth.from(date))

    fun currentMonth(): YearMonth = YearMonth.from(LocalDate.now(zone))

    fun yearMonthOf(millis: Long): YearMonth = YearMonth.from(toLocalDate(millis))

    fun formatDate(millis: Long): String = toLocalDate(millis).format(dateFormatter)

    fun formatDateTime(millis: Long): String = toLocalDateTime(millis).format(dateTimeFormatter)

    fun formatMonthDay(millis: Long): String = toLocalDate(millis).format(monthDayFormatter)

    fun formatTime(millis: Long): String = toLocalDateTime(millis).format(timeFormatter)

    /** CSV 导出用的稳定格式：2026-01-31 08:30 */
    fun formatIso(millis: Long): String = toLocalDateTime(millis).format(isoFormatter)

    fun monthLabel(month: YearMonth): String = "${month.year}年${month.monthValue}月"

    fun shortMonthLabel(month: YearMonth): String = "${month.monthValue}月"

    /** 今天 / 昨天 / 前天 / 具体日期，用于账单列表分组标题。 */
    fun friendlyDay(millis: Long): String {
        val date = toLocalDate(millis)
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            today.minusDays(2) -> "前天"
            else -> if (date.year == today.year) date.format(monthDayFormatter) else date.format(dateFormatter)
        }
    }

    fun friendlyDayOf(date: LocalDate): String = friendlyDay(toMillis(date))

    /** 解析 CSV 里的日期，失败返回 null。只给日期时按当天 00:00 处理。 */
    fun parseDateTime(text: String): Long? {
        val raw = text.trim().replace("T", " ").replace("年", "-").replace("月", "-").replace("日", "")
        if (raw.isEmpty()) return null
        for (formatter in importFormatters) {
            try {
                return LocalDateTime.parse(raw, formatter).atZone(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // 继续尝试下一种格式
            }
            try {
                return LocalDate.parse(raw, formatter).atStartOfDay(zone).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // 继续尝试下一种格式
            }
        }
        return null
    }
}
