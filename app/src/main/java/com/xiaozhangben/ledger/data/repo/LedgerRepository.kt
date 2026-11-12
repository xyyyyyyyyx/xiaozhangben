package com.xiaozhangben.ledger.data.repo

import com.xiaozhangben.ledger.data.backup.CsvCodec
import com.xiaozhangben.ledger.data.db.AccountEntity
import com.xiaozhangben.ledger.data.db.AppDatabase
import com.xiaozhangben.ledger.data.db.BudgetEntity
import com.xiaozhangben.ledger.data.db.CategoryEntity
import com.xiaozhangben.ledger.data.db.DefaultData
import com.xiaozhangben.ledger.data.db.RecurringRuleEntity
import com.xiaozhangben.ledger.data.db.TransactionEntity
import com.xiaozhangben.ledger.data.db.toDomain
import com.xiaozhangben.ledger.data.db.toEntity
import com.xiaozhangben.ledger.data.prefs.AppPreferences
import com.xiaozhangben.ledger.data.prefs.AppSettings
import com.xiaozhangben.ledger.data.prefs.ThemeMode
import com.xiaozhangben.ledger.domain.RecurringEngine
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.domain.model.Budget
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.ImportResult
import com.xiaozhangben.ledger.domain.model.RecurringRule
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 删除被引用对象时的结果。 */
sealed interface DeleteResult {
    data object Deleted : DeleteResult

    data class InUse(val transactionCount: Int, val recurringCount: Int = 0) : DeleteResult
}

/**
 * 全应用唯一的数据入口：把 Room 与 DataStore 暴露成领域模型流，并集中所有写操作。
 * 个人记账数据量小，列表筛选与统计都放在内存里完成。
 */
