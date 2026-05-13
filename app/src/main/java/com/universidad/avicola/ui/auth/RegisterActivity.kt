package com.universidad.avicola.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.databinding.ActivityRegisterBinding

/**
 * RegisterActivity.kt
 * ─────────────────────────────────────────────
 * Pantalla de registro de nuevos usuarios.
 * Crea la cuenta en Firebase Authentication.
 *
 * Coloca este archivo en:
 *   app/src/main/java/com/universidad/avicola/ui/auth/RegisterActivity.kt
 *
 * NOTA: Necesitas crear el layout activity_register.xml
 *       con campos: etNombre, etCorreo, etContrasena, etConfirmar,
 *       btnRegistrar, progressRegister
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Botón registrar
        binding.btnRegistrar.setOnClickListener {
            val correo = binding.etCorreo.text.toString().trim()
            val contrasena = binding.etContrasena.text.toString().trim()
            val confirmar = binding.etConfirmar.text.toString().trim()

            if (validar(correo, contrasena, confirmar)) {
                registrarUsuario(correo, contrasena)
            }
        }

        // Botón volver atrás
        binding.btnVolver.setOnClickListener { finish() }
    }

    private fun validar(correo: String, contrasena: String, confirmar: String): Boolean {
        if (correo.isEmpty() || contrasena.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_campos_vacios), Toast.LENGTH_SHORT).show()
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            Toast.makeText(this, getString(R.string.error_correo_invalido), Toast.LENGTH_SHORT).show()
            return false
        }
        if (contrasena.length < 6) {
            Toast.makeText(this, getString(R.string.error_contrasena_corta), Toast.LENGTH_SHORT).show()
            return false
        }
        if (contrasena != confirmar) {
            Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun registrarUsuario(correo: String, contrasena: String) {
        mostrarCargando(true)
        auth.createUserWithEmailAndPassword(correo, contrasena)
            .addOnCompleteListener(this) { task ->
                mostrarCargando(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.sendEmailVerification()
                        ?.addOnCompleteListener { verifyTask ->
                            if (verifyTask.isSuccessful) {
                                Toast.makeText(this,
                                    "Correo de verificación enviado a $correo. Revisa tu bandeja de entrada.",
                                    Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this,
                                    "No se pudo enviar el correo de verificación",
                                    Toast.LENGTH_SHORT).show()
                            }
                            auth.signOut()
                            finish()
                        }
                } else {
                    Toast.makeText(this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun mostrarCargando(cargando: Boolean) {
        binding.progressRegister.visibility = if (cargando) View.VISIBLE else View.GONE
        binding.btnRegistrar.isEnabled = !cargando
    }
}
