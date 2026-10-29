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

test("growth: a tile that fills a gap grows the yard around it", () => {
  const visited = new Set(grid([
    "#####",
    "#####",
    "#####",
    "##.##",
    "#####",
  ]));
  const layer = { visited, yards: S.findYards(visited) };
  assert.deepEqual(layer.yards.map((y) => y.length), [5]);
  const best = S.growth(layer).targets[0];
  assert.deepEqual(best.added, [`${X0 + 2},${Y0 + 3}`]);
  assert.deepEqual(best.yards, [0]);
  assert.equal(best.size, 9);
  assert.equal(best.gain, 4);
});

test("growth: a tile that joins several yards is a merge, not a growth target", () => {
  const visited = new Set(grid([
    "#####",
    "#####",
    "##.##",
    "#####",
    "#####",
  ]));
  const layer = { visited, yards: S.findYards(visited) };
  // Only the corners of the inner 3x3 qualify, and they don't touch: four 1-tile yards.
  assert.deepEqual(layer.yards.map((y) => y.length), [1, 1, 1, 1]);
  const { targets, merges } = S.growth(layer);
  assert.ok(targets.every((t) => t.yards.length < 2)); // edge tiles may still grow single yards
  assert.ok(!targets.some((t) => t.added.includes(`${X0 + 2},${Y0 + 2}`)));
  assert.deepEqual(merges[0].added, [`${X0 + 2},${Y0 + 2}`]);
  assert.equal(merges[0].yards.length, 4);
  assert.equal(merges[0].size, 9);
});

test("growth: finds one- and two-tile gaps that merge two yards", () => {
  const one = new Set(grid([
    "#########",
    "####.####",
    "#########",
  ]));
  const m1 = S.growth({ visited: one, yards: S.findYards(one) }).merges[0];
  assert.deepEqual(m1.added, [`${X0 + 4},${Y0 + 1}`]);
  assert.deepEqual(m1.yards, [0, 1]);
  assert.equal(m1.size, 7);

  const two = new Set(grid([
    "##########",
    "####..####",
    "##########",
  ]));
  const m2 = S.growth({ visited: two, yards: S.findYards(two) }).merges[0];
  assert.deepEqual(sorted(m2.added), sorted([`${X0 + 4},${Y0 + 1}`, `${X0 + 5},${Y0 + 1}`]));
  assert.equal(m2.size, 8);
});

test("Übersquadrat is read from the export, and growing it needs the cheapest extra row and column", () => {
  const visited = grid([
    "###.",
    "###.",
    "###.",
    "##..",
  ]);
  const square = grid(["###", "###", "###"]);
  const { squadratinhos } = S.analyse(kml({
    squadratinhos: { tiles: visited, z: Z },
    ubersquadratinho: { tiles: square, z: Z, size: 3 },
  }));
  assert.deepEqual(squadratinhos.uber, { x: X0, y: Y0, size: 3 });
  // Growing right and down needs column 3 (4 tiles) and one tile in row 3; left or up needs more.
  const next = S.uberGrowth(squadratinhos.visited, squadratinhos.uber);
  assert.deepEqual([next.x, next.y, next.size], [X0, Y0, 4]);
  assert.deepEqual(sorted(next.missing), sorted([0, 1, 2, 3].map((dy) => `${X0 + 3},${Y0 + dy}`).concat(`${X0 + 2},${Y0 + 3}`)));
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
