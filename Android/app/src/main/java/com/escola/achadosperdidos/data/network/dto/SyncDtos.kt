package com.escola.achadosperdidos.data.network.dto

/**
 * Item enviado em lote para POST /api/sync/itens.
 * Espelha [AchadosPerdidos.Api.DTOs.SyncItemDto] (C#).
 *
 * Status segue o ordinal do enum no servidor (0=Encontrado, 1=Devolvido, 2=Expirado).
 * Veja [StatusServidor] em ItemDto.kt.
 */
data class SyncItemDto(
    val descricao: String,
    val localEncontrado: String?,
    /** ID da categoria no servidor (se já sincronizada). */
    val categoriaServidorId: Int?,
    /** UUID da categoria no tablet (fallback quando idServidor ainda é null). */
    val categoriaIdLocalTablet: String?,
    val status: Int,             // ordinal do enum
    val dataCadastro: String,    // ISO-8601 UTC
    val dataDevolucao: String?,
    val tabletId: String,
    val idLocalTablet: String,
    /** Foto codificada em Base64 (enviada junto com o item no primeiro sync). */
    val fotoBase64: String?,
    val nomeArquivoFoto: String?
)

/** Resposta do servidor para POST /api/sync/itens. */
data class SyncResponseDto(
    val recebidos: Int,
    val criados: Int,
    val atualizados: Int,
    val erros: List<String>
)
