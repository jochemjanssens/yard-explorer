# A made-up Squadrats export for store screenshots: rides and yards around Utrecht (NL), not anyone's real data.
import math, random
random.seed(7)
Z = 17
def lon2x(lon): return (lon + 180) / 360 * 2**Z
def lat2y(lat):
    r = math.radians(lat); return (1 - math.log(math.tan(r) + 1 / math.cos(r)) / math.pi) / 2 * 2**Z
def x2lon(x, z=Z): return x / 2**z * 360 - 180
def y2lat(y, z=Z): return math.degrees(math.atan(math.sinh(math.pi - 2 * math.pi * y / 2**z)))

home = (int(lon2x(5.1214)), int(lat2y(52.0907)))
visited = set()

def blob(cx, cy, r, wobble=0.35):
    for dx in range(-r - 3, r + 4):
        for dy in range(-r - 3, r + 4):
            a = math.atan2(dy, dx)
            rr = r * (1 + wobble * math.sin(3 * a + cx % 5) * 0.5 + wobble * math.cos(5 * a + cy % 7) * 0.3)
            if dx * dx + dy * dy <= rr * rr: visited.add((cx + dx, cy + dy))

def ride(sx, sy, steps, heading):
    x, y = sx, sy
    for _ in range(steps):
        if random.random() < 0.18: heading = (heading + random.choice([-1, 1])) % 4
        dx, dy = [(1, 0), (0, 1), (-1, 0), (0, -1)][heading]
        x += dx; y += dy
        visited.add((x, y))
        if random.random() < 0.12: visited.add((x + dy, y + dx))  # wander across a tile edge now and then

# Yards: a big one around "home" and several smaller ones nearby.
blob(home[0], home[1], 13)
for (ox, oy, r) in [(-42, -30, 6), (38, -22, 5), (-30, 36, 5), (52, 30, 4), (-60, 8, 4), (14, -58, 3), (70, -6, 3), (-12, 62, 3), (30, 48, 3), (-70, -44, 2)]:
    blob(home[0] + ox, home[1] + oy, r)
# Rides out from home in all directions.
for i in range(70):
    ride(home[0] + random.randint(-6, 6), home[1] + random.randint(-6, 6), random.randint(80, 260), random.randrange(4))

def polys(tiles, z):
    out = []
    for (x, y) in tiles:
        c = " ".join(f"{x2lon(tx, z):.7f},{y2lat(ty, z):.7f}" for tx, ty in [(x, y), (x + 1, y), (x + 1, y + 1), (x, y + 1), (x, y)])
        out.append(f"<Polygon><outerBoundaryIs><LinearRing><coordinates>{c}</coordinates></LinearRing></outerBoundaryIs></Polygon>")
    return "".join(out)

squadrats = {(x >> 3, y >> 3) for (x, y) in visited}  # a z14 squadrat is 8x8 squadratinhos
def pm(name, tiles, z):
    return (f"<Placemark><name>{name}</name><ExtendedData><Data name=\"size\"><value>{len(tiles)}</value></Data></ExtendedData>"
            f"<MultiGeometry>{polys(tiles, z)}</MultiGeometry></Placemark>")
kml = ('<?xml version="1.0" encoding="UTF-8"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>'
       + pm("squadrats", sorted(squadrats), 14) + pm("squadratinhos", sorted(visited), Z) + "</Document></kml>")
open("squadrats-demo.kml", "w").write(kml)
print(len(visited), "squadratinhos,", len(squadrats), "squadrats")
