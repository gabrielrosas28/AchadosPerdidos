using AchadosPerdidos.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace AchadosPerdidos.Api.Data;

public class AchadosPerdidosContext : DbContext
{
    public AchadosPerdidosContext(DbContextOptions<AchadosPerdidosContext> options) : base(options) { }

    public DbSet<Categoria> Categorias => Set<Categoria>();
    public DbSet<Item> Itens => Set<Item>();

    protected override void OnModelCreating(ModelBuilder mb)
    {
        mb.Entity<Categoria>(e =>
        {
            e.HasIndex(c => c.Nome).IsUnique();
            e.HasIndex(c => c.IdLocalTablet);
        });

        mb.Entity<Item>(e =>
        {
            e.HasOne(i => i.Categoria)
             .WithMany(c => c.Itens)
             .HasForeignKey(i => i.CategoriaId)
             .OnDelete(DeleteBehavior.Restrict);

            e.HasIndex(i => new { i.TabletId, i.IdLocalTablet })
             .IsUnique()
             .HasFilter("IdLocalTablet IS NOT NULL");

            e.HasIndex(i => i.Status);
            e.HasIndex(i => i.DataCadastro);
        });

        // --- Seed das categorias iniciais ---
        var seedDate = new DateTime(2025, 1, 1, 0, 0, 0, DateTimeKind.Utc);
        mb.Entity<Categoria>().HasData(
            new Categoria { Id = 1, Nome = "Material didático",     Ativa = true, DataCriacao = seedDate },
            new Categoria { Id = 2, Nome = "Lancheiras e garrafas", Ativa = true, DataCriacao = seedDate },
            new Categoria { Id = 3, Nome = "Casacos",               Ativa = true, DataCriacao = seedDate },
            new Categoria { Id = 4, Nome = "Brinquedos",            Ativa = true, DataCriacao = seedDate },
            new Categoria { Id = 5, Nome = "Xuxinhas",              Ativa = true, DataCriacao = seedDate }
        );
    }
}
