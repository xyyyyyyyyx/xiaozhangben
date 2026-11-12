package com.xiaozhangben.ledger.domain

import java.util.Locale
import kotlin.math.abs

/** 金额一律以「分」为单位的 Long 存储，这里负责显示与解析。 */
object Money {

    const val CURRENCY = "¥"

    /** 1234567 -> "12,345.67" */
    fun formatPlain(cents: Long): String {
        val negative = cents < 0
        val v = abs(cents)
        val text = String.format(Locale.CHINA, "%,d.%02d", v / 100, v % 100)
        return if (negative) "-$text" else text
    }

    /** 1234567 -> "¥12,345.67" */
    fun format(cents: Long): String = CURRENCY + formatPlain(cents)

    /** 带正负号，用于流水列表：+¥20.00 / -¥12.30 */
    fun formatSigned(cents: Long): String {
        val prefix = when {
            cents > 0 -> "+"
            cents < 0 -> "-"
            else -> ""
        }
        return prefix + CURRENCY + formatPlain(abs(cents))
    }

    /** 大额缩写，图表坐标轴用：1234567 -> "1.2万" */
    fun formatCompact(cents: Long): String {
        val yuan = abs(cents) / 100.0
        val sign = if (cents < 0) "-" else ""
        return when {
            yuan >= 100_000_000 -> String.format(Locale.CHINA, "%s%.1f亿", sign, yuan / 100_000_000)
            yuan >= 10_000 -> String.format(Locale.CHINA, "%s%.1f万", sign, yuan / 10_000)
            yuan >= 1_000 -> String.format(Locale.CHINA, "%s%.0f", sign, yuan)
            else -> String.format(Locale.CHINA, "%s%.0f", sign, yuan)
        }
    }

    /**
     * 解析用户输入。支持 "12"、"12.3"、"12.34"、"1,234.5"、全角句点、前后带 ¥ 或空格。
     * 解析失败返回 null。小数部分超过两位按四舍五入处理。
     */
    fun parse(input: String): Long? {
        var cleaned = input.trim()
            .replace("，", "")
            .replace(",", "")
            .replace("。", ".")
            .replace("．", ".")
            .replace("￥", "")
            .replace(CURRENCY, "")
            .replace(" ", "")
            .replace("\u00A0", "")
        if (cleaned.isEmpty()) return null
        var negative = false
        if (cleaned.startsWith("-")) {
            negative = true
            cleaned = cleaned.substring(1)
        } else if (cleaned.startsWith("+")) {
            cleaned = cleaned.substring(1)
        }
        if (cleaned.isEmpty() || !Regex("^\\d*\\.?\\d*$").matches(cleaned) || cleaned == ".") return null

        val dot = cleaned.indexOf('.')
        val yuanPart = (if (dot >= 0) cleaned.substring(0, dot) else cleaned).ifEmpty { "0" }
        val fenPart = if (dot >= 0) cleaned.substring(dot + 1) else ""
        if (yuanPart.length > 12) return null

        val yuan = yuanPart.toLongOrNull() ?: return null
        var fen = 0L
        if (fenPart.isNotEmpty()) {
            val digits = fenPart.take(3)
            val head = digits.take(2).padEnd(2, '0').toLongOrNull() ?: return null
            fen = head
            if (digits.length == 3 && digits[2] >= '5') fen += 1
        }
        val total = yuan * 100 + fen
        return if (negative) -total else total
    }

    fun parseOrZero(input: String): Long = parse(input) ?: 0L
}
