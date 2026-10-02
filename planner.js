// Plans a round trip that crosses as many unvisited tiles as possible, routed over
// road-bike-friendly roads by BRouter (https://brouter.de).
// Works in the browser (window.Planner) and in Node (module.exports).
(function (root) {
  const S = root.Squadrats || require("./squadrats.js");

  const BROUTER = "https://brouter.de/brouter";
  const EARTH_M = 6371000;
  const DETOUR = 1.3; // roads are longer than the straight lines between waypoints
  const HEADINGS = [0, 45, 90, 135, 180, 225, 270, 315];
  const ROUTED_CANDIDATES = 3; // how many of the best-looking loops are actually routed
  const LENGTH_TOLERANCE = 0.1; // accept routes within 10% of the requested distance
  const MAX_ROUTE_ATTEMPTS = 3;

  const key = (x, y) => x + "," + y;
  const rad = (d) => (d * Math.PI) / 180;
  const deg = (r) => (r * 180) / Math.PI;

  // Point reached by travelling `meters` from `start` along `bearing` (degrees).
  function offset([lat, lon], bearing, meters) {
    const d = meters / EARTH_M, b = rad(bearing), p1 = rad(lat);
    const p2 = Math.asin(Math.sin(p1) * Math.cos(d) + Math.cos(p1) * Math.sin(d) * Math.cos(b));
    const l2 = rad(lon) + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(p1), Math.cos(d) - Math.sin(p1) * Math.sin(p2));
    return [deg(p2), deg(l2)];
  }

  function distance([lat1, lon1], [lat2, lon2]) {
    const a = Math.sin(rad(lat2 - lat1) / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(rad(lon2 - lon1) / 2) ** 2;
    return 2 * EARTH_M * Math.asin(Math.sqrt(a));
  }

  const tileSize = (lat, z) => (40075016.686 * Math.cos(rad(lat))) / 2 ** z;
  const tileOf = ([lat, lon], z) => [Math.floor(S.lonToTileX(lon, z)), Math.floor(S.latToTileY(lat, z))];
  const tileCenter = (x, y, z) => [S.tileToLat(y + 0.5, z), S.tileToLon(x + 0.5, z)];

  // Every tile a polyline passes through, sampled finely enough not to skip corners.
  function tilesAlong(coords, z) {
    const tiles = new Set();
    const step = tileSize(coords[0][0], z) / 8;
    for (let i = 0; i < coords.length; i++) {
      const [x, y] = tileOf(coords[i], z);
      tiles.add(key(x, y));
      if (i === 0) continue;
      const [a, b] = [coords[i - 1], coords[i]];
      const n = Math.ceil(distance(a, b) / step);
      for (let s = 1; s < n; s++) {
        const [tx, ty] = tileOf([a[0] + ((b[0] - a[0]) * s) / n, a[1] + ((b[1] - a[1]) * s) / n], z);
        tiles.add(key(tx, ty));
      }
    }
    return tiles;
  }

  const countNew = (tiles, visited) => [...tiles].filter((k) => !visited.has(k)).length;

  // Waypoints on a circle of radius r that passes through start, heading out along `heading`.
  function loopWaypoints(start, heading, r, n) {
    const center = offset(start, heading, r);
    const back = heading + 180; // bearing from the centre back to the start
    return Array.from({ length: n }, (_, i) => offset(center, back + ((i + 1) * 360) / (n + 1), r));
  }

  // Nudge a waypoint towards the nearby tile with the most unvisited tiles around it.
  function snapToNewTiles(pt, visited, z, radiusM) {
    const R = Math.max(1, Math.round(radiusM / tileSize(pt[0], z)));
    const k = z >= 17 ? 2 : 1; // neighbourhood radius used to measure "unvisited density"
    const [cx, cy] = tileOf(pt, z);
    let best = null, bestScore = 0;
    for (let dx = -R; dx <= R; dx++) {
      for (let dy = -R; dy <= R; dy++) {
        const d = Math.hypot(dx, dy);
        if (d > R || visited.has(key(cx + dx, cy + dy))) continue;
        let density = 0;
        for (let i = -k; i <= k; i++) for (let j = -k; j <= k; j++) if (!visited.has(key(cx + dx + i, cy + dy + j))) density++;
        const score = density * (1 - (0.5 * d) / R);
        if (score > bestScore) [best, bestScore] = [[cx + dx, cy + dy], score];
      }
    }
    return best ? tileCenter(best[0], best[1], z) : pt;
  }

  function buildLoop(start, heading, r, n, visited, z) {
    return loopWaypoints(start, heading, r, n).map((p) => snapToNewTiles(p, visited, z, r * 0.4));
  }

  async function route(points, profile) {
    const lonlats = points.map(([lat, lon]) => `${lon.toFixed(6)},${lat.toFixed(6)}`).join("|");
    const res = await fetch(`${BROUTER}?lonlats=${lonlats}&profile=${profile}&alternativeidx=0&format=geojson`);
    const body = await res.text();
    if (!res.ok || !body.startsWith("{")) throw new Error(body.trim() || res.statusText);
    const f = JSON.parse(body).features[0];
    return {
      coords: f.geometry.coordinates.map(([lon, lat]) => [lat, lon]),
      length: Number(f.properties["track-length"]),
      ascend: Number(f.properties["filtered ascend"]),
    };
  }

  // Returns candidate loops sorted by how many new target tiles they cross.
  async function plan({ start, distanceKm, target, profile, data, onProgress = () => {} }) {
    const z = data[target].zoom;
    const visited = data[target].visited;
    const D = distanceKm * 1000;
    const n = Math.min(7, Math.max(3, Math.round(distanceKm / 15)));
    const r0 = D / (2 * Math.PI * DETOUR);

    // Cheap pre-score: new tiles along the straight-line loop, before asking the router.
    const candidates = HEADINGS.map((heading) => {
      const wps = buildLoop(start, heading, r0, n, visited, z);
      return { heading, estimate: countNew(tilesAlong([start, ...wps, start], z), visited) };
    }).sort((a, b) => b.estimate - a.estimate).slice(0, ROUTED_CANDIDATES);

    const results = [];
    for (const [ci, c] of candidates.entries()) {
      let r = r0, rt = null;
      for (let attempt = 0; attempt < MAX_ROUTE_ATTEMPTS; attempt++) {
        onProgress(`Routing loop ${ci + 1} of ${candidates.length}${attempt ? " (adjusting distance)" : ""}…`);
        const wps = buildLoop(start, c.heading, r, n, visited, z);
        try {
          rt = await route([start, ...wps, start], profile);
        } catch (e) {
          // A snapped waypoint may be far from any road; retry on the plain circle.
          rt = await route([start, ...loopWaypoints(start, c.heading, r, n), start], profile).catch(() => rt);
        }
        if (!rt || Math.abs(rt.length - D) / D <= LENGTH_TOLERANCE) break;
        r *= D / rt.length;
      }
      if (!rt) continue;
      const newTiles = {};
      for (const layer of ["squadrats", "squadratinhos"]) {
        if (!data[layer]) continue;
        newTiles[layer] = [...tilesAlong(rt.coords, data[layer].zoom)].filter((k) => !data[layer].visited.has(k));
      }
      results.push({ ...rt, heading: c.heading, newTiles });
    }
    if (!results.length) throw new Error("BRouter could not find a route from this start point.");
    return results.sort((a, b) => b.newTiles[target].length - a.newTiles[target].length);
  }

  function toGpx(coords, name) {
    const pts = coords.map(([lat, lon]) => `<trkpt lat="${lat.toFixed(6)}" lon="${lon.toFixed(6)}"/>`).join("");
    return `<?xml version="1.0" encoding="UTF-8"?><gpx version="1.1" creator="Yard Explorer" xmlns="http://www.topografix.com/GPX/1/1"><trk><name>${name}</name><trkseg>${pts}</trkseg></trk></gpx>`;
  }

  const api = { plan, route, toGpx, tilesAlong, loopWaypoints };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Planner = api;
})(typeof window !== "undefined" ? window : globalThis);
