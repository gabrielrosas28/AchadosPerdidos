package com.escola.achadosperdidos.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Factory para criar instâncias de [ApiService] com OkHttp configurado.
 *
 * Em desenvolvimento, aponte [BASE_URL_PADRAO] para o IP do servidor na rede local.
 * Em produção, leia a URL das SharedPreferences (configurável pelo gestor nas telas admin).
 */
object ApiClient {

    /**
     * IP padrão do servidor Windows na rede interna da escola.
     * Troque para o IP real antes de instalar no tablet.
     * Exemplos:
     *   - Desenvolvimento local: "http://10.0.2.2:5000/"  (emulador Android aponta pro localhost do PC)
     *   - Rede da escola:        "http://192.168.1.100:5000/"
     */
    const val BASE_URL_PADRAO = "http://192.168.1.100:5000/"

    /**
     * Cria um [ApiService] pronto para uso.
     *
     * @param baseUrl  URL base da API (com barra final).
     * @param apiKey   Valor do header `X-Api-Key`. Ignorado se estiver em branco
     *                 (o servidor também ignora quando sem chave configurada).
     */
    fun criar(
        baseUrl: String = BASE_URL_PADRAO,
        apiKey: String  = ""
    ): ApiService {

        // Log de requisições apenas em builds de debug
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30,  TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)   // uploads de fotos podem ser grandes
            .addInterceptor(logging)
            // Injeta a API Key em todas as requisições (quando configurada)
            .addInterceptor { chain ->
                val original = chain.request()
                val request = if (apiKey.isNotBlank()) {
                    original.newBuilder()
                        .addHeader("X-Api-Key", apiKey)
                        .build()
                } else {
                    original
                }
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
