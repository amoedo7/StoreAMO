package com.desarrollamo.storeamo

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desarrollamo.storeamo.data.CatalogRepository
import com.desarrollamo.storeamo.data.SelfUpdateRepository
import com.desarrollamo.storeamo.model.StoreApp
import com.desarrollamo.storeamo.model.StoreArtifact
import com.desarrollamo.storeamo.theme.AmoAmber
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

private const val SUPPORT_URL_V3 = "https://cobramo.netlify.app/"
private const val TERMUX_PACKAGE_V3 = "com.termux"
private const val TERMUX_FDROID_V3 = "https://f-droid.org/packages/com.termux"
private const val TERMUX_PLAY_V3 = "https://play.google.com/store/apps/details?id=com.termux"

private enum class TabV3(val label: String, val glyph: String) {
    HOME("Inicio", "◆"), APPS("Apps", "▦"), UPDATES("Actualiz.", "↻"), SETTINGS("Ajustes", "⚙")
}

class MainActivityV3 : ComponentActivity() {
    private val resumeToken = mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StoreAmoTheme { StoreAmoV3(resumeToken.value) }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeToken.value = resumeToken.value + 1
    }
}

private fun openUrlV3(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun installedVersionV3(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()
}

private fun openInstalledV3(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun uninstallInstalledV3(context: Context, packageName: String?) {
    UninstallFlowActivity.launch(context, packageName)
}

private fun openAppInfoV3(context: Context, packageName: String?) {
    UninstallFlowActivity.openInfo(context, packageName)
}

private fun glyphV3(app: StoreApp): String = when (app.id) {
    "plataformamo" -> "P"; "presupuestamo" -> "$"; "chessi" -> "♟"; "midispositivo" -> "D"
    "mired" -> "R"; "misistema" -> "M"; "miweb" -> "W"; "miarchivos" -> "A"; "miapi" -> "API"
    "diagnosticoamo" -> "✓"; else -> app.name.take(2).uppercase()
}

private fun platformLabelV3(id: String): String = when (id) {
    "android" -> "Android"; "windows" -> "Windows"; "macos" -> "macOS"; "linux" -> "Linux"
    "web" -> "Web"; "ios" -> "iPhone / iPad"; else -> id.replaceFirstChar { it.uppercase() }
}

private fun androidArtifactV3(app: StoreApp): StoreArtifact? = app.artifacts.firstOrNull { it.platform == "android" }

private fun actionLabelV3(context: Context, artifact: StoreArtifact?, verifiedOnly: Boolean): String {
    if (artifact == null) return "Próximamente"
    val installed = installedVersionV3(context, artifact.applicationId)
    if (installed == artifact.version) return "Abrir"
    if (installed != null) return "Actualizar"
    if (verifiedOnly && !artifact.verified) return "Verificación pendiente"
    return if (artifact.verified) "Obtener" else "Obtener alpha"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreAmoV3(resumeToken: Int) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }
    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var selfUpdate by remember { mutableStateOf<StoreArtifact?>(null) }
    var selfLoading by remember { mutableStateOf(true) }
    var selfError by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(TabV3.HOME) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var verifiedOnly by remember { mutableStateOf(prefs.getBoolean("verified_only", true)) }
    var showDevelopment by remember { mutableStateOf(prefs.getBoolean("show_development", true)) }
    var themeStyle by remember { mutableStateOf(StoreThemeStyle.fromKey(prefs.getString("theme_style", StoreThemeStyle.AESTHETIC.key))) }
    var pending by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var downloaded by remember { mutableStateOf<DownloadInstaller.Pending?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val termuxInstalled = remember(resumeToken) { installedVersionV3(context, TERMUX_PACKAGE_V3) != null }

    fun refreshEverything() {
        loading = true
        selfLoading = true
        catalogError = null
        selfError = null
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { CatalogRepository.fetch(context) } }
            .onSuccess { apps = it.apps.filterNot { app -> app.id == "storeamo" }; catalogError = null }
            .onFailure { catalogError = "No pude actualizar el catálogo: ${it.message.orEmpty()}" }
        loading = false
    }

    LaunchedEffect(selfLoading) {
        if (!selfLoading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { SelfUpdateRepository.fetchLatest() } }
            .onSuccess { selfUpdate = it; selfError = null }
            .onFailure { selfUpdate = null; selfError = "No pude consultar la versión de StoreAMO: ${it.message.orEmpty()}" }
        selfLoading = false
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
                DownloadManager.STATUS_FAILED -> { notice = "La descarga falló"; pending = null; break }
                else -> delay(800)
            }
        }
    }

    LaunchedEffect(resumeToken) {
        val ready = downloaded
        if (ready != null && installedVersionV3(context, ready.artifact.applicationId) == ready.artifact.version) {
            downloaded = null
            notice = "Instalada correctamente · no detectamos actualizaciones para esta app."
            selfLoading = true
        } else if (notice?.startsWith("Permití instalar apps") == true && ready == null) {
            notice = null
        }
    }

    val filtered = apps.filter { app ->
        (showDevelopment || app.status != "development") &&
            (query.isBlank() || listOf(app.name, app.tagline, app.description, app.category).any { it.contains(query, true) })
    }
    val teamApps = filtered.filter { it.audience == "team" }
    val publicApps = filtered.filter { it.audience != "team" }
    val availableApps = publicApps.filter { it.artifacts.isNotEmpty() }
    val upcomingApps = publicApps.filter { it.artifacts.isEmpty() }
    val installedApps = filtered.mapNotNull { app ->
        val artifact = androidArtifactV3(app) ?: return@mapNotNull null
        val installed = installedVersionV3(context, artifact.applicationId) ?: return@mapNotNull null
        Triple(app, artifact, installed)
    }
    val appUpdates = installedApps.filter { (_, artifact, installed) -> installed != artifact.version }

    Scaffold(
        containerColor = AmoBackground,
        bottomBar = {
            NavigationBar(containerColor = AmoSurface, modifier = Modifier.navigationBarsPadding()) {
                TabV3.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.glyph, fontWeight = FontWeight.Black, color = if (tab == item) AmoAmber else AmoMuted) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1, color = if (tab == item) AmoAmber else AmoMuted) },
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
            item { BrandV3 { tab = TabV3.SETTINGS } }
            notice?.let { text ->
                item {
                    NoticeV3(text, downloaded != null) {
                        val ready = downloaded ?: return@NoticeV3
                        if (!DownloadInstaller.canInstallPackages(context)) {
                            DownloadInstaller.openInstallPermission(context)
                            notice = "Permití instalar apps desde StoreAMO y volvé a tocar Instalar."
                        } else DownloadInstaller.install(context, ready.file)
                    }
                }
            }

            when (tab) {
                TabV3.HOME -> {
                    item { HeroV3(loading, catalogError, ::refreshEverything) }
                    item { GoodNewsEntryV3(context) }
                    item { SearchV3(query) { query = it } }
                    val featured = teamApps.firstOrNull { it.id == "plataformamo" }
                        ?: availableApps.firstOrNull { it.featured }
                        ?: availableApps.firstOrNull()
                    if (featured != null) item { FeaturedV3(featured, context, verifiedOnly) { selected = featured } }

                    if (teamApps.isNotEmpty()) {
                        item { SectionV3("EQUIPO DESARROLLAMO", "Tu espacio de trabajo") }
                        items(teamApps, key = { "team-${it.id}" }) { app -> AppCardV3(app, context, verifiedOnly) { selected = app } }
                    }

                    if (availableApps.isNotEmpty()) {
                        item { SectionV3("DISPONIBLES AHORA", "Apps y herramientas") }
                        items(availableApps.take(8), key = { "ready-${it.id}" }) { app -> AppCardV3(app, context, verifiedOnly) { selected = app } }
                    }

                    item {
                        if (termuxInstalled) TermuxScriptGalleryCard(context) { notice = it }
                        else TermuxInstallV3(
                            onFdroid = { openUrlV3(context, TERMUX_FDROID_V3) },
                            onPlay = { openUrlV3(context, TERMUX_PLAY_V3) },
                        )
                    }

                    if (upcomingApps.isNotEmpty()) {
                        item { SectionV3("LO QUE SE VIENE", "Próximamente") }
                        items(upcomingApps.take(8), key = { "soon-${it.id}" }) { app -> UpcomingCardV3(app) { selected = app } }
                    }
                }

                TabV3.APPS -> {
                    item { PageHeaderV3("CATÁLOGO", "Todas las apps", "Lo instalable aparece primero. Los proyectos sin artefacto quedan separados en Lo que se viene.") }
                    item { SearchV3(query) { query = it } }
                    if (teamApps.isNotEmpty()) {
                        item { SectionV3("EQUIPO", "DesarrollAMO") }
                        items(teamApps, key = { "catalog-team-${it.id}" }) { app -> AppCardV3(app, context, verifiedOnly) { selected = app } }
                    }
                    if (availableApps.isNotEmpty()) {
                        item { SectionV3("DISPONIBLES", "Para instalar o abrir") }
                        items(availableApps, key = { "catalog-ready-${it.id}" }) { app -> AppCardV3(app, context, verifiedOnly) { selected = app } }
                    }
                    if (upcomingApps.isNotEmpty()) {
                        item { SectionV3("LO QUE SE VIENE", "Próximamente") }
                        items(upcomingApps, key = { "catalog-soon-${it.id}" }) { app -> UpcomingCardV3(app) { selected = app } }
                    }
                }

                TabV3.UPDATES -> {
                    item { PageHeaderV3("VERSIONES", "Actualizaciones", "StoreAMO consulta su propia Release y también compara las apps instaladas con el catálogo.") }
                    item {
                        SelfUpdateV3(
                            latest = selfUpdate,
                            loading = selfLoading,
                            error = selfError,
                            onRefresh = { selfLoading = true },
                            onUpdate = { artifact ->
                                runCatching { DownloadInstaller.start(context, "StoreAMO", artifact) }
                                    .onSuccess { pending = it; downloaded = null }
                                    .onFailure { notice = "No pude iniciar la actualización: ${it.message.orEmpty()}" }
                            },
                        )
                    }
                    if (appUpdates.isNotEmpty()) {
                        item { SectionV3("ACTUALIZACIONES", "Hay versiones nuevas") }
                        items(appUpdates, key = { "update-${it.first.id}" }) { (app, _, _) -> AppCardV3(app, context, verifiedOnly) { selected = app } }
                    } else {
                        item { StatusV3("✓", "No detectamos actualizaciones pendientes", "Las aplicaciones conocidas que están instaladas están al día.") }
                    }
                    if (installedApps.isNotEmpty()) {
                        item { SectionV3("INSTALADAS", "En este dispositivo") }
                        items(installedApps, key = { "installed-${it.first.id}" }) { (app, artifact, installed) ->
                            InstalledCardV3(
                                app = app,
                                artifact = artifact,
                                installed = installed,
                                onOpen = { selected = app },
                                onUninstall = { uninstallInstalledV3(context, artifact.applicationId) },
                                onInfo = { openAppInfoV3(context, artifact.applicationId) },
                            )
                        }
                    }
                }

                TabV3.SETTINGS -> {
                    item { PageHeaderV3("STOREAMO", "Ajustes", "Personalizá la tienda y controlá qué nivel de confianza aceptás.") }
                    item { StoreSymbolStoryV3() }
                    item { ThemeSelectorV3(themeStyle) {
                        themeStyle = it
                        prefs.edit().putString("theme_style", it.key).apply()
                    } }
                    item { ToggleV3("Sólo versiones verificadas", "Las candidatas quedan visibles pero no se instalan hasta que lo desactives.", verifiedOnly) {
                        verifiedOnly = it; prefs.edit().putBoolean("verified_only", it).apply()
                    } }
                    item { ToggleV3("Mostrar desarrollo", "Muestra también los proyectos de Lo que se viene.", showDevelopment) {
                        showDevelopment = it; prefs.edit().putBoolean("show_development", it).apply()
                    } }
                    item { ActionV3("Actualizar catálogo y versiones", "Busca nuevas Releases sin reinstalar StoreAMO.", "Actualizar", ::refreshEverything) }
                    item { ActionV3("Apoyar DesarrollAMO", "Apoyo voluntario a través de CobrAMO.", "Apoyar") { openUrlV3(context, SUPPORT_URL_V3) } }
                    item { SelfUpdateV3(selfUpdate, selfLoading, selfError, { selfLoading = true }) { artifact ->
                        runCatching { DownloadInstaller.start(context, "StoreAMO", artifact) }
                            .onSuccess { pending = it; downloaded = null }
                            .onFailure { notice = "No pude iniciar la actualización: ${it.message.orEmpty()}" }
                    } }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AmoBackground, contentColor = AmoText) {
            AppSheetV3(
                app = app,
                context = context,
                verifiedOnly = verifiedOnly,
                onDownload = { artifact ->
                    val installed = installedVersionV3(context, artifact.applicationId)
                    when {
                        installed == artifact.version -> openInstalledV3(context, artifact.applicationId)
                        verifiedOnly && !artifact.verified -> {
                            notice = "${app.name} todavía no tiene sello StoreAMO Verified. Desactivá ‘Sólo versiones verificadas’ si querés probarla."
                            selected = null
                        }
                        else -> runCatching { DownloadInstaller.start(context, app.name, artifact) }
                            .onSuccess { pending = it; downloaded = null; selected = null }
                            .onFailure { notice = "No pude iniciar la descarga: ${it.message.orEmpty()}" }
                    }
                },
                onCode = { app.repository?.let { openUrlV3(context, it) } },
            )
        }
    }
}

