# Launcher artwork

The launcher icon is vector-only since phase 3: `app/src/main/res/drawable/ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml`, over the `launcher_background` color, wired in `mipmap-anydpi-v26/ic_launcher*.xml` (with `minSdk` 26 the adaptive icon always applies, so there are no legacy PNGs). The mark is the design system's "OO": two chamfered square plates with a collared grip between them, stretched to cap height, on plate graphite (`#111513`), drawn from the design canvas (phase 2 spec, logo notes).

The `preview` build type overrides the background color and the foreground (`app/src/preview/res`) so the two apps can be told apart on the launcher.

`arm-dumbbell-source.jpg` and `arm-dumbbell-foreground.png` are the previous icon artwork, kept only as history. `tools/generate-launcher.ps1`, which generated the old PNG resources from them, was removed with the PNGs.
