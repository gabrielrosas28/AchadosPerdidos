// ═══════════════════════════════════════════════════════════════════════════
//  Painel do Gestor — Achados e Perdidos
//  Vanilla JS — consome a mesma API REST que o tablet (/api/...)
// ═══════════════════════════════════════════════════════════════════════════

// ── Configuração ───────────────────────────────────────────────────────────
const API_BASE = ''; // mesma origem — servido pelo backend .NET

// ── Estado em memória ──────────────────────────────────────────────────────
let apiKey = sessionStorage.getItem('ap-api-key') || '';
let categorias = [];
let itensAtivos = [];
let itensDevolvidos = [];
let idParaEntregar = null;

// ── Helpers ────────────────────────────────────────────────────────────────

/** Fetch wrapper que injeta a API key e trata 401. */
async function api(path, options = {}) {
    const headers = options.headers ? { ...options.headers } : {};
    if (apiKey) headers['X-Api-Key'] = apiKey;

    const resp = await fetch(API_BASE + path, { ...options, headers });

    if (resp.status === 401) {
        sessionStorage.removeItem('ap-api-key');
        apiKey = '';
        mostrarLogin('Chave inválida. Confira o valor em appsettings.json.');
        throw new Error('Não autorizado');
    }
    if (!resp.ok) {
        const texto = await resp.text();
        throw new Error(`HTTP ${resp.status}: ${texto || resp.statusText}`);
    }
    if (resp.status === 204) return null;
    const ct = resp.headers.get('content-type') || '';
    return ct.includes('json') ? await resp.json() : await resp.text();
}

function toast(msg, tipo = '') {
    const t = document.getElementById('toast');
    t.textContent = msg;
    t.className = `toast show ${tipo}`;
    clearTimeout(toast._timer);
    toast._timer = setTimeout(() => t.className = 'toast', 3500);
}

function emojiParaCategoria(nome) {
    const n = (nome || '').toLowerCase().trim();
    if (n.includes('material'))                          return '📘';
    if (n.includes('lancheira') || n.includes('garrafa')) return '🎒';
    if (n.includes('casaco')   || n.includes('agasalho')) return '🧥';
    if (n.includes('brinquedo'))                          return '🧸';
    if (n.includes('xuxinha')  || n.includes('acess'))    return '🎀';
    if (n.includes('chave'))                              return '🔑';
    if (n.includes('óculos')   || n.includes('oculos'))   return '👓';
    if (n.includes('celular')  || n.includes('eletr'))    return '📱';
    return '📦';
}

