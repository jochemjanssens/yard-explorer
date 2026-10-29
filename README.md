# Yard Explorer

A small site for [Squadrats](https://squadrats.com) players:

- **All your Yards and Yardinhos**, ranked with the gap to the leader — not just the largest one.
- **Your top 10 on the map**, each with its own colour and a numbered badge.
- **Every yard, everywhere**: yards far from home are drawn too and tagged *Away* in the standings.

## Use it

Open the site, download your KML from squadrats.com and drop it on the page. Everything runs in your browser; your export is never uploaded. The browser remembers your last export, so the map is ready next time.

## Run locally

```bash
node serve.js
```

Then open http://localhost:8765. Locally, dropped exports are saved to `exports/` (git-ignored) and the newest one is loaded on every visit.
