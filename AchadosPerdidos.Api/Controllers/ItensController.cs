using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.DTOs;
using AchadosPerdidos.Api.Models;
using AchadosPerdidos.Api.Models.Enums;
using AchadosPerdidos.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace AchadosPerdidos.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class ItensController : ControllerBase
{
    private readonly AchadosPerdidosContext _db;
    private readonly ArmazenamentoFotoService _fotos;

    public ItensController(AchadosPerdidosContext db, ArmazenamentoFotoService fotos)
    {
        _db = db;
        _fotos = fotos;
    }

    [HttpGet]
    public async Task<ActionResult<IEnumerable<ItemDto>>> Listar(
        int? categoriaId = null, StatusItem? status = null)
    {
        var q = _db.Itens.Include(i => i.Categoria).AsNoTracking();
        if (categoriaId.HasValue) q = q.Where(i => i.CategoriaId == categoriaId.Value);
        if (status.HasValue) q = q.Where(i => i.Status == status.Value);

        var itens = await q.OrderByDescending(i => i.DataCadastro).ToListAsync();
        return Ok(itens.Select(i => Mapear(i, Request)));
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<ItemDto>> Obter(int id)
    {
        var i = await _db.Itens.Include(x => x.Categoria)
                               .FirstOrDefaultAsync(x => x.Id == id);
        if (i is null) return NotFound();
        return Mapear(i, Request);
    }

    // Upload com foto (multipart/form-data) — usado quando o tablet tem internet
    [HttpPost]
    [Consumes("multipart/form-data")]
    [RequestSizeLimit(20_000_000)] // 20 MB
    public async Task<ActionResult<ItemDto>> Criar(
        [FromForm] CriarItemDto dto,
        IFormFile? foto)
    {
        var categoriaExiste = await _db.Categorias.AnyAsync(c => c.Id == dto.CategoriaId);
        if (!categoriaExiste) return BadRequest("Categoria inexistente.");

        string? nomeFoto = null;
        if (foto is { Length: > 0 })
        {
            await using var s = foto.OpenReadStream();
            nomeFoto = await _fotos.SalvarAsync(s, foto.FileName);
        }

        var item = new Item
        {
            Descricao = dto.Descricao,
            LocalEncontrado = dto.LocalEncontrado,
            CategoriaId = dto.CategoriaId,
            TabletId = dto.TabletId,
            IdLocalTablet = dto.IdLocalTablet,
            NomeArquivoFoto = nomeFoto
        };

        _db.Itens.Add(item);
        await _db.SaveChangesAsync();

        await _db.Entry(item).Reference(x => x.Categoria).LoadAsync();
        return CreatedAtAction(nameof(Obter), new { id = item.Id }, Mapear(item, Request));
    }

    [HttpPatch("{id:int}/status")]
    public async Task<IActionResult> AtualizarStatus(int id, [FromBody] AtualizarStatusItemDto dto)
    {
        var i = await _db.Itens.FindAsync(id);
        if (i is null) return NotFound();

        i.Status = dto.Status;
        if (dto.Status == StatusItem.Devolvido && i.DataDevolucao is null)
            i.DataDevolucao = DateTime.UtcNow;

        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Remover(int id)
    {
        var i = await _db.Itens.FindAsync(id);
        if (i is null) return NotFound();

        _fotos.Excluir(i.NomeArquivoFoto);
        _db.Itens.Remove(i);
        await _db.SaveChangesAsync();
        return NoContent();
    }

    private static ItemDto Mapear(Item i, HttpRequest req)
    {
        string? urlFoto = null;
        if (!string.IsNullOrWhiteSpace(i.NomeArquivoFoto))
            urlFoto = $"{req.Scheme}://{req.Host}/fotos/{i.NomeArquivoFoto}";

        return new ItemDto(
            i.Id, i.Descricao, i.LocalEncontrado,
            i.CategoriaId, i.Categoria?.Nome ?? string.Empty,
            i.Status, i.DataCadastro, i.DataDevolucao,
            urlFoto, i.TabletId, i.IdLocalTablet);
    }
}
