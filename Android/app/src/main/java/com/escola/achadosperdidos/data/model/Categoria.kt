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

    /**
     * Emoji escolhido pelo gestor. Quando `null`, a UI cai no helper
     * `emojiParaCategoria(nome)` que sugere um emoji pelo nome conhecido.
     */
    val emoji: String? = null,

    val ativa: Boolean = true,

    val dataCriacao: Date = Date(),

    // UUID estável usado na sincronização com o servidor (idempotência).
    val idLocalTablet: String = UUID.randomUUID().toString(),

    // Preenchido após o primeiro upload bem-sucedido.
    val idServidor: Int? = null
)
