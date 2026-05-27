package com.escola.achadosperdidos.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.escola.achadosperdidos.data.model.Categoria
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoriaDao {

    @Query("SELECT * FROM categorias WHERE ativa = 1 ORDER BY nome")
    fun observarAtivas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias ORDER BY nome")
    fun observarTodas(): Flow<List<Categoria>>

    @Query("SELECT * FROM categorias WHERE id = :id")
    suspend fun obterPorId(id: Long): Categoria?

    /** Busca pelo UUID estável (idempotência local). */
    @Query("SELECT * FROM categorias WHERE idLocalTablet = :uuid LIMIT 1")
    suspend fun obterPorUuid(uuid: String): Categoria?

    /** Busca pelo ID atribuído pelo servidor (idempotência ao baixar/sincronizar). */
    @Query("SELECT * FROM categorias WHERE idServidor = :idServidor LIMIT 1")
    suspend fun obterPorIdServidor(idServidor: Int): Categoria?

    @Query("SELECT * FROM categorias WHERE idServidor IS NULL")
    suspend fun obterNaoSincronizadas(): List<Categoria>

    /** Snapshot único de todas as categorias — usado pelo export de backup. */
    @Query("SELECT * FROM categorias ORDER BY nome")
    suspend fun obterTodas(): List<Categoria>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(c: Categoria): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun inserirSeNaoExiste(c: Categoria): Long

    @Update
    suspend fun atualizar(c: Categoria)

    @Query("UPDATE categorias SET ativa = 0 WHERE id = :id")
    suspend fun desativar(id: Long)

    @Query("UPDATE categorias SET ativa = 1 WHERE id = :id")
    suspend fun reativar(id: Long)

    @Query("UPDATE categorias SET nome = :nome, emoji = :emoji WHERE id = :id")
    suspend fun editar(id: Long, nome: String, emoji: String?)

    @Query("UPDATE categorias SET idServidor = :idServidor WHERE id = :id")
    suspend fun marcarSincronizada(id: Long, idServidor: Int)

    @Query("SELECT COUNT(*) FROM categorias")
    suspend fun contar(): Int
}
