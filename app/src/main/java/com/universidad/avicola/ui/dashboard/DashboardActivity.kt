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

import com.universidad.avicola.util.TutorialManager
import com.universidad.avicola.databinding.LayoutTutorialOverlayBinding
import android.widget.TextView
import android.widget.Button
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.ViewGroup
import android.graphics.RectF
import android.widget.RelativeLayout

class DashboardActivity : AppCompatActivity() {

    lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var tutorialManager: TutorialManager
    private var stepActual = 0
    private var navInicializado = false
    private var currentNavId = -1

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
        tutorialManager = TutorialManager(this)

        user.reload().addOnCompleteListener { reloadTask ->
            if (reloadTask.isSuccessful) {
                if (user.isEmailVerified) {
                    configurarNavegacion()
                    configurarToolbar()

                    if (tutorialManager.deberiaMostrarTutorial(user.uid)) {
                        iniciarTutorial()
                    }
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
                    // CORRECCIÓN DEFINITIVA DEL CRASH:
                    // 1. Limpiar todos los fragmentos y la pila de retroceso inmediatamente.
                    // Esto detiene los observadores de LiveData y listeners de Firestore.
                    supportFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    val currentFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
                    if (currentFragment != null) {
                        supportFragmentManager.beginTransaction().remove(currentFragment).commitNow()
                    }

                    // 2. Cerrar sesión tras asegurar que no hay procesos de UI activos.
                    auth.signOut()
                    irAlLogin()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun configurarNavegacion() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            if (!navInicializado) return@setOnItemSelectedListener true
            if (item.itemId == currentNavId) return@setOnItemSelectedListener true

            val animation = if (getNavIndex(item.itemId) > getNavIndex(currentNavId)) {
                intArrayOf(R.anim.slide_in_right, R.anim.slide_out_left)
            } else {
                intArrayOf(R.anim.slide_in_left, R.anim.slide_out_right)
            }

            currentNavId = item.itemId

            when (item.itemId) {
                R.id.nav_operaciones -> {
                    replaceFragment(OperacionesFragment(), "OPERACIONES", anims = animation)
                    true
                }
                R.id.nav_estimacion -> {
                    replaceFragment(EstimacionFragment(), "ESTIMACIÓN", anims = animation)
                    true
                }
                R.id.nav_finanzas -> {
                    replaceFragment(FinanzasFragment(), "FINANZAS", anims = animation)
                    true
                }
                R.id.nav_salud -> {
                    replaceFragment(SaludFragment(), "SALUD", anims = animation)
                    true
                }
                R.id.nav_perfil -> {
                    replaceFragment(PerfilFragment(), "PERFIL", anims = animation)
                    true
                }
                else -> false
            }
        }

        currentNavId = R.id.nav_finanzas
        binding.bottomNavigation.selectedItemId = R.id.nav_finanzas
        navInicializado = true
        replaceFragment(FinanzasFragment(), "FINANZAS")
    }

    private fun getNavIndex(id: Int): Int {
        return when (id) {
            R.id.nav_operaciones -> 0
            R.id.nav_estimacion -> 1
            R.id.nav_finanzas -> 2
            R.id.nav_salud -> 3
            R.id.nav_perfil -> 4
            else -> 0
        }
    }

    fun replaceFragment(fragment: Fragment, subTitle: String, showReportes: Boolean = false, anims: IntArray? = null) {
        val transaction = supportFragmentManager.beginTransaction()
        
        anims?.let {
            transaction.setCustomAnimations(it[0], it[1])
        }
        
        transaction.replace(R.id.nav_host_fragment, fragment)
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

    // ══════════════════════════════════════════════════════════════════
    //  LÓGICA DEL TUTORIAL
    // ══════════════════════════════════════════════════════════════════

    private fun iniciarTutorial() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val tutorialBinding = LayoutTutorialOverlayBinding.inflate(layoutInflater, root, true)
        
        tutorialBinding.btnTutorialSkip.setOnClickListener {
            finalizarTutorial(root, tutorialBinding.root)
        }

        tutorialBinding.btnTutorialNext.setOnClickListener {
            pasoTutorialSiguiente(tutorialBinding)
        }

        root.postDelayed({
            pasoTutorialSiguiente(tutorialBinding)
        }, 500)
    }

    private fun pasoTutorialSiguiente(b: LayoutTutorialOverlayBinding) {
        stepActual++
        val overlay = b.tutorialOverlay
        val title = b.tvTutorialTitle
        val desc = b.tvTutorialDesc
        val btnNext = b.btnTutorialNext
        val arrow = b.imgTutorialArrow
        val textLayout = b.layoutTextInfo

        when (stepActual) {
            1 -> {
                title.text = "¡Bienvenido a OVUM!"
                desc.text = "Gestiona tu granja avícola de forma profesional y eficiente."
                overlay.setSpotlight(RectF())
                arrow.visibility = View.INVISIBLE
                
                // Centrado absoluto en el primer paso
                val textParams = textLayout.layoutParams as RelativeLayout.LayoutParams
                textParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                textParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
                textParams.addRule(RelativeLayout.CENTER_IN_PARENT)
                textParams.setMargins(0, 0, 0, 0)
                textLayout.layoutParams = textParams
            }
            2 -> {
                title.text = "Módulo de Finanzas"
                desc.text = "Controla tus ingresos, gastos y el retorno de inversión (ROI) en tiempo real."
                resaltarConFlecha(R.id.nav_finanzas, b)
            }
            3 -> {
                title.text = "Operaciones de Campo"
                desc.text = "Accede rápidamente a tu Inventario y a la Gestión de Aves."
                resaltarConFlecha(R.id.nav_operaciones, b)
            }
            4 -> {
                title.text = "Cálculos de Costos"
                desc.text = "Proyecta la rentabilidad de tus lotes antes de comenzar la producción."
                resaltarConFlecha(R.id.nav_estimacion, b)
            }
            5 -> {
                title.text = "Control Sanitario"
                desc.text = "Lleva el registro de vacunas y utiliza el diagnóstico asistido por síntomas."
                resaltarConFlecha(R.id.nav_salud, b)
            }
            6 -> {
                title.text = "Cerrar Sesión"
                desc.text = "Toca aquí para salir de tu cuenta de forma segura."
                resaltarConFlecha(R.id.btnToolbarLogout, b)
                btnNext.text = "Finalizar"
            }
            else -> {
                finalizarTutorial(findViewById(android.R.id.content), b.root)
            }
        }
    }

    private fun resaltarConFlecha(viewId: Int, b: LayoutTutorialOverlayBinding) {
        // Encontrar la vista específica (el icono+texto dentro de la barra o el botón de salir)
        val targetView = if (viewId == R.id.btnToolbarLogout) findViewById<View>(viewId) 
                         else binding.bottomNavigation.findViewById<View>(viewId)
        
        if (targetView == null) return
        
        val overlay = b.tutorialOverlay
        val arrow = b.imgTutorialArrow
        val textLayout = b.layoutTextInfo

        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        
        val x = location[0].toFloat()
        val y = location[1].toFloat()
        val w = targetView.width.toFloat()
        val h = targetView.height.toFloat()

        val isBottomNav = viewId != R.id.btnToolbarLogout
        val density = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels

        // Foco milimétrico con un ligero margen de 6dp
        val padding = 6 * density
        val rect = RectF(x - padding, y - padding, x + w + padding, y + h + padding)
        
        val arrowParams = arrow.layoutParams as RelativeLayout.LayoutParams
        val textParams = textLayout.layoutParams as RelativeLayout.LayoutParams
        
        // Limpiar reglas previas para evitar desvíos a las esquinas
        textParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        textParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)
        textParams.removeRule(RelativeLayout.CENTER_IN_PARENT)
        arrowParams.removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
        arrowParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP)

