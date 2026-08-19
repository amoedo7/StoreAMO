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
import com.desarrollamo.storeamo.theme.StoreThemeStyle
import com.desarrollamo.storeamo.util.DownloadInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val SUPPORT_URL = "https://cobramo.netlify.app/"
private const val TERMUX_PACKAGE = "com.termux"

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
    "plataformamo" -> "P"
    "presupuestamo" -> "$"
    "midispositivo" -> "D"
    "mired" -> "R"
    "misistema" -> "M"
    "miweb" -> "W"
    "miarchivos" -> "A"
    "miapi" -> "API"
    "diagnosticoamo" -> "✓"
    else -> app.name.take(2).uppercase()
}

private fun isPackageInstalled(context: Context, packageName: String): Boolean = runCatching {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(packageName, 0)
    true
}.getOrDefault(false)

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreAmoRoot() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }

    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(StoreTab.HOME) }
    var query by remember { mutableStateOf("") }
    var targetPlatform by remember { mutableStateOf(prefs.getString("target_platform", "android") ?: "android") }
    var verifiedOnly by remember { mutableStateOf(prefs.getBoolean("verified_only", true)) }
    var showDevelopment by remember { mutableStateOf(prefs.getBoolean("show_development", true)) }
    var themeStyle by remember { mutableStateOf(StoreThemeStyle.fromKey(prefs.getString("theme_style", StoreThemeStyle.AESTHETIC.key))) }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var showAllPlatforms by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloaded by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloadState by remember { mutableStateOf<String?>(null) }
    val termuxInstalled = remember { isPackageInstalled(context, TERMUX_PACKAGE) }

    fun reload() {
        loading = true
        catalogError = null
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { CatalogRepository.fetch(context) } }
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
                    val ok = withContext(Dispatchers.IO) {
                        DownloadInstaller.verifySha256(current.file, current.artifact.sha256)
                    }
                    if (ok) {
                        downloaded = current
                        downloadState = "Descarga verificada · lista para instalar"
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
            (query.isBlank() || listOf(app.name, app.tagline, app.description, app.category).any {
                it.contains(query, ignoreCase = true)
            })
    }.sortedWith(
        compareByDescending<StoreApp> { it.supportedPlatforms.contains(targetPlatform) }
            .thenByDescending { it.featured }
            .thenBy { it.name }
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(AmoBackground, AmoBackground2, AmoBackground))
        )
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                NavigationBar(
                    containerColor = AmoBackground.copy(alpha = .96f),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
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
                    StatusBanner(downloadState!!, downloaded != null) {
                        val ready = downloaded ?: return@StatusBanner
                        if (!DownloadInstaller.canInstallPackages(context)) {
                            DownloadInstaller.openInstallPermission(context)
                            downloadState = "Permití instalar apps desde StoreAMO y volvé a tocar Instalar."
                        } else {
                            DownloadInstaller.install(context, ready.file)
                        }
                    }
                }

                when (tab) {
                    StoreTab.HOME -> {
                        item { HomeIntro(targetPlatform, loading, catalogError, onRetry = ::reload) }
                        item { SearchBox(query) { query = it } }

                        val publicApps = filtered.filter { it.audience != "team" }
                        val teamApps = filtered.filter { it.audience == "team" }
                        val featured = publicApps.firstOrNull { it.featured && it.supportedPlatforms.contains(targetPlatform) }
                            ?: publicApps.firstOrNull { it.featured }

                        if (featured != null) item {
                            FeaturedCard(featured, targetPlatform) {
                                selected = featured
                                showAllPlatforms = false
                            }
                        }

                        item { SectionHeader("PARA ESTE DISPOSITIVO", "Recomendadas para vos") }
                        items(publicApps.filter { it.supportedPlatforms.contains(targetPlatform) }.take(6), key = { it.id }) { app ->
                            AppRow(app, targetPlatform, verifiedOnly) {
                                selected = app
                                showAllPlatforms = false
                            }
                        }

                        if (teamApps.isNotEmpty()) {
                            item { SectionHeader("EQUIPO DESARROLLAMO", "Espacio de trabajo") }
                            items(teamApps.take(4), key = { "team-${it.id}" }) { app ->
                                AppRow(app, targetPlatform, verifiedOnly) {
                                    selected = app
                                    showAllPlatforms = false
                                }
                            }
                        }

                        if (termuxInstalled) {
                            item { TermuxCard() }
                        }

                        val other = publicApps.filterNot { it.supportedPlatforms.contains(targetPlatform) }.take(4)
                        if (other.isNotEmpty()) {
                            item { SectionHeader("OTROS DISPOSITIVOS", "Más del ecosistema") }
                            items(other, key = { "other-${it.id}" }) { app ->
                                AppRow(app, targetPlatform, verifiedOnly) {
                                    selected = app
                                    showAllPlatforms = true
                                }
                            }
                        }
                    }

                    StoreTab.APPS -> {
                        item { PageHeader("CATÁLOGO", "Aplicaciones", "Primero lo compatible con este dispositivo; el resto queda a un toque.") }
                        item { SearchBox(query) { query = it } }
                        items(filtered, key = { it.id }) { app ->
                            AppRow(app, targetPlatform, verifiedOnly) {
                                selected = app
                                showAllPlatforms = false
                            }
                        }
                    }

                    StoreTab.UPDATES -> {
                        item { PageHeader("VERSIONES", "Actualizaciones", "StoreAMO compara releases verificadas con las versiones instaladas.") }
                        item { EmptyState("↻", "Todo al día por ahora", "Cuando existan releases instalables, acá aparecerán Actualizar y Actualizar todo.") }
                    }

                    StoreTab.LIBRARY -> {
                        item { PageHeader("TU ESPACIO", "Biblioteca", "Descargas verificadas y acciones locales, sin escanear todo tu almacenamiento.") }
                        item {
                            if (downloaded != null) {
                                LibraryDownload(downloaded!!) {
                                    if (!DownloadInstaller.canInstallPackages(context)) {
                                        DownloadInstaller.openInstallPermission(context)
                                    } else {
                                        DownloadInstaller.install(context, downloaded!!.file)
                                    }
                                }
                            } else {
                                EmptyState("▣", "Tu biblioteca está limpia", "Las apps que descargues desde StoreAMO aparecerán acá.")
                            }
                        }
                    }

                    StoreTab.SETTINGS -> {
                        item { PageHeader("STOREAMO", "Ajustes", "Elegí cómo se ve, qué muestra y cómo trabaja la tienda.") }
                        item {
                            SettingsPanel(
                                targetPlatform = targetPlatform,
                                onPlatform = {
                                    targetPlatform = it
                                    prefs.edit().putString("target_platform", it).apply()
                                },
                                themeStyle = themeStyle,
                                onTheme = {
                                    themeStyle = it
                                    prefs.edit().putString("theme_style", it.key).apply()
                                },
                                verifiedOnly = verifiedOnly,
                                onVerified = {
                                    verifiedOnly = it
                                    prefs.edit().putBoolean("verified_only", it).apply()
                                },
                                showDevelopment = showDevelopment,
                                onDevelopment = {
                                    showDevelopment = it
                                    prefs.edit().putBoolean("show_development", it).apply()
                                },
                                termuxInstalled = termuxInstalled,
                                onRefresh = ::reload,
                                onSupport = { openUrl(context, SUPPORT_URL) },
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
                        when {
                            verifiedOnly && !artifact.verified -> downloadState = "Bloqueada · activaste sólo versiones verificadas"
                            !artifact.verified -> downloadState = "Esta release todavía no está verificada por StoreAMO"
                            else -> runCatching { DownloadInstaller.start(context, app.name, artifact) }
                                .onSuccess {
                                    pending = it
                                    downloaded = null
                                    selected = null
                                }
                                .onFailure { downloadState = "No pude iniciar la descarga: ${it.message.orEmpty()}" }
                        }
                    } else {
                        openUrl(context, artifact.url)
                    }
                },
                onRepository = { app.repository?.let { openUrl(context, it) } },
            )
        }
    }
}

