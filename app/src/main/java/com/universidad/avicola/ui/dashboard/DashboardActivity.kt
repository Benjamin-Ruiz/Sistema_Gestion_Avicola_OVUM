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
import com.universidad.avicola.ui.dashboard.fragments.*

class DashboardActivity : AppCompatActivity() {

    lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth

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
    }

    private fun configurarNavegacion() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
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
        binding.bottomNavigation.selectedItemId = R.id.nav_finanzas
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