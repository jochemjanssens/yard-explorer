# Yard Explorer

A small site for [Squadrats](https://squadrats.com) players:

- **All your Yards and Yardinhos**, ranked — not just the largest one.
- **Latest changes** between two exports: new tiles and how your Yard, Yardinho and Übersquadrat grew.
- **Route planner**: pick a start and a distance, and get a road-bike loop (via [BRouter](https://brouter.de)) that crosses as many new tiles as possible. Download it as GPX.

## Use it

Open the site, download your KML from squadrats.com and drop it on the page. Everything runs in your browser; your export is never uploaded. The browser remembers your last export, so dropping the next one shows what changed. Route planning sends the start point and waypoints to the public BRouter server.

## Run locally

```bash
node serve.js
```

Then open http://localhost:8765. Locally, dropped exports are saved to `exports/` (git-ignored) and the newest two are compared on every load.
