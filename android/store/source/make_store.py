# Renders the Play Store graphics from small HTML pages with headless Chrome, at exact pixel sizes.
import subprocess, os, sys
S = os.path.dirname(os.path.abspath(__file__))
OUT = sys.argv[1]
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
FONTS = '<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@500;700&family=Space+Grotesk:wght@500;700&display=block">'
BASE = """
  * { box-sizing: border-box; margin: 0; }
  html, body { width: %(w)dpx; height: %(h)dpx; overflow: hidden; }
  body { background-color: #f4f4f0; color: #121316; font-family: "Space Grotesk", sans-serif;
    background-image: linear-gradient(rgba(18,19,22,.06) 1px, transparent 1px), linear-gradient(90deg, rgba(18,19,22,.06) 1px, transparent 1px);
    background-size: %(cell)dpx %(cell)dpx; }
  .logo { display: grid; grid-template-columns: repeat(3, %(t)dpx); gap: %(g)dpx; }
  .logo i { width: %(t)dpx; height: %(t)dpx; background: #d8d8d2; }
  .logo i.on { background: #00e676; }
  .brand { display: flex; align-items: center; gap: %(bg)dpx; font: 700 %(bf)dpx/1 "Space Grotesk"; letter-spacing: -.02em; }
  mark { display: inline-block; margin-top: .12em; line-height: .98; background: #00e676; color: #121316;
    padding: 0 .12em .04em; box-shadow: .08em .08em 0 #121316; }
"""
LOGO = '<div class="logo"><i></i><i class="on"></i><i></i><i class="on"></i><i class="on"></i><i class="on"></i><i></i><i class="on"></i><i class="on"></i></div>'

def render(name, w, h, css, body):
    html = f'<!doctype html><html><head><meta charset="utf-8">{FONTS}<style>{css}</style></head><body>{body}</body></html>'
    path = os.path.join(S, name + ".html")
    open(path, "w").write(html)
    out = os.path.join(OUT, name + ".png")
    subprocess.run([CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                    f"--window-size={w},{h}", "--virtual-time-budget=8000", f"--screenshot={out}", "file://" + path],
                   check=True, capture_output=True)
    print("wrote", out)

# ---- Phone screenshots: 1080x1920 (9:16), a headline above the app screenshot (status bar cropped off).
SHOTS = [
    ("1-map", "raw-2-map", "Every yard,<br><mark>on the map.</mark>", "Your top 20 in colour and numbered, wherever you've ridden."),
    ("2-standings", "raw-3-standings", "The full<br><mark>standings.</mark>", "Every Yard and Yardinho, ranked and named, with the gap to the leader."),
    ("3-zoom", "raw-4-selected", "Tap a yard,<br><mark>zoom in.</mark>", "Bold outlines keep the streets underneath readable."),
    ("4-gps", "raw-5-gps", "Find your<br><mark>next tile.</mark>", "GPS shows the tile you're in: new, or already ridden."),
    ("5-private", "raw-1-landing", "Your data stays<br><mark>on your phone.</mark>", "Open the KML export from Squadrats. No account, no ads, no tracking."),
]
PHONE_W = 690                     # on-canvas width of the screenshot
CROP_TOP = 110                    # px of the raw 1080-wide screenshot to hide (status bar)
for name, raw, title, sub in SHOTS:
    scale = PHONE_W / 1080
    vis_h = int((2424 - CROP_TOP) * scale)
    css = BASE % dict(w=1080, h=1920, cell=36, t=14, g=3, bg=18, bf=40) + f"""
      .top {{ padding: 84px 84px 0; }}
      h1 {{ font: 700 92px/1.08 "Space Grotesk"; letter-spacing: -.045em; margin: 46px 0 22px; }}
      p {{ font: 500 34px/1.35 "Space Grotesk"; color: #5d6066; max-width: 860px; }}
      .phone {{ position: absolute; left: {(1080 - PHONE_W) // 2}px; top: 600px; width: {PHONE_W}px; height: {vis_h}px;
        border: 8px solid #121316; box-shadow: 18px 18px 0 #121316; overflow: hidden; background: #fff; }}
      .phone img {{ width: {PHONE_W - 16}px; display: block; margin-top: -{int(CROP_TOP * (PHONE_W - 16) / 1080)}px; }}
    """
    body = f'<div class="top"><div class="brand">{LOGO}Yard Explorer</div><h1>{title}</h1><p>{sub}</p></div>' \
           f'<div class="phone"><img src="{raw}.png"></div>'
    render("phone-" + name, 1080, 1920, css, body)

# ---- Feature graphic: 1024x500.
MOSAIC = ["..vv....vvv...", ".vvvv..vvvvv..", "vvyyyv.v222vv.", "vy111yvvv22vv.", "vy1111yvvvvv..",
          "vvy111yvv.vv..", ".vyyyyv..vvvv.", "..vvvv..vv33v.", "...vv..vvv33vv", "........vvvv.."]
cls = {"v": "v", "y": "y", "1": "r1", "2": "r2", "3": "r3"}
tiles = "".join(f'<i class="{cls.get(c, "")}"></i>' for row in MOSAIC for c in row)
css = BASE % dict(w=1024, h=500, cell=25, t=11, g=2.5, bg=14, bf=30) + """
  body { display: grid; grid-template-columns: 1fr 430px; align-items: center; gap: 44px; padding: 0 56px 0 64px; }
  h1 { font: 700 84px/1.06 "Space Grotesk"; letter-spacing: -.045em; margin: 30px 0 18px; }
  p { font: 500 24px/1.35 "Space Grotesk"; color: #5d6066; }
  .mosaic { display: grid; grid-template-columns: repeat(14, 1fr); gap: 4px; padding: 16px; background: #fbfbf8;
    border: 3px solid #121316; box-shadow: 10px 10px 0 #121316; }
  .mosaic i { aspect-ratio: 1; background: #ecece6; }
  .mosaic i.v { background: #d8d8d2; } .mosaic i.y { background: #00e676; }
  .mosaic i.r1 { background: #ffd400; } .mosaic i.r2 { background: #e4002b; } .mosaic i.r3 { background: #0050ff; }
"""
body = f'<div><div class="brand">{LOGO}Yard Explorer</div><h1>Every tile<br><mark>counts.</mark></h1>' \
       f'<p>Every Squadrats Yard and Yardinho you\'ve built, ranked and mapped.</p></div><div class="mosaic">{tiles}</div>'
render("feature-graphic", 1024, 500, css, body)

# ---- App icon: 512x512, the 3x3 tile mark full-bleed (Play rounds the corners itself).
css = """
  * { margin: 0; } html, body { width: 512px; height: 512px; overflow: hidden; background: #121316; }
  .g { position: absolute; left: 136px; top: 136px; display: grid; grid-template-columns: repeat(3, 72px); gap: 12px; }
  .g i { width: 72px; height: 72px; background: #3a3c42; } .g i.on { background: #00e676; }
"""
render("icon-512", 512, 512, css, '<div class="g"><i></i><i class="on"></i><i></i><i class="on"></i><i class="on"></i><i class="on"></i><i></i><i class="on"></i><i class="on"></i></div>')
