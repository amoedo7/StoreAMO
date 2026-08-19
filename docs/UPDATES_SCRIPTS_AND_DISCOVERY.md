# StoreAMO · actualizaciones, instalación y galería de scripts

Este documento fija la arquitectura de distribución de StoreAMO. La tienda no debe necesitar una nueva versión cada vez que aparece una aplicación o cambia una release.

## 1. Instalación y actualización dentro de StoreAMO

Cada aplicación del ecosistema es dueña de su `storeamo.json` y de sus GitHub Releases. `StoreAMO-Catalog` descubre manifests válidos y publica sólo artifacts que cumplen el contrato del catálogo.

En Android, el flujo esperado es:

1. StoreAMO descubre una release nueva.
2. Comprueba compatibilidad con el dispositivo.
3. Descarga el APK desde la release oficial.
4. Verifica SHA-256 y la evidencia exigida por la política de la app.
5. Comprueba package/application id y continuidad de firma.
6. Si es instalación inicial, inicia la instalación mediante Android.
7. Si ya está instalada, solicita una actualización *in-place* para preservar datos y configuración.
8. Cuando Android permita actualización sin interacción y StoreAMO sea instalador válido, se puede completar automáticamente. En cualquier otro caso se muestra la confirmación mínima exigida por Android.

Nunca descargar un APK arbitrario, nunca sustituir una app por otra firma y nunca llamar `verified` a una release sin evidencia.

### Ajustes previstos

- Buscar actualizaciones automáticamente.
- Intervalo: al abrir / diario / semanal / manual.
- Sólo Wi-Fi para descargas automáticas.
- Permitir datos móviles.
- Descargar en segundo plano.
- Instalar automáticamente cuando Android lo permita.
- Avisar cuando Android requiera confirmación.
- Canal: estable / beta.
- `Actualizar todo` para releases verificadas.
- Historial de versiones y rollback sólo cuando exista un artifact anterior compatible y firmado por la misma identidad.

## 2. Catálogo dinámico

Un repositorio no aparece por el mero hecho de existir. Para formar parte de StoreAMO debe incluir un `storeamo.json` válido.

El catálogo se genera desde los repos, no al revés:

`repo app -> storeamo.json -> discovery -> verify -> catalog -> StoreAMO`

Esto permite agregar aplicaciones y releases sin modificar el APK de StoreAMO.

## 3. Galería de scripts

StoreAMO puede ofrecer contenido ejecutable además de aplicaciones. Los scripts son un tipo distinto de artifact y nunca deben presentarse como APK.

En Android, si `com.termux` está instalado, StoreAMO puede mostrar una sección **Scripts para tu dispositivo**.

Cada script deberá declarar como mínimo:

- id y nombre;
- propósito legible;
- shell/runtime (`bash`, `fish`, `python`, etc.);
- plataformas compatibles;
- fuente oficial;
- SHA-256;
- permisos/capacidades que necesita;
- comandos que ejecutará;
- modo (`read-only`, `diagnostic`, `changes-system`);
- versión;
- evidencia de prueba;
- política StoreAMO aplicada.

### Ejecución segura

StoreAMO no ejecutará texto remoto ciegamente. Antes de ejecutar:

1. descarga o resuelve el artifact oficial;
2. verifica hash y política;
3. muestra qué hace;
4. solicita consentimiento del usuario para la primera ejecución o para scripts con efectos;
5. usa la integración oficial `RUN_COMMAND` de Termux;
6. registra resultado/exit code cuando sea posible;
7. permite revocar el permiso de ejecución.

La integración con Termux se mantiene opt-in. StoreAMO declara únicamente visibilidad para `com.termux`, no `QUERY_ALL_PACKAGES`.

## 4. Ideas aprovechables de F-Droid / tiendas de apps

Conceptos útiles para StoreAMO, adaptados al branding DesarrollAMO:

- Recientes / novedades.
- Categorías.
- Avisos y actualizaciones.
- Actualizar todo.
- Biblioteca / instaladas por StoreAMO.
- Frecuencia de actualización.
- Wi-Fi / datos móviles.
- Canal estable/beta.
- Tema sistema, claro, dark, OLED y estilos DesarrollAMO.
- Mostrar u ocultar versiones incompatibles.
- Modo experto con metadatos técnicos, hashes, firma y reports.
- Fuentes/repositorios oficiales visibles para transparencia.
- Caché del último catálogo válido para seguir mostrando información sin conexión.

No se copiarán sin necesidad funciones como Tor, proxies o repositorios locales: sólo se añadirán si existe un caso de uso real y una política de seguridad clara.

## 5. Cerca / compartir sin Internet

Puede estudiarse más adelante una modalidad `Cerca` para compartir artifacts ya verificados entre dispositivos. La transferencia no elimina la verificación: el receptor debe comprobar el mismo hash, identidad y manifest que si lo hubiera descargado de GitHub.

## 6. Prioridad UX

La experiencia principal debe seguir siendo simple:

- detectar plataforma;
- mostrar primero lo compatible;
- `Obtener`, `Actualizar` o `Abrir` como acción principal;
- esconder detalles técnicos en `Ver más` / `Modo experto`;
- nunca obligar al usuario normal a visitar GitHub para instalar una aplicación.
