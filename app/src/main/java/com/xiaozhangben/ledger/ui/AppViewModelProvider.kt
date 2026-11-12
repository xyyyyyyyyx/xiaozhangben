package com.xiaozhangben.ledger.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xiaozhangben.ledger.LedgerApp
import com.xiaozhangben.ledger.ui.accounts.AccountsViewModel
import com.xiaozhangben.ledger.ui.budget.BudgetViewModel
import com.xiaozhangben.ledger.ui.categories.CategoriesViewModel
import com.xiaozhangben.ledger.ui.editor.EditorViewModel
import com.xiaozhangben.ledger.ui.home.HomeViewModel
import com.xiaozhangben.ledger.ui.lock.LockViewModel
import com.xiaozhangben.ledger.ui.records.RecordsViewModel
import com.xiaozhangben.ledger.ui.recurring.RecurringViewModel
import com.xiaozhangben.ledger.ui.settings.SettingsViewModel
import com.xiaozhangben.ledger.ui.stats.StatsViewModel

/**
 * 全应用共用的 ViewModel 工厂。所有 ViewModel 只依赖仓库，
 * 由 Application 里的 AppContainer 提供。
 */
val AppViewModelFactory = viewModelFactory {
    initializer { HomeViewModel(app().container.repository) }
    initializer { RecordsViewModel(app().container.repository) }
    initializer { StatsViewModel(app().container.repository) }
    initializer { AccountsViewModel(app().container.repository) }
    initializer { CategoriesViewModel(app().container.repository) }
    initializer { BudgetViewModel(app().container.repository) }
    initializer { RecurringViewModel(app().container.repository) }
    initializer {
        SettingsViewModel(
            repository = app().container.repository,
            backupManager = app().container.backupManager,
        )
    }
    initializer { EditorViewModel(app().container.repository) }
    initializer { LockViewModel(app().container.repository) }
}

private fun CreationExtras.app(): LedgerApp =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as LedgerApp