function formatarData(iso) {
    if (!iso) return '';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleDateString('pt-BR') + ' às ' +
           d.toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function escapeHtml(s) {
    if (s == null) return '';
    return String(s).replace(/[&<>"']/g, c =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

function setStatusConexao(estado) {
    const el = document.getElementById('status-conexao');
    if (estado === 'ok')         { el.className = 'status ok';         el.textContent = 'Conectado'; }
    else if (estado === 'erro')  { el.className = 'status erro';       el.textContent = 'Erro de conexão'; }
    else                          { el.className = 'status carregando'; el.textContent = 'Carregando…'; }
}

// ── Login (API key) ─────────────────────────────────────────────────────────

function mostrarLogin(msgErro = '') {
    document.getElementById('erro-login').textContent = msgErro;
    const modal = document.getElementById('modal-login');
    modal.hidden = false;
    setTimeout(() => document.getElementById('input-api-key').focus(), 50);
}

document.getElementById('btn-entrar').addEventListener('click', async () => {
    const valor = document.getElementById('input-api-key').value.trim();
    if (!valor) return;
    apiKey = valor;
    sessionStorage.setItem('ap-api-key', apiKey);

    try {
        // valida com uma chamada simples
        await api('/api/categorias');
        document.getElementById('modal-login').hidden = true;
        document.getElementById('input-api-key').value = '';
        atualizarTudo();
    } catch (_) { /* o api() já trata o 401 */ }
});

document.getElementById('input-api-key').addEventListener('keydown', e => {
    if (e.key === 'Enter') document.getElementById('btn-entrar').click();
});

// ── Sair ───────────────────────────────────────────────────────────────────

document.getElementById('btn-sair').addEventListener('click', () => {
    sessionStorage.removeItem('ap-api-key');
    apiKey = '';
    categorias = []; itensAtivos = []; itensDevolvidos = [];
    document.getElementById('lista-itens').innerHTML = '';
    document.getElementById('lista-categorias').innerHTML = '';
    document.getElementById('lista-historico').innerHTML = '';
    mostrarLogin();
});

// ── Tabs ───────────────────────────────────────────────────────────────────

document.querySelectorAll('.tab').forEach(btn => {
    btn.addEventListener('click', () => {
        const tab = btn.dataset.tab;
        document.querySelectorAll('.tab').forEach(b => b.classList.toggle('active', b === btn));
        document.querySelectorAll('.tab-content').forEach(c =>
            c.classList.toggle('active', c.id === `tab-${tab}`));
    });
});

// ── Carregar dados ─────────────────────────────────────────────────────────

async function carregarCategorias() {
    // ?somenteAtivas=false para o gestor ver todas (inclui desativadas)
    categorias = await api('/api/categorias?somenteAtivas=false');
    renderCategorias();
    atualizarSelectCategoria();
}

async function carregarItensAtivos() {
    // Status no servidor: 0=Encontrado, 1=Devolvido, 2=Expirado
    itensAtivos = await api('/api/itens?status=0');
    renderItens();
}

async function carregarHistorico() {
    itensDevolvidos = await api('/api/itens?status=1');
    renderHistorico();
}

async function atualizarTudo() {
    setStatusConexao('carregando');
    try {
        await Promise.all([carregarCategorias(), carregarItensAtivos(), carregarHistorico()]);
        setStatusConexao('ok');
    } catch (e) {
        setStatusConexao('erro');
        toast('Erro ao carregar dados: ' + e.message, 'erro');
    }
}

// ── Render ─────────────────────────────────────────────────────────────────

function renderCategorias() {
    const c = document.getElementById('lista-categorias');
    if (!categorias.length) {
        c.innerHTML = `<div class="vazio"><div class="emoji">📁</div><p>Nenhuma categoria ainda.</p></div>`;
        return;
    }
    c.innerHTML = categorias.map(cat => `
        <div class="card-categoria ${cat.ativa ? '' : 'inativa'}">
            <div class="emoji">${emojiParaCategoria(cat.nome)}</div>
            <div class="nome">${escapeHtml(cat.nome)}</div>
            ${cat.ativa ? '' : '<span class="badge">Inativa</span>'}
        </div>
    `).join('');
}

function renderItens() {
    const busca = document.getElementById('busca-itens').value.trim().toLowerCase();
    const filtrados = busca
        ? itensAtivos.filter(i =>
            i.descricao.toLowerCase().includes(busca) ||
            (i.categoriaNome || '').toLowerCase().includes(busca) ||
            (i.localEncontrado || '').toLowerCase().includes(busca))
        : itensAtivos;

    const c = document.getElementById('lista-itens');
    if (!filtrados.length) {
        c.innerHTML = `<div class="vazio"><div class="emoji">📭</div><p>${
            busca ? 'Nenhum item encontrado para essa busca.' : 'Nenhum item cadastrado ainda.'
        }</p></div>`;
        return;
    }
    c.innerHTML = filtrados.map(it => `
        <div class="card-item">
            <div class="foto">
                ${it.urlFoto
                    ? `<img src="${escapeHtml(it.urlFoto)}" alt="${escapeHtml(it.descricao)}">`
                    : '📷'}
            </div>
            <div class="info">
                <h3>${escapeHtml(it.descricao)}</h3>
                <div class="meta">${emojiParaCategoria(it.categoriaNome)} ${escapeHtml(it.categoriaNome)}</div>
                ${it.localEncontrado ? `<div class="meta">📍 ${escapeHtml(it.localEncontrado)}</div>` : ''}
                <button class="btn-entregar" data-id="${it.id}" data-desc="${escapeHtml(it.descricao)}">
                    ✔️ Entregar
                </button>
            </div>
        </div>
    `).join('');

    c.querySelectorAll('.btn-entregar').forEach(b => {
        b.addEventListener('click', () => abrirConfirmacaoEntrega(b.dataset.id, b.dataset.desc));
    });
}

function renderHistorico() {
    const c = document.getElementById('lista-historico');
    if (!itensDevolvidos.length) {
        c.innerHTML = `<div class="vazio"><div class="emoji">📭</div><p>Nenhum item devolvido ainda.</p></div>`;
        return;
    }
    c.innerHTML = itensDevolvidos.map(it => `
        <div class="item-historico">
            <div class="check">✔️</div>
            <div class="info">
                <div class="descricao">${escapeHtml(it.descricao)}</div>
                <div class="meta">${emojiParaCategoria(it.categoriaNome)} ${escapeHtml(it.categoriaNome)} ·
                    Devolvido em ${escapeHtml(formatarData(it.dataDevolucao))}</div>
            </div>
        </div>
    `).join('');
}

function atualizarSelectCategoria() {
    const sel = document.getElementById('categoria-select');
    sel.innerHTML = categorias
        .filter(c => c.ativa)
        .map(c => `<option value="${c.id}">${escapeHtml(c.nome)}</option>`)
        .join('');
}

// ── Filtro de busca ────────────────────────────────────────────────────────

document.getElementById('busca-itens').addEventListener('input', renderItens);

// ── Novo Item (com foto) ───────────────────────────────────────────────────

document.getElementById('btn-novo-item').addEventListener('click', () => {
    document.getElementById('form-novo-item').reset();
    const preview = document.getElementById('foto-preview');
    preview.src = ''; preview.hidden = true;
    document.getElementById('modal-novo-item').hidden = false;
    setTimeout(() => document.getElementById('descricao').focus(), 50);
});

document.getElementById('foto-arquivo').addEventListener('change', e => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = ev => {
        const img = document.getElementById('foto-preview');
        img.src = ev.target.result;
        img.hidden = false;
    };
    reader.readAsDataURL(file);
});

document.getElementById('form-novo-item').addEventListener('submit', async e => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;

    const fd = new FormData();
    fd.append('Descricao',       document.getElementById('descricao').value.trim());
    fd.append('LocalEncontrado', document.getElementById('local').value.trim());
    fd.append('CategoriaId',     document.getElementById('categoria-select').value);

    const fileEl = document.getElementById('foto-arquivo');
    if (fileEl.files && fileEl.files[0]) {
        fd.append('foto', fileEl.files[0]);
    }

    try {
        await api('/api/itens', { method: 'POST', body: fd });
        toast('Item cadastrado com sucesso!', 'sucesso');
        document.getElementById('modal-novo-item').hidden = true;
        await carregarItensAtivos();
    } catch (err) {
        toast('Erro ao cadastrar: ' + err.message, 'erro');
    } finally {
        btn.disabled = false;
    }
});

// ── Nova Categoria ─────────────────────────────────────────────────────────

document.getElementById('btn-nova-categoria').addEventListener('click', () => {
    document.getElementById('form-nova-categoria').reset();
    document.getElementById('modal-nova-categoria').hidden = false;
    setTimeout(() => document.getElementById('categoria-nome').focus(), 50);
});

document.getElementById('form-nova-categoria').addEventListener('submit', async e => {
    e.preventDefault();
    const nome = document.getElementById('categoria-nome').value.trim();
    if (!nome) return;
    const btn = e.target.querySelector('button[type="submit"]');
    btn.disabled = true;
    try {
        await api('/api/categorias', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nome: nome, idLocalTablet: null })
        });
        toast('Categoria criada!', 'sucesso');
        document.getElementById('modal-nova-categoria').hidden = true;
        await carregarCategorias();
    } catch (err) {
        toast('Erro: ' + err.message, 'erro');
    } finally {
        btn.disabled = false;
    }
});

