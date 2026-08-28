<div align="center">

# StoreAMO

**La tienda del ecosistema DesarrollAMO.**

`Android` · `device-aware` · `StoreAMO Verified` · `GitHub Releases` · `Material 3`

## [⬇ Descargar semilla StoreAMO 0.0.1](https://github.com/amoedo7/StoreAMO/releases/download/seed-v0.0.1/StoreAMO-0.0.1.apk)

**La semilla 0.0.1 es la puerta permanente para empezar desde cero.**

</div>

---

## Semilla canónica 0.0.1

StoreAMO tiene una única semilla oficial e inmutable: **StoreAMO 0.0.1**.

Su interfaz contiene únicamente **Actualizaciones**. Su trabajo es mínimo y estable:

1. pedir a Android autorización para **instalar desde esta fuente**;
2. descargar únicamente la release estable oficial de StoreAMO;
3. verificar el APK contra su **SHA-256** publicado;
4. entregar el APK a `PackageInstaller`;
5. dejar siempre a Android la confirmación final visible.

La semilla declara sólo los permisos necesarios para ese trabajo: `INTERNET` y `REQUEST_INSTALL_PACKAGES`. No necesita almacenamiento, accesibilidad, overlay, contactos, `QUERY_ALL_PACKAGES` ni permisos de borrado.

Las antiguas pruebas `StoreAMO Install 0.0.5/0.0.6` quedan únicamente como historial de desarrollo. **No son la puerta oficial del ecosistema.**

## StoreAMO principal

StoreAMO es la puerta de entrada a las aplicaciones, herramientas y futuros scripts del ecosistema DesarrollAMO.

```text
StoreAMO 0.0.1 (semilla)
      ↓
StoreAMO estable
      ↓
StoreAMO-Catalog
      ↓
apps del ecosistema
      ↓
OBTENER / ACTUALIZAR / ABRIR
```

La Store principal y la semilla tienen responsabilidades distintas: la semilla sólo recupera/actualiza la Store; la Store principal administra el catálogo y las apps.

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

La Store principal usa números visibles, `versionCode` creciente y una identidad de firma estable. La semilla 0.0.1 no cambia de versión: existe para recuperar la Store desde cero y llevar al usuario a la release estable vigente.

## Scripts AMO

StoreAMO detecta únicamente `com.termux` como integración explícita. La galería de scripts se alimenta de piezas revisadas que comienzan en `IdeAMO`, con descripción, origen e integridad antes de ofrecer ejecución.

## Repos relacionados

- [`StoreAMO-Catalog`](https://github.com/amoedo7/StoreAMO-Catalog) — descubrimiento desde `storeamo.json` y Releases.
- [`StoreAMO-Verify`](https://github.com/amoedo7/StoreAMO-Verify) — controles de evidencia e integridad.
- [`StoreAMO-Web`](https://github.com/amoedo7/StoreAMO-Web) — experiencia web multiplataforma.
- [`IdeAMO`](https://github.com/amoedo7/IdeAMO) — ideas, scripts y prototipos todavía sin repo oficial.

---

**DesarrollAMO** · un ecosistema, una tienda, una semilla permanente.
