package com.universidad.avicola.util

import android.animation.ValueAnimator
import android.widget.TextView
import java.text.NumberFormat
import java.util.Locale

/**
 * Anima el cambio de un número en un TextView.
 */
fun TextView.animateNumber(to: Number, isCurrency: Boolean = false) {
    val from = text.toString().replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
    val animator = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
    animator.duration = 800
    
    val format = if (isCurrency) {
        NumberFormat.getCurrencyInstance(Locale("es", "GT")) // O el local que prefieras
    } else {
        null
    }

    animator.addUpdateListener { animation ->
        val value = animation.animatedValue as Float
        text = if (isCurrency) {
            format?.format(value)
        } else {
            if (to is Int || to is Long) value.toInt().toString()
            else String.format("%.1f", value)
        }
    }
    animator.start()
}