@Composable
private fun BrandV3(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface2, modifier = Modifier.size(46.dp)) {
                Box(Modifier.padding(5.dp), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(R.drawable.ic_storeamo), contentDescription = "Logo de StoreAMO", modifier = Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Store", fontWeight = FontWeight.Black, fontSize = 23.sp)
                    Text("AMO", color = AmoPink, fontWeight = FontWeight.Black, fontSize = 23.sp)
                }
                Text("DesarrollAMO · Ecosistema", color = AmoMuted, fontSize = 10.sp)
            }
        }
        Surface(onClick = onSettings, shape = CircleShape, color = AmoSurface2, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text("⚙") } }
    }
}

@Composable
private fun HeroV3(loading: Boolean, error: String?, refresh: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(AmoSurface, AmoSurface2, AmoAmber.copy(alpha = .20f))))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("DESARROLLAMO · ECOSISTEMA", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
            Text("Todo lo que construimos,\nen un solo lugar.", fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
            Text("Instalá lo que ya funciona, ejecutá herramientas y mirá aparte lo que estamos preparando.", color = AmoMuted, fontSize = 13.sp)
            Surface(shape = RoundedCornerShape(99.dp), color = AmoAmber.copy(alpha = .12f)) {
                Text("✦ v${BuildConfig.VERSION_NAME}", color = AmoAmber, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
            }
            if (loading) Text("Actualizando catálogo…", color = AmoCyan, fontSize = 11.sp)
            if (error != null) Row(verticalAlignment = Alignment.CenterVertically) { Text(error, color = AmoPink, modifier = Modifier.weight(1f), fontSize = 11.sp); TextButton(onClick = refresh) { Text("Reintentar") } }
        }
    }
}

