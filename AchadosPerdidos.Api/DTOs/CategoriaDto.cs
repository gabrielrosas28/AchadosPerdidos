namespace AchadosPerdidos.Api.DTOs;

public record CategoriaDto(
    int Id,
    string Nome,
    bool Ativa,
    DateTime DataCriacao,
    string? Emoji
);

public record CriarCategoriaDto(
    string Nome,
    string? Emoji,
    string? IdLocalTablet
);

public record AtualizarCategoriaDto(
    string Nome,
    bool Ativa,
    string? Emoji
);
