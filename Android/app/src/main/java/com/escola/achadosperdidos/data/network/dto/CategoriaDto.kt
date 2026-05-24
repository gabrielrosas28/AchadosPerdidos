package com.escola.achadosperdidos.data.network.dto

/**
 * Espelha [AchadosPerdidos.Api.DTOs.CategoriaDto] (C#).
 * Gson desserializa os campos com o mesmo nome (camelCase no JSON do .NET por padrão).
 */
data class CategoriaDto(
    val id: Int,
    val nome: String,
    val ativa: Boolean,
    val dataCriacao: String      // ISO-8601 — ex: "2026-05-23T10:00:00"
)

/** Payload enviado ao criar uma nova categoria no servidor. */
data class CriarCategoriaDto(
    val nome: String,
    val idLocalTablet: String?   // UUID do tablet; permite idempotência no servidor
)
