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
    val arguments: String = "",
)

private val amoScripts = listOf(
    AmoTermuxScript(
        id = "midispositivo",
        name = "MiDispositivo",
        description = "Sistema, CPU, memoria, disco e IP local. Sin consulta externa.",
        source = "https://raw.githubusercontent.com/amoedo7/MiDispositivo/c4ea63614fb4084c2c05c5919386ae76a818f1d8/midispositivo.py",
        sha256 = "077fd5c21fad1853d9bd75e46a1b8ebe46920f393bf268c2637f1598445e68d7",
        risk = "Solo lectura",
    ),
    AmoTermuxScript(
        id = "mired",
        name = "MiRed",
        description = "Prueba DNS, TCP y HTTPS sin escanear dispositivos de tu red.",
        source = "https://raw.githubusercontent.com/amoedo7/MiRed/1e8dc708ffc0dfdeb276325537abf4767f93ec5e/mired.py",
        sha256 = "6e025febfd9af3a8e87bb68efa93ab68f0205e5a802bb2147aecb51261cc752d",
        risk = "Red · solo lectura",
    ),
    AmoTermuxScript(
        id = "misistema",
        name = "MiSistema",
        description = "Detecta Python, Node, Java, Git, shells y otras herramientas disponibles.",
        source = "https://raw.githubusercontent.com/amoedo7/MiSistema/e637b18346dfab56ebfd638aea08129edf621260/misistema.py",
        sha256 = "c79d14cd3d08f58f5d3b44a1410efaa2184a5425df05345c9d9ccd832a058e17",
        risk = "Solo lectura",
    ),
    AmoTermuxScript(
        id = "miarchivos",
        name = "MiArchivos",
        description = "Analiza hasta 5.000 archivos del hogar de Termux sin modificarlos.",
        source = "https://raw.githubusercontent.com/amoedo7/MiArchivos/8d77e994c4b9a930b0e462d911e8600148a540c0/miarchivos.py",
        sha256 = "0cb132da05e810068ce46df512989e34963dc5461b3bc89099717a64415c1056",
        risk = "Archivos · solo lectura",
        arguments = "--max-files 5000",
    ),
    AmoTermuxScript(
        id = "miapi",
        name = "MiAPI",
        description = "Ejecuta una inspección HTTP/JSON real y muestra tiempos, cabeceras y estado.",
        source = "https://raw.githubusercontent.com/amoedo7/MiAPI/0d75ea5b0ce460e657b39a7fec7e1bbc2cf4be61/miapi.py",
        sha256 = "90930bcac2b85f290502ab877495e6cc271549ce30666f4df6a9e57d6373c987",
        risk = "Red · lectura de ejemplo.com",
        arguments = "https://example.com",
    ),
    AmoTermuxScript(
        id = "diagnosticoamo",
        name = "DiagnosticoAMO",
        description = "Genera un diagnóstico demostrativo con puntuación y recomendaciones.",
        source = "https://raw.githubusercontent.com/amoedo7/DiagnosticoAMO/a58162357d3549a19a4ca6e012a56e40a1af50ab/diagnosticoamo.py",
        sha256 = "b4b74beb37ce4ab54180e96a22b41136c9a24f410ee6b41f48a0c11f07a524f8",
        risk = "Local · datos de demostración",
        arguments = "--demo",
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
        "${'$'}PY" "${'$'}TMP" ${script.arguments}
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
