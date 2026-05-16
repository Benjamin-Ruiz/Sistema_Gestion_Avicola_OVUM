package com.universidad.avicola.ui.dashboard.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.universidad.avicola.R
import com.universidad.avicola.ui.costos.CostosActivity

/**
 * EstimacionFragment.kt
 * Ubicación: app/src/main/java/com/universidad/avicola/ui/dashboard/fragments/
 *
 * CORRECCIÓN: El fragment estaba vacío — inflaba fragment_placeholder
 * pero no tenía ninguna acción. Ahora lanza CostosActivity directamente.
 *
 * Patrón usado por los demás módulos del dashboard (Inventario, Finanzas, Aves)
 * que abren su Activity propia desde el fragment correspondiente.
 */
class EstimacionFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_placeholder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Lanzar CostosActivity y no volver al fragment
        startActivity(Intent(requireContext(), CostosActivity::class.java))
    }
}
