package com.desarrollamo.storeamo.ecosystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcosystemHub() {
    var selected by remember { mutableStateOf<EcosystemModule?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ECOSISTEMA", fontWeight = FontWeight.Black)
        Text("Una Store. Un APK. Módulos internos en lugar de proyectos desparramados.")
        EcosystemRegistry.modules.forEach { module ->
            Card(
                colors = CardDefaults.cardColors(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(module.glyph, fontWeight = FontWeight.Black)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(module.name, fontWeight = FontWeight.Black)
                        Text(module.summary)
                        Text(modeLabel(module.mode))
                    }
                    if (module.mode == ModuleMode.EMBEDDED) {
                        Button(onClick = { selected = module }) { Text("Abrir") }
                    }
                }
            }
        }
    }
    selected?.let { module ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            when (module.id) {
                "calculamo" -> CalculAmoModule()
                else -> Text("${module.name} todavía está migrando al núcleo de StoreAMO.", modifier = Modifier.padding(20.dp))
            }
        }
    }
}

private fun modeLabel(mode: ModuleMode): String = when (mode) {
    ModuleMode.EMBEDDED -> "● Integrado en StoreAMO"
    ModuleMode.MIGRATING -> "◐ Migrando a módulo interno"
    ModuleMode.TERMUX -> "⌁ Herramienta local mediante Termux"
    ModuleMode.CORE -> "◆ Núcleo/infraestructura del ecosistema"
}
