package com.escola.achadosperdidos.data.network.dto

data class CategoriaDto(
    val id: Int,
    val nome: String,
    val ativa: Boolean,
    val dataCriacao: String
)

data class CriarCategoriaDto(
    val nome: String,
    val idLocalTablet: String?
)
