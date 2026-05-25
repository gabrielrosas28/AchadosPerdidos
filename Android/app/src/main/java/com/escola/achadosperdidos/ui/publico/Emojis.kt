package com.escola.achadosperdidos.ui.publico

import com.escola.achadosperdidos.data.model.Categoria

/**
 * Mapa heurístico de **nome da categoria → emoji**.
 *
 * Usado como fallback quando o gestor ainda não escolheu um emoji manualmente
 * para uma categoria. As categorias do banco são dinâmicas — o nome é a chave,
 * normalizada para minúsculas. Para nomes desconhecidos devolve "📦".
 */
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

/**
 * Resolve o emoji de uma categoria respeitando a escolha do gestor:
 * usa [Categoria.emoji] se foi setado, senão cai no [emojiParaCategoria]
 * heurístico pelo nome.
 */
fun emojiDe(categoria: Categoria): String =
    categoria.emoji?.takeIf { it.isNotBlank() } ?: emojiParaCategoria(categoria.nome)

/**
 * Sugestões de emojis exibidas no seletor da tela "Editar Categoria".
 * Cobrem os casos típicos de itens encontrados em escola.
 */
val EMOJIS_SUGERIDOS: List<String> = listOf(
    "📘", "📚", "🎒", "🥤", "🧥", "🧦", "👟", "🧸", "🎨", "🎀",
    "🔑", "👓", "📱", "⌚", "💼", "🧢", "🧤", "🪀", "✏️", "📦"
)
