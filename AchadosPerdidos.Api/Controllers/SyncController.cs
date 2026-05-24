using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.DTOs;
using AchadosPerdidos.Api.Models;
using AchadosPerdidos.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace AchadosPerdidos.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class SyncController : ControllerBase
{
    private readonly AchadosPerdidosContext _db;
    private readonly ArmazenamentoFotoService _fotos;

    public SyncController(AchadosPerdidosContext db, ArmazenamentoFotoService fotos)
    {
        _db = db;
        _fotos = fotos;
    }

    // Upload em lote (offline-first do tablet)
    [HttpPost("itens")]
    public async Task<ActionResult<SyncResponseDto>> EnviarItens([FromBody] List<SyncItemDto> lote)
    {
        int criados = 0, atualizados = 0;
        var erros = new List<string>();

        foreach (var dto in lote)
        {
            try
            {
                var existente = await _db.Itens.FirstOrDefaultAsync(i =>
                    i.TabletId == dto.TabletId && i.IdLocalTablet == dto.IdLocalTablet);

                int categoriaId = await ResolverCategoriaIdAsync(dto);

                if (existente is null)
                {
                    string? nomeFoto = null;
                    if (!string.IsNullOrWhiteSpace(dto.FotoBase64))
                        nomeFoto = await _fotos.SalvarBase64Async(
                            dto.FotoBase64, dto.NomeArquivoFoto ?? "foto.jpg");

                    _db.Itens.Add(new Item
                    {
                        Descricao = dto.Descricao,
                        LocalEncontrado = dto.LocalEncontrado,
                        CategoriaId = categoriaId,
                        Status = dto.Status,
                        DataCadastro = dto.DataCadastro,
                        DataDevolucao = dto.DataDevolucao,
                        TabletId = dto.TabletId,
                        IdLocalTablet = dto.IdLocalTablet,
                        NomeArquivoFoto = nomeFoto
                    });
                    criados++;
                }
                else
                {
                    existente.Status = dto.Status;
                    existente.DataDevolucao = dto.DataDevolucao;
                    atualizados++;
                }
            }
            catch (Exception ex)
            {
                erros.Add($"{dto.IdLocalTablet}: {ex.Message}");
            }
        }

        await _db.SaveChangesAsync();
        return Ok(new SyncResponseDto(lote.Count, criados, atualizados, erros));
    }

    // Download de categorias atualizadas (tablet usa para receber novas categorias do servidor)
    [HttpGet("categorias")]
    public async Task<ActionResult<IEnumerable<CategoriaDto>>> BaixarCategorias(DateTime? desde = null)
    {
        var q = _db.Categorias.AsNoTracking();
        if (desde.HasValue) q = q.Where(c => c.DataCriacao >= desde.Value);

        var lista = await q
            .Select(c => new CategoriaDto(c.Id, c.Nome, c.Ativa, c.DataCriacao))
            .ToListAsync();

        return Ok(lista);
    }

    private async Task<int> ResolverCategoriaIdAsync(SyncItemDto dto)
    {
        if (dto.CategoriaServidorId is int id &&
            await _db.Categorias.AnyAsync(c => c.Id == id))
            return id;

        if (!string.IsNullOrWhiteSpace(dto.CategoriaIdLocalTablet))
        {
            var c = await _db.Categorias
                .FirstOrDefaultAsync(x => x.IdLocalTablet == dto.CategoriaIdLocalTablet);
            if (c != null) return c.Id;
        }

        throw new InvalidOperationException("Categoria não encontrada no servidor.");
    }
}
