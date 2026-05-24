package com.escola.achadosperdidos.ui.publico

/**
 * Mapa heurístico de **nome da categoria → emoji**.
 *
 * As categorias do banco são dinâmicas (gestor pode criar novas) — então não
 * mapeamos um ícone por ID. O nome é usado como chave, normalizado para
 * minúsculas e sem acentos parciais. Para categorias desconhecidas devolve
 * um emoji genérico de "caixa".
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
