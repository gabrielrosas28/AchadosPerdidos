package com.escola.achadosperdidos.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton com a instância única do [ApiService] e do OkHttp.
 *
 * - **URL base** e **API Key** devem ser configuradas via [configurar] **antes** do primeiro
 *   uso (normalmente em [com.escola.achadosperdidos.AchadosPerdidosApp.onCreate]).
 *   Se não configurada, usa os padrões de desenvolvimento.
 * - Recriar OkHttp/Retrofit a cada chamada é caro (perde-se o pool de conexões),
 *   por isso é cacheado.
 */
object ApiClient {

    /**
     * URL padrão para desenvolvimento.
     *
     * - **Emulador Android**: `http://10.0.2.2:5080/` aponta para o `localhost:5080` do PC host.
     * - **Tablet físico na rede da escola**: troque pelo IP do servidor Windows
     *   (ex.: `http://192.168.1.100:5080/`).
     */
    const val BASE_URL_PADRAO = "http://10.0.2.2:5080/"

    @Volatile private var instancia: ApiService? = null
    @Volatile private var configAtual: Config = Config(BASE_URL_PADRAO, "")

    /** Configuração atual em vigor. */
    val configuracao: Config get() = configAtual

    data class Config(val baseUrl: String, val apiKey: String)

    /**
     * Define a URL base e a API Key. Invalida a instância cacheada caso a configuração
     * mude — a próxima chamada a [obter] cria um novo cliente.
     */
    fun configurar(baseUrl: String, apiKey: String) {
        synchronized(this) {
            val nova = Config(baseUrl.ifBlank { BASE_URL_PADRAO }, apiKey)
            if (nova != configAtual) {
                configAtual = nova
                instancia = null
            }
        }
    }

    /** Retorna o [ApiService] singleton, criando-o sob demanda. */
    fun obter(): ApiService =
        instancia ?: synchronized(this) {
            instancia ?: criar(configAtual).also { instancia = it }
        }

    // ── interno ───────────────────────────────────────────────────────────────

    private fun criar(cfg: Config): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30,  TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)   // uploads de fotos podem ser grandes
            .addInterceptor(logging)
            .addInterceptor { chain ->
                val req = chain.request()
                val novaReq = if (cfg.apiKey.isNotBlank()) {
                    req.newBuilder().addHeader("X-Api-Key", cfg.apiKey).build()
                } else {
                    req
                }
                chain.proceed(novaReq)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(cfg.baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
