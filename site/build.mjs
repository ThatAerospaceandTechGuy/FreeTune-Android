#!/usr/bin/env node
/**
 * Builds the FreeTune marketing/download site into site/public.
 *
 * Reads the real APKs and checksums from ../dist, so the published page always
 * matches the binaries that are shipped. No dependencies, no bundler.
 */
import {
  copyFileSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const siteDir = dirname(fileURLToPath(import.meta.url));
const rootDir = dirname(siteDir);
const distDir = join(rootDir, "dist");
const srcDir = join(siteDir, "src");
const outDir = join(siteDir, "public");

const siteUrl = (process.env.SITE_URL || "https://freetunes.pages.dev").replace(/\/$/, "");
const github = "https://github.com/ThatAerospaceandTechGuy/FreeTune-Android";

const NOTES = {
  "0.9.1": "Equalizer, dynamic colours, forgotten favourites",
  "0.9.0": "Queue editing, artist pages, backup, playlist import, stats",
  "0.8.4": "Lyrics backfill for existing downloads",
  "0.8.3": "Lyrics saved beside every download",
  "0.8.2": "Stream recovery and automatic lyrics",
  "0.8.1": "Superseded stream URL experiment",
  "0.8.0": "Queue, repeat, sleep timer, lyrics",
  "0.7.0": "Notification controls, radio, daily mix",
  "0.6.1": "Patch build after 0.6.0",
  "0.6.0": "Earlier build",
  "0.5.0": "Earlier build",
  "0.4.0": "Earlier build",
  "0.3.0": "Earlier build",
  "0.2.0": "Earliest preserved build",
};

const hashes = new Map();
for (const line of readFileSync(join(distDir, "SHA256.txt"), "utf8").split(/\r?\n/)) {
  const match = line.match(/FreeTune-([0-9.]+)\.apk\)\s*=\s*([0-9a-fA-F]{64})/);
  if (match) hashes.set(match[1], match[2].toLowerCase());
}

const versions = readdirSync(distDir)
  .map((name) => {
    const match = name.match(/^FreeTune-([0-9.]+)\.apk$/);
    if (!match) return null;
    const stat = statSync(join(distDir, name));
    return {
      version: match[1],
      file: name,
      bytes: stat.size,
      date: stat.mtime.toISOString().slice(0, 10),
      sha: hashes.get(match[1]) || "",
    };
  })
  .filter(Boolean)
  .sort((a, b) => compareVersions(b.version, a.version));

if (!versions.length) throw new Error("No FreeTune-*.apk files found in dist/");

function compareVersions(a, b) {
  const left = a.split(".").map(Number);
  const right = b.split(".").map(Number);
  for (let i = 0; i < Math.max(left.length, right.length); i += 1) {
    const diff = (left[i] || 0) - (right[i] || 0);
    if (diff) return diff;
  }
  return 0;
}

const prettyDate = (iso) =>
  new Date(`${iso}T00:00:00Z`).toLocaleDateString("en-GB", {
    day: "numeric",
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  });

const megabytes = (bytes) => `${(bytes / 1048576).toFixed(1)} MB`;

const latest = versions[0];

const rows = versions
  .map((entry, index) => {
    const isLatest = index === 0;
    return [
      `<tr${isLatest ? ' class="is-latest"' : ""}>`,
      `<td data-label="Version"><span class="v">${entry.version}</span>${isLatest ? '<span class="tag">latest</span>' : ""}<span class="note">${NOTES[entry.version] || "Earlier build"}</span></td>`,
      `<td data-label="Built">${prettyDate(entry.date)}</td>`,
      `<td data-label="Size">${megabytes(entry.bytes)}</td>`,
      `<td data-label="SHA-256"><code title="${entry.sha}">${entry.sha.slice(0, 12)}&hellip;</code></td>`,
      `<td data-label="Download"><a class="dl" href="/downloads/${entry.file}" download>APK</a></td>`,
      "</tr>",
    ].join("");
  })
  .join("\n          ");

let html = readFileSync(join(srcDir, "template.html"), "utf8");
const tokens = {
  "{{VERSION}}": latest.version,
  "{{DATE}}": prettyDate(latest.date),
  "{{SIZE}}": megabytes(latest.bytes),
  "{{BYTES}}": String(latest.bytes),
  "{{SHA}}": latest.sha,
  "{{DOWNLOAD}}": `/downloads/${latest.file}`,
  "{{COUNT}}": String(versions.length),
  "{{ROWS}}": rows,
  "{{GITHUB}}": github,
  "{{SITE_URL}}": siteUrl,
};
for (const [token, value] of Object.entries(tokens)) html = html.split(token).join(value);

rmSync(outDir, { recursive: true, force: true });
mkdirSync(join(outDir, "downloads"), { recursive: true });

for (const asset of ["styles.css", "app.js", "favicon.svg"]) {
  copyFileSync(join(srcDir, asset), join(outDir, asset));
}
for (const entry of versions) {
  copyFileSync(join(distDir, entry.file), join(outDir, "downloads", entry.file));
}

writeFileSync(join(outDir, "index.html"), html);
writeFileSync(
  join(outDir, "robots.txt"),
  `User-agent: *\nAllow: /\n\nSitemap: ${siteUrl}/sitemap.xml\n`,
);
writeFileSync(
  join(outDir, "sitemap.xml"),
  `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
  <url>
    <loc>${siteUrl}/</loc>
    <lastmod>${latest.date}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>1.0</priority>
  </url>
</urlset>
`,
);
writeFileSync(
  join(outDir, "_headers"),
  `/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  X-Frame-Options: SAMEORIGIN
  Permissions-Policy: geolocation=(), microphone=(), camera=()

/downloads/*
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment
  Cache-Control: public, max-age=604800, immutable

/styles.css
  Cache-Control: public, max-age=3600

/app.js
  Cache-Control: public, max-age=3600
`,
);

const redirects = [
  `/latest /downloads/${latest.file} 302`,
  ...versions.map((entry) => `/v/${entry.version} /downloads/${entry.file} 302`),
];
writeFileSync(join(outDir, "_redirects"), `${redirects.join("\n")}\n`);

console.log(`Built ${outDir}`);
console.log(`  latest: ${latest.version} (${megabytes(latest.bytes)}, ${latest.sha.slice(0, 12)}...)`);
console.log(`  versions: ${versions.map((entry) => entry.version).join(", ")}`);
console.log(`  site: ${siteUrl}`);
