using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

#pragma warning disable CA1814 // Prefer jagged arrays over multidimensional

namespace AchadosPerdidos.Api.Migrations
{
    /// <inheritdoc />
    public partial class Inicial : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "Categorias",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    Nome = table.Column<string>(type: "TEXT", maxLength: 100, nullable: false),
                    Ativa = table.Column<bool>(type: "INTEGER", nullable: false),
                    DataCriacao = table.Column<DateTime>(type: "TEXT", nullable: false),
                    IdLocalTablet = table.Column<string>(type: "TEXT", maxLength: 64, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Categorias", x => x.Id);
                });

            migrationBuilder.CreateTable(
                name: "Itens",
                columns: table => new
                {
                    Id = table.Column<int>(type: "INTEGER", nullable: false)
                        .Annotation("Sqlite:Autoincrement", true),
                    Descricao = table.Column<string>(type: "TEXT", maxLength: 500, nullable: false),
                    LocalEncontrado = table.Column<string>(type: "TEXT", maxLength: 200, nullable: true),
                    CategoriaId = table.Column<int>(type: "INTEGER", nullable: false),
                    Status = table.Column<int>(type: "INTEGER", nullable: false),
                    DataCadastro = table.Column<DateTime>(type: "TEXT", nullable: false),
                    DataDevolucao = table.Column<DateTime>(type: "TEXT", nullable: true),
                    NomeArquivoFoto = table.Column<string>(type: "TEXT", maxLength: 255, nullable: true),
                    TabletId = table.Column<string>(type: "TEXT", maxLength: 64, nullable: true),
                    IdLocalTablet = table.Column<string>(type: "TEXT", maxLength: 64, nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_Itens", x => x.Id);
                    table.ForeignKey(
                        name: "FK_Itens_Categorias_CategoriaId",
                        column: x => x.CategoriaId,
                        principalTable: "Categorias",
                        principalColumn: "Id",
                        onDelete: ReferentialAction.Restrict);
                });

            migrationBuilder.InsertData(
                table: "Categorias",
                columns: new[] { "Id", "Ativa", "DataCriacao", "IdLocalTablet", "Nome" },
                values: new object[,]
                {
                    { 1, true, new DateTime(2025, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), null, "Material didático" },
                    { 2, true, new DateTime(2025, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), null, "Lancheiras e garrafas" },
                    { 3, true, new DateTime(2025, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), null, "Casacos" },
                    { 4, true, new DateTime(2025, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), null, "Brinquedos" },
                    { 5, true, new DateTime(2025, 1, 1, 0, 0, 0, 0, DateTimeKind.Utc), null, "Xuxinhas" }
                });

            migrationBuilder.CreateIndex(
                name: "IX_Categorias_IdLocalTablet",
                table: "Categorias",
                column: "IdLocalTablet");

            migrationBuilder.CreateIndex(
                name: "IX_Categorias_Nome",
                table: "Categorias",
                column: "Nome",
                unique: true);

            migrationBuilder.CreateIndex(
                name: "IX_Itens_CategoriaId",
                table: "Itens",
                column: "CategoriaId");

            migrationBuilder.CreateIndex(
                name: "IX_Itens_DataCadastro",
                table: "Itens",
                column: "DataCadastro");

            migrationBuilder.CreateIndex(
                name: "IX_Itens_Status",
                table: "Itens",
                column: "Status");

            migrationBuilder.CreateIndex(
                name: "IX_Itens_TabletId_IdLocalTablet",
                table: "Itens",
                columns: new[] { "TabletId", "IdLocalTablet" },
                unique: true,
                filter: "IdLocalTablet IS NOT NULL");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "Itens");

            migrationBuilder.DropTable(
                name: "Categorias");
        }
    }
}
