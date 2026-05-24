package com.escola.achadosperdidos.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.escola.achadosperdidos.data.model.Item
import com.escola.achadosperdidos.data.model.StatusItem
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Projeção usada nas telas de listagem — junta o item com o nome da categoria
 * para evitar N+1 ao renderizar a grade do Modo Público.
 */
data class ItemComCategoria(
    @Embedded val item: Item,
    val categoriaNome: String
)

@Dao
interface ItemDao {

    @Query("""
        SELECT i.*, c.nome AS categoriaNome
        FROM itens i
        INNER JOIN categorias c ON c.id = i.categoriaId
        ORDER BY i.dataCadastro DESC
    """)
    fun observarTodos(): Flow<List<ItemComCategoria>>

    @Query("""
        SELECT i.*, c.nome AS categoriaNome
        FROM itens i
        INNER JOIN categorias c ON c.id = i.categoriaId
        WHERE i.categoriaId = :categoriaId
        ORDER BY i.dataCadastro DESC
    """)
    fun observarPorCategoria(categoriaId: Long): Flow<List<ItemComCategoria>>

    @Query("""
        SELECT i.*, c.nome AS categoriaNome
        FROM itens i
        INNER JOIN categorias c ON c.id = i.categoriaId
        WHERE i.status = :status
        ORDER BY i.dataCadastro DESC
    """)
    fun observarPorStatus(status: StatusItem): Flow<List<ItemComCategoria>>

    @Query("SELECT * FROM itens WHERE id = :id")
    suspend fun obterPorId(id: Long): Item?

    @Query("SELECT * FROM itens WHERE sincronizado = 0")
    suspend fun obterNaoSincronizados(): List<Item>

    /**
     * Critério de limpeza de fotos (consumido pelo WorkManager no Passo 3):
     *  - Item devolvido (status = 'Devolvido'); OU
     *  - Item cadastrado há mais de 180 dias (dataCadastro <= limiteAntigo).
     * Só retorna quando ainda existe foto local para apagar.
     */
    @Query("""
        SELECT * FROM itens
        WHERE caminhoFoto IS NOT NULL
          AND (status = 'Devolvido' OR dataCadastro <= :limiteAntigo)
    """)
    suspend fun obterParaLimpezaFoto(limiteAntigo: Date): List<Item>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun inserir(i: Item): Long

    @Update
    suspend fun atualizar(i: Item)

    @Query("UPDATE itens SET status = :status, dataDevolucao = :data WHERE id = :id")
    suspend fun atualizarStatus(id: Long, status: StatusItem, data: Date?)

    @Query("UPDATE itens SET caminhoFoto = NULL WHERE id = :id")
    suspend fun limparCaminhoFoto(id: Long)

    /**
     * Marca como sincronizado.
     * [idServidor] pode ser null porque o endpoint de batch não retorna IDs individuais;
     * uma futura chamada a [observarTodos]/sync delta preencherá quando disponível.
     */
    @Query("""
        UPDATE itens
           SET sincronizado = 1,
               idServidor = :idServidor,
               nomeArquivoFotoServidor = :nomeArquivoFotoServidor
         WHERE id = :id
    """)
    suspend fun marcarSincronizado(id: Long, idServidor: Int?, nomeArquivoFotoServidor: String?)

    @Delete
    suspend fun remover(i: Item)

    @Query("SELECT COUNT(*) FROM itens")
    suspend fun contar(): Int
}
