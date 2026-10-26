import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = fileURLToPath(new URL('../app/src/main/res/', import.meta.url));
const files = ['strings_ui.xml', 'label_strings.xml', 'language_strings.xml', 'format_strings.xml', 'widget_strings.xml'];
function strings(file) {
  return new Map([...readFileSync(file, 'utf8').matchAll(/<string name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g)].map(m => [m[1], m[2]]));
}
const base = new Map(files.flatMap(f => [...strings(path.join(root, 'values', f))]));
const formats = s => [...s.matchAll(/%\d+\$s|%%/g)].map(m => m[0]).sort();
for (const locale of ['zh-rCN', 'zh-rTW', 'ko', 'ja', 'pt-rBR', 'es', 'hi', 'gu', 'bn', 'ta', 'te']) {
  const translated = strings(path.join(root, `values-${locale}`, 'strings.xml'));
  for (const [key, value] of base) {
    if (/^widget_(brand|sample|placeholder)/.test(key)) continue;
    assert.ok(translated.get(key)?.trim(), `${locale}: missing ${key}`);
    assert.deepEqual(formats(translated.get(key)), formats(value), `${locale}: placeholders differ in ${key}`);
    assert.ok(!/ZXQ|#{2,}\d/.test(translated.get(key)), `${locale}: unresolved marker in ${key}`);
  }
}
console.log('All 12 language packs have matching keys and format placeholders.');
