package com.desarrollamo.storeamo

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desarrollamo.storeamo.data.CatalogRepository
import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact
import com.desarrollamo.storeamo.model.StoreTab
import com.desarrollamo.storeamo.theme.AmoBackground
import com.desarrollamo.storeamo.theme.AmoBackground2
import com.desarrollamo.storeamo.theme.AmoCyan
import com.desarrollamo.storeamo.theme.AmoGreen
import com.desarrollamo.storeamo.theme.AmoLine
import com.desarrollamo.storeamo.theme.AmoMuted
import com.desarrollamo.storeamo.theme.AmoPink
import com.desarrollamo.storeamo.theme.AmoSurface
import com.desarrollamo.storeamo.theme.AmoSurface2
import com.desarrollamo.storeamo.theme.AmoText
import com.desarrollamo.storeamo.theme.AmoViolet
import com.desarrollamo.storeamo.theme.StoreAmoTheme
import com.desarrollamo.storeamo.util.DownloadInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StoreAmoTheme { StoreAmoRoot() } }
    }
}

private fun platformLabel(id: String): String = when (id) {
    "android" -> "Android"
    "windows" -> "Windows"
    "macos" -> "macOS"
    "linux" -> "Linux"
    "web" -> "Web"
    "ios" -> "iPhone / iPad"
    else -> id.replaceFirstChar { it.uppercase() }
}

