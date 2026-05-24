package com.escola.achadosperdidos.data.network

import com.escola.achadosperdidos.data.network.dto.*
import okhttp3.MultipartBody
import retrofit2.http.*

/**
 * Interface Retrofit que mapeia os endpoints da API REST do servidor Windows.
 *
 * Todos os métodos são `suspend` — devem ser chamados de uma coroutine (ex: ViewModel ou Worker).
 */
interface ApiService {

    // ── Sincronização offline-first ──────────────────────────────────────────

    /**
     * Envia em lote todos os itens pendentes de sincronização do tablet.
     * Rota: POST /api/sync/itens
     */
    @POST("api/sync/itens")
    suspend fun sincronizarItens(@Body lote: List<SyncItemDto>): SyncResponseDto

    /**
     * Baixa categorias criadas/alteradas no servidor após [desde] (delta sync).
     * Parâmetro opcional; sem ele retorna todas.
     * Rota: GET /api/sync/categorias?desde=<ISO-8601>
     */
    @GET("api/sync/categorias")
    suspend fun baixarCategorias(@Query("desde") desde: String? = null): List<CategoriaDto>

    // ── Categorias ───────────────────────────────────────────────────────────

    @GET("api/categorias")
    suspend fun listarCategorias(): List<CategoriaDto>

    @POST("api/categorias")
    suspend fun criarCategoria(@Body dto: CriarCategoriaDto): CategoriaDto

    // ── Itens ────────────────────────────────────────────────────────────────

    @GET("api/itens")
    suspend fun listarItens(): List<ItemDto>

    @POST("api/itens")
    suspend fun criarItem(@Body dto: CriarItemDto): ItemDto

    /**
     * Atualiza o status de um item (ex: marcar como Devolvido).
     * Rota: PUT /api/itens/{id}/status
     */
    @PUT("api/itens/{id}/status")
    suspend fun atualizarStatus(
        @Path("id") id: Int,
        @Body dto: AtualizarStatusItemDto
    ): ItemDto

    /**
     * Faz o upload da foto de um item como multipart.
     * Rota: POST /api/itens/{id}/foto
     * Part name esperado pelo servidor: "foto"
     */
    @Multipart
    @POST("api/itens/{id}/foto")
    suspend fun uploadFoto(
        @Path("id") id: Int,
        @Part foto: MultipartBody.Part
    ): ItemDto
}