@Composable
private fun SearchV3(value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text("Buscar apps, herramientas o novedades…") },
        leadingIcon = { Text("⌕", fontSize = 22.sp, color = AmoCyan) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun SectionV3(kicker: String, title: String) {
    Column { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun GoodNewsEntryV3(context: Context) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.linearGradient(listOf(AmoSurface2, AmoCyan.copy(alpha = .14f), AmoAmber.copy(alpha = .18f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("BUENAS NUEVAS", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
                    Text("El ecosistema se está moviendo", fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                Image(painter = painterResource(R.drawable.ic_storeamo), contentDescription = null, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)))
            }
            Text("Versiones, mejoras y apps que están avanzando, explicadas acá sin tener que navegar GitHub.", color = AmoMuted, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                PreviewFilterChipV3("7 días", selected = true)
                PreviewFilterChipV3("Por app")
                PreviewFilterChipV3("En desarrollo")
                PreviewFilterChipV3("Publicadas")
                PreviewFilterChipV3("Mejoras")
            }
            Button(
                onClick = { context.startActivity(Intent(context, GoodNewsActivity::class.java)) },
                colors = ButtonDefaults.buttonColors(containerColor = AmoAmber, contentColor = AmoBackground),
            ) { Text("Ver Buenas Nuevas  ›", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun PreviewFilterChipV3(label: String, selected: Boolean = false) {
    Surface(shape = RoundedCornerShape(99.dp), color = if (selected) AmoAmber.copy(alpha = .18f) else AmoSurface.copy(alpha = .72f)) {
        Text(label, color = if (selected) AmoAmber else AmoMuted, fontSize = 9.sp, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun PageHeaderV3(kicker: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(title, fontSize = 34.sp, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 13.sp) }
}

@Composable
private fun FeaturedV3(app: StoreApp, context: Context, verifiedOnly: Boolean, onOpen: () -> Unit) {
    val artifact = androidArtifactV3(app)
    Card(onClick = onOpen, shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent)) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AmoSurface2, AmoCyan.copy(alpha = .18f), AmoAmber.copy(alpha = .28f)))).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(if (app.audience == "team") "EQUIPO DESARROLLAMO" else "DESTACADA", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text(app.name, fontSize = 30.sp, fontWeight = FontWeight.Black); Text(app.tagline, color = AmoMuted, fontSize = 13.sp)
                Text(actionLabelV3(context, artifact, verifiedOnly), color = AmoAmber, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoPink, AmoAmber))), contentAlignment = Alignment.Center) { Text(glyphV3(app), color = AmoBackground, fontSize = 30.sp, fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun AppCardV3(app: StoreApp, context: Context, verifiedOnly: Boolean, onOpen: () -> Unit) {
    val artifact = androidArtifactV3(app)
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(18.dp)).background(AmoSurface2), contentAlignment = Alignment.Center) { Text(glyphV3(app), color = AmoCyan, fontSize = if (app.id == "miapi") 12.sp else 22.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(app.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.tagline, color = AmoMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.supportedPlatforms.joinToString(" · ") { platformLabelV3(it) }, color = AmoMuted.copy(alpha = .75f), fontSize = 9.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp)); OutlinedButton(onClick = onOpen) { Text(actionLabelV3(context, artifact, verifiedOnly), fontSize = 10.sp) }
        }
    }
}

@Composable
private fun UpcomingCardV3(app: StoreApp, onOpen: () -> Unit) {
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = AmoSurface.copy(alpha = .78f)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(AmoSurface2), contentAlignment = Alignment.Center) { Text(glyphV3(app), color = AmoMuted, fontWeight = FontWeight.Black, fontSize = 20.sp) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Black); Text(app.tagline, color = AmoMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text("Próximamente", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun InstalledCardV3(
    app: StoreApp,
    artifact: StoreArtifact,
    installed: String,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onInfo: () -> Unit,
) {
    val upToDate = installed == artifact.version
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(glyphV3(app), color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Black); Text("Instalada · $installed", color = AmoMuted, fontSize = 10.sp) }
                Text(if (upToDate) "Al día" else "Actualizar", color = if (upToDate) AmoGreen else AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
                    Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                }
                OutlinedButton(onClick = onInfo, modifier = Modifier.width(58.dp)) {
                    Text("ⓘ", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SelfUpdateV3(latest: StoreArtifact?, loading: Boolean, error: String?, onRefresh: () -> Unit, onUpdate: (StoreArtifact) -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("StoreAMO", fontSize = 21.sp, fontWeight = FontWeight.Black)
            Text("Instalada · ${BuildConfig.VERSION_NAME}", color = AmoGreen, fontSize = 11.sp)
            when {
                loading -> Text("Buscando una versión más nueva…", color = AmoCyan, fontSize = 11.sp)
                latest != null -> {
                    Text("Nueva versión · ${latest.version}", color = AmoCyan, fontWeight = FontWeight.Black)
                    Text("La Release viene de GitHub, StoreAMO verifica su SHA-256 y Android conserva la instalación si la firma coincide.", color = AmoMuted, fontSize = 10.sp)
                    Button(onClick = { onUpdate(latest) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text("Actualizar StoreAMO", fontWeight = FontWeight.Black) }
                }
                error != null -> { Text(error, color = AmoPink, fontSize = 10.sp); OutlinedButton(onClick = onRefresh) { Text("Reintentar") } }
                else -> Text("Al día · no se detectan actualizaciones.", color = AmoGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StatusV3(glyph: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) { Text(glyph, color = AmoCyan, fontSize = 30.sp); Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp); Text(body, color = AmoMuted, fontSize = 12.sp) } }
}

@Composable
private fun NoticeV3(text: String, installable: Boolean, onInstall: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (text.contains("Bloqueada") || text.contains("falló")) AmoPink else AmoCyan, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (installable) Button(onClick = onInstall) { Text("Instalar") }
        }
    }
}

@Composable
private fun StoreSymbolStoryV3() {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(17.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2, modifier = Modifier.size(62.dp)) {
                    Image(painter = painterResource(R.drawable.ic_storeamo), contentDescription = null, modifier = Modifier.padding(6.dp))
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("EL SÍMBOLO DE STOREAMO", color = AmoAmber, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    Text("Cuatro piezas. Un ecosistema.", fontSize = 19.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                "El dibujo nació como una caja abierta en cuatro módulos: apps, herramientas, novedades y proyectos que todavía están creciendo. El cruce del centro no los separa: los ordena y los conecta. Por eso StoreAMO es la puerta donde todo lo que construye DesarrollAMO se reúne, se verifica y llega a tus manos.",
                color = AmoMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            Text("Cian = tecnología · violeta = exploración · rosa = creación · blanco = listo para usar.", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ThemeSelectorV3(current: StoreThemeStyle, onTheme: (StoreThemeStyle) -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Estilo visual", fontWeight = FontWeight.Black, fontSize = 17.sp); Text("La identidad DesarrollAMO se conserva, pero vos elegís cómo verla.", color = AmoMuted, fontSize = 11.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                StoreThemeStyle.entries.forEach { style -> Button(onClick = { onTheme(style) }, colors = ButtonDefaults.buttonColors(containerColor = if (style == current) AmoCyan else AmoSurface2, contentColor = if (style == current) AmoBackground else AmoText)) { Text(if (style == current) "✓ ${style.label}" else style.label, fontSize = 10.sp) } }
            }
        }
    }
}

@Composable
private fun ToggleV3(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 11.sp) }; Switch(checked = checked, onCheckedChange = onChecked) } }
}

@Composable
private fun ActionV3(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 11.sp) }; Button(onClick = onClick) { Text(action, fontSize = 10.sp) } } }
}

