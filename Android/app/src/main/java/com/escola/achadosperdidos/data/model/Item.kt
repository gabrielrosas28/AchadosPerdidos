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

    // Caminho ABSOLUTO da foto no armazenamento interno do tablet.
    // Apagado pelo WorkManager quando o item for Devolvido ou após 180 dias.
    val caminhoFoto: String? = null,

    // Nome do arquivo da foto no servidor após upload (devolvido pela API).
    val nomeArquivoFotoServidor: String? = null,

    // UUID estável para idempotência na sincronização.
    val idLocalTablet: String = UUID.randomUUID().toString(),

    // Preenchido após o item ser enviado com sucesso ao servidor.
    val idServidor: Int? = null,

    val sincronizado: Boolean = false,

    // Identificador único do tablet (Settings.Secure.ANDROID_ID ou similar).
    val tabletId: String
)
