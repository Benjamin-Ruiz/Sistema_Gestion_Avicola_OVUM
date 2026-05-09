package com.universidad.avicola.ui.aves

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.universidad.avicola.data.model.Lote
import com.universidad.avicola.data.model.PropositoLote
import com.universidad.avicola.databinding.ActivityGestionAvesBinding
import com.universidad.avicola.databinding.DialogNuevoLoteBinding
import com.universidad.avicola.databinding.DialogRegistroAvesBinding
import java.util.*

class GestionAvesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGestionAvesBinding
    private val viewModel: AvesViewModel by viewModels()
    private lateinit var adapter: LoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGestionAvesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarUI()
        observarViewModel()
    }

    private fun configurarUI() {
        adapter = LoteAdapter(
            onActionClick = { abrirDialogRegistro(it) },
            onLongClick = { confirmarEliminacion(it) }
        )
        binding.recyclerLotes.layoutManager = LinearLayoutManager(this)
        binding.recyclerLotes.adapter = adapter

        binding.fabAnadirLote.setOnClickListener { abrirDialogNuevoLote() }
    }

    private fun confirmarEliminacion(lote: Lote) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.email == null) {
            Toast.makeText(this, "Sesión no válida", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Confirmar Eliminación")
            .setMessage("¿Está seguro de que desea eliminar el Lote #${lote.id.takeLast(5).uppercase()}? Esta acción borrará permanentemente todos los registros asociados.")
            .setPositiveButton("Eliminar") { _, _ ->
                solicitarPasswordParaEliminarLote(user.email!!, lote)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun solicitarPasswordParaEliminarLote(email: String, lote: Lote) {
        val inputLayout = TextInputLayout(this).apply {
            setPadding(60, 20, 60, 0)
            hint = "Ingrese contraseña para confirmar"
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(12f, 12f, 12f, 12f)
        }
        val etPassword = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 14f
        }
        inputLayout.addView(etPassword)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Verificación de Seguridad")
            .setMessage("Para eliminar el lote y sus registros, por favor verifique su identidad.")
            .setView(inputLayout)
            .setPositiveButton("Confirmar Eliminación") { _, _ ->
                val password = etPassword.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, "Contraseña requerida", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val credential = EmailAuthProvider.getCredential(email, password)
                FirebaseAuth.getInstance().currentUser?.reauthenticate(credential)
                    ?.addOnSuccessListener {
                        viewModel.eliminarLote(lote.id)
                        Toast.makeText(this, "✓ Lote eliminado con éxito", Toast.LENGTH_SHORT).show()
                    }
                    ?.addOnFailureListener {
                        Toast.makeText(this, "❌ Error: Contraseña incorrecta", Toast.LENGTH_LONG).show()
                    }
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(android.graphics.Color.RED)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        }
        dialog.show()
    }

    private fun observarViewModel() {
        viewModel.lotesActivos.observe(this) { lotes ->
            adapter.submitList(lotes)
        }
    }

    private fun abrirDialogNuevoLote() {
        val sheet = BottomSheetDialog(this)
        val b = DialogNuevoLoteBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        // Configurar Spinner de Propósito
        val propositos = PropositoLote.entries.map { it.displayName }
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, propositos)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        b.spinnerProposito.adapter = spinnerAdapter

        b.btnGuardarLote.setOnClickListener {
            val galpon = b.etGalponId.text.toString()
            val cant = b.etCantidadInicial.text.toString().toIntOrNull() ?: 0
            
            if (galpon.isEmpty() || cant <= 0) {
                Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val nuevo = Lote(
                id = UUID.randomUUID().toString(),
                galponId = galpon,
                lineaGenetica = b.etLineaGenetica.text.toString(),
                proposito = PropositoLote.entries[b.spinnerProposito.selectedItemPosition].name,
                cantidadInicial = cant,
                cantidadActual = cant,
                fechaIngreso = System.currentTimeMillis()
            )

            viewModel.crearLote(nuevo)
            sheet.dismiss()
        }
        sheet.show()
    }

    private fun abrirDialogRegistro(lote: Lote) {
        val sheet = BottomSheetDialog(this)
        val b = DialogRegistroAvesBinding.inflate(layoutInflater)
        sheet.setContentView(b.root)

        b.tvTituloLote.text = "Registro para Lote #${lote.id.takeLast(5).uppercase()}"

        b.btnGuardarRegistro.setOnClickListener {
            val mort = b.etMortalidad.text.toString().toIntOrNull() ?: 0
            val desc = b.etDescarte.text.toString().toIntOrNull() ?: 0
            val peso = b.etPeso.text.toString().toDoubleOrNull() ?: 0.0
            
            viewModel.registrarEvento(lote.id, mort, desc, peso, b.etObservaciones.text.toString())
            sheet.dismiss()
            Toast.makeText(this, "Registro guardado", Toast.LENGTH_SHORT).show()
        }
        sheet.show()
    }
}