// ── Entregar item (PATCH /status) ──────────────────────────────────────────

function abrirConfirmacaoEntrega(id, descricao) {
    idParaEntregar = id;
    document.getElementById('entregar-descricao').textContent = `"${descricao}"`;
    document.getElementById('modal-entregar').hidden = false;
}

document.getElementById('confirmar-entrega').addEventListener('click', async () => {
    if (!idParaEntregar) return;
    const btn = document.getElementById('confirmar-entrega');
    btn.disabled = true;
    try {
        await api(`/api/itens/${idParaEntregar}/status`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ status: 1 })   // 1 = Devolvido
        });
        toast('Item marcado como devolvido!', 'sucesso');
        document.getElementById('modal-entregar').hidden = true;
        idParaEntregar = null;
        await Promise.all([carregarItensAtivos(), carregarHistorico()]);
    } catch (err) {
        toast('Erro: ' + err.message, 'erro');
    } finally {
        btn.disabled = false;
    }
});

// ── Fechar modais (botões com data-fechar) ─────────────────────────────────

document.querySelectorAll('[data-fechar]').forEach(el => {
    el.addEventListener('click', () => {
        document.getElementById(el.dataset.fechar).hidden = true;
    });
});

// ── Fechar modal clicando no fundo escuro ──────────────────────────────────

document.querySelectorAll('.modal').forEach(m => {
    m.addEventListener('click', e => {
        if (e.target === m && m.id !== 'modal-login') m.hidden = true;
    });
});

// ── Bootstrap ──────────────────────────────────────────────────────────────

if (!apiKey) {
    mostrarLogin();
} else {
    atualizarTudo();
}

// Atualização periódica (cada 30s) para refletir mudanças do tablet
setInterval(() => {
    if (apiKey && document.visibilityState === 'visible') {
        atualizarTudo();
    }
}, 30000);
