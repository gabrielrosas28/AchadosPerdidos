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

class AchadosViewModel(application: Application) : AndroidViewModel(application) {

    private val app: AchadosPerdidosApp
        get() = getApplication<AchadosPerdidosApp>()

    private val categoriaDao: CategoriaDao = app.database.categoriaDao()
    private val itemDao: ItemDao = app.database.itemDao()

    private val _categoriaSelecionada = MutableStateFlow<Long?>(null)
    val categoriaSelecionada: StateFlow<Long?> = _categoriaSelecionada.asStateFlow()

    private val _busca = MutableStateFlow("")
    val busca: StateFlow<String> = _busca.asStateFlow()

    private val _statusFiltro = MutableStateFlow<StatusItem?>(StatusItem.Encontrado)
    val statusFiltro: StateFlow<StatusItem?> = _statusFiltro.asStateFlow()

    val categorias: StateFlow<List<Categoria>> = categoriaDao.observarAtivas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val todasCategorias: StateFlow<List<Categoria>> = categoriaDao.observarTodas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    fun adicionarItem(
        descricao: String,
        localEncontrado: String?,
        categoriaId: Long,
        caminhoFoto: String? = null
    ) {
        val descricaoLimpa = descricao.trim()
        if (descricaoLimpa.isEmpty()) return

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

    fun marcarComoDevolvido(itemId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            itemDao.atualizarStatus(itemId, StatusItem.Devolvido, Date())
        }
    }

    fun adicionarCategoria(nome: String, emoji: String? = null) {
        val n = nome.trim()
        if (n.isEmpty()) return
        val e = emoji?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.inserirSeNaoExiste(Categoria(nome = n, emoji = e))
        }
    }

    fun editarCategoria(categoriaId: Long, novoNome: String, novoEmoji: String?) {
        val n = novoNome.trim()
        if (n.isEmpty()) return
        val e = novoEmoji?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.editar(categoriaId, n, e)
        }
    }

    fun desativarCategoria(categoriaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.desativar(categoriaId)
        }
    }

    fun reativarCategoria(categoriaId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            categoriaDao.reativar(categoriaId)
        }
    }

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
