<div align="center">

# StoreAMO

**La tienda del ecosistema DesarrollAMO.**

`Android` · `device-aware` · `StoreAMO Verified` · `GitHub Releases` · `Material 3`

</div>

---

StoreAMO es la puerta de entrada a las aplicaciones, herramientas y futuros scripts del ecosistema DesarrollAMO.

```text
repo de cada app
      ↓
storeamo.json
      ↓
GitHub Release
      ↓
StoreAMO-Catalog
      ↓
StoreAMO
      ↓
OBTENER / ACTUALIZAR / ABRIR
```

## V3 Android

La interfaz V3 prioriza la función de tienda sobre el repositorio de código:

- **Equipo DesarrollAMO** separado del catálogo público;
- botón `Obtener`, `Actualizar` o `Abrir` cuando existe un artefacto Android;
- StoreAMO no aparece como una aplicación normal dentro de sí misma;
- estado de StoreAMO dentro de Ajustes/Actualizaciones;
- sección **Lo que se viene** para proyectos todavía no instalables;
- estilos `Sistema`, `Dark`, `Light`, `Estetic`, `Blanco y negro`, `OLED`, `Ocean` y `Sunset`;
- detección acotada de Termux sin `QUERY_ALL_PACKAGES`;
- recomendación de Termux/F-Droid cuando no está instalado;
- acceso voluntario a CobrAMO desde `Apoyar DesarrollAMO`.

## Confianza

Una app puede estar en `development`, `candidate` o `verified`. StoreAMO no confunde “está publicada” con “ya fue verificada”. El usuario puede mantener activado **Sólo versiones verificadas** o entrar deliberadamente en modo de prueba para candidatos.

Las descargas Android se realizan por HTTPS y el APK se compara contra su SHA-256 antes de abrir el instalador del sistema.

## Actualizaciones

La línea `0.4.3.N` usa sólo números visibles, un `versionCode` creciente y una identidad de firma de desarrollo fijada explícitamente. StoreAMO V3 usa `PackageInstaller`, evita el selector genérico “Abrir con” y valida identidad del APK antes de solicitar la instalación.

### Self-update probe

La línea 0.4.3 se prueba con dos builds consecutivas: una base limpia y una posterior que debe aparecer sola en **Actualiz.**. El objetivo es demostrar `GitHub Release → StoreAMO detecta → descarga → verifica → actualizar encima` sin volver a Termux.

## Scripts AMO

StoreAMO detecta únicamente `com.termux` como integración explícita. La galería de scripts se alimenta de piezas revisadas que comienzan en `IdeAMO`, con descripción, origen e integridad antes de ofrecer ejecución.

## Repos relacionados

- [`StoreAMO-Catalog`](https://github.com/amoedo7/StoreAMO-Catalog) — descubrimiento desde `storeamo.json` y Releases.
- [`StoreAMO-Verify`](https://github.com/amoedo7/StoreAMO-Verify) — controles de evidencia e integridad.
- [`StoreAMO-Web`](https://github.com/amoedo7/StoreAMO-Web) — experiencia web multiplataforma.
- [`StoreAMO-Install`](https://github.com/amoedo7/StoreAMO-Install) — comandos e instalación multiplataforma.
- [`IdeAMO`](https://github.com/amoedo7/IdeAMO) — ideas, scripts y prototipos todavía sin repo oficial.

---

**DesarrollAMO** · un ecosistema, una tienda, varias plataformas.
