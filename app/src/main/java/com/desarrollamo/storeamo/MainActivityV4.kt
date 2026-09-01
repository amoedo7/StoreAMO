package com.desarrollamo.storeamo

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.desarrollamo.storeamo.data.CatalogRepository
import com.desarrollamo.storeamo.data.FeedbackRepository
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
import com.desarrollamo.storeamo.util.DownloadInstaller
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SUPPORT_URL_V4 = "https://cobramo.netlify.app/"
private const val TERMUX_PACKAGE_V4 = "com.termux"
private const val TERMUX_FDROID_V4 = "https://f-droid.org/packages/com.termux"
private const val TERMUX_PLAY_V4 = "https://play.google.com/store/apps/details?id=com.termux"
private const val CLIMA_PACKAGE = "com.desarrollamo.climaamo"
private const val CLIMA_LEGACY_VERSION = "0.2.0"
private const val MIGRATION_PREFS = "storeamo_signature_migrations"
private const val MIGRATION_APP = "pending_app_id"
private const val MIGRATION_VERSION = "pending_target_version"

private enum class TabV4(val label: String, val glyph: String) {
    HOME("Inicio", "◆"), APPS("Apps", "▦"), UPDATES("Actualiz.", "↻"), SETTINGS("Ajustes", "⚙")
}

class MainActivityV4 : ComponentActivity() {
    private val resumeToken = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { StoreAmoTheme { StoreAmoV4(resumeToken.intValue) } }
    }

    override fun onResume() {
        super.onResume()
        resumeToken.intValue += 1
    }
}

private fun installedVersionV4(context: Context, packageName: String?): String? {
    if (packageName.isNullOrBlank()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0).versionName
    }.getOrNull()
}

private fun androidArtifactV4(app: StoreApp): StoreArtifact? = app.artifacts.firstOrNull { it.platform == "android" }

