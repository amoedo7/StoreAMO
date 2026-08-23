package com.desarrollamo.storeamo

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desarrollamo.storeamo.data.FeedbackRepository
import com.desarrollamo.storeamo.theme.AmoAmber
import com.desarrollamo.storeamo.theme.AmoBackground
import com.desarrollamo.storeamo.theme.AmoCyan
import com.desarrollamo.storeamo.theme.AmoGreen
import com.desarrollamo.storeamo.theme.AmoMuted
import com.desarrollamo.storeamo.theme.AmoPink
import com.desarrollamo.storeamo.theme.AmoSurface
import com.desarrollamo.storeamo.theme.AmoSurface2
import com.desarrollamo.storeamo.theme.AmoText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FeedbackPanelV3(
    appId: String,
    canContribute: Boolean,
    onChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stats by remember(appId) { mutableStateOf<FeedbackRepository.RatingStats?>(null) }
    var comments by remember(appId) { mutableStateOf<List<FeedbackRepository.PublicComment>>(emptyList()) }
    var loading by remember(appId) { mutableStateOf(true) }
    var selectedRating by remember(appId) { mutableIntStateOf(0) }
    var kind by remember(appId) { mutableStateOf("mejora") }
    var body by remember(appId) { mutableStateOf("") }
    var message by remember(appId) { mutableStateOf<String?>(null) }
    var busy by remember(appId) { mutableStateOf(false) }
    var refreshToken by remember(appId) { mutableIntStateOf(0) }

    LaunchedEffect(appId, refreshToken) {
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                FeedbackRepository.fetchSummary(appId) to FeedbackRepository.fetchPublicComments(appId, 3)
            }
        }.onSuccess { (newStats, newComments) ->
            stats = newStats
            comments = newComments
        }.onFailure {
            message = "No pudimos cargar la valoración comunitaria ahora."
        }
        loading = false
    }

    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("COMUNIDAD · AYUDANOS A MEJORAR", color = AmoAmber, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text("Valorá esta aplicación", color = AmoText, fontSize = 20.sp, fontWeight = FontWeight.Black)

            when {
                loading -> Text("Cargando valoraciones reales…", color = AmoCyan, fontSize = 11.sp)
                stats?.ratingCount == 0L -> Text("Todavía no hay valoraciones. La primera puede ser la tuya.", color = AmoMuted, fontSize = 11.sp)
                else -> {
                    val current = stats
                    Text(
                        "★ ${current?.average ?: "—"} · ${current?.ratingCount ?: 0} ${if (current?.ratingCount == 1L) "valoración" else "valoraciones"}",
                        color = AmoAmber,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
            }

            Text(
                "Somos desarrolladores independientes. Si después de probar la aplicación se te ocurre una modificación, una mejora o viste algo que no funciona, dejános tu comentario. Lo leemos para decidir qué mejorar.",
                color = AmoMuted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )

            if (!canContribute) {
                Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface2) {
                    Text(
                        "Instalá y probá la app antes de valorarla o enviar feedback.",
                        color = AmoCyan,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(11.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { value ->
                    Surface(
                        onClick = { if (canContribute && !busy) selectedRating = value },
                        shape = RoundedCornerShape(12.dp),
                        color = if (value <= selectedRating) AmoAmber.copy(alpha = .22f) else AmoSurface2,
                    ) {
                        Text(
                            "★",
                            color = if (value <= selectedRating) AmoAmber else AmoMuted,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (selectedRating !in 1..5 || busy) return@Button
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { FeedbackRepository.submitRating(context, appId, selectedRating) }
                        }.onSuccess {
                            message = "Gracias · tu valoración quedó guardada."
                            refreshToken++
                            onChanged()
                        }.onFailure {
                            message = "No pudimos guardar la valoración. Probá otra vez."
                        }
                        busy = false
                    }
                },
                enabled = canContribute && selectedRating in 1..5 && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = AmoAmber, contentColor = AmoBackground),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enviar valoración", fontWeight = FontWeight.Black) }

            Text("¿Qué querés contarnos?", fontWeight = FontWeight.Black, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FeedbackKindChip("idea", "💡 Idea", kind) { kind = it }
                FeedbackKindChip("mejora", "✨ Mejora", kind) { kind = it }
                FeedbackKindChip("error", "🐛 Algo no funciona", kind) { kind = it }
            }

            TextField(
                value = body,
                onValueChange = { if (it.length <= 800) body = it },
                placeholder = { Text("Contanos qué cambiarías, qué mejorarías o qué falló…") },
                minLines = 3,
                maxLines = 6,
                enabled = canContribute && !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("${body.length}/800 · sin cuenta ni perfil público") },
            )

            OutlinedButton(
                onClick = {
                    val clean = body.trim()
                    if (clean.length !in 5..800 || busy) return@OutlinedButton
                    busy = true
                    message = null
                    scope.launch {
                        runCatching {
                            withContext(Dispatchers.IO) { FeedbackRepository.submitFeedback(context, appId, kind, clean) }
                        }.onSuccess {
                            body = ""
                            message = "Comentario recibido. Lo revisamos antes de mostrarlo públicamente."
                            refreshToken++
                            onChanged()
                        }.onFailure { error ->
                            message = if (error.message?.contains("feedback_rate_limited") == true) {
                                "Esperá un momento antes de enviar otro comentario."
                            } else {
                                "No pudimos enviar el comentario. Probá otra vez."
                            }
                        }
                        busy = false
                    }
                },
                enabled = canContribute && body.trim().length in 5..800 && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Enviar comentario", fontWeight = FontWeight.Black) }

            message?.let { text ->
                Text(
                    text,
                    color = when {
                        text.startsWith("Gracias") || text.startsWith("Comentario recibido") -> AmoGreen
                        text.startsWith("No pudimos") -> AmoPink
                        else -> AmoCyan
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (comments.isNotEmpty()) {
                Text("COMENTARIOS REVISADOS", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                comments.forEach { comment ->
                    Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface2) {
                        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(kindLabel(comment.kind), color = kindColor(comment.kind), fontSize = 9.sp, fontWeight = FontWeight.Black)
                            Text(comment.body, color = AmoText, fontSize = 11.sp, lineHeight = 15.sp)
                        }
                    }
                }
            } else if (!loading) {
                Text("No es un muro: sólo mostramos comentarios útiles después de revisarlos.", color = AmoMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun FeedbackKindChip(value: String, label: String, current: String, onSelect: (String) -> Unit) {
    val selected = value == current
    Surface(
        onClick = { onSelect(value) },
        shape = RoundedCornerShape(99.dp),
        color = if (selected) AmoCyan.copy(alpha = .18f) else AmoSurface2,
    ) {
        Text(
            label,
            color = if (selected) AmoCyan else AmoMuted,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        )
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    "idea" -> "💡 IDEA"
    "error" -> "🐛 ALGO NO FUNCIONA"
    else -> "✨ MEJORA"
}

private fun kindColor(kind: String): Color = when (kind) {
    "idea" -> AmoAmber
    "error" -> AmoPink
    else -> AmoCyan
}
