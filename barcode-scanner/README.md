# Barcode Scanner (Android)

Kotlin app that scans barcodes and QR codes live from the camera using CameraX + ML Kit (bundled, works offline).

## Features
- Live scanning of QR, EAN/UPC, Code 128/39/93, Data Matrix, PDF417, Aztec, ITF, Codabar
- Shows format, type and value
- Copy to clipboard; open URLs, phone numbers, emails and locations
- Camera permission handling (incl. "denied permanently" → app settings)

## Build & run
Requires Android Studio (or Android SDK 34) and JDK 17. Min Android 8.0 (API 26).

```bash
cd barcode-scanner
./gradlew installDebug   # with a device connected
```

Or open the `barcode-scanner` folder in Android Studio and press Run.