        if (isBottomNav) {
            // Caso: Barra Inferior
            // Posicionar texto sobre el botón
            textParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            textParams.setMargins((screenWidth/2 - (140 * density)).toInt(), 0, 0, (h + 90 * density).toInt())
            
            // Flecha apuntando abajo al centro del botón
            arrow.visibility = View.VISIBLE
            arrow.rotation = 180f
            arrowParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            val arrowMarginStart = (x + w/2 - 20 * density).toInt()
            arrowParams.setMargins(arrowMarginStart, 0, 0, (h + 10 * density).toInt())
        } else {
            // Caso: Botón Salir (Arriba)
            // Posicionar texto debajo del botón
            textParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            textParams.setMargins((screenWidth/2 - (140 * density)).toInt(), (y + h + 80 * density).toInt(), 0, 0)
            
            // Flecha apuntando arriba al centro del botón
            arrow.visibility = View.VISIBLE
            arrow.rotation = 0f
            arrowParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
            val arrowMarginStart = (x + w/2 - 20 * density).toInt()
            arrowParams.setMargins(arrowMarginStart, (y + h + 10 * density).toInt(), 0, 0)
        }
        
        textLayout.layoutParams = textParams
        arrow.layoutParams = arrowParams
        overlay.setSpotlight(rect)
    }

    private fun finalizarTutorial(root: ViewGroup, overlay: View) {
        root.removeView(overlay)
        auth.currentUser?.let {
            tutorialManager.marcarTutorialComoVisto(it.uid)
        }
    }
}