private fun appGlyph(app: StoreApp): String = when (app.id) {
    "chessi" -> "♟"
    "storeamo" -> "S"
    "midispositivo" -> "D"
    "mired" -> "R"
    "misistema" -> "M"
    "miweb" -> "W"
    "miarchivos" -> "A"
    "miapi" -> "API"
    "diagnosticoamo" -> "✓"
    else -> app.name.take(2).uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreAmoRoot() {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(StoreTab.HOME) }
    var query by remember { mutableStateOf("") }
    val prefs = remember { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }
    var targetPlatform by remember { mutableStateOf(prefs.getString("target_platform", "android") ?: "android") }
    var verifiedOnly by remember { mutableStateOf(prefs.getBoolean("verified_only", true)) }
    var showDevelopment by remember { mutableStateOf(prefs.getBoolean("show_development", true)) }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var showAllPlatforms by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloaded by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloadState by remember { mutableStateOf<String?>(null) }

    fun reload() {
        loading = true
        catalogError = null
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { CatalogRepository.fetch() } }
            .onSuccess { apps = it.apps; catalogError = null }
            .onFailure { catalogError = "No pude actualizar el catálogo. ${it.message.orEmpty()}" }
        loading = false
    }

    LaunchedEffect(pending?.id) {
        val current = pending ?: return@LaunchedEffect
        downloadState = "Descargando ${current.artifact.version}…"
        while (true) {
            when (DownloadInstaller.status(context, current.id)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val ok = withContext(Dispatchers.IO) { DownloadInstaller.verifySha256(current.file, current.artifact.sha256) }
                    if (ok) {
                        downloaded = current
                        downloadState = "Descarga verificada · SHA-256 correcto"
                    } else {
                        current.file.delete()
                        downloadState = "Bloqueada · el SHA-256 no coincide"
                    }
                    pending = null
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    downloadState = "La descarga falló"
                    pending = null
                    break
                }
                else -> delay(900)
            }
        }
    }

    val filtered = apps.filter { app ->
        (showDevelopment || app.status != "development") &&
            (query.isBlank() || listOf(app.name, app.tagline, app.description, app.category).any { it.contains(query, ignoreCase = true) })
    }.sortedWith(compareByDescending<StoreApp> { it.supportedPlatforms.contains(targetPlatform) }.thenByDescending { it.featured }.thenBy { it.name })

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AmoBackground, AmoBackground2, AmoBackground))
        )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = AmoBackground.copy(alpha = .96f), modifier = Modifier.navigationBarsPadding()) {
                    StoreTab.entries.forEach { item ->
                        NavigationBarItem(
                            selected = tab == item,
                            onClick = { tab = item },
                            icon = { Text(item.glyph, fontWeight = FontWeight.Black) },
                            label = { Text(item.label, fontSize = 9.sp, maxLines = 1) },
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { StoreTopBar(onSettings = { tab = StoreTab.SETTINGS }) }
                if (downloadState != null) item {
                    StatusBanner(downloadState!!, downloaded != null, onInstall = {
                        val ready = downloaded ?: return@StatusBanner
                        if (!DownloadInstaller.canInstallPackages(context)) {
                            DownloadInstaller.openInstallPermission(context)
                            downloadState = "Permití instalar apps desconocidas para StoreAMO y luego tocá Instalar otra vez."
                        } else {
                            DownloadInstaller.install(context, ready.file)
                        }
                    })
                }
                when (tab) {
                    StoreTab.HOME -> {
                        item { HomeIntro(targetPlatform, loading, catalogError, onRetry = ::reload) }
                        item { SearchBox(query) { query = it } }
                        val featured = filtered.firstOrNull { it.featured && it.supportedPlatforms.contains(targetPlatform) }
                            ?: filtered.firstOrNull { it.featured }
                        if (featured != null) item { FeaturedCard(featured, targetPlatform) { selected = featured; showAllPlatforms = false } }
                        item { SectionHeader("PARA ESTE DISPOSITIVO", "Recomendadas para vos") }
                        items(filtered.filter { it.supportedPlatforms.contains(targetPlatform) }.take(6), key = { it.id }) { app ->
                            AppRow(app, targetPlatform, verifiedOnly) { selected = app; showAllPlatforms = false }
                        }
                        val other = filtered.filterNot { it.supportedPlatforms.contains(targetPlatform) }.take(4)
                        if (other.isNotEmpty()) {
                            item { SectionHeader("ECOSISTEMA", "Más de DesarrollAMO") }
                            items(other, key = { "other-${it.id}" }) { app -> AppRow(app, targetPlatform, verifiedOnly) { selected = app; showAllPlatforms = true } }
                        }
                    }
                    StoreTab.APPS -> {
                        item { PageHeader("CATÁLOGO", "Aplicaciones", "Compatible primero; el resto sigue disponible con Ver más.") }
                        item { SearchBox(query) { query = it } }
                        items(filtered, key = { it.id }) { app -> AppRow(app, targetPlatform, verifiedOnly) { selected = app; showAllPlatforms = false } }
                    }
                    StoreTab.UPDATES -> {
                        item { PageHeader("VERSIONES", "Actualizaciones", "Las releases verificadas aparecerán acá cuando podamos compararlas con una instalación conocida.") }
                        item { EmptyState("↻", "Nada que actualizar todavía", "No vamos a publicar APK viejos sólo para llenar esta pantalla.") }
                    }
                    StoreTab.LIBRARY -> {
                        item { PageHeader("TU ESPACIO", "Biblioteca", "Descargas verificadas de StoreAMO y acciones locales.") }
                        item {
                            if (downloaded != null) {
                                LibraryDownload(downloaded!!) {
                                    if (!DownloadInstaller.canInstallPackages(context)) DownloadInstaller.openInstallPermission(context)
                                    else DownloadInstaller.install(context, downloaded!!.file)
                                }
                            } else EmptyState("▣", "Tu biblioteca está limpia", "Una descarga verificada aparecerá acá sin escanear todo el almacenamiento.")
                        }
                    }
                    StoreTab.SETTINGS -> {
                        item { PageHeader("STOREAMO", "Configuración", "La tienda adapta el catálogo sin perder el control manual.") }
                        item {
                            SettingsPanel(
                                targetPlatform = targetPlatform,
                                onPlatform = { targetPlatform = it; prefs.edit().putString("target_platform", it).apply() },
                                verifiedOnly = verifiedOnly,
                                onVerified = { verifiedOnly = it; prefs.edit().putBoolean("verified_only", it).apply() },
                                showDevelopment = showDevelopment,
                                onDevelopment = { showDevelopment = it; prefs.edit().putBoolean("show_development", it).apply() },
                                onRefresh = ::reload,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(22.dp)) }
            }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            containerColor = AmoBackground2,
            contentColor = AmoText,
        ) {
            AppSheet(
                app = app,
                platform = targetPlatform,
                verifiedOnly = verifiedOnly,
                showAll = showAllPlatforms,
                onToggleAll = { showAllPlatforms = !showAllPlatforms },
                onArtifact = { artifact ->
                    if (artifact.platform == "android") {
                        if (verifiedOnly && !artifact.verified) {
                            downloadState = "Bloqueada · activaste sólo versiones verificadas"
                        } else if (!artifact.verified) {
                            downloadState = "Esta release todavía no está verificada por StoreAMO"
                        } else {
                            runCatching { DownloadInstaller.start(context, app.name, artifact) }
                                .onSuccess { pending = it; downloaded = null }
                                .onFailure { downloadState = "No pude iniciar la descarga: ${it.message.orEmpty()}" }
                        }
                    } else {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(artifact.url)))
                    }
                },
                onRepository = {
                    app.repository?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                }
            )
        }
    }
}

