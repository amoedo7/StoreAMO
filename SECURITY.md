# StoreAMO · Security

StoreAMO es infraestructura de distribución. Un fallo en la tienda puede afectar a otras aplicaciones, por eso su frontera de confianza es más estricta que la de una demo común.

## Principios

- ningún token, contraseña o clave privada dentro del APK;
- catálogo y descargas únicamente por HTTPS;
- una descarga Android se compara con el SHA-256 del catálogo antes de ofrecer instalación;
- `StoreAMO Verified` requiere evidencia previa de StoreAMO-Verify;
- StoreAMO no instala APK silenciosamente: Android conserva su confirmación normal;
- no se usa `MANAGE_EXTERNAL_STORAGE` ni `QUERY_ALL_PACKAGES` para navegar la tienda;
- los APK se descargan al espacio externo propio de StoreAMO, no se recorre todo el almacenamiento;
- una release retirada deja de recomendarse sin borrar su trazabilidad histórica.

## Claves de firma

Las claves privadas de firma de aplicaciones no pertenecen al repositorio, al catálogo ni a StoreAMO. Se almacenan como secretos de CI y se materializan sólo durante el job que firma el artefacto.

Un keystore que haya sido publicado, incluso codificado en Base64, se considera expuesto. Borrarlo del último commit no recupera su confidencialidad. La línea estable de StoreAMO debe usar una identidad nueva y segura; la identidad debug anterior queda limitada a pruebas/migración.

StoreAMO publica únicamente el fingerprint SHA-256 del certificado, que puede utilizarse para verificar que una actualización pertenece a la identidad esperada sin revelar la clave privada.

## Modelo de confianza

```text
fuente privada o pública
        ↓
CI con permisos mínimos
        ↓
build + firma con Secrets
        ↓
artefacto + SHA-256 + fingerprint público
        ↓
StoreAMO-Verify
        ↓
StoreAMO-Catalog
        ↓
StoreAMO
        ↓
verificación local
        ↓
instalador del sistema operativo
```

## Qué NO significa Verified

`Verified` no afirma que una aplicación no tenga bugs ni reemplaza una auditoría de seguridad. Afirma solamente que los controles declarados para esa release tienen evidencia reproducible y que el artefacto distribuido coincide con el esperado.

## Reportar un problema

No publiques credenciales, tokens, claves privadas ni datos personales en un Issue público. Para fallos que no involucren secretos puede abrirse un Issue describiendo versión, dispositivo, pasos de reproducción y resultado observado.
