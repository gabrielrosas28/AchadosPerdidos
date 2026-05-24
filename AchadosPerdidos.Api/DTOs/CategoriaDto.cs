namespace AchadosPerdidos.Api.DTOs;

public record CategoriaDto(
    int Id,
    string Nome,
    bool Ativa,
    DateTime DataCriacao
);

public record CriarCategoriaDto(
    string Nome,
    string? IdLocalTablet
);

public record AtualizarCategoriaDto(
    string Nome,
    bool Ativa
);
