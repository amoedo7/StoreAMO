# MVP · StoreAMO

## Resultado que debe entregar
Desde un Android compatible, una persona puede instalar la semilla, llegar a StoreAMO, descubrir una app y **obtener/actualizar/abrir** un artefacto cuya identidad e integridad estén claras.

## Flujo mínimo
1. Instalar semilla 0.0.1.
2. Semilla consulta release estable oficial.
3. Verifica SHA-256.
4. Android confirma instalación.
5. StoreAMO carga catálogo.
6. Filtra por dispositivo/canal de confianza.
7. Muestra ficha y estado real.
8. Descarga artefacto.
9. Verifica hash antes de abrir PackageInstaller.
10. Tras instalación ofrece **Abrir** o **Actualizar**.

## Criterios obligatorios
- semilla recupera la Store desde cero;
- candidate y verified no se mezclan;
- sin claves privadas en APK/repo;
- no elude Play Protect ni confirmaciones del sistema;
- Store no requiere actualización para descubrir nuevas apps;
- errores de red/hash quedan visibles;
- una app sin artefacto válido no muestra un botón de instalación engañoso.

## Fuera del MVP
- pagos in-app;
- cuenta obligatoria;
- instalación silenciosa;
- catálogo ficticio.
