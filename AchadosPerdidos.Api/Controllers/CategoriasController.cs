using AchadosPerdidos.Api.Data;
using AchadosPerdidos.Api.DTOs;
using AchadosPerdidos.Api.Models;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace AchadosPerdidos.Api.Controllers;

[ApiController]
[Route("api/[controller]")]
public class CategoriasController : ControllerBase
{
    private readonly AchadosPerdidosContext _db;
    public CategoriasController(AchadosPerdidosContext db) => _db = db;

    [HttpGet]
    public async Task<ActionResult<IEnumerable<CategoriaDto>>> Listar(bool somenteAtivas = true)
    {
        var q = _db.Categorias.AsNoTracking();
        if (somenteAtivas) q = q.Where(c => c.Ativa);

        var lista = await q
            .OrderBy(c => c.Nome)
            .Select(c => new CategoriaDto(c.Id, c.Nome, c.Ativa, c.DataCriacao, c.Emoji))
            .ToListAsync();

        return Ok(lista);
    }

    [HttpGet("{id:int}")]
    public async Task<ActionResult<CategoriaDto>> Obter(int id)
    {
        var c = await _db.Categorias.FindAsync(id);
        if (c is null) return NotFound();
        return new CategoriaDto(c.Id, c.Nome, c.Ativa, c.DataCriacao, c.Emoji);
    }

    [HttpPost]
    public async Task<ActionResult<CategoriaDto>> Criar([FromBody] CriarCategoriaDto dto)
    {
        if (string.IsNullOrWhiteSpace(dto.Nome))
            return BadRequest("Nome é obrigatório.");

        var nome = dto.Nome.Trim();
        var existe = await _db.Categorias.AnyAsync(c => c.Nome == nome);
        if (existe) return Conflict("Já existe uma categoria com este nome.");

        var c = new Categoria
        {
            Nome = nome,
            Emoji = string.IsNullOrWhiteSpace(dto.Emoji) ? null : dto.Emoji.Trim(),
            IdLocalTablet = dto.IdLocalTablet
        };
        _db.Categorias.Add(c);
        await _db.SaveChangesAsync();

        return CreatedAtAction(nameof(Obter), new { id = c.Id },
            new CategoriaDto(c.Id, c.Nome, c.Ativa, c.DataCriacao, c.Emoji));
    }

    [HttpPut("{id:int}")]
    public async Task<IActionResult> Atualizar(int id, [FromBody] AtualizarCategoriaDto dto)
    {
        var c = await _db.Categorias.FindAsync(id);
        if (c is null) return NotFound();

        c.Nome = dto.Nome.Trim();
        c.Ativa = dto.Ativa;
        c.Emoji = string.IsNullOrWhiteSpace(dto.Emoji) ? null : dto.Emoji.Trim();
        await _db.SaveChangesAsync();
        return NoContent();
    }

    [HttpDelete("{id:int}")]
    public async Task<IActionResult> Desativar(int id)
    {
        var c = await _db.Categorias.FindAsync(id);
        if (c is null) return NotFound();

        c.Ativa = false;
        await _db.SaveChangesAsync();
        return NoContent();
    }
}
