using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.Services;
using Microsoft.Data.Sqlite;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Host.UseWindowsService(opt => opt.ServiceName = "AchadosPerdidosApi");

var provider = builder.Configuration["DatabaseProvider"] ?? "Sqlite";
var connectionString = builder.Configuration.GetConnectionString("Default")
    ?? throw new InvalidOperationException("ConnectionStrings:Default não configurada.");

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

builder.Services.AddScoped<ArmazenamentoFotoService>();
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddOpenApi();

builder.Services.AddCors(o => o.AddDefaultPolicy(p =>
    p.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader()));

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.MapOpenApi();
}

app.UseDefaultFiles();
app.UseStaticFiles();
app.UseCors();

app.Use(async (ctx, next) =>
{
    var path = ctx.Request.Path;
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

using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AchadosPerdidosContext>();
    db.Database.Migrate();
}

app.Run();
