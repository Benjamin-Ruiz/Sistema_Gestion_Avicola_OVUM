package com.universidad.avicola.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.databinding.ActivityLoginBinding
import com.universidad.avicola.ui.dashboard.DashboardActivity

/**
 * LoginActivity.kt
 * ─────────────────────────────────────────────
 * Pantalla de autenticación del Sistema Avícola.
 * Usa Firebase Authentication (email + password).
 *
 * Modificado: validación de estado de cuenta tras login.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Si el usuario ya inició sesión, ir directo al Dashboard
        if (auth.currentUser != null) {
            irAlDashboard()
            return
        }

        configurarBotones()
    }

    private fun configurarBotones() {
        // Botón Iniciar Sesión
        binding.btnIniciarSesion.setOnClickListener {
            val correo = binding.etUsuario.text.toString().trim()
            val contrasena = binding.etContrasena.text.toString().trim()

            if (validarCampos(correo, contrasena)) {
                iniciarSesion(correo, contrasena)
            }
        }

        // Botón Registrarse → ir a RegisterActivity
        binding.btnRegistrarse.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Ocultar error al escribir
        binding.etUsuario.setOnFocusChangeListener { _, _ ->
            binding.tilUsuario.error = null
        }
        binding.etContrasena.setOnFocusChangeListener { _, _ ->
            binding.tilContrasena.error = null
        }
    }

    private fun validarCampos(correo: String, contrasena: String): Boolean {
        var valido = true

        if (correo.isEmpty()) {
            binding.tilUsuario.error = getString(R.string.error_campos_vacios)
            valido = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            binding.tilUsuario.error = getString(R.string.error_correo_invalido)
            valido = false
        }

        if (contrasena.isEmpty()) {
            binding.tilContrasena.error = getString(R.string.error_campos_vacios)
            valido = false
        } else if (contrasena.length < 6) {
            binding.tilContrasena.error = getString(R.string.error_contrasena_corta)
            valido = false
        }

        return valido
    }

    // ──────────────────────────────────────────
    //  Autenticación con Firebase + validación de cuenta
    // ──────────────────────────────────────────
    private fun iniciarSesion(correo: String, contrasena: String) {
        mostrarCargando(true)

        auth.signInWithEmailAndPassword(correo, contrasena)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.reload()?.addOnCompleteListener { reloadTask ->
                        mostrarCargando(false)
                        if (reloadTask.isSuccessful) {
                            if (user.isEmailVerified) {
                                irAlDashboard()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Revisa tu bandeja de correo y verifica para continuar.",
                                    Toast.LENGTH_LONG
                                ).show()
                                auth.signOut()
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "Error al verificar el estado de la cuenta. Intenta de nuevo.",
                                Toast.LENGTH_SHORT
                            ).show()
                            auth.signOut()
                        }
                    }
                } else {
                    // Error de autenticación
                    mostrarCargando(false)
                    binding.tilContrasena.error = getString(R.string.error_login)
                    Toast.makeText(this, getString(R.string.error_login), Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ──────────────────────────────────────────
    //  Navegación al Dashboard
    // ──────────────────────────────────────────
    private fun irAlDashboard() {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ──────────────────────────────────────────
    //  Control de carga visual
    // ──────────────────────────────────────────
    private fun mostrarCargando(cargando: Boolean) {
        binding.progressLogin.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnIniciarSesion.isEnabled = !cargando
        binding.btnRegistrarse.isEnabled = !cargando
    }
}
