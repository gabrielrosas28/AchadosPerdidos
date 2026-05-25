package com.escola.achadosperdidos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.escola.achadosperdidos.data.model.Categoria
import com.escola.achadosperdidos.data.model.Item
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [Categoria::class, Item::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoriaDao(): CategoriaDao
    abstract fun itemDao(): ItemDao

    companion object {
        private const val DB_NAME = "achados_perdidos.db"

        // Categorias que devem existir já na primeira execução do app.
        private val CATEGORIAS_PADRAO = listOf(
            "Material didático",
            "Lancheiras e garrafas",
            "Casacos",
            "Brinquedos",
            "Xuxinhas"
        )

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun obter(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    // Em desenvolvimento aceita reset; em produção trocar por Migrations explícitas.
                    .fallbackToDestructiveMigration()
                    .addCallback(SeedCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback que insere as categorias iniciais APENAS quando o banco é criado
         * pela primeira vez (após instalação ou wipe). Executa em IO para não bloquear
         * a thread principal.
         */
        private class SeedCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val instancia = INSTANCE ?: return
                scope.launch(Dispatchers.IO) {
                    val dao = instancia.categoriaDao()
                    CATEGORIAS_PADRAO.forEach { nome ->
                        dao.inserirSeNaoExiste(Categoria(nome = nome))
                    }
                }
            }
        }
    }
}
