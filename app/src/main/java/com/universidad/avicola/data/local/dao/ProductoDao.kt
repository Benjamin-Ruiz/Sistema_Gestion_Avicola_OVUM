package com.universidad.avicola.data.local.dao

import androidx.room.*
import com.universidad.avicola.data.local.entities.ProductoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductoDao {
    @Query("SELECT * FROM productos ORDER BY nombre ASC")
    fun getAllFlow(): Flow<List<ProductoEntity>>

    @Query("SELECT * FROM productos WHERE id = :id")
    suspend fun getById(id: String): ProductoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(producto: ProductoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(productos: List<ProductoEntity>)

    @Update
    suspend fun update(producto: ProductoEntity)

    @Query("DELETE FROM productos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM productos WHERE isSynced = 0")
    suspend fun getUnsynced(): List<ProductoEntity>

    @Query("UPDATE productos SET isSynced = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    // ═══════════════════════════════════════════════════════════════════════
    //  NUEVO: Reconciliación con Firestore (borrado de obsoletos)
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Devuelve todos los IDs locales. Útil para comparar contra el remoto.
     */
    @Query("SELECT id FROM productos")
    suspend fun getAllIds(): List<String>

    /**
     * Borra productos cuyos IDs NO estén en la lista de remotos,
     * pero solo si están sincronizados (isSynced = 1).
     * Esto preserva los cambios locales que aún no se han subido.
     */
    @Query("DELETE FROM productos WHERE id NOT IN (:idsRemotos) AND isSynced = 1")
    suspend fun deleteSincronizadosNoEn(idsRemotos: List<String>)

    /**
     * Borra todos los productos sincronizados. Se usa cuando Firestore
     * está completamente vacío (caso límite para evitar query con IN ()).
     */
    @Query("DELETE FROM productos WHERE isSynced = 1")
    suspend fun deleteTodosSincronizados()

    /**
     * Reemplaza el estado local con el estado remoto de forma atómica.
     *  - Inserta/actualiza todos los productos remotos.
     *  - Elimina los locales sincronizados que ya no existen en remoto.
     *  - Preserva los productos no sincronizados (cambios locales pendientes).
     */
    @Transaction
    suspend fun reconciliarConRemoto(productosRemotos: List<ProductoEntity>) {
        if (productosRemotos.isEmpty()) {
            // Firestore vacío: limpiar todo lo sincronizado
            deleteTodosSincronizados()
        } else {
            val idsRemotos = productosRemotos.map { it.id }
            deleteSincronizadosNoEn(idsRemotos)
            insertAll(productosRemotos)
        }
    }
}