@Composable
private fun StoreTopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(62.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text("AMO", color = AmoBackground, fontWeight = FontWeight.Black, fontSize = 10.sp)
            }
            Spacer(Modifier.width(10.dp))
            Text("Store", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("AMO", color = AmoPink, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Surface(onClick = onSettings, shape = CircleShape, color = AmoSurface2, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("⚙", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun HomeIntro(platform: String, loading: Boolean, error: String?, onRetry: () -> Unit) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("DESARROLLAMO · STOREAMO", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        Text("Tu ecosistema,\nen un solo lugar.", fontSize = 42.sp, lineHeight = 42.sp, fontWeight = FontWeight.Black, letterSpacing = (-1.7).sp)
        Text("Estás en ${platformLabel(platform)}. Priorizamos lo compatible y dejamos las demás plataformas detrás de Ver más.", color = AmoMuted)
        if (loading) Text("Actualizando catálogo…", color = AmoCyan, fontSize = 12.sp)
        if (error != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = AmoPink, modifier = Modifier.weight(1f), fontSize = 12.sp)
                TextButton(onClick = onRetry) { Text("Reintentar") }
            }
        }
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
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(AmoSurface2, AmoViolet.copy(alpha = .58f), AmoPink.copy(alpha = .45f))))
                .padding(24.dp)
        ) {
            Column(Modifier.fillMaxWidth(.76f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DESTACADA · ${app.status.uppercase()}", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 10.sp)
                Text(app.name, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Text(app.tagline, color = AmoText.copy(alpha = .84f))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniPill(if (app.supportedPlatforms.contains(platform)) platformLabel(platform) else app.supportedPlatforms.joinToString(" · ") { platformLabel(it) })
                    if (app.status == "verified") MiniPill("✓ Verified", AmoGreen)
                }
                Button(
                    onClick = onOpen,
                    colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground),
                ) { Text("Ver app", fontWeight = FontWeight.Black) }
            }
            Box(
                Modifier.align(Alignment.TopEnd).size(92.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text(appGlyph(app), fontSize = 34.sp, color = AmoBackground, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun SectionHeader(kicker: String, title: String) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
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
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = AmoSurface.copy(alpha = .90f)),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(58.dp).clip(RoundedCornerShape(17.dp))
                    .background(Brush.linearGradient(listOf(AmoSurface2, AmoViolet.copy(alpha = .42f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(appGlyph(app), fontWeight = FontWeight.Black, fontSize = if (app.id == "miapi") 13.sp else 21.sp)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.audience == "team") Text("EQUIPO", color = AmoCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Text(app.tagline, color = AmoMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${app.category} · ${if (app.supportedPlatforms.contains(platform)) platformLabel(platform) else app.supportedPlatforms.joinToString(" · ") { platformLabel(it) }}",
                    color = AmoMuted.copy(alpha = .78f),
                    fontSize = 10.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpen) {
                Text(
                    when {
                        artifact != null -> "Obtener"
                        app.supportedPlatforms.contains(platform) -> "Próximamente"
                        else -> "Ver más"
                    },
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun TermuxCard() {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TERMUX DETECTADO", color = AmoGreen, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Scripts AMO", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("StoreAMO podrá mostrar scripts compatibles, explicar qué hacen y ejecutarlos en Termux sólo después de verificar origen e integridad.", color = AmoMuted, fontSize = 12.sp)
            MiniPill("Galería en preparación", AmoCyan)
        }
    }
}

@Composable
private fun MiniPill(text: String, color: Color = AmoText) {
    Surface(shape = RoundedCornerShape(50), color = AmoSurface2) {
        Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun StatusBanner(text: String, canInstall: Boolean, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2, tonalElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = if (text.contains("Bloqueada") || text.contains("falló")) AmoPink else AmoCyan,
                fontSize = 12.sp,
            )
            if (canInstall) Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun EmptyState(glyph: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            Column(Modifier.weight(1f)) {
                Text(pending.file.name, fontWeight = FontWeight.Bold)
                Text("SHA-256 verificado", color = AmoGreen, fontSize = 12.sp)
            }
            Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun SettingsPanel(
    targetPlatform: String,
    onPlatform: (String) -> Unit,
    themeStyle: StoreThemeStyle,
    onTheme: (StoreThemeStyle) -> Unit,
    verifiedOnly: Boolean,
    onVerified: (Boolean) -> Unit,
    showDevelopment: Boolean,
    onDevelopment: (Boolean) -> Unit,
    termuxInstalled: Boolean,
    onRefresh: () -> Unit,
    onSupport: () -> Unit,
) {
    val platforms = listOf("android", "windows", "macos", "linux", "web")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Estilo visual", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Tu StoreAMO, con el mismo ecosistema y una apariencia que se adapte a vos.", color = AmoMuted, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    StoreThemeStyle.entries.forEach { style ->
                        val selected = style == themeStyle
                        Button(
                            onClick = { onTheme(style) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) AmoCyan else AmoSurface2,
                                contentColor = if (selected) AmoBackground else AmoText,
                            ),
                        ) {
                            Text(if (selected) "✓ ${style.label}" else style.label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Dispositivo", fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("Android es el dispositivo actual; también podés explorar otras plataformas.", color = AmoMuted, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    platforms.forEach { p ->
                        OutlinedButton(
                            onClick = { onPlatform(p) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = if (p == targetPlatform) AmoCyan else AmoText),
                        ) {
                            Text(if (p == targetPlatform) "✓ ${platformLabel(p)}" else platformLabel(p), fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        SettingCard("Solo versiones verificadas", "Bloquea descargas sin evidencia suficiente.") {
            Switch(checked = verifiedOnly, onCheckedChange = onVerified)
        }
        SettingCard("Mostrar en desarrollo", "Muestra futuras apps aunque todavía no tengan una release instalable.") {
            Switch(checked = showDevelopment, onCheckedChange = onDevelopment)
        }
        SettingCard("Termux", if (termuxInstalled) "Detectado · se habilitará la galería de scripts AMO." else "No detectado · StoreAMO no intenta instalarlo ni escanear otras apps.") {
            Text(if (termuxInstalled) "Detectado" else "No", color = if (termuxInstalled) AmoGreen else AmoMuted, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        SettingCard("Actualizar catálogo", "Busca nuevas apps y releases sin actualizar StoreAMO.") {
            TextButton(onClick = onRefresh) { Text("Actualizar") }
        }
        SettingCard("Apoyar DesarrollAMO", "El software puede seguir siendo accesible mientras habilitamos una forma voluntaria de apoyar el proyecto.") {
            Button(onClick = onSupport, colors = ButtonDefaults.buttonColors(containerColor = AmoPink, contentColor = AmoBackground)) {
                Text("Apoyar", fontWeight = FontWeight.Black, fontSize = 11.sp)
            }
        }
        SettingCard("Privacidad", "Sin MANAGE_EXTERNAL_STORAGE ni QUERY_ALL_PACKAGES. HTTPS y catálogo verificable.") {
            Text("Local", color = AmoGreen, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
        SettingCard("Instalación", "Android conserva su confirmación de seguridad cuando corresponda.") {
            Text("Seguro", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SettingCard(title: String, body: String, action: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Black)
                Text(body, color = AmoMuted, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

@Composable
private fun AppSheet(
    app: StoreApp,
    platform: String,
    verifiedOnly: Boolean,
    showAll: Boolean,
    onToggleAll: () -> Unit,
    onArtifact: (StoreArtifact) -> Unit,
    onRepository: () -> Unit,
) {
    val current = app.artifacts.firstOrNull { it.platform == platform }
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(68.dp).clip(RoundedCornerShape(20.dp))
                    .background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))),
                contentAlignment = Alignment.Center,
            ) {
                Text(appGlyph(app), color = AmoBackground, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (app.audience == "team") "EQUIPO DESARROLLAMO" else app.category.uppercase(), color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(app.name, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(
                    if (app.status == "verified") "✓ StoreAMO Verified" else app.status,
                    color = if (app.status == "verified") AmoGreen else AmoMuted,
                    fontSize = 11.sp,
                )
            }
        }

        Text(app.description, color = AmoMuted)
        Text("Para este dispositivo", fontWeight = FontWeight.Black)
        if (current != null) {
            ArtifactRow(current, platform == "android", verifiedOnly, onArtifact)
        } else {
            EmptyState(
                "○",
                if (app.supportedPlatforms.contains(platform)) "Todavía no hay release" else "No hay versión para ${platformLabel(platform)}",
                if (app.supportedPlatforms.contains(platform)) "Está anunciada, pero StoreAMO no muestra un botón de instalación hasta tener un artefacto verificable." else "Usá Ver más dispositivos para mirar otras plataformas.",
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onToggleAll) { Text(if (showAll) "Ocultar otras" else "Ver más dispositivos") }
            if (app.repository != null) OutlinedButton(onClick = onRepository) { Text("Código") }
        }

        if (showAll) {
            HorizontalDivider(color = AmoLine)
            Text("Otros dispositivos", fontWeight = FontWeight.Black)
            app.supportedPlatforms.distinct().forEach { p ->
                val artifact = app.artifacts.firstOrNull { it.platform == p }
                if (artifact != null) {
                    ArtifactRow(artifact, false, verifiedOnly, onArtifact)
                } else {
                    Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface) {
                        Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(platformLabel(p), fontWeight = FontWeight.Bold)
                            Text("Próximamente", color = AmoMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun ArtifactRow(
    artifact: StoreArtifact,
    installable: Boolean,
    verifiedOnly: Boolean,
    onArtifact: (StoreArtifact) -> Unit,
) {
    Surface(shape = RoundedCornerShape(15.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(platformLabel(artifact.platform), fontWeight = FontWeight.Black)
                Text(
                    "${artifact.version}${artifact.format?.let { " · $it" }.orEmpty()} · ${if (artifact.verified) "verificada" else "sin verificar"}",
                    color = if (artifact.verified) AmoGreen else AmoMuted,
                    fontSize = 11.sp,
                )
            }
            Button(
                onClick = { onArtifact(artifact) },
                enabled = !(verifiedOnly && !artifact.verified),
                colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground),
            ) {
                Text(if (installable) "Obtener" else "Abrir", fontWeight = FontWeight.Black)
            }
        }
    }
}