class LedgerRepository(
    database: AppDatabase,
    private val preferences: AppPreferences,
) {

    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val accountDao = database.accountDao()
    private val budgetDao = database.budgetDao()
    private val recurringDao = database.recurringRuleDao()

    val settings: Flow<AppSettings> = preferences.settings

    val categories: Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    val accounts: Flow<List<Account>> =
        accountDao.observeAll().map { list -> list.map { it.toDomain() } }

    val budgets: Flow<List<Budget>> =
        budgetDao.observeAll().map { list -> list.map { it.toDomain() } }

    val transactions: Flow<List<Transaction>> = combine(
        transactionDao.observeAll(),
        categoryDao.observeAll(),
        accountDao.observeAll(),
    ) { transactions, categories, accounts ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        transactions.map { entity ->
            val category = categoryMap[entity.categoryId]
            val account = accountMap[entity.accountId]
            entity.toDomain(
                categoryName = category?.name ?: "未分类",
                categoryIcon = category?.icon ?: "📦",
                categoryColor = category?.color ?: DefaultData.FALLBACK_COLOR,
                accountName = account?.name ?: "已删除账户",
            )
        }
    }

    val recurringRules: Flow<List<RecurringRule>> = combine(
        recurringDao.observeAll(),
        categoryDao.observeAll(),
        accountDao.observeAll(),
    ) { rules, categories, accounts ->
        val categoryMap = categories.associateBy { it.id }
        val accountMap = accounts.associateBy { it.id }
        rules.map { entity ->
            val category = categoryMap[entity.categoryId]
            entity.toDomain(
                categoryName = category?.name ?: "未分类",
                categoryIcon = category?.icon ?: "📦",
                accountName = accountMap[entity.accountId]?.name ?: "已删除账户",
            )
        }
    }

    // ---------------- 初始化 ----------------

    /** 首次启动写入默认分类与账户。 */
    suspend fun ensureSeeded() {
        if (preferences.current().seeded) return
        if (categoryDao.count() == 0) categoryDao.insertAll(DefaultData.categories())
        if (accountDao.count() == 0) accountDao.insertAll(DefaultData.accounts())
        preferences.setSeeded(true)
    }

    // ---------------- 流水 ----------------

    suspend fun addTransaction(transaction: Transaction): Long =
        transactionDao.insert(transaction.toEntity())

    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.update(transaction.toEntity())

    suspend fun deleteTransaction(id: Long) = transactionDao.deleteById(id)

    suspend fun deleteTransactions(ids: List<Long>) {
        ids.forEach { transactionDao.deleteById(it) }
    }

    suspend fun findTransaction(id: Long): Transaction? =
        transactionDao.getById(id)?.toDomain()

    // ---------------- 分类 ----------------

    suspend fun addCategory(category: Category): Long {
        val sortOrder = (categoryDao.maxSortOrder(category.type) ?: -1) + 1
        return categoryDao.insert(category.copy(sortOrder = sortOrder).toEntity())
    }

    suspend fun updateCategory(category: Category) =
        categoryDao.update(category.toEntity())

    suspend fun setCategoryArchived(id: Long, archived: Boolean) {
        val entity = categoryDao.getById(id) ?: return
        categoryDao.update(entity.copy(archived = archived))
    }

    /** 上移或下移一位，用于自定义分类排序。 */
    suspend fun moveCategory(id: Long, delta: Int) {
        val all = categoryDao.getAllOnce()
        val target = all.firstOrNull { it.id == id } ?: return
        val siblings = all.filter { it.type == target.type }.sortedBy { it.sortOrder }
        val index = siblings.indexOfFirst { it.id == id }
        val swapIndex = index + delta
        if (index < 0 || swapIndex !in siblings.indices) return
        val other = siblings[swapIndex]
        categoryDao.update(target.copy(sortOrder = other.sortOrder))
        categoryDao.update(other.copy(sortOrder = target.sortOrder))
    }

    /** 分类被流水或周期账单引用时不允许删除。 */
    suspend fun deleteCategory(id: Long): DeleteResult {
        val used = transactionDao.countByCategory(id)
        val recurring = recurringDao.countByCategory(id)
        if (used > 0 || recurring > 0) return DeleteResult.InUse(used, recurring)
        categoryDao.deleteById(id)
        budgetDao.getByCategory(id)?.let { budgetDao.deleteById(it.id) }
        return DeleteResult.Deleted
    }

    // ---------------- 账户 ----------------

    suspend fun addAccount(account: Account): Long {
        val sortOrder = (accountDao.maxSortOrder() ?: -1) + 1
        return accountDao.insert(account.copy(sortOrder = sortOrder).toEntity())
    }

    suspend fun updateAccount(account: Account) =
        accountDao.update(account.toEntity())

    suspend fun setAccountArchived(id: Long, archived: Boolean) {
        val entity = accountDao.getById(id) ?: return
        accountDao.update(entity.copy(archived = archived))
    }

    suspend fun deleteAccount(id: Long): DeleteResult {
        val used = transactionDao.countByAccount(id)
        val recurring = recurringDao.countByAccount(id)
        if (used > 0 || recurring > 0) return DeleteResult.InUse(used, recurring)
        accountDao.deleteById(id)
        return DeleteResult.Deleted
    }

    // ---------------- 预算 ----------------

    /** 同一个分类（或总预算）只保留一条记录。 */
    suspend fun saveBudget(categoryId: Long?, amount: Long) {
        if (amount <= 0L) {
            budgetDao.getByCategory(categoryId)?.let { budgetDao.deleteById(it.id) }
            return
        }
        val existing = budgetDao.getByCategory(categoryId)
        if (existing == null) {
            budgetDao.insert(BudgetEntity(categoryId = categoryId, amount = amount, enabled = true))
        } else {
            budgetDao.update(existing.copy(amount = amount, enabled = true))
        }
    }

    suspend fun setBudgetEnabled(id: Long, enabled: Boolean) {
        val existing = budgetDao.getAllOnce().firstOrNull { it.id == id } ?: return
        budgetDao.update(existing.copy(enabled = enabled))
    }

    suspend fun deleteBudget(id: Long) = budgetDao.deleteById(id)

    // ---------------- 周期账单 ----------------

    suspend fun addRecurring(rule: RecurringRule): Long = recurringDao.insert(rule.toEntity())

    suspend fun updateRecurring(rule: RecurringRule) = recurringDao.update(rule.toEntity())

    suspend fun deleteRecurring(id: Long) = recurringDao.deleteById(id)

    suspend fun findRecurring(id: Long): RecurringRule? = recurringDao.getById(id)?.toDomain()

    /** 生成所有到期流水并推进下次执行时间，返回新增条数。 */
    suspend fun applyDueRecurring(now: Long = System.currentTimeMillis()): Int {
        val due = recurringDao.getDue(now)
        if (due.isEmpty()) return 0
        var created = 0
        for (rule in due) {
            if (rule.amount <= 0L) continue
            val occurrences = RecurringEngine.dueOccurrences(rule.nextRunAt, rule.frequency, now)
            if (occurrences.isEmpty()) continue
            val entities = occurrences.map { at ->
                TransactionEntity(
                    type = rule.type,
                    amount = rule.amount,
                    categoryId = rule.categoryId,
                    accountId = rule.accountId,
                    note = rule.note.ifBlank { "周期账单" },
                    occurredAt = at,
                    createdAt = now,
                )
            }
            transactionDao.insertAll(entities)
            created += entities.size
            recurringDao.update(
                rule.copy(nextRunAt = RecurringEngine.next(occurrences.last(), rule.frequency)),
            )
        }
        return created
    }

    // ---------------- 备份 ----------------

    suspend fun exportCsv(): String {
        val all = transactions.first()
        val text = CsvCodec.encode(all.sortedBy { it.occurredAt })
        preferences.markExported(System.currentTimeMillis())
        return text
    }

    /** 导入 CSV：缺失的分类/账户会自动按名称补建。 */
    suspend fun importCsv(text: String): ImportResult {
        val rows = CsvCodec.decode(text)
        if (rows.isEmpty()) return ImportResult()

        val categories = categoryDao.getAllOnce().toMutableList()
        val accounts = accountDao.getAllOnce().toMutableList()
        var newCategories = 0
        var newAccounts = 0

        val entities = ArrayList<TransactionEntity>(rows.size)
        val now = System.currentTimeMillis()
        for (row in rows) {
            val categoryName = row.category.ifBlank { "其他" }
            var category = categories.firstOrNull { it.name == categoryName && it.type == row.type }
            if (category == null) {
                val sortOrder = (categories.filter { it.type == row.type }.maxOfOrNull { it.sortOrder } ?: -1) + 1
                val entity = CategoryEntity(
                    name = categoryName,
                    type = row.type,
                    icon = "📦",
                    color = DefaultData.FALLBACK_COLOR,
                    sortOrder = sortOrder,
                )
                val id = categoryDao.insert(entity)
                category = entity.copy(id = id)
                categories.add(category)
                newCategories++
            }

            val accountName = row.account.ifBlank { "现金" }
            var account = accounts.firstOrNull { it.name == accountName }
            if (account == null) {
                val entity = AccountEntity(
                    name = accountName,
                    type = AccountType.OTHER,
                    initialBalance = 0L,
                    color = DefaultData.FALLBACK_COLOR,
                    sortOrder = (accounts.maxOfOrNull { it.sortOrder } ?: -1) + 1,
                )
                val id = accountDao.insert(entity)
                account = entity.copy(id = id)
                accounts.add(account)
                newAccounts++
            }

            entities.add(
                TransactionEntity(
                    type = row.type,
                    amount = row.amount,
                    categoryId = category.id,
                    accountId = account.id,
                    note = row.note,
                    occurredAt = row.occurredAt,
                    createdAt = now,
                ),
            )
        }

        if (entities.isNotEmpty()) transactionDao.insertAll(entities)
        preferences.markImported(now)

        return ImportResult(
            transactions = entities.size,
            newCategories = newCategories,
            newAccounts = newAccounts,
            skipped = 0,
        )
    }

    // ---------------- 安全 / 外观 ----------------

    suspend fun verifyPin(pin: String): Boolean = preferences.verifyPin(pin)

    suspend fun setPin(pin: String) = preferences.setPin(pin)

    suspend fun setLockEnabled(enabled: Boolean) = preferences.setLockEnabled(enabled)

    suspend fun setBiometricEnabled(enabled: Boolean) = preferences.setBiometricEnabled(enabled)

    suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)

    suspend fun clearPin() = preferences.clearPin()

    // ---------------- 危险操作 ----------------

    /** 清空全部流水，保留分类与账户设置。 */
    suspend fun clearTransactions() = transactionDao.clearAll()

    /** 恢复出厂：连分类、账户、预算、周期账单一起清掉，下次启动会重新写入默认数据。 */
    suspend fun resetEverything() {
        transactionDao.clearAll()
        recurringDao.clearAll()
        budgetDao.clearAll()
        val categories = categoryDao.getAllOnce()
        categories.forEach { categoryDao.deleteById(it.id) }
        val accounts = accountDao.getAllOnce()
        accounts.forEach { accountDao.deleteById(it.id) }
        preferences.setSeeded(false)
        ensureSeeded()
    }

    suspend fun transactionCount(): Int = transactionDao.getAllOnce().size

    /** 供周期账单页做「立即执行一次」用的工具方法。 */
    suspend fun postRecurringNow(rule: RecurringRule) {
        val now = System.currentTimeMillis()
        transactionDao.insert(
            TransactionEntity(
                type = rule.type,
                amount = rule.amount,
                categoryId = rule.categoryId,
                accountId = rule.accountId,
                note = rule.note.ifBlank { "周期账单" },
                occurredAt = rule.nextRunAt,
                createdAt = now,
            ),
        )
        recurringDao.update(rule.toEntity().copy(nextRunAt = RecurringEngine.next(rule.nextRunAt, rule.frequency)))
    }

    /** 统计某个分类下已有的流水条数，用于界面提示。 */
    suspend fun categoryUsage(id: Long): Int = transactionDao.countByCategory(id)

    suspend fun accountUsage(id: Long): Int = transactionDao.countByAccount(id)

    suspend fun categoriesOnce(): List<Category> = categoryDao.getAllOnce().map { it.toDomain() }

    suspend fun accountsOnce(): List<Account> = accountDao.getAllOnce().map { it.toDomain() }

    suspend fun categoriesOf(type: TxnType): List<Category> =
        categoryDao.getAllOnce().filter { it.type == type && !it.archived }.map { it.toDomain() }
}