private fun openUrlV4(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun openInstalledV4(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { context.startActivity(it) }
}

private fun appGlyphV4(app: StoreApp): String = when (app.id) {
    "climaamo" -> "☀"
    "plataformamo" -> "P"
    "chessi" -> "♟"
    "presupuestamo" -> "$"
    else -> app.name.take(2).uppercase()
}

private fun actionLabelV4(context: Context, artifact: StoreArtifact?, verifiedOnly: Boolean): String {
    if (artifact == null) return "Próximamente"
    val installed = installedVersionV4(context, artifact.applicationId)
    if (installed == artifact.version) return "Abrir"
    if (installed != null) return "Actualizar"
    if (verifiedOnly && !artifact.verified) return "Verificación pendiente"
    return if (artifact.verified) "Obtener" else "Obtener candidate"
}

private fun isKnownClimaMigration(app: StoreApp, installed: String?, artifact: StoreArtifact?): Boolean =
    app.id == "climaamo" && installed == CLIMA_LEGACY_VERSION && artifact?.applicationId == CLIMA_PACKAGE && artifact.version != CLIMA_LEGACY_VERSION

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreAmoV4(resumeToken: Int) {
    val context = LocalContext.current
    val settings = remember { context.getSharedPreferences("storeamo_settings", Context.MODE_PRIVATE) }
    val migrationPrefs = remember { context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE) }

    var apps by remember { mutableStateOf<List<StoreApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var ratings by remember { mutableStateOf<Map<String, FeedbackRepository.RatingStats>>(emptyMap()) }
    var ratingsRefresh by remember { mutableIntStateOf(0) }
    var selfUpdate by remember { mutableStateOf<StoreArtifact?>(null) }
    var selfLoading by remember { mutableStateOf(true) }
    var tab by remember { mutableStateOf(TabV4.HOME) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<StoreApp?>(null) }
    var verifiedOnly by remember { mutableStateOf(settings.getBoolean("verified_only", false)) }
    var showDevelopment by remember { mutableStateOf(settings.getBoolean("show_development", true)) }
    var notice by remember { mutableStateOf<String?>(null) }
    val termuxInstalled = remember(resumeToken) { installedVersionV4(context, TERMUX_PACKAGE_V4) != null }

    fun refreshAll() {
        loading = true
        selfLoading = true
        ratingsRefresh++
        catalogError = null
    }

    fun startArtifact(app: StoreApp, artifact: StoreArtifact) {
        val installed = installedVersionV4(context, artifact.applicationId)
        when {
            installed == artifact.version -> openInstalledV4(context, artifact.applicationId)
            verifiedOnly && !artifact.verified -> notice = "${app.name} todavía no tiene sello StoreAMO Verified. Podés habilitar candidates desde Ajustes."
            else -> runCatching { DownloadInstaller.start(context, app.name, artifact) }
                .onFailure { notice = "No pude iniciar la descarga: ${it.message.orEmpty()}" }
        }
    }

    fun startSelfUpdate(artifact: StoreArtifact) {
        if (artifact.applicationId != BuildConfig.APPLICATION_ID) {
            notice = "Actualización de StoreAMO bloqueada: el APK no declara el paquete esperado."
            return
        }
        runCatching { DownloadInstaller.start(context, "StoreAMO", artifact) }
            .onFailure { notice = "No pude iniciar la actualización interna de StoreAMO: ${it.message.orEmpty()}" }
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { CatalogRepository.fetch(context) } }
            .onSuccess { apps = it.apps.filterNot { app -> app.id == "storeamo" }; catalogError = null }
            .onFailure { catalogError = "No pude actualizar el catálogo: ${it.message.orEmpty()}" }
        loading = false
    }

    LaunchedEffect(ratingsRefresh) {
        runCatching { withContext(Dispatchers.IO) { FeedbackRepository.fetchOverview() } }
            .onSuccess { ratings = it }
    }

    LaunchedEffect(selfLoading) {
        if (!selfLoading) return@LaunchedEffect
        runCatching { withContext(Dispatchers.IO) { SelfUpdateRepository.fetchLatest() } }
            .onSuccess { selfUpdate = it }
        selfLoading = false
    }

    LaunchedEffect(resumeToken, apps) {
        val pendingAppId = migrationPrefs.getString(MIGRATION_APP, null) ?: return@LaunchedEffect
        val targetVersion = migrationPrefs.getString(MIGRATION_VERSION, null) ?: return@LaunchedEffect
        val app = apps.firstOrNull { it.id == pendingAppId } ?: return@LaunchedEffect
        val artifact = androidArtifactV4(app)?.takeIf { it.version == targetVersion } ?: return@LaunchedEffect
        if (installedVersionV4(context, artifact.applicationId) == null) {
            migrationPrefs.edit().clear().apply()
            notice = "Firma anterior eliminada · continuando automáticamente con ${app.name} $targetVersion."
            startArtifact(app, artifact)
        } else {
            notice = "La desinstalación todavía no se completó. Android debe quitar la versión anterior antes de continuar."
        }
    }

    val filtered = apps.filter { app ->
        (showDevelopment || app.status != "development") &&
            (query.isBlank() || listOf(app.name, app.tagline, app.description, app.category).any { it.contains(query, true) })
    }
    val available = filtered.filter { it.artifacts.isNotEmpty() }
    val upcoming = filtered.filter { it.artifacts.isEmpty() }
    val bestRated = available.mapNotNull { app -> ratings[app.id]?.takeIf { it.ratingCount > 0 }?.let { app to it } }
        .sortedWith(compareByDescending<Pair<StoreApp, FeedbackRepository.RatingStats>> { it.second.average ?: 0.0 }.thenByDescending { it.second.ratingCount })
        .take(5)
    val installedApps = available.mapNotNull { app ->
        val artifact = androidArtifactV4(app) ?: return@mapNotNull null
        val installed = installedVersionV4(context, artifact.applicationId) ?: return@mapNotNull null
        Triple(app, artifact, installed)
    }
    val updates = installedApps.filter { it.second.version != it.third }

    Scaffold(
        containerColor = AmoBackground,
        bottomBar = {
            NavigationBar(containerColor = AmoSurface, modifier = Modifier.navigationBarsPadding()) {
                TabV4.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.glyph, color = if (tab == item) AmoAmber else AmoMuted, fontWeight = FontWeight.Black) },
                        label = { Text(item.label, color = if (tab == item) AmoAmber else AmoMuted, fontSize = 10.sp) },
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
            item { BrandV4 { tab = TabV4.SETTINGS } }
            notice?.let { text -> item { NoticeV4(text) { notice = null } } }

            when (tab) {
                TabV4.HOME -> {
                    item { HeroV4(loading, catalogError, ::refreshAll) }
                    item { GoodNewsV4(context) }
                    item { SearchV4(query) { query = it } }

                    if (bestRated.isNotEmpty()) {
                        item { SectionV4("COMUNIDAD", "Mejor valoradas") }
                        items(bestRated, key = { "best-${it.first.id}" }) { (app, stat) ->
                            AppCardV4(app, context, verifiedOnly, stat) { selected = app }
                        }
                    }

                    if (available.isNotEmpty()) {
                        item { SectionV4("DISPONIBLES AHORA", "Apps y herramientas") }
                        items(available.take(10), key = { "home-${it.id}" }) { app ->
                            AppCardV4(app, context, verifiedOnly, ratings[app.id]) { selected = app }
                        }
                    }

                    item {
                        if (termuxInstalled) {
                            TermuxScriptGalleryCard(context) { notice = it }
                        } else {
                            TermuxScriptInstallCard(
                                onFdroid = { openUrlV4(context, TERMUX_FDROID_V4) },
                                onPlay = { openUrlV4(context, TERMUX_PLAY_V4) },
                            )
                        }
                    }

                    if (upcoming.isNotEmpty()) {
                        item { SectionV4("LO QUE SE VIENE", "Proyectos en desarrollo") }
                        items(upcoming.take(8), key = { "soon-${it.id}" }) { app ->
                            AppCardV4(app, context, verifiedOnly, ratings[app.id]) { selected = app }
                        }
                    }
                }

                TabV4.APPS -> {
                    item { PageHeaderV4("CATÁLOGO", "Todas las apps", "Las valoraciones son reales y aparecen sólo cuando alguien probó y valoró una app.") }
                    item { SearchV4(query) { query = it } }
                    items(filtered, key = { "catalog-${it.id}" }) { app ->
                        AppCardV4(app, context, verifiedOnly, ratings[app.id]) { selected = app }
                    }
                }

                TabV4.UPDATES -> {
                    item { PageHeaderV4("VERSIONES", "Actualizaciones", "StoreAMO compara las apps instaladas con el catálogo y también revisa su propia versión.") }
                    item { SelfUpdateCardV4(selfUpdate, selfLoading, ::startSelfUpdate) }
                    if (updates.isNotEmpty()) {
                        item { SectionV4("ACTUALIZACIONES", "Hay versiones nuevas") }
                        items(updates, key = { "update-${it.first.id}" }) { (app, _, _) ->
                            AppCardV4(app, context, verifiedOnly, ratings[app.id]) { selected = app }
                        }
                    } else item { StatusV4("✓", "Todo al día", "No detectamos actualizaciones pendientes para las apps conocidas instaladas.") }
                }

                TabV4.SETTINGS -> {
                    item { PageHeaderV4("STOREAMO", "Ajustes", "Controlá candidates, catálogo, actualizaciones y participación comunitaria.") }
                    item {
                        ToggleV4("Permitir versiones candidate", "Las candidates siguen indicando su estado, pero podés instalarlas para probarlas.", !verifiedOnly) {
                            verifiedOnly = !it
                            settings.edit().putBoolean("verified_only", verifiedOnly).apply()
                        }
                    }
                    item {
                        ToggleV4("Mostrar desarrollo", "Incluye también proyectos que todavía no tienen APK.", showDevelopment) {
                            showDevelopment = it
                            settings.edit().putBoolean("show_development", it).apply()
                        }
                    }
                    item { ActionV4("Actualizar catálogo y valoraciones", "Trae versiones, ranking comunitario y estado actual.", "Actualizar", ::refreshAll) }
                    item { ActionV4("Apoyar DesarrollAMO", "Apoyo voluntario a desarrolladores independientes.", "Apoyar") { openUrlV4(context, SUPPORT_URL_V4) } }
                    item { SelfUpdateCardV4(selfUpdate, selfLoading, ::startSelfUpdate) }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    selected?.let { app ->
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = AmoBackground, contentColor = AmoText) {
            AppSheetV4(
                app = app,
                context = context,
                verifiedOnly = verifiedOnly,
                rating = ratings[app.id],
                onDownload = { artifact -> startArtifact(app, artifact) },
                onMigrate = { artifact ->
                    migrationPrefs.edit().putString(MIGRATION_APP, app.id).putString(MIGRATION_VERSION, artifact.version).apply()
                    runCatching { DownloadInstaller.requestOfficialUninstall(context, artifact.applicationId.orEmpty()) }
                        .onSuccess {
                            notice = "Android va a quitar ${app.name} $CLIMA_LEGACY_VERSION. Al volver, StoreAMO continuará solo con ${artifact.version}."
                            selected = null
                        }
                        .onFailure {
                            migrationPrefs.edit().clear().apply()
                            notice = "No pude abrir el desinstalador de Android: ${it.message.orEmpty()}"
                        }
                },
                onRatingsChanged = { ratingsRefresh++ },
            )
        }
    }
}

@Composable
private fun BrandV4(onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = AmoSurface2, modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("▦", color = AmoAmber, fontSize = 25.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(11.dp))
            Column {
                Row { Text("Store", fontWeight = FontWeight.Black, fontSize = 23.sp); Text("AMO", color = AmoPink, fontWeight = FontWeight.Black, fontSize = 23.sp) }
                Text("DesarrollAMO · Comunidad", color = AmoMuted, fontSize = 10.sp)
            }
        }
        Surface(onClick = onSettings, shape = CircleShape, color = AmoSurface2, modifier = Modifier.size(42.dp)) { Box(contentAlignment = Alignment.Center) { Text("⚙") } }
    }
}

