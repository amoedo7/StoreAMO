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
import com.desarrollamo.storeamo.theme.AmoBackground
import com.desarrollamo.storeamo.theme.AmoCyan
import com.desarrollamo.storeamo.theme.AmoGreen
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

private const val SUPPORT_URL_V2 = "https://cobramo.netlify.app/"
private const val TERMUX_PACKAGE_V2 = "com.termux"
private const val TERMUX_FDROID = "https://f-droid.org/packages/com.termux"
private const val TERMUX_PLAY = "https://play.google.com/store/apps/details?id=com.termux"

enum class StoreTabV2(val label: String, val glyph: String) {
    HOME("Inicio", "◆"),
    APPS("Apps", "▦"),
    UPDATES("Actualiz.", "↻"),
    SETTINGS("Ajustes", "⚙"),
}

class MainActivityV2 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StoreAmoTheme { StoreAmoV2() } }
    }
}

private fun openUrlV2(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun isInstalledV2(context: Context, packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return false
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)
}

private fun installedVersionV2(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()
}

private fun openInstalledV2(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun platformLabelV2(id: String): String = when (id) {
    "android" -> "Android"
    "windows" -> "Windows"
    "macos" -> "macOS"
    "linux" -> "Linux"
    "web" -> "Web"
    "ios" -> "iPhone / iPad"
    else -> id.replaceFirstChar { it.uppercase() }
}

private fun glyphV2(app: StoreApp): String = when (app.id) {
    "plataformamo" -> "P"
    "presupuestamo" -> "$"
    "chessi" -> "♟"
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
fun StoreAmoV2() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(StoreTabV2.HOME) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var verifiedOnly by remember { mutableStateOf(prefs.getBoolean("verified_only", true)) }
    var showDevelopment by remember { mutableStateOf(prefs.getBoolean("show_development", true)) }
    var themeStyle by remember { mutableStateOf(StoreThemeStyle.fromKey(prefs.getString("theme_style", StoreThemeStyle.AESTHETIC.key))) }
    var pending by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloaded by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val termuxInstalled = remember { isInstalledV2(context, TERMUX_PACKAGE_V2) }

    fun refresh() {
        loading = true
        error = null
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { CatalogRepository.fetch(context) } }
            .onSuccess { apps = it.apps.filterNot { app -> app.id == "storeamo" }; error = null }
            .onFailure { error = "No pude actualizar el catálogo: ${it.message.orEmpty()}" }
        loading = false
    }

    LaunchedEffect(pending?.id) {
        val current = pending ?: return@LaunchedEffect
        notice = "Descargando ${current.artifact.version}…"
        while (true) {
            when (DownloadInstaller.status(context, current.id)) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val valid = withContext(Dispatchers.IO) { DownloadInstaller.verifySha256(current.file, current.artifact.sha256) }
                    if (valid) {
                        downloaded = current
                        notice = "Descarga verificada · lista para instalar"
                    } else {
                        current.file.delete()
                        notice = "Bloqueada · el SHA-256 no coincide"
                    }
                    pending = null
                    break
                }
                DownloadManager.STATUS_FAILED -> {
                    notice = "La descarga falló"
                    pending = null
                    break
                }
                else -> delay(850)
            }
        }
    }

    val visible = apps.filter { app ->
        (showDevelopment || app.status != "development") &&
            (query.isBlank() || listOf(app.name, app.tagline, app.description, app.category).any { it.contains(query, true) })
    }
    val publicApps = visible.filter { it.audience != "team" }
    val teamApps = visible.filter { it.audience == "team" }

    Scaffold(
        containerColor = AmoBackground,
        bottomBar = {
            NavigationBar(containerColor = AmoSurface, modifier = Modifier.navigationBarsPadding()) {
                StoreTabV2.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Black) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { BrandBar { tab = StoreTabV2.SETTINGS } }
            notice?.let { text -> item { NoticeCard(text, downloaded != null) {
                val ready = downloaded ?: return@NoticeCard
                if (!DownloadInstaller.canInstallPackages(context)) {
                    DownloadInstaller.openInstallPermission(context)
                    notice = "Permití instalar apps desde StoreAMO y volvé a tocar Instalar."
                } else DownloadInstaller.install(context, ready.file)
            } } }

            when (tab) {
                StoreTabV2.HOME -> {
                    item { HeroV2(loading, error, ::refresh) }
                    item { SearchV2(query) { query = it } }
                    val featured = teamApps.firstOrNull { it.id == "plataformamo" }
                        ?: publicApps.firstOrNull { it.featured }
                        ?: publicApps.firstOrNull()
                    if (featured != null) item { FeaturedV2(featured, context, verifiedOnly) { selected = featured } }

                    if (teamApps.isNotEmpty()) {
                        item { SectionV2("EQUIPO DESARROLLAMO", "Tu espacio de trabajo") }
                        items(teamApps, key = { "team-${it.id}" }) { app -> AppCardV2(app, context, verifiedOnly) { selected = app } }
                    }

                    item { SectionV2("PARA ESTE DISPOSITIVO", "Apps y herramientas") }
                    items(publicApps.take(8), key = { it.id }) { app -> AppCardV2(app, context, verifiedOnly) { selected = app } }

                    item {
                        if (termuxInstalled) TermuxReadyCard() else TermuxInstallCard(
                            onFdroid = { openUrlV2(context, TERMUX_FDROID) },
                            onPlay = { openUrlV2(context, TERMUX_PLAY) },
                        )
                    }
                }

                StoreTabV2.APPS -> {
                    item { PageHeaderV2("CATÁLOGO", "Todas las apps", "Primero mostramos lo útil en Android. Cada repositorio publica su propia metadata y StoreAMO la descubre.") }
                    item { SearchV2(query) { query = it } }
                    items(visible, key = { "all-${it.id}" }) { app -> AppCardV2(app, context, verifiedOnly) { selected = app } }
                }

                StoreTabV2.UPDATES -> {
                    item { PageHeaderV2("VERSIONES", "Actualizaciones", "Las apps instaladas conservan datos y configuración cuando la firma y el paquete coinciden.") }
                    item { SelfStatusCard() }
                    val updates = visible.mapNotNull { app ->
                        val a = app.artifacts.firstOrNull { it.platform == "android" } ?: return@mapNotNull null
                        val installed = installedVersionV2(context, a.applicationId) ?: return@mapNotNull null
                        if (installed != a.version) app else null
                    }
                    if (updates.isEmpty()) item { EmptyV2("✓", "No detectamos actualizaciones pendientes", "Cuando una Release compatible sea más nueva, aparecerá acá.") }
                    else items(updates, key = { "update-${it.id}" }) { app -> AppCardV2(app, context, verifiedOnly) { selected = app } }
                }

                StoreTabV2.SETTINGS -> {
                    item { PageHeaderV2("STOREAMO", "Ajustes", "Personalizá la tienda y controlá qué nivel de confianza aceptás.") }
                    item { ThemeSelectorV2(themeStyle) {
                        themeStyle = it
                        prefs.edit().putString("theme_style", it.key).apply()
                    } }
                    item { SettingToggleV2("Sólo versiones verificadas", "Si está activo, las candidatas sin verificación no se pueden instalar.", verifiedOnly) {
                        verifiedOnly = it
                        prefs.edit().putBoolean("verified_only", it).apply()
                    } }
                    item { SettingToggleV2("Mostrar desarrollo", "Muestra proyectos todavía sin Release instalable.", showDevelopment) {
                        showDevelopment = it
                        prefs.edit().putBoolean("show_development", it).apply()
                    } }
                    item { ActionSettingV2("Actualizar catálogo", "Busca nuevas apps y Releases sin reinstalar StoreAMO.", "Actualizar", ::refresh) }
                    item { ActionSettingV2("Apoyar DesarrollAMO", "Apoyo voluntario a través de CobrAMO.", "Apoyar") { openUrlV2(context, SUPPORT_URL_V2) } }
                    item { SelfStatusCard() }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AmoBackground, contentColor = AmoText) {
            AppSheetV2(
                app = app,
                context = context,
                verifiedOnly = verifiedOnly,
                onDownload = { artifact ->
                    when {
                        artifact.platform != "android" -> openUrlV2(context, artifact.url)
                        isInstalledV2(context, artifact.applicationId) && installedVersionV2(context, artifact.applicationId) == artifact.version -> {
                            openInstalledV2(context, artifact.applicationId)
                        }
                        verifiedOnly && !artifact.verified -> {
                            notice = "${app.name} es candidata y aún no tiene sello StoreAMO Verified. Desactivá ‘Sólo versiones verificadas’ si querés probarla."
                            selected = null
                        }
                        else -> runCatching { DownloadInstaller.start(context, app.name, artifact) }
                            .onSuccess { pending = it; downloaded = null; selected = null }
                            .onFailure { notice = "No pude iniciar la descarga: ${it.message.orEmpty()}" }
                    }
                },
                onCode = { app.repository?.let { openUrlV2(context, it) } },
            )
        }
    }
}

@Composable
private fun BrandBar(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) {
                Text("AMO", color = AmoBackground, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text("Store", fontWeight = FontWeight.Black, fontSize = 22.sp)
            Text("AMO", color = AmoPink, fontWeight = FontWeight.Black, fontSize = 22.sp)
        }
        Surface(onClick = onSettings, shape = CircleShape, color = AmoSurface2, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Text("⚙") }
        }
    }
}

