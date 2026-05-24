using AchadosPerdidos.Api.Models.Enums;

namespace AchadosPerdidos.Api.DTOs;

public record ItemDto(
    int Id,
    string Descricao,
    string? LocalEncontrado,
    int CategoriaId,
    string CategoriaNome,
    StatusItem Status,
    DateTime DataCadastro,
    DateTime? DataDevolucao,
    string? UrlFoto,
    string? TabletId,
    string? IdLocalTablet
);

public record CriarItemDto(
    string Descricao,
    string? LocalEncontrado,
    int CategoriaId,
    string? TabletId,
    string? IdLocalTablet
);

public record AtualizarStatusItemDto(
    StatusItem Status
);
