// Parses a Squadrats KML export and finds every Yard / Yardinho, not just the largest.
// Works in the browser (window.Squadrats) and in Node (module.exports).
(function (root) {
  const ZOOM = { squadrats: 14, squadratinhos: 17 };

  function lonToTileX(lon, z) {
    return ((lon + 180) / 360) * 2 ** z;
  }

  function latToTileY(lat, z) {
    const r = (lat * Math.PI) / 180;
    return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** z;
  }

  function tileToLon(x, z) {
    return (x / 2 ** z) * 360 - 180;
  }

  function tileToLat(y, z) {
    const n = Math.PI - (2 * Math.PI * y) / 2 ** z;
    return (180 / Math.PI) * Math.atan(Math.sinh(n));
  }

  const key = (x, y) => x + "," + y;

  function parsePlacemarks(kml) {
    const out = {};
    for (const [, body] of kml.matchAll(/<Placemark>([\s\S]*?)<\/Placemark>/g)) {
      const name = (body.match(/<name>([^<]*)<\/name>/) || [])[1];
      const size = Number((body.match(/<Data name="size"><value>([^<]*)<\/value>/) || [])[1]);
      const rings = [...body.matchAll(/<coordinates>([\s\S]*?)<\/coordinates>/g)].map(([, c]) =>
        c.trim().split(/\s+/).map((p) => p.split(",").map(Number))
      );
      if (name) out[name] = { size, rings };
    }
    return out;
  }

  // Rasterise polygon rings (outer + holes) into a set of tile keys using the even-odd rule.
  // Squadrats polygons are unions of tiles, so every vertex sits on a tile corner.
  function rasterise(rings, z) {
    const edgesByRow = new Map();
    for (const ring of rings) {
      const pts = ring.map(([lon, lat]) => [Math.round(lonToTileX(lon, z)), Math.round(latToTileY(lat, z))]);
      for (let i = 0; i < pts.length - 1; i++) {
        const [x1, y1] = pts[i];
        const [x2, y2] = pts[i + 1];
        if (x1 !== x2 || y1 === y2) continue; // only vertical edges cross a row
        for (let y = Math.min(y1, y2); y < Math.max(y1, y2); y++) {
          if (!edgesByRow.has(y)) edgesByRow.set(y, []);
          edgesByRow.get(y).push(x1);
        }
      }
    }
    const tiles = new Set();
    for (const [y, xs] of edgesByRow) {
      xs.sort((a, b) => a - b);
      for (let i = 0; i + 1 < xs.length; i += 2) {
        for (let x = xs[i]; x < xs[i + 1]; x++) tiles.add(key(x, y));
      }
    }
    return tiles;
  }

  // A yard tile is a visited tile whose four neighbours are all visited.
  // Yards are the 4-connected clusters of yard tiles, sorted largest first.
  function findYards(visited) {
    const eligible = new Set();
    for (const k of visited) {
      const [x, y] = k.split(",").map(Number);
      if (visited.has(key(x + 1, y)) && visited.has(key(x - 1, y)) && visited.has(key(x, y + 1)) && visited.has(key(x, y - 1))) {
        eligible.add(k);
      }
    }
    const seen = new Set();
    const yards = [];
    for (const start of eligible) {
      if (seen.has(start)) continue;
      const tiles = [];
      const stack = [start];
      seen.add(start);
      while (stack.length) {
        const k = stack.pop();
        tiles.push(k);
        const [x, y] = k.split(",").map(Number);
        for (const n of [key(x + 1, y), key(x - 1, y), key(x, y + 1), key(x, y - 1)]) {
          if (eligible.has(n) && !seen.has(n)) {
            seen.add(n);
            stack.push(n);
          }
        }
      }
      yards.push(tiles);
    }
    return yards.sort((a, b) => b.length - a.length);
  }

  function analyse(kml) {
    const placemarks = parsePlacemarks(kml);
    const result = {};
    for (const [layer, z] of Object.entries(ZOOM)) {
      const pm = placemarks[layer];
      if (!pm) continue;
      const visited = rasterise(pm.rings, z);
      result[layer] = { zoom: z, visited, reportedSize: pm.size, yards: findYards(visited) };
    }
    result.reported = Object.fromEntries(Object.entries(placemarks).map(([n, p]) => [n, p.size]));
    return result;
  }

  const api = { analyse, parsePlacemarks, rasterise, findYards, tileToLat, tileToLon, lonToTileX, latToTileY };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Squadrats = api;
})(typeof window !== "undefined" ? window : globalThis);
