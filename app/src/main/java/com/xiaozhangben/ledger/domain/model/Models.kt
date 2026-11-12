package com.xiaozhangben.ledger.domain.model

import com.xiaozhangben.ledger.domain.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** 收支类型。 */
enum class TxnType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入");

    /** 收入为正、支出为负，便于求和。 */
    fun sign(): Int = if (this == INCOME) 1 else -1

    companion object {
        fun fromLabel(text: String): TxnType? = when (text.trim().lowercase()) {
            "支出", "expense", "-", "out", "支出 ", "消费" -> EXPENSE
            "收入", "income", "+", "in", "入账" -> INCOME
            else -> null
        }
    }
}

/** 账户类型，决定默认图标。 */
enum class AccountType(val label: String, val defaultIcon: String) {
    CASH("现金", "💵"),
    WECHAT("微信", "💚"),
    ALIPAY("支付宝", "💙"),
    BANK("银行卡", "🏦"),
    CREDIT("信用卡", "💳"),
    OTHER("其他", "👛");

    companion object {
        fun fromLabel(text: String): AccountType? =
            entries.firstOrNull { it.label == text.trim() || it.name.equals(text.trim(), ignoreCase = true) }
    }
}

/** 周期账单重复频率。 */
enum class RecurrenceFrequency(val label: String) {
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    YEARLY("每年");

    companion object {
        fun fromLabel(text: String): RecurrenceFrequency? =
            entries.firstOrNull { it.label == text.trim() || it.name.equals(text.trim(), ignoreCase = true) }
    }
}

data class Category(
    val id: Long = 0L,
    val name: String,
    val type: TxnType,
    val icon: String = "📦",
    val color: Long = 0xFF607D8B,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

data class Account(
    val id: Long = 0L,
    val name: String,
    val type: AccountType = AccountType.CASH,
    val initialBalance: Long = 0L,
    val color: Long = 0xFF0288D1,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

data class Transaction(
    val id: Long = 0L,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val categoryName: String = "",
    val categoryIcon: String = "📦",
    val categoryColor: Long = 0xFF607D8B,
    val accountName: String = "",
) {
    val signedAmount: Long get() = amount * type.sign()
}

data class Budget(
    val id: Long = 0L,
    val categoryId: Long? = null,
    val amount: Long = 0L,
    val enabled: Boolean = true,
) {
    val isTotal: Boolean get() = categoryId == null
}

data class RecurringRule(
    val id: Long = 0L,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String = "",
    val frequency: RecurrenceFrequency = RecurrenceFrequency.MONTHLY,
    val nextRunAt: Long = System.currentTimeMillis(),
    val autoPost: Boolean = true,
    val enabled: Boolean = true,
    val categoryName: String = "",
    val categoryIcon: String = "📦",
    val accountName: String = "",
)

data class PeriodSummary(val expense: Long = 0L, val income: Long = 0L) {
    val balance: Long get() = income - expense
}

data class CategorySlice(
    val categoryId: Long,
    val name: String,
    val icon: String,
    val color: Long,
    val total: Long,
)

data class MonthlyPoint(
    val year: Int,
    val month: Int,
    val expense: Long,
    val income: Long,
) {
    val balance: Long get() = income - expense
    val label: String get() = "${month}月"
}

data class AccountBalance(
    val account: Account,
    val income: Long,
    val expense: Long,
) {
    val balance: Long get() = account.initialBalance + income - expense
}

data class BudgetProgress(
    val budget: Budget,
    val category: Category?,
    val spent: Long,
) {
    val limit: Long get() = budget.amount
    val remaining: Long get() = budget.amount - spent
    val over: Boolean get() = budget.amount > 0 && spent > budget.amount
    val ratio: Float get() = if (budget.amount <= 0L) 0f else (spent.toFloat() / budget.amount.toFloat())
}

data class ImportResult(
    val transactions: Int = 0,
    val newCategories: Int = 0,
    val newAccounts: Int = 0,
    val skipped: Int = 0,
)

/** 账单列表的筛选条件。 */
data class TransactionFilter(
    val keyword: String = "",
    val type: TxnType? = null,
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val yearMonth: YearMonth? = null,
) {
    val isActive: Boolean
        get() = keyword.isNotBlank() || type != null || categoryId != null || accountId != null || yearMonth != null

    fun matches(t: Transaction): Boolean {
        if (type != null && t.type != type) return false
        if (categoryId != null && t.categoryId != categoryId) return false
        if (accountId != null && t.accountId != accountId) return false
        if (yearMonth != null) {
            val date = Instant.ofEpochMilli(t.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
            if (YearMonth.from(date) != yearMonth) return false
        }
        val k = keyword.trim()
        if (k.isNotEmpty()) {
            val hit = t.note.contains(k, ignoreCase = true) ||
                t.categoryName.contains(k, ignoreCase = true) ||
                t.accountName.contains(k, ignoreCase = true) ||
                Money.formatPlain(t.amount).contains(k)
            if (!hit) return false
        }
        return true
    }
}
