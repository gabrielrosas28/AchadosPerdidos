package com.escola.achadosperdidos.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.escola.achadosperdidos.AchadosPerdidosApp
import com.escola.achadosperdidos.data.local.CategoriaDao
import com.escola.achadosperdidos.data.local.ItemComCategoria
import com.escola.achadosperdidos.data.local.ItemDao
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.model.Item
import com.escola.achadosperdidos.data.model.StatusItem
import com.escola.achadosperdidos.data.network.ApiClient
import com.escola.achadosperdidos.data.network.SyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date

/**
 * ViewModel central da camada de dados consumida pela UI Compose.
 *
 * Expõe:
 *  - [categorias]: categorias ativas (Modo Público)
 *  - [todasCategorias]: incluindo desativadas (Modo Gestor)
 *  - [itens]: lista reativa filtrada por categoria + busca + status
 *  - filtros mutáveis: [categoriaSelecionada], [busca], [somenteDisponiveis]
 *  - ações: [adicionarItem], [marcarComoDevolvido], [adicionarCategoria], [desativarCategoria]
 *  - [sincronizarAgora]: dispara o [SyncRepository] manualmente
 *
 * No Compose (uso típico):
 * ```kotlin
 * @Composable
 * fun TelaPublica() {
 *     val vm: AchadosViewModel = viewModel()
 *     val categorias by vm.categorias.collectAsState()
 *     val itens      by vm.itens.collectAsState()
 *     val busca      by vm.busca.collectAsState()
 *
 *     Column {
 *         OutlinedTextField(value = busca, onValueChange = vm::setBusca)
 *         GradeCategorias(categorias, onClick = vm::selecionarCategoria)
 *         GradeItens(itens)
 *     }
 * }
 * ```
 *
 * Estende [AndroidViewModel] para acessar [Application] (e por consequência o
 * `AchadosPerdidosApp.tabletId` e o banco) sem precisar de Factory.
 */
class AchadosViewModel(application: Application) : AndroidViewModel(application) {

    private val app: AchadosPerdidosApp
        get() = getApplication<AchadosPerdidosApp>()

    private val categoriaDao: CategoriaDao = app.database.categoriaDao()
    private val itemDao: ItemDao = app.database.itemDao()

    // ── Filtros (mutáveis pela UI) ──────────────────────────────────────────

    private val _categoriaSelecionada = MutableStateFlow<Long?>(null)
    val categoriaSelecionada: StateFlow<Long?> = _categoriaSelecionada.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    /**
     * Filtro por status:
     *  - [StatusItem.Encontrado] (default) — Modo Público / tela "Entregar Item"
     *  - [StatusItem.Devolvido]            — tela "Histórico" do gestor
     *  - `null`                            — todos os status
     */
    private val _statusFiltro = MutableStateFlow<StatusItem?>(StatusItem.Encontrado)
    val statusFiltro: StateFlow<StatusItem?> = _statusFiltro.asStateFlow()

    // ── Categorias (reativo a partir do Room) ───────────────────────────────

    /** Apenas categorias ativas — usado nas grades do Modo Público. */
    val categorias: StateFlow<List<Categoria>> = categoriaDao.observarAtivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Todas as categorias (incluindo desativadas) — usado no Painel do Gestor. */
    val todasCategorias: StateFlow<List<Categoria>> = categoriaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Itens (filtrados reativamente) ──────────────────────────────────────

