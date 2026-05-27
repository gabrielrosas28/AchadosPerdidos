using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// Permite rodar como Windows Service (sem perder o modo console em desenvolvimento)
builder.Host.UseWindowsService(opt => opt.ServiceName = "AchadosPerdidosApi");

// --- Banco de Dados ---
// Por padrão usa SQLite (zero configuração). Para produção, mude
// "DatabaseProvider" para "SqlServer" no appsettings.json e ajuste a connection string.
var provider = builder.Configuration["DatabaseProvider"] ?? "Sqlite";
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default não configurada.");

// Quando rodando como Windows Service, o CWD do processo e' C:\Windows\System32.
// Se "Data Source" for caminho relativo, o SQLite criaria o banco la — o que e'
// confuso pra backup/admin. Ancora em AppContext.BaseDirectory (pasta do .exe)
// pra deixar o banco junto da instalacao (ex.: C:\AchadosPerdidos\).
if (string.Equals(provider, "Sqlite", StringComparison.OrdinalIgnoreCase))
{
    var b = new SqliteConnectionStringBuilder(connectionString);
    if (!string.IsNullOrEmpty(b.DataSource) && !Path.IsPathRooted(b.DataSource))
    {
        b.DataSource = Path.Combine(AppContext.BaseDirectory, b.DataSource);
        connectionString = b.ConnectionString;
    }
}

builder.Services.AddDbContext<AchadosPerdidosContext>(opt =>
{
    if (string.Equals(provider, "SqlServer", StringComparison.OrdinalIgnoreCase))
        opt.UseSqlServer(connectionString);
    else
        opt.UseSqlite(connectionString);
});

// --- Serviços ---
builder.Services.AddScoped<ArmazenamentoFotoService>();
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

// CORS aberto na rede interna da escola (ajuste em produção se necessário)
builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader()));

var app = builder.Build();

// --- Pipeline ---
if (app.Environment.IsDevelopment())
{
    app.MapOpenApi(); // documento OpenAPI em /openapi/v1.json
}

// Servir arquivos estáticos:
//  - /admin/* (painel web do gestor) — wwwroot/admin/
//  - /fotos/* (uploads de itens)     — wwwroot/fotos/
// UseDefaultFiles faz /admin/ servir automaticamente o index.html.
app.UseDefaultFiles();
app.UseStaticFiles();
app.UseCors();

// Middleware de API Key (proteção do tablet -> servidor)
app.Use(async (ctx, next) =>
{
    var path = ctx.Request.Path;
    // Rotas públicas (não exigem X-Api-Key):
    //  /                  → redireciona pro painel
    //  /info, /health     → metadados/monitoramento
    //  /openapi/*         → documento OpenAPI
    //  /fotos/*           → imagens (servidor de estáticos cuida disso)
    //  /admin/*           → arquivos do painel (servido por UseStaticFiles antes daqui)
    if (path.StartsWithSegments("/openapi") ||
        path.StartsWithSegments("/fotos") ||
        path.StartsWithSegments("/admin") ||
        path == "/" || path == "/health" || path == "/info")
    {
        await next();
        return;
    }

    var configuredKey = app.Configuration["ApiKey"];
    if (string.IsNullOrWhiteSpace(configuredKey))
    {
        await next();
        return;
    }

    if (!ctx.Request.Headers.TryGetValue("X-Api-Key", out var sent) || sent != configuredKey)
    {
        ctx.Response.StatusCode = StatusCodes.Status401Unauthorized;
        await ctx.Response.WriteAsync("API Key inválida ou ausente.");
        return;
    }
    await next();
});

// Redireciona a raiz para o Painel do Gestor (HTML estático em wwwroot/admin)
app.MapGet("/", () => Results.Redirect("/admin/"));
app.MapGet("/info", () => Results.Ok(new
{
    api = "Achados & Perdidos - Colégio Santa Chiara",
    versao = "1.0.0",
    openapi = "/openapi/v1.json",
    painel = "/admin/"
}));
app.MapGet("/health", () => Results.Ok(new { status = "ok", hora = DateTime.UtcNow }));

app.MapControllers();

// Aplica migrations na inicialização (cria/atualiza o banco automaticamente)
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AchadosPerdidosContext>();
    db.Database.Migrate();
}

app.Run();
