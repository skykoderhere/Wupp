package com.wupp.meow

import android.app.Application
import android.content.Context
import com.wupp.meow.core.service.WaRealtimeService
import com.wupp.meow.data.db.WaUiDatabase
import com.wupp.meow.data.media.EagerMediaVault
import com.wupp.meow.data.media.SoundPoolManager
import com.wupp.meow.domain.security.AppLockManager
import com.wupp.meow.domain.stealth.StealthMatrixManager
import com.wupp.meow.ui.theme.LocaleHelper

class WaBeastApp : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        EagerMediaVault.ensureDirectories(this)
        StealthMatrixManager.init(this)
        AppLockManager.init(this)
        SoundPoolManager.init(this)
        WaUiDatabase.getInstance(this)
    }
}
