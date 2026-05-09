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

@Database(entities = [ProductoEntity::class, LoteEntity::class, RegistroDiarioEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productoDao(): ProductoDao
    abstract fun loteDao(): LoteDao

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
