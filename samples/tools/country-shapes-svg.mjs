// Draws the outline of every UN member state big enough to recognise as a small SVG, into shapes.json.
// Copy the three country-shapes files into a folder, run npm install world-atlas topojson-client d3-geo
// i18n-iso-countries there, then node country-shapes-svg.mjs. The outlines come from Natural Earth
// (public domain) through the world-atlas package.
import fs from 'fs';
import { feature } from 'topojson-client';
import { geoAzimuthalEqualArea, geoPath, geoCentroid, geoArea, geoDistance } from 'd3-geo';
import countries from 'i18n-iso-countries';
const topo = JSON.parse(fs.readFileSync('node_modules/world-atlas/countries-50m.json'));
const all = feature(topo, topo.objects.countries).features;

// Microstates and island nations too small to recognise by shape at this resolution.
const tooSmall = new Set(['VA','MC','SM','LI','AD','NR','MH','FM','PW','KI','MV','TO','WS','SC','KN','LC','VC','GD','DM','AG','BB','ST','KM','MU','CV','BH','SG','MT','TV']);
// Not UN member states.
const notMembers = new Set(['MP','VI','GU','AS','PR','GS','IO','SH','PN','AI','FK','KY','BM','VG','TC','MS','JE','GG','IM','TW','EH','PS','PM','WF','MF','BL','PF','NC','TF','AX','GL','FO','MO','HK','HM','NF','AQ','SX','NU','CK','AW','CW']);
const overrides = JSON.parse(fs.readFileSync(new URL('country-shapes-overrides.json', import.meta.url)));

const out = [];
const seen = new Set();
for (const f of all) {
  const a2 = countries.numericToAlpha2(f.id);
  if (!a2 || seen.has(a2) || tooSmall.has(a2) || notMembers.has(a2)) continue;
  if (a2 === 'AU' && f.properties.name !== 'Australia') continue;
  seen.add(a2);
  const polys = f.geometry.type === 'Polygon' ? [f.geometry.coordinates] : f.geometry.coordinates;
  const parts = polys.map(p => { const g = { type: 'Polygon', coordinates: p }; return { p, area: geoArea(g), c: geoCentroid(g) }; });
  const main = parts.reduce((a, b) => (b.area > a.area ? b : a));
  const radius = Math.max(...main.p[0].map(pt => geoDistance(pt, main.c)));
  const o = overrides[a2] || {};
  const k = o.k ?? 1.6;
  const kept = parts.filter(x => geoDistance(x.c, main.c) <= k * radius + (o.extra ?? 0.02));
  const geom = { type: 'MultiPolygon', coordinates: kept.map(x => x.p) };
  const centre = geoCentroid(geom);
  const proj = geoAzimuthalEqualArea().rotate([-centre[0], -centre[1]]).fitExtent([[6, 6], [194, 194]], geom);
  const d = simplifiedPath(proj, geom);
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="200" height="200" viewBox="0 0 200 200"><path fill="#3a7bc8" stroke="#1d4f8c" stroke-width="1" stroke-linejoin="round" d="${d}"/></svg>`;
  out.push({ a2, name: f.properties.name, svg, dropped: parts.length - kept.length });
}
fs.writeFileSync('shapes.json', JSON.stringify(out));
// Then run country-shapes-stack.cjs, which adds the names and writes the sample stack.
const total = out.reduce((s, x) => s + x.svg.length, 0);
console.log(out.length, 'countries', Math.round(total / 1024), 'KB svg');
console.log(out.sort((a,b)=>b.svg.length-a.svg.length).slice(0,8).map(x=>x.a2+':'+Math.round(x.svg.length/1024)+'KB').join(' '));

function simplifiedPath(proj, geom) {
  const rings = []; let ring = null;
  geoPath(proj, {
    moveTo(x, y) { ring = [[x, y]]; rings.push(ring); },
    lineTo(x, y) { ring.push([x, y]); },
    closePath() {},
    arc() {},
  })(geom);
  const r1 = v => Math.round(v * 10) / 10;
  return rings
    .filter(r => Math.abs(ringArea(r)) >= 1.5)
    .map(r => rdp(r, 0.35))
    .filter(r => r.length >= 3)
    .map(r => {
      let s = 'M' + r1(r[0][0]) + ' ' + r1(r[0][1]);
      for (let i = 1; i < r.length; i++) {
        const dx = r1(r[i][0] - r[i - 1][0]), dy = r1(r[i][1] - r[i - 1][1]);
        s += 'l' + dx + (dy < 0 ? '' : ' ') + dy;
      }
      return s + 'z';
    }).join('');
}
function ringArea(r) { let a = 0; for (let i = 0; i < r.length; i++) { const [x1, y1] = r[i], [x2, y2] = r[(i + 1) % r.length]; a += x1 * y2 - x2 * y1; } return a / 2; }
function rdp(pts, eps) {
  if (pts.length < 3) return pts;
  const [ax, ay] = pts[0], [bx, by] = pts[pts.length - 1];
  let max = 0, idx = 0;
  const len = Math.hypot(bx - ax, by - ay) || 1e-9;
  for (let i = 1; i < pts.length - 1; i++) {
    const d = len < 1e-6 ? Math.hypot(pts[i][0] - ax, pts[i][1] - ay) : Math.abs((bx - ax) * (ay - pts[i][1]) - (ax - pts[i][0]) * (by - ay)) / len;
    if (d > max) { max = d; idx = i; }
  }
  if (max <= eps) return [pts[0], pts[pts.length - 1]];
  return rdp(pts.slice(0, idx + 1), eps).slice(0, -1).concat(rdp(pts.slice(idx), eps));
}
