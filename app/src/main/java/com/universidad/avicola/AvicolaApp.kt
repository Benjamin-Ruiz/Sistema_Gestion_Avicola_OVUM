package com.universidad.avicola

import android.app.Application
import androidx.work.*
import com.universidad.avicola.data.local.AppDatabase
import com.universidad.avicola.data.sync.SyncWorker
import java.util.concurrent.TimeUnit

class AvicolaApp : Application() {

    val database by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate() {
        super.onCreate()
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