@Composable
private fun HeroV2(loading: Boolean, error: String?, onRefresh: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("DESARROLLAMO · ECOSISTEMA", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Todo lo que construimos,\nen un solo lugar.", fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
            Text("StoreAMO descubre aplicaciones desde sus propios repositorios, verifica lo que puede comprobar y muestra primero lo compatible con tu dispositivo.", color = AmoMuted, fontSize = 13.sp)
            if (loading) Text("Actualizando catálogo…", color = AmoCyan, fontSize = 11.sp)
            if (error != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = AmoPink, modifier = Modifier.weight(1f), fontSize = 11.sp)
                TextButton(onClick = onRefresh) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun SearchV2(value: String, onChange: (String) -> Unit) {
    TextField(value = value, onValueChange = onChange, placeholder = { Text("Buscar apps") }, leadingIcon = { Text("⌕", fontSize = 22.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp))
}

@Composable
private fun SectionV2(kicker: String, title: String) {
    Column { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun FeaturedV2(app: StoreApp, context: Context, verifiedOnly: Boolean, onOpen: () -> Unit) {
    val artifact = app.artifacts.firstOrNull { it.platform == "android" }
    Card(onClick = onOpen, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AmoSurface2, AmoViolet.copy(alpha = .42f), AmoPink.copy(alpha = .30f)))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (app.audience == "team") "EQUIPO DESARROLLAMO" else "DESTACADA", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(app.name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                Text(app.tagline, color = AmoMuted, fontSize = 13.sp)
                Text(buttonLabelV2(context, artifact, verifiedOnly), color = if (artifact?.verified == true) AmoGreen else AmoCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) {
                Text(glyphV2(app), color = AmoBackground, fontSize = 30.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

private fun buttonLabelV2(context: Context, artifact: StoreArtifact?, verifiedOnly: Boolean): String {
    if (artifact == null) return "Próximamente"
    val installed = installedVersionV2(context, artifact.applicationId)
    if (installed != null && installed == artifact.version) return "Abrir"
    if (installed != null) return "Actualizar"
    if (verifiedOnly && !artifact.verified) return "Verificación pendiente"
    return if (artifact.verified) "Obtener · verificada" else "Obtener alpha"
}

@Composable
private fun AppCardV2(app: StoreApp, context: Context, verifiedOnly: Boolean, onOpen: () -> Unit) {
    val artifact = app.artifacts.firstOrNull { it.platform == "android" }
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(AmoSurface2), contentAlignment = Alignment.Center) {
                Text(glyphV2(app), color = AmoCyan, fontSize = if (app.id == "miapi") 12.sp else 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(app.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (app.audience == "team") Text("EQUIPO", color = AmoCyan, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
                Text(app.tagline, color = AmoMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${app.category} · ${app.supportedPlatforms.joinToString(" · ") { platformLabelV2(it) }}", color = AmoMuted.copy(alpha = .75f), fontSize = 9.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpen) { Text(buttonLabelV2(context, artifact, verifiedOnly), fontSize = 10.sp) }
        }
    }
}

@Composable
private fun TermuxReadyCard() {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TERMUX DETECTADO", color = AmoGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text("Scripts AMO", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("La galería de scripts aparecerá acá cuando los scripts de IdeAMO pasen revisión, declaren qué hacen y tengan integridad verificable.", color = AmoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TermuxInstallCard(onFdroid: () -> Unit, onPlay: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("SCRIPTS AMO", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text("¿Querés ejecutar herramientas en Android?", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Recomendamos Termux desde F-Droid para la experiencia completa. La variante de Google Play es experimental y tiene funcionalidad reducida.", color = AmoMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onFdroid, colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text("F-Droid", fontWeight = FontWeight.Black) }
                OutlinedButton(onClick = onPlay) { Text("Google Play") }
            }
        }
    }
}

@Composable
private fun PageHeaderV2(kicker: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
        Text(title, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(body, color = AmoMuted, fontSize = 13.sp)
    }
}

@Composable
private fun EmptyV2(glyph: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(glyph, color = AmoCyan, fontSize = 30.sp)
            Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text(body, color = AmoMuted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun NoticeCard(text: String, installable: Boolean, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (text.contains("Bloqueada") || text.contains("falló")) AmoPink else AmoCyan, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (installable) Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun ThemeSelectorV2(current: StoreThemeStyle, onTheme: (StoreThemeStyle) -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Estilo visual", fontWeight = FontWeight.Black, fontSize = 17.sp)
            Text("La identidad DesarrollAMO se conserva, pero vos elegís cómo verla.", color = AmoMuted, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StoreThemeStyle.entries.forEach { style ->
                    Button(onClick = { onTheme(style) }, colors = ButtonDefaults.buttonColors(containerColor = if (style == current) AmoCyan else AmoSurface2, contentColor = if (style == current) AmoBackground else AmoText)) {
                        Text(if (style == current) "✓ ${style.label}" else style.label, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleV2(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 11.sp) }
            Switch(checked = checked, onCheckedChange = onChecked)
        }
    }
}

@Composable
private fun ActionSettingV2(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 11.sp) }
            Button(onClick = onClick) { Text(action, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun SelfStatusCard() {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) { Text("S", color = AmoBackground, fontWeight = FontWeight.Black, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("StoreAMO", fontWeight = FontWeight.Black)
                Text("Instalada · ${BuildConfig.VERSION_NAME}", color = AmoGreen, fontSize = 11.sp)
                Text("StoreAMO no se muestra como una app para instalarse a sí misma; acá vive su estado y, más adelante, su actualización propia.", color = AmoMuted, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun AppSheetV2(app: StoreApp, context: Context, verifiedOnly: Boolean, onDownload: (StoreArtifact) -> Unit, onCode: () -> Unit) {
    val androidArtifact = app.artifacts.firstOrNull { it.platform == "android" }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) { Text(glyphV2(app), color = AmoBackground, fontSize = 26.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (app.audience == "team") "EQUIPO DESARROLLAMO" else app.category.uppercase(), color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(app.name, fontSize = 27.sp, fontWeight = FontWeight.Black)
                Text(if (androidArtifact?.verified == true) "✓ StoreAMO Verified" else app.status.uppercase(), color = if (androidArtifact?.verified == true) AmoGreen else AmoMuted, fontSize = 10.sp)
            }
        }
        Text(app.description, color = AmoMuted, fontSize = 13.sp)
        if (androidArtifact != null) {
            Surface(shape = RoundedCornerShape(18.dp), color = AmoSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Android · ${androidArtifact.version}", fontWeight = FontWeight.Black)
                    Text(if (androidArtifact.verified) "Integridad publicada y release verificada." else "Release candidata. Podés probarla si desactivás ‘Sólo versiones verificadas’.", color = if (androidArtifact.verified) AmoGreen else AmoMuted, fontSize = 11.sp)
                    Button(onClick = { onDownload(androidArtifact) }, enabled = !(verifiedOnly && !androidArtifact.verified), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) {
                        Text(buttonLabelV2(context, androidArtifact, verifiedOnly), fontWeight = FontWeight.Black)
                    }
                }
            }
        } else {
            EmptyV2("○", "Todavía no hay APK", "El repositorio ya está en el ecosistema, pero StoreAMO espera una Release compatible antes de mostrar Obtener.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            app.repository?.let { OutlinedButton(onClick = onCode) { Text("Código") } }
            Text(app.supportedPlatforms.joinToString(" · ") { platformLabelV2(it) }, color = AmoMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically))
        }
        Spacer(Modifier.height(22.dp))
    }
}
