package com.xiaozhangben.ledger.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.domain.model.Budget
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.RecurrenceFrequency
import com.xiaozhangben.ledger.domain.model.RecurringRule
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: TxnType,
    val icon: String,
    val color: Long,
    val sortOrder: Int,
    val archived: Boolean = false,
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: AccountType,
    val initialBalance: Long,
    val color: Long,
    val sortOrder: Int,
    val archived: Boolean = false,
)

@Entity(
    tableName = "transactions",
    indices = [Index("occurredAt"), Index("categoryId"), Index("accountId")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
)

/** categoryId 为 null 表示「本月总预算」。 */
@Entity(tableName = "budgets", indices = [Index(value = ["categoryId"], unique = true)])
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val categoryId: Long?,
    val amount: Long,
    val enabled: Boolean = true,
)

@Entity(tableName = "recurring_rules")
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: TxnType,
    val amount: Long,
    val categoryId: Long,
    val accountId: Long,
    val note: String,
    val frequency: RecurrenceFrequency,
    val nextRunAt: Long,
    val autoPost: Boolean,
    val enabled: Boolean,
)

// ---------- 实体 -> 领域模型 ----------

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    icon = icon,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
)

fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    type = type,
    initialBalance = initialBalance,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    initialBalance = initialBalance,
    color = color,
    sortOrder = sortOrder,
    archived = archived,
)

fun TransactionEntity.toDomain(
    categoryName: String = "",
    categoryIcon: String = "📦",
    categoryColor: Long = 0xFF607D8B,
    accountName: String = "",
): Transaction = Transaction(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    categoryName = categoryName,
    categoryIcon = categoryIcon,
    categoryColor = categoryColor,
    accountName = accountName,
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
)

fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = categoryId,
    amount = amount,
    enabled = enabled,
)

fun RecurringRuleEntity.toDomain(
    categoryName: String = "",
    categoryIcon: String = "📦",
    accountName: String = "",
): RecurringRule = RecurringRule(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    frequency = frequency,
    nextRunAt = nextRunAt,
    autoPost = autoPost,
    enabled = enabled,
    categoryName = categoryName,
    categoryIcon = categoryIcon,
    accountName = accountName,
)

fun RecurringRule.toEntity(): RecurringRuleEntity = RecurringRuleEntity(
    id = id,
    type = type,
    amount = amount,
    categoryId = categoryId,
    accountId = accountId,
    note = note,
    frequency = frequency,
    nextRunAt = nextRunAt,
    autoPost = autoPost,
    enabled = enabled,
)
