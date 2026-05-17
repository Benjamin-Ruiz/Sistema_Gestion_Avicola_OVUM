package com.universidad.avicola.util

import android.content.Context
import android.content.SharedPreferences

class TutorialManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("ovum_tutorial_prefs", Context.MODE_PRIVATE)

    fun deberiaMostrarTutorial(userId: String): Boolean {
        if (userId.isEmpty()) return false
        return !prefs.getBoolean("tutorial_visto_$userId", false)
    }

    fun marcarTutorialComoVisto(userId: String) {
        if (userId.isNotEmpty()) {
            prefs.edit().putBoolean("tutorial_visto_$userId", true).apply()
        }
    }
}
