// Produces the Latin subsets of JetBrains Mono the widget bundles.
// Source: JetBrains Mono (OFL 1.1), as shipped in Android Studio's JetBrains Runtime fonts folder.
import { readFileSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import subsetFont from "subset-font";

const toolDirectory = dirname(fileURLToPath(import.meta.url));
const source = resolve(process.argv[2] ?? "C:/Program Files/Android/Android Studio/jbr/lib/fonts");
const targetDirectory = resolve(toolDirectory, "../app/src/main/res/font");
const text = [...Array(0x7f - 0x20).keys()].map((i) => String.fromCharCode(0x20 + i)).join("")
    + "\u00a0¡¢£¤¥§©«®°±²³µ·»¼½¾¿ÀÁÂÃÄÅÆÇÈÉÊËÌÍÎÏÑÒÓÔÕÖØÙÚÛÜÝßàáâãäåæçèéêëìíîïñòóôõöøùúûüýÿ"
    + "€…·•–—‘’“”‹›→←↑↓×÷≈≠≤≥∞∑Σ";
for (const [file, target] of [["JetBrainsMono-Regular.ttf", "jetbrains_mono_regular.ttf"], ["JetBrainsMono-Bold.ttf", "jetbrains_mono_bold.ttf"]]) {
    const buffer = await subsetFont(readFileSync(resolve(source, file)), text, { targetFormat: "truetype" });
    writeFileSync(resolve(targetDirectory, target), buffer);
    console.log(target, buffer.length, "bytes");
}
