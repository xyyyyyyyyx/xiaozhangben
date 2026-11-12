package com.xiaozhangben.ledger.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.xiaozhangben.ledger.data.prefs.AppSettings
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.accounts.AccountsScreen
import com.xiaozhangben.ledger.ui.budget.BudgetScreen
import com.xiaozhangben.ledger.ui.categories.CategoriesScreen
import com.xiaozhangben.ledger.ui.editor.TransactionEditorScreen
import com.xiaozhangben.ledger.ui.home.HomeScreen
import com.xiaozhangben.ledger.ui.lock.LockScreen
import com.xiaozhangben.ledger.ui.lock.LockSettingsScreen
import com.xiaozhangben.ledger.ui.records.RecordsScreen
import com.xiaozhangben.ledger.ui.recurring.RecurringScreen
import com.xiaozhangben.ledger.ui.settings.SettingsScreen
import com.xiaozhangben.ledger.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val RECORDS = "records"
    const val STATS = "stats"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
    const val CATEGORIES = "categories"
    const val BUDGET = "budget"
    const val RECURRING = "recurring"
    const val LOCK_SETTINGS = "lock_settings"

    const val EDITOR_PATTERN = "editor?txnId={txnId}&type={type}"

    fun editor(txnId: Long? = null, type: TxnType? = null): String {
        val typeName = type?.name.orEmpty()
        return if (txnId != null && txnId > 0L) {
            "editor?txnId=$txnId&type=$typeName"
        } else {
            "editor?type=$typeName"
        }
    }
}

private data class BottomTab(val route: String, val label: String, val emoji: String)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", "🏠"),
    BottomTab(Routes.RECORDS, "账单", "📋"),
    BottomTab(Routes.STATS, "统计", "📊"),
    BottomTab(Routes.SETTINGS, "我的", "👤"),
)

/** 应用根节点：负责初始化数据、应用锁拦截与导航。 */
@Composable
fun LedgerRoot(
    repository: LedgerRepository,
    settings: AppSettings,
) {
    var unlocked by rememberSaveable { mutableStateOf(!settings.lockEnabled) }
    val lockEnabled by rememberUpdatedState(settings.lockEnabled)
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(Unit) {
        runCatching {
            repository.ensureSeeded()
            repository.applyDueRecurring()
        }
    }

    LaunchedEffect(settings.lockEnabled) {
        if (!settings.lockEnabled) unlocked = true
    }

    // 退到后台后重新上锁
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && lockEnabled) {
                unlocked = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!unlocked) {
        LockScreen(onUnlocked = { unlocked = true })
    } else {
        LedgerNavHost()
    }
}

@Composable
private fun LedgerNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = bottomTabs.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Text(
                                    text = tab.emoji,
                                    fontSize = 20.sp,
                                    color = LocalContentColor.current,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onAddExpense = { navController.navigate(Routes.editor(type = TxnType.EXPENSE)) },
                    onAddIncome = { navController.navigate(Routes.editor(type = TxnType.INCOME)) },
                    onEditTransaction = { id -> navController.navigate(Routes.editor(txnId = id)) },
                    onOpenRecords = { navController.navigate(Routes.RECORDS) },
                    onOpenBudget = { navController.navigate(Routes.BUDGET) },
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenStats = { navController.navigate(Routes.STATS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.RECORDS) {
                RecordsScreen(
                    onAdd = { navController.navigate(Routes.editor()) },
                    onEdit = { id -> navController.navigate(Routes.editor(txnId = id)) },
                )
            }

            composable(Routes.STATS) {
                StatsScreen()
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onOpenAccounts = { navController.navigate(Routes.ACCOUNTS) },
                    onOpenCategories = { navController.navigate(Routes.CATEGORIES) },
                    onOpenBudget = { navController.navigate(Routes.BUDGET) },
                    onOpenRecurring = { navController.navigate(Routes.RECURRING) },
                    onOpenLock = { navController.navigate(Routes.LOCK_SETTINGS) },
                )
            }

            composable(Routes.ACCOUNTS) {
                AccountsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.CATEGORIES) {
                CategoriesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.BUDGET) {
                BudgetScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.RECURRING) {
                RecurringScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LOCK_SETTINGS) {
                LockSettingsScreen(onBack = { navController.popBackStack() })
            }

            composable(
                route = Routes.EDITOR_PATTERN,
                arguments = listOf(
                    navArgument("txnId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val rawId = entry.arguments?.getLong("txnId") ?: -1L
                val typeArg = entry.arguments?.getString("type").orEmpty()
                val initialType = TxnType.entries.firstOrNull { it.name == typeArg }
                TransactionEditorScreen(
                    txnId = rawId.takeIf { it > 0L },
                    initialType = initialType,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                )
            }
        }
    }
}
