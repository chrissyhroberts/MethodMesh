# MethodMesh widget shortcuts + bundle pop-out v3.5

This patch is designed to be applied on top of the v3.3/v3.4 widget bundle work.

## Visual changes

- `Solid` preserves the existing full 1×1 tile.
- `Frosted` is now a compact round shortcut with a translucent coloured disc and launcher-style label underneath.
- `Compact` replaces the old `Transparent`/`Minimal` name and uses the same round shortcut geometry with a solid coloured disc.
- Existing saved `GLASS` widgets migrate to `FROSTED`.
- Existing saved `TRANSPARENT` or `MINIMAL` widgets migrate to `COMPACT`.
- Existing legacy widgets with no appearance remain `SOLID`.
- Newly configured widgets default to `FROSTED`.

## Bundle interaction

The in-widget previous/next/close controls are removed. Tapping a bundle now opens a compact pop-out containing all bundled targets at once. The pop-out:

- keeps the bundle's configured order;
- shows live schedule state;
- keeps missing targets visible as unavailable;
- closes when an item is selected or when the user taps outside;
- positions itself beside the widget when the launcher supplies source bounds, otherwise it falls back to the centre of the screen.

Android does not expose the launcher's own folder/shortcut-group surface to AppWidgets. The pop-out is therefore a small translucent Activity styled to behave like that surface without requesting overlay permissions.

## Apply

From the MethodMesh repository root after unzipping the patch:

```bash
python3 tools/apply_methodmesh_widget_bundle_manifest.py
./gradlew :app:compileDebugKotlin
```

The manifest helper is idempotent and backs up the existing manifest before adding the one required Activity declaration.
