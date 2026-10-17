# jvm-check

Compiles the app's Kotlin and runs its unit tests and screenshot tests on a plain JVM.
It needs no Android SDK: Robolectric's `android-all` jar stands in for the framework, and
small stubs replace the generated `R` class and the parts of `androidx.test` that Robolectric touches.

```sh
cd farm-game/Tools/jvm-check
gradle compileKotlin     # compile check
gradle test              # unit + screenshot smoke tests
gradle test -Pshots      # also write 3x PNGs of rendered screens to build/shots/
```

The real build (AGP, resources, APK) runs in GitHub Actions (`.github/workflows/dither-farm-android.yml`).
