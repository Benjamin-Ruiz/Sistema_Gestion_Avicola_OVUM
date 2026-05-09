# OVUM - Sistema de Gestión Avícola 🥚🐔

**OVUM** es una solución móvil integral diseñada para modernizar y optimizar la administración de empresas avícolas. El sistema permite un control preciso de la producción, inventarios y salud de las aves, facilitando la toma de decisiones basada en datos reales.

---

## 🚀 Características Principales

### 📦 Gestión de Inventario
- Control detallado de insumos, alimentos y medicamentos.
- Actualización en tiempo real de existencias.
- Clasificación por categorías de productos.

### 🐥 Control de Aves y Lotes
- Seguimiento completo del ciclo de vida de los lotes.
- Registro de mortalidad y descarte diario.
- Monitoreo de líneas genéticas y propósitos (carne/huevo).
- Historial detallado por galpón.

### 🔐 Seguridad y Acceso
- Autenticación robusta integrada con **Firebase Auth**.
- Registro de usuarios para personal administrativo y operativo.
- Protección de datos sensibles.

### 📊 Próximamente (Roadmap)
- 📈 Módulo de Alimentación y Nutrición.
- 🌡️ Control de Temperatura y Humedad.
- 💰 Módulo de Costos y Finanzas.
- 🩺 Registro Médico y Vacunación.

---

## 🛠️ Stack Tecnológico

El proyecto está construido bajo los estándares más modernos de desarrollo Android:

- **Lenguaje:** [Kotlin](https://kotlinlang.org/) - Moderno, seguro y expresivo.
- **Arquitectura:** MVVM (Model-View-ViewModel) para una separación clara de responsabilidades.
- **Base de Datos Local:** [Room](https://developer.android.com/training/data-storage/room) - Abstracción de SQLite para persistencia offline.
- **Backend/Auth:** [Firebase](https://firebase.google.com/) - Autenticación y sincronización en la nube.
- **UI:** Material Design 3, View Binding, y layouts complejos con ConstraintLayout.
- **Componentes de Ciclo de Vida:** LiveData, ViewModel y Coroutines para operaciones asíncronas.

---

## 📂 Estructura del Proyecto

```text
app/src/main/java/com/universidad/avicola/
├── data/           # Repositorios, DAOs, Entidades (Room) y modelos de dominio.
├── ui/             # Actividades, ViewModels y Adapters organizados por módulos.
│   ├── auth/       # Login y Registro.
│   ├── aves/       # Gestión de lotes y registros diarios.
│   ├── dashboard/  # Panel principal de navegación.
│   └── inventario/ # Control de stock y productos.
└── util/           # Clases de utilidad y extensiones de Kotlin.
```

---

## 🛠️ Instalación y Configuración

1. **Clonar el repositorio:**
   ```bash
   git clone https://github.com/Benjamin-Ruiz/Sistema_Gestion_Avicola_OVUM.git
   ```
2. **Abrir en Android Studio:**
   Importa el proyecto y deja que Gradle descargue las dependencias.
3. **Configurar Firebase:**
   - Crea un proyecto en la consola de Firebase.
   - Agrega el archivo `google-services.json` en el directorio `app/`.
4. **Ejecutar:**
   Conecta un dispositivo físico o emulador y presiona **Run**.

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas. Si deseas mejorar algún módulo o corregir un error:
1. Haz un **Fork** del proyecto.
2. Crea una rama para tu mejora (`git checkout -b feature/NuevaMejora`).
3. Haz un **Commit** de tus cambios.
4. Realiza un **Pull Request**.

---

## 📧 Contacto
**Benjamín Ruiz** - [Tu Perfil de GitHub](https://github.com/Benjamin-Ruiz)  
Proyecto desarrollado para la gestión eficiente del sector avícola.