@Composable
private fun StoreTopBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) {
                Text("AMO", color = AmoBackground, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("Store", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("AMO", color = AmoPink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Surface(onClick = onSettings, shape = CircleShape, color = AmoSurface2, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("A", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun HomeIntro(platform: String, loading: Boolean, error: String?, onRetry: () -> Unit) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DESARROLLAMO · CATÁLOGO OFICIAL", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text("Apps hechas para\ntu dispositivo.", fontSize = 44.sp, lineHeight = 43.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.8).sp)
        Text("Detectamos Android. Mostramos primero ${platformLabel(platform)} y escondemos el resto detrás de Ver más.", color = AmoMuted)
        if (loading) Text("Actualizando catálogo…", color = AmoCyan, fontSize = 12.sp)
        if (error != null) Row(verticalAlignment = Alignment.CenterVertically) { Text(error, color = AmoPink, modifier = Modifier.weight(1f), fontSize = 12.sp); TextButton(onClick = onRetry) { Text("Reintentar") } }
    }
}

@Composable
private fun SearchBox(value: String, onValue: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValue,
        placeholder = { Text("Buscar apps") },
        leadingIcon = { Text("⌕", fontSize = 24.sp) },
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FeaturedCard(app: StoreApp, platform: String, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1A516F), Color(0xFF272055), Color(0xFF60234F)))).padding(24.dp)) {
            Column(Modifier.fillMaxWidth(.78f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DESTACADA · ${app.status.uppercase()}", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text(app.name, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text(app.tagline, color = AmoText.copy(alpha = .82f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniPill(if (app.supportedPlatforms.contains(platform)) platformLabel(platform) else app.supportedPlatforms.joinToString(" · ") { platformLabel(it) })
                    if (app.status == "verified") MiniPill("✓ StoreAMO Verified", AmoGreen)
                }
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text("Ver app", fontWeight = FontWeight.Black) }
            }
            Box(Modifier.align(Alignment.TopEnd).size(96.dp).clip(RoundedCornerShape(30.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) {
                Text(appGlyph(app), fontSize = 36.sp, color = AmoBackground, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SectionHeader(kicker: String, title: String) {
    Column(Modifier.padding(top = 12.dp)) { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun PageHeader(kicker: String, title: String, body: String) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text(title, fontSize = 38.sp, fontWeight = FontWeight.Black)
        Text(body, color = AmoMuted)
    }
}

@Composable
private fun AppRow(app: StoreApp, platform: String, verifiedOnly: Boolean, onOpen: () -> Unit) {
    val artifact = app.artifacts.firstOrNull { it.platform == platform && (!verifiedOnly || it.verified) }
    Card(onClick = onOpen, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = AmoSurface.copy(alpha = .88f))) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF12314D), Color(0xFF4A2847)))), contentAlignment = Alignment.Center) {
                Text(appGlyph(app), fontWeight = FontWeight.Black, fontSize = if (app.id == "miapi") 13.sp else 21.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.tagline, color = AmoMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${app.category} · ${if (app.supportedPlatforms.contains(platform)) platformLabel(platform) else app.supportedPlatforms.joinToString(" · ") { platformLabel(it) }}", color = AmoMuted.copy(alpha = .75f), fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpen) { Text(if (artifact != null) "Obtener" else if (app.supportedPlatforms.contains(platform)) "Próximamente" else "Ver más", fontSize = 11.sp) }
        }
    }
}

@Composable
private fun MiniPill(text: String, color: Color = AmoText) {
    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .08f)) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) }
}

