# Launcher artwork

`arm-dumbbell-source.jpg` is an unchanged copy of the supplied image. The original Desktop file is never modified.

`arm-dumbbell-foreground.png` contains the original artwork RGB pixels on transparent alpha. `tools/generate-launcher.ps1` removes the edge-connected neutral checkerboard, preserves interior dumbbell highlights, and resamples the foreground into Android resources. The dark background is the app's existing `#101713` palette color. The complete silhouette is contained inside a centered 64dp safe diameter of the 108dp adaptive layer.

The generated resources include adaptive foreground/background layers, an alpha-derived monochrome layer for Android 13 themed icons, and legacy square/round images at all five standard densities. Android 12+ splash settings use the adaptive launcher icon; older supported devices use a dark launch background with the same foreground.

The adaptive XML uses the Android [adaptive icon resource structure](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive) in `mipmap-anydpi-v26`, alongside legacy density PNGs. Its packaged background, foreground and monochrome tags are checked in the final APK.

Regenerate from the project root on Windows PowerShell:

```powershell
.\tools\generate-launcher.ps1
```

The script writes mask and splash previews to `app/build/outputs/icon-previews/`. Resources remain inside the project, independently of the original image location.

The built-in image-editing tool was also evaluated with this prompt: “Remove only the gray-and-white checkerboard background; preserve the exact original arm, hand, green shading, outline and dumbbell design; output a transparent PNG.” Its generated cutout changed the shading, so it was not used in the shipped resources. The final native asset pipeline preserves the supplied artwork instead. No image-generation CLI or API key was used.
