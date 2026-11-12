package com.xiaozhangben.ledger.data.db

import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.domain.model.TxnType

/** 首次启动写入的默认分类与账户，用户之后可以随意增删改。 */
object DefaultData {

    const val FALLBACK_COLOR = 0xFF78909C

    fun categories(): List<CategoryEntity> = buildList {
        val expenses = listOf(
            "餐饮" to "🍚" to 0xFFEF5350,
            "交通" to "🚌" to 0xFF42A5F5,
            "购物" to "🛍️" to 0xFFAB47BC,
            "居住" to "🏠" to 0xFF8D6E63,
            "娱乐" to "🎮" to 0xFF7E57C2,
            "医疗" to "💊" to 0xFF26A69A,
            "学习" to "📚" to 0xFF5C6BC0,
            "通讯" to "📱" to 0xFF29B6F6,
            "人情" to "🎁" to 0xFFEC407A,
            "旅行" to "✈️" to 0xFF26C6DA,
            "宠物" to "🐾" to 0xFFFFA726,
            "其他" to "📦" to 0xFF78909C,
        )
        expenses.forEachIndexed { index, (nameAndIcon, color) ->
            val (name, icon) = nameAndIcon
            add(
                CategoryEntity(
                    name = name,
                    type = TxnType.EXPENSE,
                    icon = icon,
                    color = color,
                    sortOrder = index,
                ),
            )
        }

        val incomes = listOf(
            "工资" to "💰" to 0xFF66BB6A,
            "奖金" to "🏆" to 0xFFFFCA28,
            "兼职" to "🧾" to 0xFF9CCC65,
            "理财" to "📈" to 0xFF26A69A,
            "红包" to "🧧" to 0xFFEF5350,
            "报销" to "🧮" to 0xFF42A5F5,
            "其他" to "📦" to 0xFF78909C,
        )
        incomes.forEachIndexed { index, (nameAndIcon, color) ->
            val (name, icon) = nameAndIcon
            add(
                CategoryEntity(
                    name = name,
                    type = TxnType.INCOME,
                    icon = icon,
                    color = color,
                    sortOrder = index,
                ),
            )
        }
    }

    fun accounts(): List<AccountEntity> = listOf(
        AccountEntity(name = "现金", type = AccountType.CASH, initialBalance = 0L, color = 0xFF66BB6A, sortOrder = 0),
        AccountEntity(name = "微信", type = AccountType.WECHAT, initialBalance = 0L, color = 0xFF43A047, sortOrder = 1),
        AccountEntity(name = "支付宝", type = AccountType.ALIPAY, initialBalance = 0L, color = 0xFF1E88E5, sortOrder = 2),
        AccountEntity(name = "银行卡", type = AccountType.BANK, initialBalance = 0L, color = 0xFF5C6BC0, sortOrder = 3),
    )

    /** 分类图标选择器里提供的候选图标。 */
    val categoryIcons: List<String> = listOf(
        "🍚", "🍜", "🍔", "🍎", "☕", "🍰",
        "🚌", "🚇", "🚕", "⛽", "🚲", "✈️",
        "🛍️", "👕", "👟", "💄", "📦", "🎁",
        "🏠", "💡", "💧", "🔧", "🛋️", "🧹",
        "🎮", "🎬", "🎵", "🏀", "📷", "🎫",
        "💊", "🏥", "🦷", "💪", "📚", "✏️",
        "📱", "💻", "🌐", "🐾", "🧧", "💰",
        "🏆", "📈", "🧾", "🧮", "🚗", "❤️",
    )

    /** 分类调色板。 */
    val categoryColors: List<Long> = listOf(
        0xFFEF5350, 0xFFEC407A, 0xFFAB47BC, 0xFF7E57C2,
        0xFF5C6BC0, 0xFF42A5F5, 0xFF29B6F6, 0xFF26C6DA,
        0xFF26A69A, 0xFF66BB6A, 0xFF9CCC65, 0xFFD4E157,
        0xFFFFCA28, 0xFFFFA726, 0xFFFF7043, 0xFF8D6E63,
        0xFF78909C, 0xFF546E7A,
    )
}
