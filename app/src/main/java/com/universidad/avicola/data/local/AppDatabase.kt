package com.universidad.avicola.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.universidad.avicola.data.local.dao.LoteDao
import com.universidad.avicola.data.local.dao.ProductoDao
import com.universidad.avicola.data.local.entities.LoteEntity
import com.universidad.avicola.data.local.entities.ProductoEntity
import com.universidad.avicola.data.local.entities.RegistroDiarioEntity
import com.universidad.avicola.data.local.entities.EstimacionCostosEntity
import com.universidad.avicola.data.local.dao.EstimacionCostosDao
import com.universidad.avicola.data.local.entities.RegistroMedicoEntity
import com.universidad.avicola.data.local.entities.VacunacionEntity
import com.universidad.avicola.data.local.dao.RegistroMedicoDao
import com.universidad.avicola.data.local.dao.VacunacionDao





@Database(entities = [ProductoEntity::class, LoteEntity::class, RegistroDiarioEntity::class, EstimacionCostosEntity::class, RegistroMedicoEntity::class, VacunacionEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun loteDao(): LoteDao
    abstract fun estimacionCostosDao(): EstimacionCostosDao
    abstract fun registroMedicoDao(): RegistroMedicoDao
    abstract fun vacunacionDao(): VacunacionDao




    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "avicola_database",
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
