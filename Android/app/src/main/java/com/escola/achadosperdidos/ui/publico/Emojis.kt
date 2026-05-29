package com.escola.achadosperdidos.ui.publico

import com.escola.achadosperdidos.data.model.Categoria

fun emojiParaCategoria(nome: String): String = when (nome.lowercase().trim()) {
    "material didático", "material didatico", "material" -> "📘"
    "lancheiras e garrafas", "lancheiras", "garrafas"     -> "🎒"
    "casacos", "agasalhos"                                -> "🧥"
    "brinquedos"                                          -> "🧸"
    "xuxinhas", "acessórios", "acessorios"                -> "🎀"
    "chaves"                                              -> "🔑"
    "óculos", "oculos"                                    -> "👓"
    "celulares", "eletrônicos", "eletronicos"             -> "📱"
    "outros"                                              -> "📦"
    else                                                  -> "📦"
}

fun emojiDe(categoria: Categoria): String =
    categoria.emoji?.takeIf { it.isNotBlank() } ?: emojiParaCategoria(categoria.nome)

val EMOJIS_SUGERIDOS: List<String> = listOf(
    "📘", "📚", "🎒", "🥤", "🧥", "🧦", "👟", "🧸", "🎨", "🎀",
    "🔑", "👓", "📱", "⌚", "💼", "🧢", "🧤", "🪀", "✏️", "📦"
)
