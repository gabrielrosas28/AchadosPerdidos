using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

// --- Banco de Dados ---
// Por padrão usa SQLite (zero configuração). Para produção, mude
// "DatabaseProvider" para "SqlServer" no appsettings.json e ajuste a connection string.
var provider = builder.Configuration["DatabaseProvider"] ?? "Sqlite";
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default não configurada.");

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

app.UseStaticFiles(); // serve fotos em /fotos/{arquivo}
app.UseCors();

// Middleware de API Key (proteção do tablet -> servidor)
app.Use(async (ctx, next) =>
{
    var path = ctx.Request.Path;
    if (path.StartsWithSegments("/openapi") ||
        path.StartsWithSegments("/fotos") ||
        path == "/" || path == "/health")
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

app.MapGet("/", () => Results.Ok(new
{
    api = "Achados & Perdidos - Colégio Santa Chiara",
    versao = "1.0.0",
    openapi = "/openapi/v1.json"
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
