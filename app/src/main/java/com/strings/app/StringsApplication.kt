package com.strings.app

import android.app.Application
import com.strings.app.di.appModule
import com.strings.app.di.dataModule
import com.strings.app.di.domainModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class StringsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@StringsApplication)
            modules(dataModule, domainModule, appModule)
        }
    }
}