@Composable
private fun StatusBanner(text: String, canInstall: Boolean, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), color = if (text.contains("Bloqueada") || text.contains("falló")) AmoPink else AmoCyan, fontSize = 12.sp)
            if (canInstall) Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun EmptyState(glyph: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(glyph, fontSize = 34.sp, color = AmoCyan)
            Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Text(body, color = AmoMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun LibraryDownload(pending: DownloadInstaller.Pending, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(pending.file.name, fontWeight = FontWeight.Bold); Text("SHA-256 verificado", color = AmoGreen, fontSize = 12.sp) }
            Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun SettingsPanel(targetPlatform: String, onPlatform: (String) -> Unit, verifiedOnly: Boolean, onVerified: (Boolean) -> Unit, showDevelopment: Boolean, onDevelopment: (Boolean) -> Unit, onRefresh: () -> Unit) {
    val platforms = listOf("android", "windows", "macos", "linux", "web")
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        SettingCard("Dispositivo", "Android es automático en esta app. Podés mirar el catálogo de otra plataforma.") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                platforms.forEach { p -> OutlinedButton(onClick = { onPlatform(p) }, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (p == targetPlatform) AmoCyan else AmoText)) { Text(platformLabel(p), fontSize = 10.sp) } }
            }
        }
        SettingCard("Solo versiones verificadas", "Bloquea descargas sin evidencia suficiente.") { Switch(checked = verifiedOnly, onCheckedChange = onVerified) }
        SettingCard("Mostrar en desarrollo", "Permite ver futuras apps aunque todavía no tengan release.") { Switch(checked = showDevelopment, onCheckedChange = onDevelopment) }
        SettingCard("Actualizar catálogo", CatalogRepository.CATALOG_URL) { TextButton(onClick = onRefresh) { Text("Actualizar") } }
        SettingCard("Privacidad", "Sin escaneo masivo, sin MANAGE_EXTERNAL_STORAGE y sin QUERY_ALL_PACKAGES.") { Text("Local", color = AmoGreen, fontWeight = FontWeight.Black, fontSize = 11.sp) }
        SettingCard("Instalación", "Android siempre muestra su confirmación normal al instalar un APK.") { Text("Seguro", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 11.sp) }
    }
}

@Composable
private fun SettingCard(title: String, body: String, action: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.width(12.dp)); action()
        }
    }
}

@Composable
private fun AppSheet(app: StoreApp, platform: String, verifiedOnly: Boolean, showAll: Boolean, onToggleAll: () -> Unit, onArtifact: (StoreArtifact) -> Unit, onRepository: () -> Unit) {
    val current = app.artifacts.firstOrNull { it.platform == platform }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) { Text(appGlyph(app), color = AmoBackground, fontSize = 26.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(app.category.uppercase(), color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(app.name, fontSize = 28.sp, fontWeight = FontWeight.Black); Text(if (app.status == "verified") "✓ StoreAMO Verified" else app.status, color = if (app.status == "verified") AmoGreen else AmoMuted, fontSize = 11.sp) }
        }
        Text(app.description, color = AmoMuted)
        Text("Para este dispositivo", fontWeight = FontWeight.Black)
        if (current != null) ArtifactRow(current, platform == "android", verifiedOnly, onArtifact)
        else EmptyState("○", if (app.supportedPlatforms.contains(platform)) "Todavía no hay release" else "No hay versión para ${platformLabel(platform)}", if (app.supportedPlatforms.contains(platform)) "Está prevista en el catálogo, pero no publicamos un binario hasta verificarlo." else "Usá Ver más para mirar otras plataformas.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleAll) { Text(if (showAll) "Ocultar otras" else "Ver más dispositivos") }
            if (app.repository != null) OutlinedButton(onClick = onRepository) { Text("Código") }
        }
        if (showAll) {
            HorizontalDivider(color = AmoLine)
            Text("Otros dispositivos", fontWeight = FontWeight.Black)
            app.supportedPlatforms.distinct().forEach { p ->
                val a = app.artifacts.firstOrNull { it.platform == p }
                if (a != null) ArtifactRow(a, false, verifiedOnly, onArtifact)
                else Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface) { Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(platformLabel(p), fontWeight = FontWeight.Bold); Text("Próximamente", color = AmoMuted, fontSize = 11.sp) } }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ArtifactRow(a: StoreArtifact, installable: Boolean, verifiedOnly: Boolean, onArtifact: (StoreArtifact) -> Unit) {
    Surface(shape = RoundedCornerShape(15.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(platformLabel(a.platform), fontWeight = FontWeight.Black)
                Text("${a.version}${a.format?.let { " · $it" }.orEmpty()} · ${if (a.verified) "verificada" else "sin verificar"}", color = if (a.verified) AmoGreen else AmoMuted, fontSize = 11.sp)
            }
            Button(onClick = { onArtifact(a) }, enabled = !(verifiedOnly && !a.verified), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) {
                Text(if (installable) "Obtener" else "Abrir", fontWeight = FontWeight.Black)
            }
        }
    }
}
