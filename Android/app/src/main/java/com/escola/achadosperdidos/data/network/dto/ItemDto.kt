package com.escola.achadosperdidos.data.network.dto

/**
 * Espelha [AchadosPerdidos.Api.DTOs.ItemDto] (C#).
 *
 * **Atenção:** o backend serializa o enum `StatusItem` como **inteiro** (System.Text.Json
 * default, sem `JsonStringEnumConverter`). Use [StatusServidor] para converter.
 */
data class ItemDto(
    val id: Int,
    val descricao: String,
    val localEncontrado: String?,
    val categoriaId: Int,
    val categoriaNome: String,
    val status: Int,             // 0 = Encontrado, 1 = Devolvido, 2 = Expirado
    val dataCadastro: String,    // ISO-8601
    val dataDevolucao: String?,
    val urlFoto: String?,        // URL pública servida pelo servidor (ex: /fotos/abc.jpg)
    val tabletId: String?,
    val idLocalTablet: String?
)

/**
 * Mapeamento int (servidor) ↔ enum (Kotlin).
 * Mantido em um único lugar para evitar números mágicos espalhados.
 */
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
        s.ordinal   // Encontrado=0, Devolvido=1, Expirado=2 — alinhado ao enum C#
}

/** Payload para PATCH /api/itens/{id}/status — envia o enum como inteiro. */
data class AtualizarStatusItemDto(
    val status: Int   // use [StatusServidor.paraOrdinal]
)