@Composable
private fun HeroV4(loading: Boolean, error: String?, refresh: () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(AmoSurface, AmoSurface2, AmoAmber.copy(alpha = .20f)))).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("STOREAMO · COMUNIDAD", color = AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            Text("Probamos. Valoramos.\nMejoramos.", fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Black)
            Text("Las estrellas y comentarios nacen de personas que prueban las apps. Sin números inventados y sin convertir StoreAMO en una red social.", color = AmoMuted, fontSize = 12.sp)
            Surface(shape = RoundedCornerShape(99.dp), color = AmoAmber.copy(alpha = .12f)) {
                Text("✦ v${BuildConfig.VERSION_NAME}", color = AmoAmber, fontSize = 10.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
            }
            if (loading) Text("Actualizando catálogo…", color = AmoCyan, fontSize = 11.sp)
            error?.let { Row(verticalAlignment = Alignment.CenterVertically) { Text(it, color = AmoPink, fontSize = 10.sp, modifier = Modifier.weight(1f)); TextButton(onClick = refresh) { Text("Reintentar") } } }
        }
    }
}

@Composable
private fun GoodNewsV4(context: Context) {
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("BUENAS NUEVAS", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black)
                Text("Qué estamos mejorando", fontSize = 19.sp, fontWeight = FontWeight.Black)
                Text("Versiones, cambios y proyectos en movimiento.", color = AmoMuted, fontSize = 10.sp)
            }
            Button(onClick = { context.startActivity(Intent(context, GoodNewsActivity::class.java)) }, colors = ButtonDefaults.buttonColors(containerColor = AmoAmber, contentColor = AmoBackground)) { Text("Ver", fontWeight = FontWeight.Black) }
        }
    }
}

