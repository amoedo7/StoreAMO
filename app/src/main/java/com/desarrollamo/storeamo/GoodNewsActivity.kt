package com.desarrollamo.storeamo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desarrollamo.storeamo.data.NewsRepository
import com.desarrollamo.storeamo.model.StoreNewsItem
import com.desarrollamo.storeamo.theme.AmoAmber
import com.desarrollamo.storeamo.theme.AmoBackground
import com.desarrollamo.storeamo.theme.AmoCyan
import com.desarrollamo.storeamo.theme.AmoGreen
import com.desarrollamo.storeamo.theme.AmoMuted
import com.desarrollamo.storeamo.theme.AmoPink
import com.desarrollamo.storeamo.theme.AmoSurface
import com.desarrollamo.storeamo.theme.AmoSurface2
import com.desarrollamo.storeamo.theme.AmoText
import com.desarrollamo.storeamo.theme.StoreAmoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private enum class NewsWindow(val label: String, val maxHours: Long?) {
    TODAY("Hoy", 24),
    WEEK("7 días", 24 * 7),
    MONTH("30 días", 24 * 30),
    ALL("Todo", null),
}

private enum class NewsKind(val label: String) {
    ALL("Todas"),
    DEVELOPMENT("En desarrollo"),
    PUBLISHED("Publicadas"),
    IMPROVEMENTS("Mejoras"),
}

class GoodNewsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StoreAmoTheme {
                GoodNewsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
private fun GoodNewsScreen(onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<StoreNewsItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableIntStateOf(0) }
    var window by remember { mutableStateOf(NewsWindow.WEEK) }
    var selectedApp by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(NewsKind.ALL) }

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { NewsRepository.fetch() } }
            .onSuccess { items = it }
            .onFailure { error = "No pude cargar Buenas Nuevas: ${it.message.orEmpty()}" }
        loading = false
    }

    val appNames = remember(items) { items.map { it.appName }.distinct().sorted() }
    val filteredItems = items.filter { item ->
        isInsideWindow(item.publishedAt, window) &&
            (selectedApp == null || item.appName == selectedApp) &&
            matchesKind(item, kind)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = AmoBackground) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("← Volver", color = AmoCyan) }
                    Spacer(Modifier.weight(1f))
                    Text("StoreAMO", color = AmoMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ECOSISTEMA EN MOVIMIENTO", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 11.sp)
                    Text("Buenas Nuevas", color = AmoText, fontWeight = FontWeight.Black, fontSize = 38.sp)
                    Text(
                        "Cambios, nuevas versiones y aplicaciones que están avanzando. Ahora podés mirar sólo lo que te interesa.",
                        color = AmoMuted,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
                    )
                }
            }

            if (!loading && error == null && items.isNotEmpty()) {
                item {
                    NewsFilters(
                        window = window,
                        onWindow = { window = it },
                        appNames = appNames,
                        selectedApp = selectedApp,
                        onApp = { selectedApp = it },
                        kind = kind,
                        onKind = { kind = it },
                        resultCount = filteredItems.size,
                    )
                }
            }

            if (loading) {
                item { StatusNewsCard("Buscando novedades…", "StoreAMO está consultando la actividad pública más reciente.") }
            } else if (error != null) {
                item {
                    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmoSurface)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("No pudimos actualizar ahora", color = AmoText, fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text(error.orEmpty(), color = AmoMuted)
                            Button(onClick = { refreshToken++ }) { Text("Reintentar") }
                        }
                    }
                }
            } else if (items.isEmpty()) {
                item { StatusNewsCard("Todavía no hay novedades", "Cuando una aplicación avance, aparezca una versión o cambie su estado, lo vas a ver acá.") }
            } else if (filteredItems.isEmpty()) {
                item { StatusNewsCard("No hay noticias con esos filtros", "Probá ampliar el período, elegir otra app o volver a Todas.") }
            } else {
                items(filteredItems, key = { it.id }) { item -> GoodNewsCard(item) }
                item {
                    Text(
                        "Los repositorios privados sólo muestran actividad sanitizada. StoreAMO nunca publica su código ni mensajes privados de commit.",
                        color = AmoMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewsFilters(
    window: NewsWindow,
    onWindow: (NewsWindow) -> Unit,
    appNames: List<String>,
    selectedApp: String?,
    onApp: (String?) -> Unit,
    kind: NewsKind,
    onKind: (NewsKind) -> Unit,
    resultCount: Int,
) {
    Surface(shape = RoundedCornerShape(24.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("FILTRAR BUENAS NUEVAS", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    Text("Encontrá lo importante sin perderte en el feed.", color = AmoMuted, fontSize = 11.sp)
                }
                Surface(shape = RoundedCornerShape(99.dp), color = AmoAmber.copy(alpha = .15f)) {
                    Text("$resultCount", color = AmoAmber, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }

            FilterRowLabel("TIEMPO")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NewsWindow.entries.forEach { option ->
                    FilterChip(
                        selected = window == option,
                        onClick = { onWindow(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoAmber.copy(alpha = .18f),
                            selectedLabelColor = AmoAmber,
                        ),
                    )
                }
            }

            FilterRowLabel("APP")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FilterChip(
                    selected = selectedApp == null,
                    onClick = { onApp(null) },
                    label = { Text("Todas") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AmoAmber.copy(alpha = .18f),
                        selectedLabelColor = AmoAmber,
                    ),
                )
                appNames.forEach { appName ->
                    FilterChip(
                        selected = selectedApp == appName,
                        onClick = { onApp(appName) },
                        label = { Text(appName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoAmber.copy(alpha = .18f),
                            selectedLabelColor = AmoAmber,
                        ),
                    )
                }
            }

            FilterRowLabel("TIPO")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                NewsKind.entries.forEach { option ->
                    FilterChip(
                        selected = kind == option,
                        onClick = { onKind(option) },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AmoAmber.copy(alpha = .18f),
                            selectedLabelColor = AmoAmber,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRowLabel(text: String) {
    Text(text, color = AmoMuted, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
}

@Composable
private fun GoodNewsCard(item: StoreNewsItem) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmoSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.appName, color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                StatusPill(item.status)
                Spacer(Modifier.weight(1f))
                Text(relativeTime(item.publishedAt), color = AmoMuted, fontSize = 11.sp)
            }
            Text(item.title, color = AmoText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            if (item.summary.isNotBlank()) {
                Text(item.summary, color = AmoMuted, fontSize = 14.sp, lineHeight = 20.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NewsTypePill(item.type)
                if (item.sourceVisibility == "private") {
                    Spacer(Modifier.width(8.dp))
                    Text("Actividad pública sanitizada", color = AmoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val label = when (status) {
        "candidate" -> "CANDIDATE"
        "verified" -> "VERIFIED"
        "development" -> "EN DESARROLLO"
        else -> status.uppercase()
    }
    Surface(shape = RoundedCornerShape(99.dp), color = AmoSurface2) {
        Text(label, color = if (status == "verified") AmoGreen else AmoPink, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun NewsTypePill(type: String) {
    Surface(shape = RoundedCornerShape(99.dp), color = AmoAmber.copy(alpha = .12f)) {
        Text(newsTypeLabel(type), color = AmoAmber, fontSize = 9.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun StatusNewsCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmoSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = AmoText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(body, color = AmoMuted, lineHeight = 20.sp)
        }
    }
}

private fun isInsideWindow(value: String, window: NewsWindow): Boolean {
    val maxHours = window.maxHours ?: return true
    return runCatching {
        val elapsed = Duration.between(Instant.parse(value), Instant.now()).toHours()
        elapsed in 0..maxHours
    }.getOrDefault(true)
}

private fun matchesKind(item: StoreNewsItem, kind: NewsKind): Boolean {
    if (kind == NewsKind.ALL) return true
    val text = "${item.type} ${item.title} ${item.summary}".lowercase()
    return when (kind) {
        NewsKind.ALL -> true
        NewsKind.DEVELOPMENT -> item.status == "development"
        NewsKind.PUBLISHED -> item.status == "candidate" || item.status == "verified" || text.contains("release") || text.contains("publicad")
        NewsKind.IMPROVEMENTS -> text.contains("mejora") || text.contains("improvement") || text.contains("enhancement") || text.contains("feature") || text.contains("fix")
    }
}

private fun newsTypeLabel(type: String): String = when (type.lowercase()) {
    "release" -> "VERSIÓN"
    "improvement", "enhancement" -> "MEJORA"
    "app", "new_app" -> "NUEVA APP"
    "status" -> "ESTADO"
    else -> type.replace('_', ' ').uppercase()
}

private fun relativeTime(value: String): String {
    return runCatching {
        val duration = Duration.between(Instant.parse(value), Instant.now())
        val minutes = duration.toMinutes().coerceAtLeast(0)
        when {
            minutes < 1 -> "ahora"
            minutes < 60 -> "hace ${minutes} min"
            minutes < 1_440 -> "hace ${minutes / 60} h"
            minutes < 10_080 -> "hace ${minutes / 1_440} d"
            else -> "hace ${minutes / 10_080} sem"
        }
    }.getOrDefault("")
}
