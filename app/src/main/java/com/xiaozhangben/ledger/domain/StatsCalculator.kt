package com.xiaozhangben.ledger.domain

import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountBalance
import com.xiaozhangben.ledger.domain.model.Budget
import com.xiaozhangben.ledger.domain.model.BudgetProgress
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.CategorySlice
import com.xiaozhangben.ledger.domain.model.MonthlyPoint
import com.xiaozhangben.ledger.domain.model.PeriodSummary
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType
import java.time.LocalDate
import java.time.YearMonth

/** 所有统计都在内存里聚合：个人记账数据量小，换来实现简单且不会有 SQL 方言坑。 */
object StatsCalculator {

    fun summarize(transactions: List<Transaction>): PeriodSummary {
        var expense = 0L
        var income = 0L
        for (t in transactions) {
            if (t.type == TxnType.EXPENSE) expense += t.amount else income += t.amount
        }
        return PeriodSummary(expense = expense, income = income)
    }

    fun inMonth(transactions: List<Transaction>, month: YearMonth): List<Transaction> =
        transactions.filter { DateUtils.yearMonthOf(it.occurredAt) == month }

    fun inRange(transactions: List<Transaction>, fromMillis: Long, toMillisExclusive: Long): List<Transaction> =
        transactions.filter { it.occurredAt >= fromMillis && it.occurredAt < toMillisExclusive }

    fun byCategory(transactions: List<Transaction>, type: TxnType = TxnType.EXPENSE): List<CategorySlice> {
        val buckets = LinkedHashMap<Long, CategorySlice>()
        for (t in transactions) {
            if (t.type != type) continue
            val previous = buckets[t.categoryId]
            buckets[t.categoryId] = if (previous == null) {
                CategorySlice(
                    categoryId = t.categoryId,
                    name = t.categoryName.ifEmpty { "未分类" },
                    icon = t.categoryIcon,
                    color = t.categoryColor,
                    total = t.amount,
                )
            } else {
                previous.copy(total = previous.total + t.amount)
            }
        }
        return buckets.values.sortedByDescending { it.total }
    }

    /** 以 anchor 结尾、向前共 months 个月的收支趋势，按时间正序返回。 */
    fun monthlyTrend(transactions: List<Transaction>, anchor: YearMonth, months: Int): List<MonthlyPoint> {
        if (months <= 0) return emptyList()
        val start = anchor.minusMonths((months - 1).toLong())
        val expense = HashMap<YearMonth, Long>()
        val income = HashMap<YearMonth, Long>()
        for (t in transactions) {
            val month = DateUtils.yearMonthOf(t.occurredAt)
            if (month < start || month > anchor) continue
            if (t.type == TxnType.EXPENSE) {
                expense[month] = (expense[month] ?: 0L) + t.amount
            } else {
                income[month] = (income[month] ?: 0L) + t.amount
            }
        }
        return (0 until months).map { index ->
            val month = start.plusMonths(index.toLong())
            MonthlyPoint(
                year = month.year,
                month = month.monthValue,
                expense = expense[month] ?: 0L,
                income = income[month] ?: 0L,
            )
        }
    }

    /** 某月每天的支出合计，索引 0 对应 1 号。 */
    fun dailyExpense(transactions: List<Transaction>, month: YearMonth): List<Long> {
        val days = month.lengthOfMonth()
        val result = LongArray(days)
        for (t in transactions) {
            if (t.type != TxnType.EXPENSE) continue
            val date = DateUtils.toLocalDate(t.occurredAt)
            if (YearMonth.from(date) != month) continue
            result[date.dayOfMonth - 1] += t.amount
        }
        return result.toList()
    }

    fun accountBalances(accounts: List<Account>, transactions: List<Transaction>): List<AccountBalance> {
        val grouped = transactions.groupBy { it.accountId }
        return accounts.map { account ->
            var income = 0L
            var expense = 0L
            grouped[account.id]?.forEach { t ->
                if (t.type == TxnType.INCOME) income += t.amount else expense += t.amount
            }
            AccountBalance(account = account, income = income, expense = expense)
        }
    }

    /** 本月预算执行情况，总预算排在最前，其余按使用比例从高到低。 */
    fun budgetProgress(
        budgets: List<Budget>,
        monthTransactions: List<Transaction>,
        categories: List<Category>,
    ): List<BudgetProgress> {
        val expenses = monthTransactions.filter { it.type == TxnType.EXPENSE }
        val byCategory = expenses.groupBy { it.categoryId }
        return budgets.map { budget ->
            val spent = if (budget.categoryId == null) {
                expenses.sumOf { it.amount }
            } else {
                byCategory[budget.categoryId]?.sumOf { it.amount } ?: 0L
            }
            BudgetProgress(
                budget = budget,
                category = categories.firstOrNull { it.id == budget.categoryId },
                spent = spent,
            )
        }.sortedWith(
            compareByDescending<BudgetProgress> { it.budget.isTotal }
                .thenByDescending { it.ratio },
        )
    }

    /** 最近 n 天的每日支出，用于首页迷你走势。 */
    fun recentDailyExpense(transactions: List<Transaction>, days: Int, today: LocalDate = LocalDate.now()): List<Long> {
        if (days <= 0) return emptyList()
        val buckets = HashMap<LocalDate, Long>()
        for (t in transactions) {
            if (t.type != TxnType.EXPENSE) continue
            val date = DateUtils.toLocalDate(t.occurredAt)
            buckets[date] = (buckets[date] ?: 0L) + t.amount
        }
        return (days - 1 downTo 0).map { offset ->
            buckets[today.minusDays(offset.toLong())] ?: 0L
        }
    }
}
