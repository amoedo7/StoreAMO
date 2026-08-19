# CI signing

`storeamo-debug.keystore.b64` contiene una **clave de depuración pública y deliberadamente no secreta** usada sólo por el variant `debug` (`com.desarrollamo.storeamo.debug`).

Su objetivo es que los APK de prueba producidos por distintos runners de GitHub Actions mantengan la misma identidad y puedan actualizarse entre sí durante el desarrollo.

## Nunca usar para producción

La futura release estable `com.desarrollamo.storeamo` debe firmarse con una clave privada distinta, almacenada fuera del repositorio y suministrada al pipeline mediante un mecanismo secreto apropiado.

La clave debug no otorga confianza `StoreAMO Verified` ni debe usarse para firmar releases estables.
