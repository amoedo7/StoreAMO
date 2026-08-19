package com.desarrollamo.storeamo

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.desarrollamo.storeamo.theme.AmoBackground
import com.desarrollamo.storeamo.theme.AmoCyan
import com.desarrollamo.storeamo.theme.AmoGreen
import com.desarrollamo.storeamo.theme.AmoMuted
import com.desarrollamo.storeamo.theme.AmoSurface

private const val TERMUX_RUN_PERMISSION = "com.termux.permission.RUN_COMMAND"
private const val TERMUX_PACKAGE = "com.termux"
private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
private const val TERMUX_ACTION = "com.termux.RUN_COMMAND"
private const val REQUEST_TERMUX_RUN = 7412
private const val TERMUX_SETUP = "mkdir -p ~/.termux; grep -q '^allow-external-apps=true$' ~/.termux/termux.properties 2>/dev/null || printf '\\nallow-external-apps=true\\n' >> ~/.termux/termux.properties; echo 'StoreAMO: allow-external-apps=true listo'"

data class AmoTermuxScript(
    val id: String,
    val name: String,
    val description: String,
    val source: String,
    val sha256: String,
    val risk: String,
)

private val amoScripts = listOf(
    AmoTermuxScript(
        id = "midispositivo",
        name = "MiDispositivo rápido",
        description = "Sistema, CPU, memoria, disco e IP local. Sin consulta externa.",
        source = "https://raw.githubusercontent.com/amoedo7/IdeAMO/a1ca34a270a1dc10336c686b151b3d85266c09aa/scripts-inbox/storeamo/midispositivo.py",
        sha256 = "8f3f5440f4589baae07983672d643d7bbc10935a8b45c1e1a0a89e18f61a9974",
        risk = "Solo lectura",
    ),
    AmoTermuxScript(
        id = "mired",
        name = "MiRed rápido",
        description = "Prueba DNS, TCP y HTTPS sin escanear dispositivos de tu red.",
        source = "https://raw.githubusercontent.com/amoedo7/IdeAMO/a1ca34a270a1dc10336c686b151b3d85266c09aa/scripts-inbox/storeamo/mired.py",
        sha256 = "69848beaa674b829ec7ec31ad379ee9f8127f14445672480a3b0beaba1c5ce9b",
        risk = "Red · solo lectura",
    ),
    AmoTermuxScript(
        id = "misistema",
        name = "MiSistema rápido",
        description = "Detecta Python, Node, Java, Git, shells y otras herramientas disponibles.",
        source = "https://raw.githubusercontent.com/amoedo7/IdeAMO/a1ca34a270a1dc10336c686b151b3d85266c09aa/scripts-inbox/storeamo/misistema.py",
        sha256 = "5d916952bb4d4d47d8734f95c84e6e56eeeed62759be1e9f28b24d85d8a9fc4a",
        risk = "Solo lectura",
    ),
)

private fun hasTermuxRunPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, TERMUX_RUN_PERMISSION) == PackageManager.PERMISSION_GRANTED

private fun requestTermuxRunPermission(context: Context): Boolean {
    val activity = context as? Activity ?: return false
    ActivityCompat.requestPermissions(activity, arrayOf(TERMUX_RUN_PERMISSION), REQUEST_TERMUX_RUN)
    return true
}

private fun copySetupAndOpenTermux(context: Context) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("StoreAMO Termux setup", TERMUX_SETUP))
    context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)?.let {
        context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun runScriptInTermux(context: Context, script: AmoTermuxScript): Result<Unit> = runCatching {
    check(hasTermuxRunPermission(context)) { "Falta el permiso ‘Ejecutar comandos en Termux’." }

    val shell = """
        set -eu
        URL='${script.source}'
        EXPECTED='${script.sha256}'
        TMP="${'$'}(mktemp)"
        trap 'rm -f "${'$'}TMP"' EXIT
        command -v curl >/dev/null 2>&1 || { echo 'StoreAMO: falta curl'; exit 127; }
        command -v sha256sum >/dev/null 2>&1 || { echo 'StoreAMO: falta sha256sum'; exit 127; }
        PY="${'$'}(command -v python || command -v python3 || true)"
        [ -n "${'$'}PY" ] || { echo 'StoreAMO: falta Python'; exit 127; }
        echo 'StoreAMO · ${script.name}'
        echo 'Descargando fuente fijada…'
        curl -fsSL "${'$'}URL" -o "${'$'}TMP"
        ACTUAL="${'$'}(sha256sum "${'$'}TMP" | awk '{print ${'$'}1}')"
        [ "${'$'}ACTUAL" = "${'$'}EXPECTED" ] || { echo 'BLOQUEADO: SHA-256 no coincide'; exit 65; }
        echo 'Integridad OK · ejecutando…'
        echo
        "${'$'}PY" "${'$'}TMP"
        echo
        echo 'StoreAMO · ejecución terminada'
    """.trimIndent()

    val intent = Intent().apply {
        setClassName(TERMUX_PACKAGE, TERMUX_SERVICE)
        action = TERMUX_ACTION
        putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
        putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", shell))
        putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
        putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
        putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
    }
    context.startService(intent) ?: error("Termux no aceptó el comando")
}

@Composable
fun TermuxScriptGalleryCard(context: Context, onNotice: (String) -> Unit) {
    val permissionGranted = hasTermuxRunPermission(context)
    Surface(shape = RoundedCornerShape(22.dp), color = AmoSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TERMUX DETECTADO", color = AmoGreen, fontSize = 9.sp, fontWeight = FontWeight.Black)
            Text("Scripts AMO", fontSize = 22.sp, fontWeight = FontWeight.Black)
            Text("Ya no es una maqueta: cada script usa una fuente fijada por commit y StoreAMO comprueba su SHA-256 antes de ejecutarlo.", color = AmoMuted, fontSize = 12.sp)

            if (!permissionGranted) {
                Surface(shape = RoundedCornerShape(16.dp), color = AmoBackground.copy(alpha = .35f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Preparar Termux una sola vez", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        Text("1) Permití a StoreAMO ejecutar comandos en Termux. 2) Copiá la configuración y pegala en Termux.", color = AmoMuted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (!requestTermuxRunPermission(context)) onNotice("No pude abrir el permiso de Termux desde este contexto.")
                            }, colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground)) {
                                Text("Dar permiso", fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                            OutlinedButton(onClick = {
                                copySetupAndOpenTermux(context)
                                onNotice("Comando copiado. Pegalo una vez en Termux y volvé a StoreAMO.")
                            }) { Text("Configurar", fontSize = 10.sp) }
                        }
                    }
                }
            }

            amoScripts.forEach { script ->
                Surface(shape = RoundedCornerShape(16.dp), color = AmoBackground.copy(alpha = .25f)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(script.name, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Text(script.description, color = AmoMuted, fontSize = 10.sp)
                            Text("${script.risk} · SHA ${script.sha256.take(8)}…", color = AmoGreen, fontSize = 9.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                runScriptInTermux(context, script)
                                    .onSuccess { onNotice("${script.name}: enviado a Termux con verificación SHA-256.") }
                                    .onFailure { onNotice("${script.name}: ${it.message ?: "no se pudo ejecutar"}") }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmoCyan, contentColor = AmoBackground),
                        ) { Text("Ejecutar", fontSize = 10.sp, fontWeight = FontWeight.Black) }
                    }
                }
            }
        }
    }
}
