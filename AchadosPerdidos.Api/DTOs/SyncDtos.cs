using AchadosPerdidos.Api.Models.Enums;

namespace AchadosPerdidos.Api.DTOs;

public record SyncItemDto(
    string Descricao,
    string? LocalEncontrado,
    int? CategoriaServidorId,
    string? CategoriaIdLocalTablet,
    StatusItem Status,
    DateTime DataCadastro,
    DateTime? DataDevolucao,
    string TabletId,
    string IdLocalTablet,
    string? FotoBase64,
    string? NomeArquivoFoto
);

public record SyncResponseDto(
    int Recebidos,
    int Criados,
    int Atualizados,
    List<string> Erros
);
