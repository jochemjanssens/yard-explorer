// Tiny local server: static files, plus an exports/ folder of Squadrats KML snapshots.
//   GET /exports.json          list of saved exports, oldest first
//   PUT /exports/<name>.kml    save a new export (never overwrites)
// Usage: node serve.js [port]
const http = require("http");
const fs = require("fs");
const path = require("path");

const port = Number(process.argv[2]) || 8765;
const exportsDir = path.join(__dirname, "exports");
const types = { ".html": "text/html", ".js": "text/javascript", ".json": "application/json", ".kml": "application/vnd.google-earth.kml+xml" };

function listExports() {
  if (!fs.existsSync(exportsDir)) return [];
  return fs.readdirSync(exportsDir).filter((f) => f.endsWith(".kml"))
    .map((f) => ({ name: f, date: (f.match(/\d{4}-\d{2}-\d{2}/) || [""])[0], mtime: fs.statSync(path.join(exportsDir, f)).mtimeMs }))
    // Order by the date in the file name (squadrats-YYYY-MM-DD.kml), then by when it was saved.
    .sort((a, b) => a.date.localeCompare(b.date) || a.mtime - b.mtime)
    .map((e) => e.name);
}

function saveExport(req, res, name) {
  if (!/^[\w.-]+\.kml$/.test(name)) return res.writeHead(400).end("Bad file name");
  const target = path.join(exportsDir, name);
  if (fs.existsSync(target)) return res.writeHead(200).end("Already saved");
  const chunks = [];
  req.on("data", (c) => chunks.push(c));
  req.on("end", () => {
    const body = Buffer.concat(chunks);
    if (!body.toString("utf8", 0, 200).includes("<kml")) return res.writeHead(400).end("Not a KML file");
    fs.mkdirSync(exportsDir, { recursive: true });
    if (listExports().some((f) => fs.readFileSync(path.join(exportsDir, f)).equals(body))) return res.writeHead(200).end("Already saved");
    fs.writeFileSync(target, body);
    res.writeHead(201).end("Saved");
  });
}

http.createServer((req, res) => {
  const url = decodeURIComponent(req.url.split("?")[0]);
  if (req.method === "GET" && url === "/exports.json") {
    return res.writeHead(200, { "Content-Type": types[".json"] }).end(JSON.stringify(listExports()));
  }
  if (req.method === "PUT" && url.startsWith("/exports/")) return saveExport(req, res, url.slice("/exports/".length));

  const file = path.join(__dirname, path.normalize(url).replace(/^(\.\.[/\\])+/, ""));
  const target = file.endsWith(path.sep) ? path.join(file, "index.html") : file;
  fs.readFile(target, (err, body) => {
    if (err) return res.writeHead(404).end("Not found");
    res.writeHead(200, { "Content-Type": types[path.extname(target)] || "application/octet-stream" }).end(body);
  });
}).listen(port, () => console.log(`Yard Explorer on http://localhost:${port}`));
