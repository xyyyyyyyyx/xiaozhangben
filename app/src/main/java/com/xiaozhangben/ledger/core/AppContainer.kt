package com.xiaozhangben.ledger.core

import android.content.Context
import com.xiaozhangben.ledger.data.backup.BackupManager
import com.xiaozhangben.ledger.data.db.AppDatabase
import com.xiaozhangben.ledger.data.prefs.AppPreferences
import com.xiaozhangben.ledger.data.repo.LedgerRepository

/** 手写依赖容器：这个规模的应用不需要 Hilt。 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    val preferences: AppPreferences by lazy { AppPreferences(appContext) }

    val backupManager: BackupManager by lazy { BackupManager(appContext) }

    val repository: LedgerRepository by lazy { LedgerRepository(database, preferences) }
}