@Composable
private fun SearchV4(value: String, onChange: (String) -> Unit) {
    TextField(value = value, onValueChange = onChange, placeholder = { Text("Buscar apps…") }, leadingIcon = { Text("⌕", color = AmoCyan, fontSize = 22.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp))
}

@Composable
private fun SectionV4(kicker: String, title: String) {
    Column { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun PageHeaderV4(kicker: String, title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(title, fontSize = 32.sp, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 12.sp) }
}

@Composable
private fun RatingBadgeV4(stat: FeedbackRepository.RatingStats?) {
    Surface(shape = RoundedCornerShape(99.dp), color = if (stat?.ratingCount ?: 0L > 0L) AmoAmber.copy(alpha = .14f) else AmoSurface2) {
        Text(
            if (stat != null && stat.ratingCount > 0) "★ ${stat.average} (${stat.ratingCount})" else "☆ Sin valorar",
            color = if (stat != null && stat.ratingCount > 0) AmoAmber else AmoMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun AppCardV4(app: StoreApp, context: Context, verifiedOnly: Boolean, stat: FeedbackRepository.RatingStats?, onOpen: () -> Unit) {
    val artifact = androidArtifactV4(app)
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(AmoSurface2), contentAlignment = Alignment.Center) { Text(appGlyphV4(app), color = AmoCyan, fontSize = 22.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(app.name, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.tagline, color = AmoMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                RatingBadgeV4(stat)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onOpen) { Text(actionLabelV4(context, artifact, verifiedOnly), fontSize = 9.sp) }
        }
    }
}

@Composable
private fun AppSheetV4(
    app: StoreApp,
    context: Context,
    verifiedOnly: Boolean,
    rating: FeedbackRepository.RatingStats?,
    onDownload: (StoreArtifact) -> Unit,
    onMigrate: (StoreArtifact) -> Unit,
    onRatingsChanged: () -> Unit,
) {
    val artifact = androidArtifactV4(app)
    val installed = installedVersionV4(context, artifact?.applicationId)
    val migration = isKnownClimaMigration(app, installed, artifact)
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(AmoCyan, AmoViolet, AmoPink))), contentAlignment = Alignment.Center) { Text(appGlyphV4(app), color = AmoBackground, fontSize = 27.sp, fontWeight = FontWeight.Black) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(app.category.uppercase(), color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black); Text(app.name, fontSize = 27.sp, fontWeight = FontWeight.Black); RatingBadgeV4(rating) }
        }
        Text(app.description, color = AmoMuted, fontSize = 12.sp)

        if (artifact != null) {
            Surface(shape = RoundedCornerShape(18.dp), color = AmoSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Android · ${artifact.version}", fontWeight = FontWeight.Black)
                    when {
                        installed == artifact.version -> Text("Instalada · al día.", color = AmoGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        migration -> Text("Firma legacy conocida: StoreAMO puede guiar una migración única y continuar automáticamente.", color = AmoAmber, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        installed != null -> Text("Tenés $installed · hay una versión nueva.", color = AmoCyan, fontSize = 11.sp)
                        artifact.verified -> Text("Release verificada.", color = AmoGreen, fontSize = 11.sp)
                        else -> Text("Release candidate.", color = AmoMuted, fontSize = 11.sp)
                    }
                    Button(
                        onClick = { if (migration) onMigrate(artifact) else onDownload(artifact) },
                        enabled = installed == artifact.version || migration || !(verifiedOnly && !artifact.verified),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = if (migration) AmoAmber else AmoCyan, contentColor = AmoBackground),
                    ) { Text(if (migration) "Migrar una vez y actualizar" else actionLabelV4(context, artifact, verifiedOnly), fontWeight = FontWeight.Black) }
                    if (migration) Text("Android quitará la 0.2.0 y, al volver, StoreAMO descargará e instalará la línea de firma nueva. Es una sola vez.", color = AmoMuted, fontSize = 9.sp)
                    if (installed != null) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { UninstallFlowActivity.launch(context, artifact.applicationId) }, modifier = Modifier.weight(1f)) { Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black) }
                            OutlinedButton(onClick = { UninstallFlowActivity.openInfo(context, artifact.applicationId) }) { Text("ⓘ", color = AmoCyan) }
                        }
                    }
                }
            }
        } else StatusV4("◇", "Todavía en desarrollo", "Podés seguir el proyecto, pero aún no hay APK para probar ni valorar.")

        FeedbackPanelV3(app.id, canContribute = installed != null, onChanged = onRatingsChanged)

        app.repository?.let { url -> OutlinedButton(onClick = { openUrlV4(context, url) }, modifier = Modifier.fillMaxWidth()) { Text("Ver código / proyecto") } }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun SelfUpdateCardV4(latest: StoreArtifact?, loading: Boolean, onUpdate: (StoreArtifact) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("StoreAMO ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Black)
            when {
                loading -> Text("Buscando versión nueva…", color = AmoCyan, fontSize = 10.sp)
                latest != null -> {
                    Text("Disponible · ${latest.version}", color = AmoAmber, fontWeight = FontWeight.Black)
                    Text("StoreAMO descarga y verifica dentro de StoreAMO su propia actualización con el mismo flujo seguro usado para las apps. Android conserva la confirmación final.", color = AmoMuted, fontSize = 10.sp)
                    Button(onClick = { onUpdate(latest) }, modifier = Modifier.fillMaxWidth()) { Text("Actualizar dentro de StoreAMO") }
                }
                else -> Text("StoreAMO está al día.", color = AmoGreen, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun StatusV4(glyph: String, title: String, body: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) { Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text(glyph, color = AmoCyan, fontSize = 28.sp); Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 10.sp) } }
}

@Composable
private fun NoticeV4(text: String, dismiss: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = AmoSurface2) {
        Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = AmoCyan, fontSize = 10.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = dismiss) { Text("Cerrar") }
        }
    }
}

@Composable
private fun ToggleV4(title: String, body: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 10.sp) }; Switch(checked = checked, onCheckedChange = onChecked) } }
}

@Composable
private fun ActionV4(title: String, body: String, action: String, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = AmoSurface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black); Text(body, color = AmoMuted, fontSize = 10.sp) }; Button(onClick = onClick) { Text(action, fontSize = 9.sp) } } }
}
