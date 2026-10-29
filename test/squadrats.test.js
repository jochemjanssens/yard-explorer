// Tests for squadrats.js against small made-up KML exports (never a real one: those reveal where someone rides).
const test = require("node:test");
const assert = require("node:assert/strict");
const S = require("../squadrats.js");

const Z = 17;
const X0 = 67200, Y0 = 43900; // somewhere in Belgium; any tile works

// Draw a grid as tiles: "#" = visited. Row 0 is the top (smallest y).
function grid(rows, x0 = X0, y0 = Y0) {
  const tiles = [];
  rows.forEach((row, dy) => [...row].forEach((c, dx) => c === "#" && tiles.push(`${x0 + dx},${y0 + dy}`)));
  return tiles;
}

// One polygon per tile; the even-odd rasteriser turns shared edges back into solid areas.
function polygons(tiles, z = Z) {
  return tiles.map((k) => {
    const [x, y] = k.split(",").map(Number);
    const c = [[x, y], [x + 1, y], [x + 1, y + 1], [x, y + 1], [x, y]]
      .map(([tx, ty]) => `${S.tileToLon(tx, z)},${S.tileToLat(ty, z)}`).join(" ");
    return `<Polygon><outerBoundaryIs><LinearRing><coordinates>${c}</coordinates></LinearRing></outerBoundaryIs></Polygon>`;
  }).join("");
}

function kml(layers) {
  const pm = Object.entries(layers).map(([name, { tiles, z, size }]) =>
    `<Placemark><name>${name}</name><ExtendedData><Data name="size"><value>${size ?? tiles.length}</value></Data></ExtendedData>` +
    `<MultiGeometry>${polygons(tiles, z)}</MultiGeometry></Placemark>`).join("");
  return `<?xml version="1.0" encoding="UTF-8"?><kml xmlns="http://www.opengis.net/kml/2.2"><Document>${pm}</Document></kml>`;
}

const sorted = (a) => [...a].sort();

test("rasterise reads back exactly the tiles that were drawn", () => {
  const tiles = grid(["##.#", "#..#", "####"]);
  const { squadratinhos } = S.analyse(kml({ squadratinhos: { tiles, z: Z } }));
  assert.deepEqual(sorted(squadratinhos.visited), sorted(tiles));
});

test("findYards: tiles with all four neighbours visited, grouped and sorted biggest first", () => {
  const visited = new Set(grid([
    "#####.###",
    "#####.###",
    "#####.###",
    "#####....",
  ]));
  const yards = S.findYards(visited);
  assert.deepEqual(yards.map((y) => y.length), [6, 1]);
  assert.deepEqual(sorted(yards[0]), sorted(grid([".....", ".###.", ".###."])));
  assert.deepEqual(yards[1], [`${X0 + 7},${Y0 + 1}`]);
});

test("outline returns an outer ring and a hole, without corners on straight runs", () => {
  const rings = S.outline(grid(["###", "#.#", "###"], 0, 0));
  assert.equal(rings.length, 2);
  assert.deepEqual(sorted(rings.map((r) => r.length)), [4, 4]);
  assert.deepEqual(sorted(rings[0].map(String)), sorted(["0,0", "3,0", "3,3", "0,3"]));
});

test("analyse matches the reported sizes for both layers", () => {
  const tiles14 = grid(["####", "####", "####"], 8390, 5470);
  const tiles17 = grid(["#####", "#####", "#####", "#####"]);
  const d = S.analyse(kml({
    squadrats: { tiles: tiles14, z: 14 },
    squadratinhos: { tiles: tiles17, z: Z },
  }));
  assert.equal(d.squadrats.visited.size, d.reported.squadrats);
  assert.equal(d.squadratinhos.visited.size, d.reported.squadratinhos);
  assert.deepEqual(d.squadratinhos.yards.map((y) => y.length), [6]);
});
