package com.desarrollamo.storeamo.ecosystem

enum class ModuleMode { EMBEDDED, MIGRATING, TERMUX, CORE }

data class EcosystemModule(
    val id: String,
    val name: String,
    val glyph: String,
    val summary: String,
    val mode: ModuleMode,
)

object EcosystemRegistry {
    val modules = listOf(
        EcosystemModule("calculamo", "CalculAMO", "=", "Calculadora nativa dentro de StoreAMO.", ModuleMode.EMBEDDED),
        EcosystemModule("chessi", "Chessi", "♟", "Ajedrez + Stockfish + accesibilidad.", ModuleMode.MIGRATING),
        EcosystemModule("generalaamo", "GeneralaAMO", "⚄", "Juego de Generala del ecosistema.", ModuleMode.MIGRATING),
        EcosystemModule("alarmamo", "AlarmAMO", "⏰", "Alarmas y recordatorios.", ModuleMode.MIGRATING),
        EcosystemModule("calendamo", "CalendAMO", "▣", "Calendario AMO.", ModuleMode.MIGRATING),
        EcosystemModule("calendaramo", "CalendarAMO", "□", "Calendario experimental histórico.", ModuleMode.MIGRATING),
        EcosystemModule("finamo", "FinAMO", "$", "Herramientas financieras.", ModuleMode.MIGRATING),
        EcosystemModule("mesaamo", "MesaAMO", "M", "Mesa de trabajo multi-IA.", ModuleMode.MIGRATING),
        EcosystemModule("nfcamo", "NfcAMO", "N", "Herramientas NFC.", ModuleMode.MIGRATING),
        EcosystemModule("pagamo", "PagAMO", "P", "Pagos y utilidades relacionadas.", ModuleMode.MIGRATING),
        EcosystemModule("presupuestamo", "PresupuestAMO", "%", "Presupuestos y cotizaciones.", ModuleMode.MIGRATING),
        EcosystemModule("transferamo", "TransferAMO", "⇄", "Transferencias con cámara/ubicación.", ModuleMode.MIGRATING),
        EcosystemModule("wifiamo", "WifiAMO", "⌁", "Diagnóstico y utilidades Wi-Fi.", ModuleMode.MIGRATING),
        EcosystemModule("installamo", "InstallAMO", "↓", "Instalación guiada de apps AMO.", ModuleMode.MIGRATING),
        EcosystemModule("circuitamo", "CircuitAMO", "C", "Fábrica de apps y utilidades en Termux.", ModuleMode.TERMUX),
        EcosystemModule("claroamo", "ClaroAMO", "CL", "Herramientas Python locales.", ModuleMode.TERMUX),
        EcosystemModule("controlamo", "ControlAMO", "◎", "Inventario, radar y control del ecosistema.", ModuleMode.TERMUX),
        EcosystemModule("damo", "DAMO", "D", "Orquestador y agentes locales.", ModuleMode.TERMUX),
        EcosystemModule("generalamo", "GeneralAMO", "G", "Servicios y utilidades generales.", ModuleMode.TERMUX),
        EcosystemModule("rankingiamo", "RankingIAMO", "R", "Ranking de agentes e ingresos verificados.", ModuleMode.TERMUX),
        EcosystemModule("cerebramo", "CerebrAMO", "AI", "Núcleo multi-IA.", ModuleMode.CORE),
        EcosystemModule("depositamo", "DepositAMO", "▤", "Depósito común del ecosistema.", ModuleMode.CORE),
        EcosystemModule("raizamo", "RaizAMO", "⌂", "Mapa y contratos raíz del ecosistema.", ModuleMode.CORE),
        EcosystemModule("vaultamo", "VaultAMO", "V", "Bóveda y secretos; nunca se expone directamente.", ModuleMode.CORE),
        EcosystemModule("webamo", "WebAMO", "W", "Capa web del ecosistema.", ModuleMode.CORE),
    )
}
