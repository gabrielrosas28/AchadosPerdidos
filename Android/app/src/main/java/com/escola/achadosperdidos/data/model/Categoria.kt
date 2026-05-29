package com.escola.achadosperdidos.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "categorias",
    indices = [
        Index(value = ["nome"], unique = true),
        Index(value = ["idLocalTablet"])
    ]
)
data class Categoria(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val nome: String,

    val emoji: String? = null,

    val ativa: Boolean = true,

    val dataCriacao: Date = Date(),

    val idLocalTablet: String = UUID.randomUUID().toString(),

    val idServidor: Int? = null
)
