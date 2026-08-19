<div align="center">

# StoreAMO

**La tienda del ecosistema DesarrollAMO.**

`Android` · `device-aware` · `StoreAMO Verified` · `GitHub Releases` · `Material 3`

</div>

---

StoreAMO deja atrás el enfoque de “escanear APK/ZIP del teléfono”. La nueva arquitectura está pensada como una tienda real del ecosistema:

```text
StoreAMO Catalog
      ↓
detectar dispositivo
      ↓
mostrar apps compatibles primero
      ↓
ficha + versión + verificación
      ↓
OBTENER / ACTUALIZAR / ABRIR
      ↓
VER MÁS → otras plataformas
```

## Diseño

Tomamos de las tiendas maduras las ideas que funcionan —inicio, búsqueda, categorías, actualizaciones, biblioteca y configuración— pero **no copiamos Google Play**. StoreAMO usa branding propio de DesarrollAMO:

- azul noche `#06101C`;
- cyan `#67D2FF`;
- rosa `#F16AB5`;
- violeta `#8C74FF`;
- superficies oscuras y jerarquía visual limpia.

## Seguridad y privacidad

La nueva base elimina del corazón del producto los permisos de inventario masivo del StoreAMO anterior. No usa `MANAGE_EXTERNAL_STORAGE` ni `QUERY_ALL_PACKAGES` para navegar el catálogo. Las descargas públicas no necesitan token de GitHub.

La instalación de APK sigue requiriendo la confirmación normal de Android. StoreAMO no intenta instalar silenciosamente.

## Repos relacionados

- [`StoreAMO-Catalog`](https://github.com/amoedo7/StoreAMO-Catalog) — fuente pública de apps y artefactos.
- [`StoreAMO-Verify`](https://github.com/amoedo7/StoreAMO-Verify) — evidencia e integridad antes de marcar una release como verificada.
- [`StoreAMO-Web`](https://github.com/amoedo7/StoreAMO-Web) — la misma tienda desde un navegador.

## Estado

**En reconstrucción.** Los ZIP/APK antiguos se usan como referencia técnica, pero no se publican como releases oficiales. La primera release pública será una versión revisada y verificable.

---

**DesarrollAMO** · un ecosistema, una tienda, varias plataformas.