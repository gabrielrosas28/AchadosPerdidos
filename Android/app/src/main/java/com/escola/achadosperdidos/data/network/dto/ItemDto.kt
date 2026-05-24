package com.escola.achadosperdidos.data.network.dto

/**
 * Espelha [AchadosPerdidos.Api.DTOs.ItemDto] (C#).
 */
data class ItemDto(
    val id: Int,
    val descricao: String,
    val localEncontrado: String?,
    val categoriaId: Int,
    val categoriaNome: String,
    val status: String,          // "Encontrado" | "Devolvido" | "Expirado"
    val dataCadastro: String,    // ISO-8601
    val dataDevolucao: String?,
    val urlFoto: String?,        // URL pública servida pelo servidor (ex: /fotos/abc.jpg)
    val tabletId: String?,
    val idLocalTablet: String?
)

/** Payload para criar item (sem foto — a foto é enviada num segundo request multipart). */
data class CriarItemDto(
    val descricao: String,
    val localEncontrado: String?,
    val categoriaId: Int,
    val tabletId: String?,
    val idLocalTablet: String?
)

/** Payload para atualizar apenas o status de um item. */
data class AtualizarStatusItemDto(
    val status: String           // "Encontrado" | "Devolvido" | "Expirado"
)
