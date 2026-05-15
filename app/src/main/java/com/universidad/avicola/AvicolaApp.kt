package com.universidad.avicola

import android.app.Application
import androidx.work.*
import com.universidad.avicola.data.local.AppDatabase
import com.universidad.avicola.data.sync.SyncWorker
import java.util.concurrent.TimeUnit

class AvicolaApp : Application() {

    companion object {
        lateinit var instance: AvicolaApp
            private set
    }

    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        setupPeriodicSync()
    }

    private fun setupPeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "PeriodicInventorySync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }
}
