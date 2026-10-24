// Rebuild the original hero composition with transparent, theme-specific artwork.
// Run: node tools/build-readme-hero.mjs
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
const require = createRequire(import.meta.url);
const sharp = require('sharp');
const root = fileURLToPath(new URL('../docs/images/', import.meta.url));
const width = 1440, height = 720;
const phone = await sharp(path.join(root, 'framed-home.png')).resize({ width: 340 }).png().toBuffer();
const phoneHeight = (await sharp(phone).metadata()).height;
const shadow = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}"><defs><filter id="shadow" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="20"/></filter></defs><rect x="1028" y="48" width="340" height="${phoneHeight}" rx="50" fill="#000" opacity="0.18" filter="url(#shadow)"/></svg>`);
for (const [theme, colors] of Object.entries({
  light: { ink: '#182d41', muted: '#4b5967', accent: '#26705c', footer: '#647080' },
  dark: { ink: '#eef5fb', muted: '#a3adbb', accent: '#b7ead4', footer: '#7d8794' },
})) {
  const text = (x, y, value, { size = 23, color = colors.muted, weight = 400, mono = false, spacing = 0 } = {}) =>
    `<text x="${x}" y="${y}" font-family="${mono ? 'Consolas, Menlo, monospace' : 'Segoe UI, Helvetica, Arial, sans-serif'}" font-size="${size}" font-weight="${weight}" fill="${color}" letter-spacing="${spacing}">${value}</text>`;
  const copy = text(72, 164, 'ANDROID COMPANION', { size: 17, color: colors.accent, weight: 700, mono: true, spacing: 4 }) +
    text(72, 242, 'Your desktop usage.', { size: 58, color: colors.ink, weight: 700 }) +
    text(72, 310, 'In your pocket.', { size: 58, color: colors.ink, weight: 700 }) +
    text(72, 370, 'Totals, account limits, models and trends from') +
    text(72, 404, 'the Token Monitor Hub on your own desktop.') +
    text(72, 438, 'The phone reads. The desktop stays in control.') +
    text(72, 510, 'PRIVATE HUB  ·  READ ONLY  ·  NO ANALYTICS', { size: 16, color: colors.accent, weight: 700, mono: true, spacing: 2 }) +
    text(72, 552, 'Android 8+  ·  synthetic demonstration data', { size: 18, color: colors.footer, mono: true });
  const overlay = Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}">${copy}</svg>`);
  await sharp({ create: { width, height, channels: 4, background: { r: 0, g: 0, b: 0, alpha: 0 } } })
    .composite([{ input: shadow, left: 0, top: 0 }, { input: phone, left: 1028, top: 36 }, { input: overlay, left: 0, top: 0 }])
    .png({ compressionLevel: 9 }).toFile(path.join(root, `hero-${theme}.png`));
}
console.log('Rendered transparent light and dark README heroes from the existing capture.');
