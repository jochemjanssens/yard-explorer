# Yard Explorer

A small site for [Squadrats](https://squadrats.com) players:

- **All your Yards and Yardinhos**, ranked with the gap to the leader — not just the largest one.
- **Your top 20 on the map**, each with its own colour and a numbered badge.
- **Every yard, everywhere**: yards far from home are drawn too and tagged *Away* in the standings. Rows show the yard's
  place name and can be filtered by size.

## Use it

Open the site, download your KML from squadrats.com and drop it on the page. Everything runs in your browser; your export is never uploaded. The browser remembers your last export, so the map is ready next time. Place names are looked up with OpenStreetMap's Nominatim (one coordinate per listed yard, cached in the browser).

## Run locally

```bash
node serve.js
```

Then open http://localhost:8765. Locally, dropped exports are saved to `exports/` (git-ignored); the newest one is loaded on every visit and only the newest three are kept.

## Test

```bash
npm test
```

The tests build small made-up KML exports, so no real ride data is needed or committed.

## Android app

A native Jetpack Compose version lives in [`android/`](android): the same standings, top-20 map, Away tags, place names
(from Android's geocoder) and GPS, on a full-screen [MapLibre](https://maplibre.org) map with OpenStreetMap tiles.
Open a KML from the app, or open the `.kml` from your Downloads and choose Yard Explorer.

```bash
cd android
./gradlew :app:assembleDebug      # build
./gradlew :app:testDebugUnitTest  # tests (same made-up exports as the web tests)
```

The yard logic is a Kotlin port of `squadrats.js`. A parity test checks that both rank a real export identically; it
only runs when you point it at one, so no ride data is committed:

```bash
YARD_KML=/path/to/squadrats.kml ./gradlew :app:testDebugUnitTest
```
