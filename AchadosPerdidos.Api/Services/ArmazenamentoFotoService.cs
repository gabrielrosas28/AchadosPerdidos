namespace AchadosPerdidos.Api.Services;

public class ArmazenamentoFotoService
{
    private readonly string _pasta;
    private readonly ILogger<ArmazenamentoFotoService> _log;

    public ArmazenamentoFotoService(IConfiguration cfg, IWebHostEnvironment env,
                                    ILogger<ArmazenamentoFotoService> log)
    {
        var rel = cfg["PastaFotos"] ?? "wwwroot/fotos";
        _pasta = Path.IsPathRooted(rel) ? rel : Path.Combine(env.ContentRootPath, rel);
        Directory.CreateDirectory(_pasta);
        _log = log;
    }

    public async Task<string> SalvarAsync(Stream stream, string nomeOriginal, CancellationToken ct = default)
    {
        var ext = Path.GetExtension(nomeOriginal);
        if (string.IsNullOrWhiteSpace(ext)) ext = ".jpg";

        var nome = $"{Guid.NewGuid():N}{ext}";
        var caminho = Path.Combine(_pasta, nome);

        await using var fs = File.Create(caminho);
        await stream.CopyToAsync(fs, ct);

        _log.LogInformation("Foto salva: {Nome}", nome);
        return nome;
    }

    public async Task<string> SalvarBase64Async(string base64, string nomeOriginal, CancellationToken ct = default)
    {
        var bytes = Convert.FromBase64String(base64);
        using var ms = new MemoryStream(bytes);
        return await SalvarAsync(ms, nomeOriginal, ct);
    }

    public void Excluir(string? nomeArquivo)
    {
        if (string.IsNullOrWhiteSpace(nomeArquivo)) return;
        var caminho = Path.Combine(_pasta, nomeArquivo);
        if (File.Exists(caminho))
        {
            File.Delete(caminho);
            _log.LogInformation("Foto removida: {Nome}", nomeArquivo);
        }
    }
}
