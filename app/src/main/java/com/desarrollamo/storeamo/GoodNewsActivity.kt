package com.desarrollamo.storeamo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    LaunchedEffect(refreshToken) {
        loading = true
        error = null
        runCatching { withContext(Dispatchers.IO) { NewsRepository.fetch() } }
            .onSuccess { items = it }
            .onFailure { error = "No pude cargar Buenas Nuevas: ${it.message.orEmpty()}" }
        loading = false
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
                        "Cambios, nuevas versiones y aplicaciones que están avanzando. Sin tener que entrar a GitHub.",
                        color = AmoMuted,
                        fontSize = 16.sp,
                        lineHeight = 23.sp,
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
            } else {
                items(items, key = { it.id }) { item -> GoodNewsCard(item) }
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
            if (item.sourceVisibility == "private") {
                Text("Actividad pública sanitizada", color = AmoGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
private fun StatusNewsCard(title: String, body: String) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = AmoSurface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = AmoText, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text(body, color = AmoMuted, lineHeight = 20.sp)
        }
    }
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
