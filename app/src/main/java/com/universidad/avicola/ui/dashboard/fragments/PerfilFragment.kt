package com.universidad.avicola.ui.dashboard.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.universidad.avicola.R
import com.universidad.avicola.databinding.FragmentPerfilBinding

class PerfilFragment : Fragment() {

    private var _binding: FragmentPerfilBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPerfilBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cargarDatosUsuario()
        configurarBotones()
    }

    private fun cargarDatosUsuario() {
        val user = Firebase.auth.currentUser
        binding.tvCorreoPerfilHeader.text = user?.email ?: "correo@ejemplo.com"
        binding.etNombreCompleto.setText(user?.displayName ?: "Usuario Avícola")
        binding.etRolSistema.setText("Administrador")
        
        // Datos de ubicación y enfoque
        binding.etUbicacion.setText("Sector Central, Zona 4")
        binding.etEnfoqueProduccion.setText("Gallinas ponedoras")
    }

    private fun configurarBotones() {
        binding.btnActualizarPerfil.setOnClickListener {
            Toast.makeText(requireContext(), "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
        }
        
        binding.btnEditarFoto.setOnClickListener {
            Toast.makeText(requireContext(), "Cargar nueva fotografía...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}