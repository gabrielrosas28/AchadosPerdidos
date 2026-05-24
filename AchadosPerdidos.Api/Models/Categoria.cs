using System.ComponentModel.DataAnnotations;

namespace AchadosPerdidos.Api.Models;

public class Categoria
{
    public int Id { get; set; }

    [Required, MaxLength(100)]
    public string Nome { get; set; } = string.Empty;

    public bool Ativa { get; set; } = true;

    public DateTime DataCriacao { get; set; } = DateTime.UtcNow;

    [MaxLength(64)]
    public string? IdLocalTablet { get; set; }

    public ICollection<Item> Itens { get; set; } = new List<Item>();
}
