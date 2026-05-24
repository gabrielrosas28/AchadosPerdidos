using System.ComponentModel.DataAnnotations;
using AchadosPerdidos.Api.Models.Enums;

namespace AchadosPerdidos.Api.Models;

public class Item
{
    public int Id { get; set; }

    [Required, MaxLength(500)]
    public string Descricao { get; set; } = string.Empty;

    [MaxLength(200)]
    public string? LocalEncontrado { get; set; }

    public int CategoriaId { get; set; }
    public Categoria? Categoria { get; set; }

    public StatusItem Status { get; set; } = StatusItem.Encontrado;

    public DateTime DataCadastro { get; set; } = DateTime.UtcNow;
    public DateTime? DataDevolucao { get; set; }

    [MaxLength(255)]
    public string? NomeArquivoFoto { get; set; }

    [MaxLength(64)]
    public string? TabletId { get; set; }

    [MaxLength(64)]
    public string? IdLocalTablet { get; set; }
}
