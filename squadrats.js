// Parses a Squadrats KML export and finds every Yard / Yardinho, not just the largest.
// Works in the browser (window.Squadrats) and in Node (module.exports).
(function (root) {
  const ZOOM = { squadrats: 14, squadratinhos: 17 };
  const UBER = { squadrats: "ubersquadrat", squadratinhos: "ubersquadratinho" };

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

  // Outline rings (in tile-corner coordinates) around a set of tiles, for drawing merged shapes.
  // Boundary edges run clockwise around each tile; chaining them yields closed rings
  // (outer edges and holes), which render correctly with the even-odd fill rule.
  function outline(tiles) {
    const set = tiles instanceof Set ? tiles : new Set(tiles);
    const next = new Map(); // corner "x,y" -> list of corners an edge leads to
    const addEdge = (x1, y1, x2, y2) => {
      const k = key(x1, y1);
      if (!next.has(k)) next.set(k, []);
      next.get(k).push([x2, y2]);
    };
    for (const k of set) {
      const [x, y] = k.split(",").map(Number);
      if (!set.has(key(x, y - 1))) addEdge(x, y, x + 1, y);
      if (!set.has(key(x + 1, y))) addEdge(x + 1, y, x + 1, y + 1);
      if (!set.has(key(x, y + 1))) addEdge(x + 1, y + 1, x, y + 1);
      if (!set.has(key(x - 1, y))) addEdge(x, y + 1, x, y);
    }
    const rings = [];
    for (const [startKey, outs] of next) {
      while (outs.length) {
        const ring = [startKey.split(",").map(Number)];
        let cur = outs.pop();
        while (key(cur[0], cur[1]) !== startKey) {
          ring.push(cur);
          cur = next.get(key(cur[0], cur[1])).pop();
        }
        // Drop corners on straight runs so shapes stay light.
        rings.push(ring.filter((p, i) => {
          const a = ring[(i - 1 + ring.length) % ring.length], b = ring[(i + 1) % ring.length];
          return (b[0] - a[0]) * (p[1] - a[1]) !== (b[1] - a[1]) * (p[0] - a[0]);
        }));
      }
    }
    return rings;
  }

  const neighbours = (x, y) => [key(x + 1, y), key(x - 1, y), key(x, y + 1), key(x, y - 1)];
  const coords = (k) => k.split(",").map(Number);

  // What riding through the `added` tiles would do to the yards. New yard tiles appear on the added
  // tiles and their neighbours; each connected group of them joins the yards it touches.
  // Returns the group that grows a yard the most: { added, yards (indices, biggest first), size, gain },
  // or null when no yard tile would be created.
  function simulate(visited, yards, yardOf, added) {
    const has = (k) => visited.has(k) || added.includes(k);
    const fresh = new Set();
    for (const a of added) {
      const [x, y] = coords(a);
      for (const c of [a, ...neighbours(x, y)]) {
        if (fresh.has(c) || yardOf.has(c) || !has(c)) continue;
        const [cx, cy] = coords(c);
        if (neighbours(cx, cy).every(has)) fresh.add(c);
      }
    }
    let best = null;
    const seen = new Set();
    for (const start of fresh) {
      if (seen.has(start)) continue;
      let count = 0;
      const touched = new Set(), stack = [start];
      seen.add(start);
      while (stack.length) {
        const [x, y] = coords(stack.pop());
        count++;
        for (const n of neighbours(x, y)) {
          if (yardOf.has(n)) touched.add(yardOf.get(n));
          else if (fresh.has(n) && !seen.has(n)) { seen.add(n); stack.push(n); }
        }
      }
      const ids = [...touched].sort((a, b) => a - b); // yards are sorted biggest first
      const size = count + ids.reduce((sum, i) => sum + yards[i].length, 0);
      const gain = size - (ids.length ? yards[ids[0]].length : 0);
      if (!best || gain > best.gain) best = { added, yards: ids, size, gain };
    }
    return best;
  }

  // Unvisited tiles worth riding through: single tiles that grow one yard (or start a new one), and the
  // cheapest way (one or two tiles) to merge each pair of yards that are that close (a merge may pull in more).
  function growth({ visited, yards }, limit = 15) {
    const yardOf = new Map();
    yards.forEach((tiles, i) => tiles.forEach((k) => yardOf.set(k, i)));

    // Only unvisited tiles next to a visited one can create yard tiles.
    const frontier = new Set();
    for (const k of visited) {
      const [x, y] = coords(k);
      for (const n of neighbours(x, y)) if (!visited.has(n)) frontier.add(n);
    }

    const singles = [];
    for (const t of frontier) {
      const r = simulate(visited, yards, yardOf, [t]);
      if (r && r.gain > 0) singles.push(r);
    }
    const firstYard = (r) => (r.yards.length ? r.yards[0] : Infinity);
    singles.sort((a, b) => b.gain - a.gain || firstYard(a) - firstYard(b));

    const merges = new Map(); // "i+j" -> cheapest way to merge those yards
    const consider = (r) => {
      if (!r || r.yards.length < 2) return;
      const id = r.yards.slice(0, 2).join("+"), cur = merges.get(id); // keyed by the two biggest yards joined
      if (!cur || r.added.length < cur.added.length || (r.added.length === cur.added.length && r.size > cur.size)) merges.set(id, r);
    };
    singles.forEach(consider);
    // Two-tile gaps: pairs of nearby frontier tiles that lie within two tiles of a yard.
    const nearYard = [...frontier].filter((t) => {
      const [x, y] = coords(t);
      for (let dx = -2; dx <= 2; dx++) for (let dy = -2; dy <= 2; dy++) if (yardOf.has(key(x + dx, y + dy))) return true;
      return false;
    });
    const nearSet = new Set(nearYard);
    for (const a of nearYard) {
      const [x, y] = coords(a);
      for (let dx = -2; dx <= 2; dx++) {
        for (let dy = -2; dy <= 2; dy++) {
          const b = key(x + dx, y + dy);
          if (b > a && nearSet.has(b)) consider(simulate(visited, yards, yardOf, [a, b]));
        }
      }
    }

    return {
      targets: singles.filter((r) => r.yards.length < 2).slice(0, limit), // tiles that join yards are listed under merges
      merges: [...merges.values()].sort((a, b) => b.gain - a.gain || a.added.length - b.added.length).slice(0, limit),
    };
  }

  // The square a placemark covers, in tiles: { x, y, size } (top-left corner and side length).
  function squareOf(tiles) {
    const pts = [...tiles].map(coords);
    const xs = pts.map((p) => p[0]), ys = pts.map((p) => p[1]);
    const x = Math.min(...xs), y = Math.min(...ys);
    return { x, y, size: Math.max(Math.max(...xs) - x, Math.max(...ys) - y) + 1 };
  }

  // The cheapest one-bigger square around the current Übersquadrat: of the four ways to add a row
  // and a column, the one with the fewest unvisited tiles. Returns { x, y, size, missing }.
  function uberGrowth(visited, { x, y, size }) {
    let best = null;
    for (const [dx, dy] of [[0, 0], [-1, 0], [0, -1], [-1, -1]]) {
      const missing = [];
      for (let i = 0; i <= size; i++) {
        for (let j = 0; j <= size; j++) {
          const k = key(x + dx + i, y + dy + j);
          if (!visited.has(k)) missing.push(k);
        }
      }
      if (!best || missing.length < best.missing.length) best = { x: x + dx, y: y + dy, size: size + 1, missing };
    }
    return best;
  }

  function analyse(kml) {
    const placemarks = parsePlacemarks(kml);
    const result = {};
    for (const [layer, z] of Object.entries(ZOOM)) {
      const pm = placemarks[layer];
      if (!pm) continue;
      const visited = rasterise(pm.rings, z);
      result[layer] = { zoom: z, visited, reportedSize: pm.size, yards: findYards(visited) };
      const uber = placemarks[UBER[layer]];
      if (uber) result[layer].uber = squareOf(rasterise(uber.rings, z));
    }
    result.reported = Object.fromEntries(Object.entries(placemarks).map(([n, p]) => [n, p.size]));
    return result;
  }

  const api = {
    analyse, parsePlacemarks, rasterise, findYards, outline, simulate, growth, squareOf, uberGrowth,
    tileToLat, tileToLon, lonToTileX, latToTileY,
  };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  else root.Squadrats = api;
})(typeof window !== "undefined" ? window : globalThis);
