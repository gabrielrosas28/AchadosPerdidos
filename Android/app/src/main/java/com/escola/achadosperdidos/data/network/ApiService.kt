package com.escola.achadosperdidos.data.network

import com.escola.achadosperdidos.data.network.dto.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/sync/itens")
    suspend fun sincronizarItens(@Body lote: List<SyncItemDto>): SyncResponseDto

    @GET("api/sync/categorias")
    suspend fun baixarCategorias(@Query("desde") desde: String? = null): List<CategoriaDto>

    @Streaming
    @GET
    suspend fun baixarArquivo(@Url url: String): ResponseBody

    @GET("api/categorias")
    suspend fun listarCategorias(@Query("somenteAtivas") somenteAtivas: Boolean = true): List<CategoriaDto>

    @POST("api/categorias")
    suspend fun criarCategoria(@Body dto: CriarCategoriaDto): CategoriaDto

    @GET("api/itens")
    suspend fun listarItens(
        @Query("categoriaId") categoriaId: Int? = null,
        @Query("status") status: Int? = null
    ): List<ItemDto>

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

    @PATCH("api/itens/{id}/status")
    suspend fun atualizarStatus(
        @Path("id") id: Int,
        @Body dto: AtualizarStatusItemDto
    ): Response<Unit>

    @DELETE("api/itens/{id}")
    suspend fun removerItem(@Path("id") id: Int): Response<Unit>
}
