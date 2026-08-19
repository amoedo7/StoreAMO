# StoreAMO · política de publicación v1

StoreAMO separa claramente **estar en desarrollo**, **ser candidata** y **ser descargable como release verificada**.

## Estados

```text
development
    ↓
candidate
    ↓
verified
    ↓
deprecated
```

## Requisitos mínimos para `verified`

Una release debe tener, como mínimo:

1. repositorio fuente identificable;
2. versión explícita y no reutilizada;
3. artefacto descargable por HTTPS;
4. SHA-256 registrado;
5. identidad de paquete/binario comprobada cuando la plataforma lo permita;
6. firma/certificado coherente con la línea de actualizaciones cuando corresponda;
7. permisos esperados documentados;
8. smoke tests del proyecto;
9. reporte StoreAMO-Verify asociado;
10. changelog breve y entendible.

`StoreAMO Verified` no significa “sin bugs”. Significa que existe evidencia reproducible de los controles declarados.

## Canales

- **stable**: opción por defecto para usuarios.
- **beta**: versiones de prueba elegidas explícitamente.

Una beta nunca reemplaza silenciosamente una estable en el catálogo.

## Android

Para poder actualizar una app Android sin romper la instalación deben mantenerse:

- `applicationId`;
- continuidad de firma;
- `versionCode` creciente.

StoreAMO no almacena claves privadas de firma en la aplicación ni en el catálogo público.

## Permisos

Una nueva versión que agregue permisos sensibles debe declararlo en el changelog y en su manifiesto StoreAMO. El verificador debe poder marcar diferencias entre permisos esperados y permisos observados.

## Rollback

Una release retirada permanece trazable, pero deja de ser la recomendada. El catálogo puede apuntar nuevamente a la última versión verificada anterior sin borrar evidencia histórica.

## Privacidad

La tienda no necesita telemetría para descargar aplicaciones. La detección de plataforma se realiza localmente. Cualquier analítica futura debe ser opcional, explícita y separada de la instalación.
