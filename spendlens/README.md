# SpendLens — spending from payment screenshots (Android)

Add screenshots of your payments (Google Pay, PhonePe, Paytm, BHIM, card/bank apps, receipts…) and SpendLens reads them **on your phone**, then charts your spending by **day, month and year**.

## Features

- **Screenshot import** – pick many at once, share a screenshot to SpendLens from any app, or let **Auto-find** scan your Screenshots folder (last 30 days) and keep only the ones that look like payments.
- **On-device OCR** (ML Kit) – pulls out amount, payee, date/time, payment app and UPI/UTR reference. Handles Indian lakh formatting, garbled ₹ signs, balances/cashback lines, receipt totals.
- **Review before saving** – editable cards with a live "scanning" preview, duplicate/failed/incoming-money detection, category chips, date & time pickers.
- **Dashboard** – Day / Month / Year tabs, animated total with comparison vs. the same point last period, cumulative sparkline, interactive bar chart (tap or scrub), spending calendar (month) and GitHub-style heatmap (year), category donut, top places, smart insights.
- **Budget** – monthly budget with progress and "you can spend ₹X/day" guidance.
- **Activity** – search, category filters, day-grouped list with sticky headers, swipe to delete with undo.
- **Details** – full screenshot viewer with pinch-zoom, copyable reference, the raw text that was read.
- Manual entries, CSV export, currency choice (₹ $ € £ …), light/dark/system theme.

## Get the APK

Every push that touches `spendlens/` builds the app in GitHub Actions (**SpendLens Android** workflow).
Open the latest run → **Artifacts** → `SpendLens-apk`, unzip, and install `app-release.apk` on your phone
(allow "Install unknown apps" for your browser/files app when asked).

Both APKs are signed with the checked-in `app/signing/sideload.keystore`, so new builds install over old ones without losing data.
Use your own key if you ever publish to the Play Store.

## Build locally

Requires JDK 17 and the Android SDK (API 35).

```bash
cd spendlens
./gradlew testDebugUnitTest assembleRelease
# app/build/outputs/apk/release/app-release.apk
```

## How it's built

Kotlin, Jetpack Compose (Material 3, custom Canvas charts), Room, DataStore, ML Kit text recognition, Coil.

| Package | What's there |
| --- | --- |
| `domain/` | Pure Kotlin: `PaymentParser` (OCR text → payment), `CategoryClassifier`, `Analytics` (dashboard numbers), money/period helpers. Unit tested. |
| `ocr/` | ML Kit wrapper, image storage, Screenshots-folder finder, `ImportManager` (scan → review → save). |
| `data/` | Room database, settings, CSV export. |
| `ui/` | Theme, components (charts, heatmaps, nav bar…), screens. |

Everything stays on the device — there is no network permission.
