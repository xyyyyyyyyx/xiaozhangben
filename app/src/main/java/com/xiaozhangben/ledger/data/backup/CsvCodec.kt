package com.xiaozhangben.ledger.data.backup

import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType

/**
 * 极简 CSV 编解码。导出列固定为：日期,类型,金额,分类,账户,备注。
 * 导入时对常见的列顺序差异（例如多一列自增 id）做兼容。
 */
object CsvCodec {

    const val HEADER = "日期,类型,金额,分类,账户,备注"

    data class CsvRow(
        val occurredAt: Long,
        val type: TxnType,
        val amount: Long,
        val category: String,
        val account: String,
        val note: String,
    )

    fun encode(transactions: List<Transaction>): String {
        val builder = StringBuilder()
        builder.append(HEADER).append("\r\n")
        for (t in transactions) {
            builder.append(escape(DateUtils.formatIso(t.occurredAt))).append(',')
                .append(t.type.label).append(',')
                .append(Money.formatPlain(t.amount)).append(',')
                .append(escape(t.categoryName)).append(',')
                .append(escape(t.accountName)).append(',')
                .append(escape(t.note)).append("\r\n")
        }
        return builder.toString()
    }

    fun decode(text: String): List<CsvRow> {
        val result = ArrayList<CsvRow>()
        for (cells in parseRows(text)) {
            if (cells.isEmpty() || cells.all { it.isBlank() }) continue
            var fields = cells
            val first = fields[0].trim().trimStart('\uFEFF')
            if (first == "日期" || first.equals("date", ignoreCase = true)) continue
            // 兼容「多一列序号」的导出文件
            if (fields.size > 6 && fields[0].trim().toLongOrNull() != null &&
                DateUtils.parseDateTime(fields[1]) != null
            ) {
                fields = fields.subList(1, fields.size)
            }
            if (fields.size < 3) continue

            val occurredAt = DateUtils.parseDateTime(fields[0]) ?: continue
            val type = TxnType.fromLabel(fields[1]) ?: continue
            val amount = Money.parse(fields[2]) ?: continue
            if (amount <= 0L) continue
            result.add(
                CsvRow(
                    occurredAt = occurredAt,
                    type = type,
                    amount = amount,
                    category = fields.getOrNull(3)?.trim().orEmpty(),
                    account = fields.getOrNull(4)?.trim().orEmpty(),
                    note = fields.getOrNull(5)?.trim().orEmpty(),
                ),
            )
        }
        return result
    }

    private fun escape(value: String): String {
        val flat = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        return if (flat.contains(',') || flat.contains('"')) {
            "\"" + flat.replace("\"", "\"\"") + "\""
        } else {
            flat
        }
    }

    private fun parseRows(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var current = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        val source = text.removePrefix("\uFEFF")
        var index = 0
        while (index < source.length) {
            val char = source[index]
            when {
                inQuotes -> when {
                    char == '"' && index + 1 < source.length && source[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }

                    char == '"' -> inQuotes = false
                    else -> field.append(char)
                }

                char == '"' -> inQuotes = true
                char == ',' -> {
                    current.add(field.toString())
                    field.setLength(0)
                }

                char == '\n' -> {
                    current.add(field.toString())
                    field.setLength(0)
                    rows.add(current)
                    current = ArrayList()
                }

                char == '\r' -> Unit
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || current.isNotEmpty()) {
            current.add(field.toString())
            rows.add(current)
        }
        return rows
    }
}
