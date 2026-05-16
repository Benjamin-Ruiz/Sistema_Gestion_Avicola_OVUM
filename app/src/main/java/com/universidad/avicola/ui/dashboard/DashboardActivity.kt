package com.universidad.avicola.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.databinding.ActivityDashboardBinding
import com.universidad.avicola.ui.auth.LoginActivity
import com.universidad.avicola.ui.costos.CostosActivity
import com.universidad.avicola.ui.dashboard.fragments.*

class DashboardActivity : AppCompatActivity() {

    lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

    /**
     * FLAG: indica si el BottomNav ya fue inicializado por el usuario.
     *
     * CORRECCIÓN DEL BUG:
     * El problema era que binding.bottomNavigation.selectedItemId = R.id.nav_finanzas
     * al final de configurarNavegacion() dispara onItemSelected ANTES de que el usuario
     * toque nada. Si el usuario luego navega a InventarioFragment (que vive también
     * dentro del DashboardActivity), cualquier recreación del fragment o cambio de
     * configuración volvía a ejecutar configurarNavegacion(), que fijaba nav_finanzas
     * y reemplazaba InventarioFragment con FinanzasFragment.
     *
     * Solución: usamos un flag booleano para que la selección programática inicial
     * (setSelectedItemId) no dispare replaceFragment. Solo las pulsaciones reales
     * del usuario (posteriores al primer setup) ejecutan la navegación.
     */
    private var navInicializado = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        if (auth.currentUser == null) {
            irAlLogin()
            return
        }

        val user = auth.currentUser!!
        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                if (user.isEmailVerified) {
                    configurarNavegacion()
                    configurarToolbar()
                } else {
                    Toast.makeText(this, "Verifica tu correo electrónico.", Toast.LENGTH_LONG).show()
                    auth.signOut()
                    irAlLogin()
                }
            } else {
                auth.signOut()
                irAlLogin()
            }
        }
    }

    private fun configurarToolbar() {
        binding.btnToolbarLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que deseas salir?")
                .setPositiveButton("Sí") { _, _ ->
                    auth.signOut()
                    irAlLogin()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        binding.cardModuloCostos.setOnClickListener {
            startActivity(Intent(this, CostosActivity::class.java))
        }
    }

    private fun configurarNavegacion() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            // CORRECCIÓN: ignorar el primer disparo programático.
            // Solo navegar cuando el usuario ya interactuó con el nav al menos una vez.
            if (!navInicializado) return@setOnItemSelectedListener true

            when (item.itemId) {
                R.id.nav_operaciones -> {
                    replaceFragment(OperacionesFragment(), "OPERACIONES")
                    true
                }
                R.id.nav_estimacion -> {
                    replaceFragment(EstimacionFragment(), "ESTIMACIÓN")
                    true
                }
                R.id.nav_finanzas -> {
                    replaceFragment(FinanzasFragment(), "FINANZAS")
                    true
                }
                R.id.nav_salud -> {
                    replaceFragment(SaludFragment(), "SALUD")
                    true
                }
                R.id.nav_perfil -> {
                    replaceFragment(PerfilFragment(), "PERFIL")
                    true
                }
                else -> false
            }
        }

        // Esta línea dispara onItemSelected, pero navInicializado es false → se ignora.
        binding.bottomNavigation.selectedItemId = R.id.nav_finanzas

        // A partir de aquí el flag es true: el listener procesará los toques reales.
        navInicializado = true

        // Cargar el fragment inicial manualmente (sin depender del listener).
        replaceFragment(FinanzasFragment(), "FINANZAS")
    }

    fun replaceFragment(fragment: Fragment, subTitle: String, showReportes: Boolean = false) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
        binding.tvToolbarSubTitle.text = subTitle
        binding.btnToolbarReportes.visibility = if (showReportes) View.VISIBLE else View.GONE
    }

    private fun irAlLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    @Suppress("MissingSuperCall", "DEPRECATION")
    override fun onBackPressed() {}
}