    /**
     * Lista de itens (com nome da categoria) filtrada por:
     *  - categoria selecionada (ou todas se `null`)
     *  - busca textual (descrição, categoria ou local) — case-insensitive
     *  - status (Encontrado apenas, ou todos)
     *
     * Usa [combine] + [flatMapLatest]: quando um filtro muda, cancela a query
     * anterior do Room e reinscreve com o novo filtro.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val itens: StateFlow<List<ItemComCategoria>> = combine(
        _categoriaSelecionada,
        _busca,
        _statusFiltro
    ) { catId, busca, status ->
        Triple(catId, busca, status)
    }.flatMapLatest { (catId, busca, status) ->
        val base = if (catId != null) {
            itemDao.observarPorCategoria(catId)
        } else {
            itemDao.observarTodos()
        }

        base.map { lista ->
            var resultado: List<ItemComCategoria> = lista
            if (status != null) {
                resultado = resultado.filter { it.item.status == status }
            }
            val q = busca.trim()
            if (q.isNotEmpty()) {
                resultado = resultado.filter { ele ->
                    ele.item.descricao.contains(q, ignoreCase = true) ||
                            ele.categoriaNome.contains(q, ignoreCase = true) ||
                            (ele.item.localEncontrado?.contains(q, ignoreCase = true) == true)
                }
            }
            resultado
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ── Ações de filtro (chamadas pela UI) ──────────────────────────────────

    fun selecionarCategoria(id: Long?) {
        _categoriaSelecionada.value = id
    }

    fun setBusca(query: String) {
        _busca.value = query
    }

    fun setStatusFiltro(status: StatusItem?) {
        _statusFiltro.value = status
    }

    fun limparFiltros() {
        _categoriaSelecionada.value = null
        _busca.value = ""
        _statusFiltro.value = StatusItem.Encontrado
    }

    // ── Cadastro e atualização ──────────────────────────────────────────────

    /**
     * Adiciona um novo item ao Room. A sincronização com o servidor acontece
     * automaticamente no próximo ciclo do
     * [com.escola.achadosperdidos.data.worker.LimpezaFotoWorker], ou
     * manualmente via [sincronizarAgora].
     *
     * @param caminhoFoto Caminho absoluto de uma foto já salva em
     *                    `Context.filesDir/fotos_itens/` (capturada pela UI
     *                    do gestor no Passo 6). `null` se sem foto.
     */
    fun adicionarItem(
        descricao: String,
        localEncontrado: String?,
        categoriaId: Long,
        caminhoFoto: String? = null
    ) {
        val descricaoLimpa = descricao.trim()
        if (descricaoLimpa.isEmpty()) return   // UI valida antes; defesa em profundidade

        viewModelScope.launch(Dispatchers.IO) {
            val novo = Item(
                descricao       = descricaoLimpa,
                localEncontrado = localEncontrado?.trim()?.takeIf { it.isNotEmpty() },
                categoriaId     = categoriaId,
                caminhoFoto     = caminhoFoto,
                tabletId        = app.tabletId
            )
            itemDao.inserir(novo)
        }
    }

    /** Marca um item como Devolvido (gestor confirmou a entrega ao dono). */
    fun marcarComoDevolvido(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.atualizarStatus(itemId, StatusItem.Devolvido, Date())
        }
    }

    /** Cria uma nova categoria; ignora silenciosamente se o nome já existe. */
    fun adicionarCategoria(nome: String, emoji: String? = null) {
        val n = nome.trim()
        if (n.isEmpty()) return
        val e = emoji?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.inserirSeNaoExiste(Categoria(nome = n, emoji = e))
        }
    }

    /**
     * Edita nome e/ou emoji de uma categoria existente.
     * @param emoji `null` ou em branco → volta a usar o emoji heurístico pelo nome.
     */
    fun editarCategoria(categoriaId: Long, novoNome: String, novoEmoji: String?) {
        val n = novoNome.trim()
        if (n.isEmpty()) return
        val e = novoEmoji?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.editar(categoriaId, n, e)
        }
    }

    /** Soft-delete: categoria some das grades públicas mas continua no histórico. */
    fun desativarCategoria(categoriaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.desativar(categoriaId)
        }
    }

    /** Reativa uma categoria que estava desativada. */
    fun reativarCategoria(categoriaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.reativar(categoriaId)
        }
    }

    // ── Sincronização manual ────────────────────────────────────────────────

    /**
     * Dispara o ciclo de sincronização imediatamente (botão "Sincronizar agora"
     * no Painel do Gestor). Roda em background; chama [onConcluido] no
     * dispatcher de IO ao terminar — a UI deve trocar de dispatcher se precisar
     * tocar Compose state.
     */
    fun sincronizarAgora(onConcluido: (sucesso: Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val sucesso = runCatching {
                val sync = SyncRepository(ApiClient.obter(), categoriaDao, itemDao, getApplication())
                sync.sincronizarTudo()
            }.isSuccess
            onConcluido(sucesso)
        }
    }
}
