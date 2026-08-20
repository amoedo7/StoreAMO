# StoreAMO 0.0.1 · Bootstrap

La `0.0.1` es la única StoreAMO que una persona debería necesitar descargar manualmente.

Su APK no contiene catálogo, Termux, galería, biblioteca ni lógica de otras aplicaciones. Sólo hace esto:

```text
abrir StoreAMO 0.0.1
        ↓
consultar Releases oficiales de amoedo7/StoreAMO
        ↓
elegir la versión estable más nueva
        ↓
descargar APK por HTTPS
        ↓
verificar SHA-256
        ↓
verificar applicationId com.desarrollamo.storeamo
        ↓
PackageInstaller de Android
        ↓
la StoreAMO completa reemplaza a 0.0.1
```

## Identidad

La Release pública debe firmarse con la misma identidad estable que todas las versiones futuras de StoreAMO. Nunca se publica una clave privada en Git. La rama debug histórica `com.desarrollamo.storeamo.debug` no se usa como bootstrap público porque no podría actualizar de forma segura a la identidad estable.

- `applicationId`: `com.desarrollamo.storeamo`
- `versionName`: `0.0.1`
- `versionCode`: `1`
- versión mínima que acepta como StoreAMO completa: `0.5.0`

El descubrimiento de versiones no queda fijado a `0.5.0.x`: entiende tags numéricos de tres o cuatro segmentos para poder seguir encontrando versiones futuras.

## Release permanente

El workflow `StoreAMO bootstrap 0.0.1` compila y prueba una variante aislada en cada cambio. La publicación se ejecuta manualmente sólo cuando los secretos de firma estable existen y crea una Release inmutable con tag `bootstrap` y un asset de nombre fijo:

```text
StoreAMO.apk
```

Eso permite compartir para siempre un único enlace de GitHub:

```text
https://github.com/amoedo7/StoreAMO/releases/download/bootstrap/StoreAMO.apk
```

La Release `bootstrap` no debe reemplazarse por versiones posteriores: el objetivo es que la propia `0.0.1` haga el salto a la StoreAMO más nueva.
