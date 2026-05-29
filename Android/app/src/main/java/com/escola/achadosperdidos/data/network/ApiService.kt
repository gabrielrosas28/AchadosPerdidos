package com.escola.achadosperdidos.data.network

import com.escola.achadosperdidos.data.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface Retrofit que mapeia os endpoints da API REST do servidor Windows.
 *
 * Todos os métodos são `suspend` — devem ser chamados de uma coroutine
 * (ex: ViewModel ou Worker).
 */
interface ApiService {

    // ── Sincronização offline-first (uso primário do tablet) ──────────────────

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

    /**
     * Baixa um arquivo (foto de item) a partir de uma URL **absoluta** — a
     * `urlFoto` que o servidor devolve em [dto.ItemDto] (ex:
     * `http://192.168.15.61:5080/fotos/abc.jpg`). `@Url` faz o Retrofit usar a
     * URL inteira, ignorando a baseUrl. `@Streaming` evita carregar tudo em
     * memória de uma vez.
     */
    @Streaming
    @GET
    suspend fun baixarArquivo(@Url url: String): ResponseBody

    // ── Categorias ────────────────────────────────────────────────────────────

    @GET("api/categorias")
    suspend fun listarCategorias(@Query("somenteAtivas") somenteAtivas: Boolean = true): List<CategoriaDto>

    @POST("api/categorias")
    suspend fun criarCategoria(@Body dto: CriarCategoriaDto): CategoriaDto

    // ── Itens ─────────────────────────────────────────────────────────────────

    @GET("api/itens")
    suspend fun listarItens(
        @Query("categoriaId") categoriaId: Int? = null,
        @Query("status") status: Int? = null
    ): List<ItemDto>

    /**
     * Cria um item enviando seus campos + foto opcional em **multipart/form-data**.
     * Backend: `ItensController.Criar` com `[FromForm] CriarItemDto` + `IFormFile? foto`.
     *
     * Use [Multiparts] para construir os RequestBody's de forma legível.
     */
    @Multipart
    @POST("api/itens")
    suspend fun criarItem(
        @Part("Descricao")        descricao: RequestBody,
        @Part("LocalEncontrado")  localEncontrado: RequestBody?,
        @Part("CategoriaId")      categoriaId: RequestBody,
        @Part("TabletId")         tabletId: RequestBody?,
        @Part("IdLocalTablet")    idLocalTablet: RequestBody?,
        @Part                     foto: MultipartBody.Part?
    ): ItemDto

    /**
     * Atualiza apenas o status do item (ex: marcar como Devolvido).
     * Backend: `[HttpPatch("{id:int}/status")]` → retorna **204 No Content**.
     */
    @PATCH("api/itens/{id}/status")
    suspend fun atualizarStatus(
        @Path("id") id: Int,
        @Body dto: AtualizarStatusItemDto
    ): Response<Unit>

    @DELETE("api/itens/{id}")
    suspend fun removerItem(@Path("id") id: Int): Response<Unit>
}
