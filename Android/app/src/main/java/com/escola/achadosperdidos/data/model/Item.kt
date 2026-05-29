package com.escola.achadosperdidos.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "itens",
    foreignKeys = [
        ForeignKey(
            entity = Categoria::class,
            parentColumns = ["id"],
            childColumns = ["categoriaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoriaId"]),
        Index(value = ["status"]),
        Index(value = ["dataCadastro"]),
        Index(value = ["idLocalTablet"], unique = true)
    ]
)
data class Item(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val descricao: String,

    val localEncontrado: String? = null,

    val categoriaId: Long,

    val status: StatusItem = StatusItem.Encontrado,

    val dataCadastro: Date = Date(),

    val dataDevolucao: Date? = null,

    val caminhoFoto: String? = null,

    val nomeArquivoFotoServidor: String? = null,

    val idLocalTablet: String = UUID.randomUUID().toString(),

    val idServidor: Int? = null,

    val sincronizado: Boolean = false,

    val tabletId: String
)
