// Renders the README boards from the emulator captures in docs/images.
// Run `npm install --prefix tools` once, then `node tools/build-showcase.mjs`.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = fileURLToPath(new URL('../docs/images/', import.meta.url));
const at = (file) => path.join(root, file + '.png');

const ink = '#eef5fb';
const muted = '#a3adbb';
const mint = '#b7ead4';
const sans = 'Segoe UI, Helvetica, Arial, sans-serif';
const mono = 'Consolas, Menlo, monospace';
const preserveHero = process.env.TOKEN_MONITOR_PRESERVE_HERO === '1';

const text = (x, y, value, { size = 24, color = muted, weight = 400, family = sans, spacing = 0, anchor = 'start' } = {}) =>
  `<text x="${x}" y="${y}" font-family="${family}" font-size="${size}" font-weight="${weight}" fill="${color}" letter-spacing="${spacing}" text-anchor="${anchor}">${value}</text>`;

/** The dashboard's charcoal gradient with two soft accent glows. */
const backdrop = (width, height, glows = true) => `
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#343a41"/><stop offset="0.45" stop-color="#22262c"/><stop offset="1" stop-color="#0f1318"/></linearGradient>
    <filter id="blur" x="-60%" y="-60%" width="220%" height="220%"><feGaussianBlur stdDeviation="90"/></filter>
  </defs>
  <rect width="${width}" height="${height}" fill="url(#bg)"/>
  ${glows ? `<circle cx="${width * 0.78}" cy="${height * 0.15}" r="${height * 0.35}" fill="#b7ead4" opacity="0.10" filter="url(#blur)"/>
  <circle cx="${width * 0.25}" cy="${height * 0.95}" r="${height * 0.3}" fill="#73bdf5" opacity="0.08" filter="url(#blur)"/>` : ''}`;

const svgLayer = (width, height, body) => ({ input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">${body}</svg>`), left: 0, top: 0 });

/** Rounds a raster's corners so it can sit inside a frame or float as a card. */
async function rounded(file, width, radius, crop) {
  let image = sharp(at(file));
  if (crop) image = image.extract(crop);
  const buffer = await image.resize({ width }).png().toBuffer();
  const { height } = await sharp(buffer).metadata();
  const mask = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><rect width="${width}" height="${height}" rx="${radius}" ry="${radius}" fill="#fff"/></svg>`);
  return { buffer: await sharp(buffer).composite([{ input: mask, blend: 'dest-in' }]).png().toBuffer(), width, height };
}

/** A soft drop shadow the size of a card, drawn as a blurred rounded rect. */
const shadow = (width, height, radius, spread = 40) => ({
  input: Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width + spread * 2}" height="${height + spread * 2}"><defs><filter id="s" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="${spread / 2}"/></filter></defs><rect x="${spread}" y="${spread + 12}" width="${width}" height="${height}" rx="${radius}" fill="#000" opacity="0.55" filter="url(#s)"/></svg>`),
  width: width + spread * 2, height: height + spread * 2, spread,
});

/**
 * A phone frame around a dashboard capture. The captures carry an empty status-bar band at
 * the top, which is cropped so the screen starts at the app's own header.
 */
async function phone(file, screenWidth) {
  const bezel = Math.round(screenWidth * 0.055);
  const radius = Math.round(screenWidth * 0.14);
  const screen = await rounded(file, screenWidth, radius - bezel, { left: 0, top: 110, width: 1179, height: 2350 });
  const width = screenWidth + bezel * 2;
  const height = screen.height + bezel * 2;
  const frame = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">
    <rect x="1" y="1" width="${width - 2}" height="${height - 2}" rx="${radius}" fill="#0b0e12" stroke="#4a525c" stroke-width="2"/>
    <rect x="${bezel / 2}" y="${bezel / 2}" width="${width - bezel}" height="${height - bezel}" rx="${radius - bezel / 2}" fill="none" stroke="#1c2128" stroke-width="${bezel / 2}"/>
    <circle cx="${width / 2}" cy="${bezel + Math.round(screenWidth * 0.035)}" r="${Math.round(screenWidth * 0.018)}" fill="#05070a"/>
  </svg>`);
  const buffer = await sharp(frame).composite([{ input: screen.buffer, left: bezel, top: bezel }]).png().toBuffer();
  return { buffer, width, height };
}

async function place(canvas, layers) {
  const composites = [];
  for (const layer of layers) {
    if (layer.shadow) {
      const s = shadow(layer.item.width, layer.item.height, layer.radius ?? 28);
      composites.push({ input: s.input, left: layer.left - s.spread, top: layer.top - s.spread });
    }
    composites.push({ input: layer.item.buffer, left: layer.left, top: layer.top });
  }
  return canvas.composite(composites);
}

async function board(name, width, height, copy, layers) {
  const base = sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">${backdrop(width, height)}</svg>`)).png();
  const withArt = await (await place(base, layers)).png().toBuffer();
  await sharp(withArt).composite([svgLayer(width, height, copy)]).png().toFile(at(name));
}

