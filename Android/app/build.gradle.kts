import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)   // necessário para Jetpack Compose no Kotlin 2.x
    alias(libs.plugins.ksp)
}

// Le segredos e configuracao do servidor de local.properties (nao versionado).
// Falhar a build se as chaves nao existirem evita publicar APK sem configuracao.
val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val achadosBaseUrl: String = localProperties.getProperty("achados.baseUrl")
    ?: error("Defina achados.baseUrl em local.properties (ex: http://192.168.3.157:5080/)")
val achadosApiKey: String = localProperties.getProperty("achados.apiKey")
    ?: error("Defina achados.apiKey em local.properties (valor de C:\\AchadosPerdidos\\appsettings.json)")

android {
    namespace  = "com.escola.achadosperdidos"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.escola.achadosperdidos"
        minSdk        = 24
        targetSdk     = 35
        // Versionamento SemVer (MAJOR.MINOR.PATCH).
        // - MAJOR: mudança incompatível de dados ou de fluxo do gestor.
        // - MINOR: nova feature (export de backup, nova tela admin, etc.).
        // - PATCH: correção de bug, ajuste cosmético.
        // versionCode é monotônico (incrementa 1 a cada build publicado).
        versionCode   = 8
        versionName   = "1.2.5"

        // BuildConfig.ACHADOS_BASE_URL / ACHADOS_API_KEY consumidos pelo
        // ApiClient via AchadosPerdidosApp.onCreate. Trocar rede = editar
        // local.properties + recompilar (sem mexer no codigo versionado).
        buildConfigField("String", "ACHADOS_BASE_URL", "\"$achadosBaseUrl\"")
        buildConfigField("String", "ACHADOS_API_KEY", "\"$achadosApiKey\"")

        // Exporta o schema do Room para versionamento das migrations
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.generateKotlin", "true")
        }
    }

    buildFeatures {
        compose = true        // habilita compilador do Compose
        buildConfig = true    // habilita BuildConfig (AGP 8+ desliga por default)
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
        }
    }
}

dependencies {
    // ── Core & Lifecycle ─────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // ── Jetpack Compose (via BOM — versões alinhadas automaticamente) ─────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // ── Retrofit + OkHttp ────────────────────────────────────────────────────
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging)

    // ── WorkManager ───────────────────────────────────────────────────────────
    implementation(libs.androidx.work.runtime.ktx)

    // ── Coil (carregamento de imagens locais e remotas no Compose) ────────────
    implementation(libs.coil.compose)
}