@Composable
private fun TermuxInstallV3(onFdroid: () -> Unit, onPlay: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("SCRIPTS AMO", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text("¿Querés ejecutar herramientas en Android?", fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("Recomendamos Termux desde F-Droid para la experiencia completa. Google Play ofrece una variante con limitaciones.", color = AmoMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onFdroid, colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text("F-Droid", fontWeight = FontWeight.Black) }; OutlinedButton(onClick = onPlay) { Text("Google Play") } }
        }
    }
}

@Composable
private fun AppSheetV3(app: StoreApp, context: Context, verifiedOnly: Boolean, onDownload: (StoreArtifact) -> Unit, onCode: () -> Unit) {
    val artifact = androidArtifactV3(app)
    val installed = installedVersionV3(context, artifact?.applicationId)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(70.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) { Text(glyphV3(app), color = AmoBackground, fontSize = 26.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(if (app.audience == "team") "EQUIPO DESARROLLAMO" else app.category.uppercase(), color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(app.name, fontSize = 27.sp, fontWeight = FontWeight.Black); Text(app.status.uppercase(), color = AmoMuted, fontSize = 10.sp) }
        }
        Text(app.description, color = AmoMuted, fontSize = 13.sp)
        if (artifact != null) {
            Surface(shape = RoundedCornerShape(18.dp), color = AmoSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Android · ${artifact.version}", fontWeight = FontWeight.Black)
                    when {
                        installed == artifact.version -> Text("Instalada · no se detectan actualizaciones.", color = AmoGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        installed != null -> Text("Tenés $installed · hay una versión nueva disponible.", color = AmoCyan, fontSize = 11.sp)
                        artifact.verified -> Text("Release con integridad verificada.", color = AmoGreen, fontSize = 11.sp)
                        else -> Text("Release candidata. Podés probarla si desactivás ‘Sólo versiones verificadas’.", color = AmoMuted, fontSize = 11.sp)
                    }
                    Button(onClick = { onDownload(artifact) }, enabled = installed == artifact.version || !(verifiedOnly && !artifact.verified), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text(actionLabelV3(context, artifact, verifiedOnly), fontWeight = FontWeight.Black) }
                    if (installed != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { uninstallInstalledV3(context, artifact.applicationId) }, modifier = Modifier.weight(1f)) {
                                Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = { openAppInfoV3(context, artifact.applicationId) }, modifier = Modifier.width(58.dp)) {
                                Text("ⓘ", color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
                            }
                        }
                        Text("ⓘ abre Info. de la aplicación de Android. Si el desinstalador directo no completa el proceso, StoreAMO también cae automáticamente en esa pantalla.", color = AmoMuted, fontSize = 9.sp)
                    }
                }
            }
        } else {
            StatusV3("◇", "Lo que se viene", "Este proyecto ya forma parte del ecosistema, pero todavía no ofrece un artefacto instalable.")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { app.repository?.let { OutlinedButton(onClick = onCode) { Text("Código") } }; Text(app.supportedPlatforms.joinToString(" · ") { platformLabelV3(it) }, color = AmoMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterVertically)) }
        Spacer(Modifier.height(22.dp))
    }
}
