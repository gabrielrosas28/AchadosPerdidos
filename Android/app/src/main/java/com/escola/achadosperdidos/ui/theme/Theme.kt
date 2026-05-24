package com.escola.achadosperdidos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Esquema único (claro). O app é exposto em horário comercial num tablet de
 * escola — não faz sentido seguir o dark mode do sistema.
 */
private val EsquemaCelesteClaro = lightColorScheme(
    primary             = AzulCelesteEscuro,
    onPrimary           = Branco,
    primaryContainer    = AzulCeleste,
    onPrimaryContainer  = Branco,
    secondary           = AzulCeleste,
    onSecondary         = Branco,
    secondaryContainer  = AzulCelesteClaro,
    onSecondaryContainer = CinzaTexto,
    error               = VermelhoAlerta,
    onError             = Branco,
    errorContainer      = VermelhoClaro,
    onErrorContainer    = VermelhoAlerta,
    background          = AzulCelesteClaro,
    onBackground        = CinzaTexto,
    surface             = Branco,
    onSurface           = CinzaTexto,
    surfaceVariant      = BrancoQuente,
    onSurfaceVariant    = CinzaSecundario,
    outline             = CinzaClaro
)

@Composable
fun AchadosPerdidosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaCelesteClaro,
        typography  = TipografiaTablet,
        content     = content
    )
}