// Hero: the pitch on the left, the phone with two widgets floating in front of it on the right.
if (!preserveHero) {
  const home = await phone('home', 340);
  const large = await rounded('widget-large', 300, 28);
  // One grid: the phone's top sits on the kicker's cap line, and the phone, the widget and the
  // footer line share a bottom edge.
  // Equal margins above the phone and below the shared bottom line.
  const top = 80, bottom = top + home.height;
  await board('hero', 1440, bottom + top,
    text(72, top + 12, 'ANDROID COMPANION', { size: 17, color: mint, weight: 700, family: mono, spacing: 4 }) +
    text(72, top + 94, 'Your desktop usage.', { size: 60, color: ink, weight: 700 }) +
    text(72, top + 162, 'In your pocket.', { size: 60, color: ink, weight: 700 }) +
    text(72, top + 224, 'Tokens, limits, models and trends from the', { size: 23 }) +
    text(72, top + 258, 'Token Monitor Hub on your own desktop.', { size: 23 }) +
    text(72, top + 292, 'Live when you look. Sharp on the home screen.', { size: 23 }) +
    text(72, top + 364, 'PRIVATE HUB  ·  READ ONLY  ·  RESIZABLE WIDGETS', { size: 16, color: mint, weight: 700, family: mono, spacing: 2 }) +
    text(72, bottom, 'v0.60.0  ·  Android 8+  ·  synthetic demonstration data', { size: 16, family: mono, color: '#7d8794' }),
    [
      { item: home, left: 1440 - 72 - home.width, top, shadow: true, radius: 50 },
      { item: large, left: 1440 - 72 - home.width - 40 - large.width, top: bottom - large.height, shadow: true, radius: 28 },
    ]);
}

// Social preview: the same pitch at the 1280×640 card GitHub shows for links.
{
  const home = await phone('home', 260);
  const large = await rounded('widget-large', 240, 24);
  // The card is a fixed 1280×640, so the phone is centered vertically and the text follows it.
  const top = Math.round((640 - home.height) / 2), bottom = top + home.height;
  await board('social-preview', 1280, 640,
    text(64, top + 12, 'TOKEN MONITOR FOR ANDROID', { size: 16, color: mint, weight: 700, family: mono, spacing: 4 }) +
    text(64, top + 94, 'Your desktop usage.', { size: 54, color: ink, weight: 700 }) +
    text(64, top + 156, 'In your pocket.', { size: 54, color: ink, weight: 700 }) +
    text(64, top + 216, 'Live totals, account limits, models and trends', { size: 22 }) +
    text(64, top + 248, 'over your own private Hub. Read-only by design.', { size: 22 }) +
    text(64, bottom, 'v0.60.0  ·  Android 8+  ·  synthetic demonstration data', { size: 15, family: mono, color: '#7d8794' }),
    [
      { item: home, left: 1280 - 64 - home.width, top, shadow: true, radius: 36 },
      { item: large, left: 1280 - 64 - home.width - 36 - large.width, top: bottom - large.height, shadow: true, radius: 24 },
    ]);
}

// Framed dashboard captures for the README gallery.
for (const name of ['home', 'filtered-models', 'trends', 'devices', 'projects', 'settings']) {
  const framed = await phone(name, 560);
  await sharp(framed.buffer).png({ compressionLevel: 9 }).toFile(at('framed-' + name));
}

// Widget boards.
{
  const label = (x, y, value) => text(x, y, value, { size: 16, color: mint, weight: 700, family: mono, spacing: 3 });
  const compact = await rounded('widget-compact', 240, 30);
  const portrait = await rounded('widget-portrait', 240, 30);
  const wide = await rounded('widget-wide', 510, 30);
  const overview = await rounded('widget-overview', 510, 30);
  const large = await rounded('widget-large', 510, 30);
  await board('widget-gallery', 1440, 1040,
    text(56, 78, 'A layout for the space you give it.', { size: 42, color: ink, weight: 700 }) +
    text(56, 118, 'Five compositions, rendered from the real widget with demonstration data.', { size: 22 }) +
    label(56, 190, 'COMPACT') + label(56, 500, 'PORTRAIT') + label(346, 190, 'WIDE') + label(346, 500, 'OVERVIEW') + label(896, 190, 'DETAILED') +
    text(56, 1000, 'Resize in your launcher. Cell sizes and padding vary by phone; the widget reflows for the space it gets.', { size: 20 }),
    [
      { item: compact, left: 56, top: 210, shadow: true, radius: 30 },
      { item: portrait, left: 56, top: 520, shadow: true, radius: 30 },
      { item: wide, left: 346, top: 210, shadow: true, radius: 30 },
      { item: overview, left: 346, top: 520, shadow: true, radius: 30 },
      { item: large, left: 896, top: 210, shadow: true, radius: 30 },
    ]);
  const themes = [];
  for (const [index, theme] of ['default', 'obsidian', 'porcelain', 'custom'].entries()) {
    themes.push({ item: await rounded('widget-theme-' + theme, 300, 24), left: 56 + index * 346, top: 200, shadow: true, radius: 24 });
  }
  await board('widget-themes', 1440, 620,
    text(56, 78, 'One theme, inside the app and on the home screen.', { size: 36, color: ink, weight: 700 }) +
    text(56, 118, 'Default, Obsidian, Porcelain and a pasted desktop theme code.', { size: 22 }) +
    label(56, 180, 'DEFAULT') + label(402, 180, 'OBSIDIAN') + label(748, 180, 'PORCELAIN') + label(1094, 180, 'CUSTOM') +
    text(56, 590, 'Changing the theme in Settings recolors saved widgets on the spot.', { size: 20 }),
    themes);
}

console.log(preserveHero
  ? 'Preserved hero; rendered social preview, framed captures, widget gallery and widget themes.'
  : 'Rendered hero, social preview, framed captures, widget gallery and widget themes.');
