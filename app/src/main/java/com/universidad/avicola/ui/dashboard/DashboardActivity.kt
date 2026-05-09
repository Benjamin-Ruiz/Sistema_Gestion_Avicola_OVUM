package com.universidad.avicola.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.databinding.ActivityDashboardBinding
import com.universidad.avicola.ui.auth.LoginActivity
import com.universidad.avicola.ui.aves.GestionAvesActivity
import com.universidad.avicola.ui.inventario.InventarioActivity

/**
 * DashboardActivity.kt
 * ─────────────────────────────────────────────
 * Pantalla de bienvenida con grid de módulos.
 * Solo el módulo Inventario está activo; los demás
 * muestran un Toast indicando "próximamente".
 *
 * Coloca este archivo en:
 *   app/src/main/java/com/universidad/avicola/ui/dashboard/DashboardActivity.kt
 */
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Verificar sesión activa
        if (auth.currentUser == null) {
            irAlLogin()
            return
        }

        configurarModulos()
        configurarCerrarSesion()
    }

    // ──────────────────────────────────────────
    //  Configuración de módulos
    // ──────────────────────────────────────────
    private fun configurarModulos() {

        // ✅ MÓDULO ACTIVO: Inventario
        binding.cardInventario.setOnClickListener {
            startActivity(Intent(this, InventarioActivity::class.java))
        }

        // ✅ MÓDULO ACTIVO: Gestión de Aves
        binding.cardGestionAves.setOnClickListener {
            startActivity(Intent(this, GestionAvesActivity::class.java))
        }

        // 🔒 Módulos próximamente disponibles
        val modulosInactivos = listOf(
            binding.cardAlimentacion,
            binding.cardTemperatura,
            binding.cardMedico,
            binding.cardFinanzas,
            binding.cardEnfermedades,
            binding.cardCostos
        )

        modulosInactivos.forEach { card ->
            card.setOnClickListener {
                Toast.makeText(
                    this,
                    getString(R.string.modulo_no_disponible),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ──────────────────────────────────────────
    //  Cerrar sesión con confirmación
    // ──────────────────────────────────────────
    private fun configurarCerrarSesion() {
        binding.btnCerrarSesion.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro que deseas cerrar sesión?")
                .setPositiveButton("Sí") { _, _ ->
                    auth.signOut()
                    irAlLogin()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    // ──────────────────────────────────────────
    //  Navegar al Login
    // ──────────────────────────────────────────
    private fun irAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // Bloquear el botón atrás en el dashboard
    override fun onBackPressed() {
        // No hacer nada — el usuario debe usar "Cerrar sesión"
    }
}
