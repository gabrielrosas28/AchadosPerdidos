package com.escola.achadosperdidos.data.network.dto

data class SyncItemDto(
    val descricao: String,
    val localEncontrado: String?,
    val categoriaServidorId: Int?,
    val categoriaIdLocalTablet: String?,
    val status: Int,
    val dataCadastro: String,
    val dataDevolucao: String?,
    val tabletId: String,
    val idLocalTablet: String,
    val fotoBase64: String?,
    val nomeArquivoFoto: String?
)

data class SyncResponseDto(
    val recebidos: Int,
    val criados: Int,
    val atualizados: Int,
    val erros: List<String>
)
