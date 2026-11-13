# Yard Explorer on Google Play

Everything to fill in Play Console for **be.jochemjanssens.yardexplorer**. Copy the texts as they are; the form
answers are suggestions based on what the app actually does. Check them, since you sign the declarations.

---

## Main store listing

**App name** (max 30): `Yard Explorer`

**Short description** (max 80):

```
See every Squadrats Yard and Yardinho you've built, ranked and on the map.
```

**Full description** (max 4000):

```
Squadrats only shows your biggest Yard and Yardinho. Yard Explorer shows all of them.

Open the KML export from your Squadrats map and see every Yard and Yardinho you've built: ranked by size, named after the place they're in, and drawn on a full-screen map.

WHAT YOU GET
• The full standings: every Yard and Yardinho, with its size and the gap to the leader
• Your top 20 on the map, each in its own colour with a numbered tile
• Place names for your yards, so you know which one is where
• Yards from trips far from home, tagged "Away" — tap one to jump there
• Switch between Yardinhos (squadratinhos) and Yards (squadrats)
• Filter out the tiny ones, or show them all
• GPS: see the tile you're standing in, and whether it's new or already ridden
• Smooth map gestures: pinch, double-tap, double-tap-and-drag, two-finger tap
• Light and dark mode

HOW IT WORKS
1. Sign in at squadrats.com and open your map.
2. Download the KML export from the panel on the right of the map.
3. Open it in Yard Explorer — or open the .kml from your Downloads and choose Yard Explorer.
After new rides, download a fresh export and open it again.

PRIVATE BY DESIGN
No account, no ads, no analytics, no tracking. Your export is read and kept on your phone. Your GPS location is only shown on the map and never stored or sent. Place names come from Android's geocoder and the map from OpenStreetMap.

Yard Explorer is an independent project and is not affiliated with Squadrats.
Map data © OpenStreetMap contributors.
```

### Graphics (in `android/store/`)

| Play Console field | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512 × 512 |
| Feature graphic | `feature-graphic.png` | 1024 × 500 |
| Phone screenshots (upload in this order) | `phone-1-map.png` … `phone-5-private.png` | 1080 × 1920 |

All screenshots use made-up demo data around Utrecht, never real rides. To redo them: put new raw screenshots
(1080 × 2424, Pixel 10 emulator) in `android/store/source/` and run
`python3 android/store/source/make_store.py android/store` (needs Google Chrome).

---

## Store settings

- **App or game:** App
- **Category:** Maps & Navigation
- **Tags:** pick from Play's list, e.g. *Cycling*, *Maps*, *Navigation*
- **Contact email:** an address you're happy to show publicly (required)
- **Website:** `https://jochemjanssens.github.io/yard-explorer/`
- **Privacy policy:** `https://jochemjanssens.github.io/yard-explorer/privacy.html`

---

## App content (Policy → App content)

### Privacy policy
`https://jochemjanssens.github.io/yard-explorer/privacy.html`

### Ads
**No**, the app doesn't contain ads.

### App access
**All or some functionality is restricted** → add instructions:

```
No account or login is needed. The app shows the user's Squadrats map from a KML export file.
To test it, download this demo export (made-up data around Utrecht, NL):
https://jochemjanssens.github.io/yard-explorer/demo/squadrats-demo.kml
Then open Yard Explorer, tap "Choose KML file" and select the downloaded file (or open the file from Downloads and choose Yard Explorer).
```

### Content rating
Start the questionnaire, enter your email, category **All other app types**, and answer **No** to every question
(no violence, sexual content, language, controlled substances, gambling, user interaction, sharing of location
with other users, or digital purchases). Expected result: **Everyone / PEGI 3**.

### Target audience and content
- **Target age groups:** 18 and over (Squadrats is for adult cyclists; this keeps the app out of the Families
  programme's extra requirements)
- **Could the app unintentionally appeal to children?** No

### News app
No.

### Health apps / Financial features / Government apps
None / No.

### Data safety

What the app actually does:
- The KML export is processed and stored **only on the device** → not "collected".
- GPS location is used **only on the device** (shown on the map, never stored or sent) → not "collected".
- **Place names:** for each listed yard, one point rounded to about 100 m is sent to **Android's geocoder**, which on
  most phones is a Google Play services network lookup. That point comes from the user's ride data, so declaring it
  is the honest choice.
- Map tiles come from OpenStreetMap over HTTPS (like loading a web page; no user data in the request beyond the
  map area).
- No analytics, no crash reporting, no ads SDKs, no accounts.

Suggested answers:
1. **Does your app collect or share any of the required user data types?** Yes
2. **Is all of the user data collected by your app encrypted in transit?** Yes
3. **Do you provide a way for users to request that their data is deleted?** No: nothing is stored off the
   device (users clear it by clearing the app's storage or uninstalling)
4. **Data types → Location → Approximate location:**
   - Collected: **Yes** · Shared: **No** (processed by Google's geocoder on the app's behalf)
   - **Processed ephemerally:** Yes
   - **Required or optional:** Required (place names are part of the app)
   - **Purpose:** App functionality
5. Every other data type (precise location, personal info, files and docs, app activity, device IDs, …): **not
   collected**.

---

## Release (Testing → Closed testing)

**Release name:** `0.0.1`

**Release notes** (en-US):

```
<en-US>
First release: every Squadrats Yard and Yardinho you've built, ranked, named and on the map. Top 20 in colour, Away tags for trips far from home, and GPS to see the tile you're in.
</en-US>
```

**Bundle:** build it with GitHub → Actions → *Release (Android)* → *Run workflow*, and download the
`yard-explorer-release-aab` artifact. Before every new upload, raise `versionCode` in
`android/app/build.gradle.kts` (and `versionName`, e.g. 0.0.2).

### Personal developer accounts
New personal accounts need a **closed test with at least 12 testers opted in for 14 days in a row** before you can
apply for production access. Add testers by email list or a Google Group, share the opt-in link with them, and keep
the test running for the full 14 days.
