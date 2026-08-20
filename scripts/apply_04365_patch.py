#!/usr/bin/env python3
from pathlib import Path

path = Path('app/src/main/java/com/desarrollamo/storeamo/MainActivityV3.kt')
text = path.read_text(encoding='utf-8')


def replace_once(old: str, new: str, label: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected exactly one match, found {count}')
    text = text.replace(old, new, 1)


if 'private fun GoodNewsEntryV3' in text and 'Intent.ACTION_DELETE' in text:
    print('PATCH_ALREADY_APPLIED')
    raise SystemExit(0)

replace_once(
'''private fun openInstalledV3(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
''',
'''private fun openInstalledV3(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    context.packageManager.getLaunchIntentForPackage(packageName)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun uninstallInstalledV3(context: Context, packageName: String?) {
    if (packageName.isNullOrBlank()) return
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
''',
'add uninstall helper',
)

replace_once(
'''                TabV3.HOME -> {
                    item { HeroV3(loading, catalogError, ::refreshEverything) }
                    item { SearchV3(query) { query = it } }
                    val featured = teamApps.firstOrNull { it.id == "plataformamo" }
''',
'''                TabV3.HOME -> {
                    item { HeroV3(loading, catalogError, ::refreshEverything) }
                    item { GoodNewsEntryV3(context) }
                    item { SearchV3(query) { query = it } }
                    val featured = teamApps.firstOrNull { it.id == "plataformamo" }
''',
'insert Buenas Nuevas on Home',
)

replace_once(
'''                        items(installedApps, key = { "installed-${it.first.id}" }) { (app, artifact, installed) ->
                            InstalledCardV3(app, artifact, installed) { selected = app }
                        }
''',
'''                        items(installedApps, key = { "installed-${it.first.id}" }) { (app, artifact, installed) ->
                            InstalledCardV3(
                                app = app,
                                artifact = artifact,
                                installed = installed,
                                onOpen = { selected = app },
                                onUninstall = { uninstallInstalledV3(context, artifact.applicationId) },
                            )
                        }
''',
'wire uninstall in installed list',
)

replace_once(
'''@Composable
private fun SectionV3(kicker: String, title: String) {
    Column { Text(kicker, color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Black) }
}
''',
'''@Composable
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
                .background(Brush.linearGradient(listOf(AmoSurface2, AmoCyan.copy(alpha = .16f), AmoPink.copy(alpha = .15f))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("BUENAS NUEVAS", color = AmoCyan, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.1.sp)
            Text("El ecosistema se está moviendo", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Versiones, mejoras y apps que están avanzando, explicadas acá sin tener que navegar GitHub.", color = AmoMuted, fontSize = 12.sp)
            Button(
                onClick = { context.startActivity(Intent(context, GoodNewsActivity::class.java)) },
                colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground),
            ) { Text("Ver Buenas Nuevas", fontWeight = FontWeight.Black) }
        }
    }
}
''',
'add Buenas Nuevas home card',
)

replace_once(
'''@Composable
private fun InstalledCardV3(app: StoreApp, artifact: StoreArtifact, installed: String, onOpen: () -> Unit) {
    val upToDate = installed == artifact.version
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = AmoSurface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(glyphV3(app), color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(44.dp))
            Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Black); Text("Instalada · $installed", color = AmoMuted, fontSize = 10.sp) }
            Text(if (upToDate) "Al día" else "Actualizar", color = if (upToDate) AmoGreen else AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
        }
    }
}
''',
'''@Composable
private fun InstalledCardV3(
    app: StoreApp,
    artifact: StoreArtifact,
    installed: String,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    val upToDate = installed == artifact.version
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(glyphV3(app), color = AmoCyan, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(44.dp))
                Column(Modifier.weight(1f)) { Text(app.name, fontWeight = FontWeight.Black); Text("Instalada · $installed", color = AmoMuted, fontSize = 10.sp) }
                Text(if (upToDate) "Al día" else "Actualizar", color = if (upToDate) AmoGreen else AmoCyan, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            OutlinedButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
            }
        }
    }
}
''',
'add Desinstalar button to installed card',
)

replace_once(
'''                    Button(onClick = { onDownload(artifact) }, enabled = installed == artifact.version || !(verifiedOnly && !artifact.verified), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text(actionLabelV3(context, artifact, verifiedOnly), fontWeight = FontWeight.Black) }
''',
'''                    Button(onClick = { onDownload(artifact) }, enabled = installed == artifact.version || !(verifiedOnly && !artifact.verified), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) { Text(actionLabelV3(context, artifact, verifiedOnly), fontWeight = FontWeight.Black) }
                    if (installed != null) {
                        OutlinedButton(onClick = { uninstallInstalledV3(context, artifact.applicationId) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Desinstalar", color = AmoPink, fontWeight = FontWeight.Black)
                        }
                    }
''',
'add Desinstalar button to app sheet',
)

path.write_text(text, encoding='utf-8')
print('STOREAMO_04365_PATCH_APPLIED')
