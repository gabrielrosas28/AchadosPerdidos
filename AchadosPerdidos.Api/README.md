# Achados & Perdidos — Back-end (Passo 1)

API REST do sistema de Achados e Perdidos do colégio. Roda em Windows Server, recebe os dados do tablet (offline-first) e mantém o histórico permanente.

## Stack
- **ASP.NET Core 10** Web API
- **Entity Framework Core 10**
- **SQLite** (default, zero-config para desenvolvimento)
- **SQL Server** (suportado — basta alterar `DatabaseProvider` no `appsettings.json`)
- **OpenAPI** built-in em `/openapi/v1.json`

## Rodando localmente

```powershell
cd AchadosPerdidos.Api
dotnet run
```

A API sobe em `http://localhost:5080`. O banco SQLite (`achadosperdidos.db`) é criado e populado automaticamente na primeira execução, incluindo as 5 categorias iniciais:

1. Material didático
2. Lancheiras e garrafas
3. Casacos
4. Brinquedos
5. Xuxinhas

## Autenticação

Todas as rotas sob `/api/*` exigem o header **`X-Api-Key`**. A chave está em `appsettings.json` (`ApiKey`). Trocar em produção.

Rotas públicas (sem chave): `/`, `/health`, `/openapi/*`, `/fotos/*`.

## Endpoints principais

| Método | Rota | Descrição |
|---|---|---|
| GET | `/api/categorias` | Lista categorias (param: `somenteAtivas`) |
| POST | `/api/categorias` | Cria categoria (JSON) |
| PUT | `/api/categorias/{id}` | Atualiza categoria |
| DELETE | `/api/categorias/{id}` | Desativa (soft delete) |
| GET | `/api/itens` | Lista itens (params: `categoriaId`, `status`) |
| POST | `/api/itens` | Cria item com foto (multipart/form-data) |
| PATCH | `/api/itens/{id}/status` | Marca como Devolvido/Expirado |
| DELETE | `/api/itens/{id}` | Remove item + foto |
| POST | `/api/sync/itens` | Upload em lote do tablet (JSON, base64 da foto) |
| GET | `/api/sync/categorias` | Download de categorias (param: `desde`) |

## Exemplos rápidos (PowerShell)

```powershell
$key = "TROCAR-ESTA-CHAVE-EM-PRODUCAO-32-CARACTERES-NO-MINIMO"
$headers = @{ "X-Api-Key" = $key }

# Listar categorias
Invoke-RestMethod http://localhost:5080/api/categorias -Headers $headers

# Criar item (multipart)
$form = @{
    Descricao      = "Garrafa rosa"
    LocalEncontrado = "Quadra"
    CategoriaId    = 2
    TabletId       = "tab-001"
    IdLocalTablet  = "uuid-do-tablet"
    foto           = Get-Item .\teste.jpg
}
Invoke-RestMethod http://localhost:5080/api/itens -Method Post -Headers $headers -Form $form
```

## Migrações

```powershell
# Criar nova migration
dotnet ef migrations add NomeDaMigration

# Aplicar manualmente (já roda no startup, mas útil em produção)
dotnet ef database update

# Reverter última
dotnet ef migrations remove
```

## Alternando para SQL Server (produção)

1. Em `appsettings.json`:
   ```json
   "DatabaseProvider": "SqlServer",
   "ConnectionStrings": {
     "Default": "Server=SEU-SERVIDOR;Database=AchadosPerdidos;User Id=sa;Password=SENHA;TrustServerCertificate=True;"
   }
   ```
2. Apagar `Migrations/` e gerar uma nova migration: `dotnet ef migrations add Inicial` (migrations são provider-specific).
3. `dotnet ef database update`.

## Publicar como Serviço do Windows (produção)

```powershell
dotnet publish -c Release -o C:\inetpub\AchadosPerdidos.Api
sc.exe create AchadosPerdidosApi binPath= "C:\inetpub\AchadosPerdidos.Api\AchadosPerdidos.Api.exe" start= auto
sc.exe start AchadosPerdidosApi
```

## Estrutura do projeto

```
AchadosPerdidos.Api/
├── AchadosPerdidos.Api.csproj
├── Program.cs                # Bootstrap, DI, middleware de API Key
├── appsettings.json
├── Properties/launchSettings.json
├── Models/
│   ├── Categoria.cs          # entidade Categoria
│   ├── Item.cs               # entidade Item (com FK para Categoria)
│   └── Enums/StatusItem.cs   # Encontrado, Devolvido, Expirado
├── Data/
│   └── AchadosPerdidosContext.cs   # DbContext + seed das 5 categorias
├── DTOs/
│   ├── CategoriaDto.cs
│   ├── ItemDto.cs
│   └── SyncDtos.cs
├── Services/
│   └── ArmazenamentoFotoService.cs # salva/exclui foto em wwwroot/fotos
├── Controllers/
│   ├── CategoriasController.cs
│   ├── ItensController.cs
│   └── SyncController.cs
├── Migrations/                     # geradas pelo dotnet ef
└── wwwroot/fotos/                  # imagens servidas em /fotos/{arquivo}
```
