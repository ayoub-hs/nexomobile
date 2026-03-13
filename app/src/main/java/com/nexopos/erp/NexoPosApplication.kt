package com.nexopos.erp

import android.app.Application
import com.nexopos.erp.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * HIGH-001: Application class with Koin initialization.
 */
class NexoPosApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger(Level.ERROR) // Only log errors in production
            androidContext(this@NexoPosApplication)
            modules(appModules)
        }
    }
}
