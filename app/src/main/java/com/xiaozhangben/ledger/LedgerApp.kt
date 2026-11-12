package com.xiaozhangben.ledger

import android.app.Application
import com.xiaozhangben.ledger.core.AppContainer

class LedgerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
