package com.escola.achadosperdidos.data.network.dto

data class ItemDto(
    val id: Int,
    val descricao: String,
    val localEncontrado: String?,
    val categoriaId: Int,
    val categoriaNome: String,
    val status: Int,
    val dataCadastro: String,
    val dataDevolucao: String?,
    val urlFoto: String?,
    val tabletId: String?,
    val idLocalTablet: String?
)

object StatusServidor {
    const val ENCONTRADO = 0
    const val DEVOLVIDO  = 1
    const val EXPIRADO   = 2

    fun deOrdinal(v: Int): com.escola.achadosperdidos.data.model.StatusItem = when (v) {
        DEVOLVIDO -> com.escola.achadosperdidos.data.model.StatusItem.Devolvido
        EXPIRADO  -> com.escola.achadosperdidos.data.model.StatusItem.Expirado
        else      -> com.escola.achadosperdidos.data.model.StatusItem.Encontrado
    }

    fun paraOrdinal(s: com.escola.achadosperdidos.data.model.StatusItem): Int =
        s.ordinal
}

data class AtualizarStatusItemDto(
    val status: Int
)
