package com.escola.achadosperdidos.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    const val BASE_URL_PADRAO = "http://192.168.15.61:5080/"

    @Volatile private var instancia: ApiService? = null
    @Volatile private var configAtual: Config = Config(BASE_URL_PADRAO, "")

    val configuracao: Config get() = configAtual

    data class Config(val baseUrl: String, val apiKey: String)

    fun configurar(baseUrl: String, apiKey: String) {
        synchronized(this) {
            val nova = Config(baseUrl.ifBlank { BASE_URL_PADRAO }, apiKey)
            if (nova != configAtual) {
                configAtual = nova
                instancia = null
            }
        }
    }

    fun obter(): ApiService =
        instancia ?: synchronized(this) {
            instancia ?: criar(configAtual).also { instancia = it }
        }

    private fun criar(cfg: Config): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttp = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30,  TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
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
