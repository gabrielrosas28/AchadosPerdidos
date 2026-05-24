# Achados e Perdidos — Colégio Santa Chiara

Sistema híbrido de Achados e Perdidos para um tablet exposto na escola.
O tablet opera **offline-first** (Room) e envia dados periodicamente para o servidor Windows da escola (ASP.NET Core + EF Core), que mantém o histórico permanente.

## Arquitetura

```
┌─────────────────────────────┐        sync REST + foto       ┌──────────────────────────────┐
│  Tablet Android (Quiosque)  │ ───────────────────────────▶  │  Windows Server (intranet)   │
│  Kotlin · Compose · MVVM    │                                │  ASP.NET Core 10 · EF Core   │
│  Room (SQLite local)        │ ◀───── novas categorias ────  │  SQLite (dev) / SQL Server   │
└─────────────────────────────┘                                └──────────────────────────────┘
```

| Pasta | Descrição | Tecnologia |
|---|---|---|
| [`AchadosPerdidos.Api/`](./AchadosPerdidos.Api) | API REST + banco do servidor | ASP.NET Core 10, EF Core 10 |
| [`Android/`](./Android) | Aplicativo do tablet | Kotlin, Jetpack Compose, Room |

Cada subpasta tem seu próprio README com instruções de build e execução.

## Roteiro de entregas

- ✅ **Passo 1** — Back-end (API REST + modelo relacional + seed das categorias)
- ✅ **Passo 2** — Models + Room Database (entidades, DAOs, seed local)
- ✅ **Passo 3** — Retrofit + WorkManager (sync offline-first, limpeza automática de fotos, FileProvider, permissões)
- ⬜ Passo 4 — ViewModels (Modo Público vs Modo Gestor, autenticação por PIN)
- ⬜ Passo 5 — UI do Modo Público (Compose, grades, mascote com long click)
- ⬜ Passo 6 — UI do Modo Gestor (painel, cadastro com câmera, gestão de categorias)

## Regras de negócio principais

- **Modo Público (default):** alunos, pais e funcionários só visualizam.
- **Modo Gestor:** acionado por **long click de 3 s no mascote** + PIN. Único modo onde se cadastra item / categoria.
- **Limpeza automática de fotos:** se o item for marcado `Devolvido` OU passar de 180 dias, a foto local é apagada (texto e foto permanecem no servidor).
- **Categorias dinâmicas:** entidade no banco, não enum. Categorias iniciais: *Material didático, Lancheiras e garrafas, Casacos, Brinquedos, Xuxinhas*.
