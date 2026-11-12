package com.xiaozhangben.ledger.domain

import com.xiaozhangben.ledger.domain.model.RecurrenceFrequency
import java.time.Instant
import java.time.ZoneId

/** 周期账单推进逻辑：nextRunAt 指的是「下一次应该生成的时刻」。 */
object RecurringEngine {

    fun next(from: Long, frequency: RecurrenceFrequency): Long {
        val zoned = Instant.ofEpochMilli(from).atZone(ZoneId.systemDefault())
        val advanced = when (frequency) {
            RecurrenceFrequency.DAILY -> zoned.plusDays(1)
            RecurrenceFrequency.WEEKLY -> zoned.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> zoned.plusMonths(1)
            RecurrenceFrequency.YEARLY -> zoned.plusYears(1)
        }
        return advanced.toInstant().toEpochMilli()
    }

    /**
     * 返回 until 之前所有到期的时刻（含下次执行时刻本身），最多 limit 条，
     * 防止用户改系统时间或长期未打开 App 时一次生成上千条。
     */
    fun dueOccurrences(
        nextRunAt: Long,
        frequency: RecurrenceFrequency,
        until: Long,
        limit: Int = 400,
    ): List<Long> {
        val result = ArrayList<Long>()
        var cursor = nextRunAt
        while (cursor <= until && result.size < limit) {
            result.add(cursor)
            val advanced = next(cursor, frequency)
            if (advanced <= cursor) break
            cursor = advanced
        }
        return result
    }

    /** 把发生时间对齐到指定时刻的「当天同样时分」，用于新建规则。 */
    fun anchorAt(reference: Long, dayOfMonth: Int? = null): Long {
        val zoned = Instant.ofEpochMilli(reference).atZone(ZoneId.systemDefault())
        val target = if (dayOfMonth != null) {
            val safeDay = dayOfMonth.coerceIn(1, zoned.toLocalDate().lengthOfMonth())
            zoned.withDayOfMonth(safeDay)
        } else {
            zoned
        }
        return target.toInstant().toEpochMilli()
    }
}
