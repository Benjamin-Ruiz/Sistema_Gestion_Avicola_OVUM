package com.universidad.avicola.ui.dashboard.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.universidad.avicola.R
import com.universidad.avicola.ui.aves.GestionAvesActivity
import com.universidad.avicola.ui.dashboard.DashboardActivity

class OperacionesFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_operaciones, container, false)
        
        view.findViewById<View>(R.id.cardInventario).setOnClickListener {
            (activity as? DashboardActivity)?.replaceFragment(InventarioFragment(), "INVENTARIO", true)
        }

        view.findViewById<View>(R.id.cardGestionAves).setOnClickListener {
            startActivity(Intent(requireContext(), GestionAvesActivity::class.java))
        }
        
        return view
    }
}