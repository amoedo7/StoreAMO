# StoreAMO SuperApp

Objetivo: una sola aplicación Android y un solo repositorio canónico para el ecosistema de uso diario.

## Regla de migración
1. Identificar la carpeta canónica de cada familia.
2. Convertir la función en un módulo interno de StoreAMO.
3. Compilar y probar el módulo dentro del APK debug.
4. Comparar funciones con la app independiente.
5. Recién entonces retirar APK, source y backups externos reemplazados.

Nunca se eliminan claves, recovery, evidencia de firma ni datos de usuario durante una migración.

## Modos
- `EMBEDDED`: ya funciona dentro del APK StoreAMO.
- `MIGRATING`: app Android detectada y pendiente de integración.
- `TERMUX`: función local que seguirá usando un puente controlado a Termux.
- `CORE`: infraestructura que StoreAMO muestra/coordina pero no expone como app común.

## Primera integración 0.4.3.88
- CalculAMO: módulo nativo embebido.
- 26 familias registradas en `EcosystemRegistry`.
- nueva pestaña `Ecosistema` en V4.
- StoreAMO estable 0.4.3.87 permanece intacta mientras se prueba la debug.
