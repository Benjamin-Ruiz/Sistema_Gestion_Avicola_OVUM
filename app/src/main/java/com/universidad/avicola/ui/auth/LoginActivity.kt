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
 * Coloca este archivo en:
 *   app/src/main/java/com/universidad/avicola/ui/auth/LoginActivity.kt
 */
class LoginActivity : AppCompatActivity() {

    // ViewBinding — evita findViewById en toda la Activity
    private lateinit var binding: ActivityLoginBinding

    // Instancia de Firebase Auth
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflar el layout con ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar Firebase Auth
        auth = Firebase.auth

        // Si el usuario ya inició sesión, ir directo al Dashboard
        if (auth.currentUser != null) {
            irAlDashboard()
            return
        }

        configurarBotones()
    }

    // ──────────────────────────────────────────
    //  Configuración de botones
    // ──────────────────────────────────────────
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

    // ──────────────────────────────────────────
    //  Validación de campos
    // ──────────────────────────────────────────
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
    //  Autenticación con Firebase
    // ──────────────────────────────────────────
    private fun iniciarSesion(correo: String, contrasena: String) {
        mostrarCargando(true)

        auth.signInWithEmailAndPassword(correo, contrasena)
            .addOnCompleteListener(this) { task ->
                mostrarCargando(false)

                if (task.isSuccessful) {
                    // Login exitoso → ir al Dashboard
                    irAlDashboard()
                } else {
                    // Error de autenticación
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
        // Limpiar el backstack — no volver al Login con el botón atrás
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
