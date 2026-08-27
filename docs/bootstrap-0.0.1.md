# StoreAMO Bootstrap 0.0.1

`0.0.1` es la semilla canónica e inmutable de StoreAMO para instalaciones desde cero.

## Identidad

- Android package: `com.desarrollamo.storeamo.debug`
- versionName: `0.0.1`
- versionCode: `1`
- firma: la misma clave canónica usada por la línea estable `0.4.3.x`
- release: `bootstrap-v0.0.1`

La identidad compartida permite que Android acepte una versión estable posterior como actualización in-place, sin crear una segunda StoreAMO.

## Comportamiento

Al abrir una build bootstrap:

1. `BootstrapActivity` detecta `BuildConfig.BOOTSTRAP_SEED`;
2. `SelfUpdateRepository` consulta las releases oficiales de `amoedo7/StoreAMO`;
3. sólo acepta tags estables `0.4.3.x` con APK y SHA-256 válido;
4. `DownloadInstaller` descarga por HTTPS y verifica el artefacto antes de pedir la instalación;
5. Android conserva la última confirmación de instalación como frontera de seguridad del sistema operativo.

## Inmutabilidad

La release `bootstrap-v0.0.1` no se reemplaza automáticamente. Si ya existe, el workflow termina sin sobreescribirla.

El bootstrap puede seguir encontrando versiones futuras de la línea estable sin ser reconstruido mientras el contrato de releases `0.4.3.x` permanezca compatible.

## Verificación

`.github/workflows/bootstrap-001.yml` comprueba antes de publicar:

- regresiones de instalación y actualización;
- build Android;
- package/version exactos;
- firma APK contra la clave canónica;
- integridad ZIP;
- SHA-256 del artefacto.
