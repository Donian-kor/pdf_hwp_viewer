import { copyFile, mkdir } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const packageWasm = resolve(here, '../node_modules/@rhwp/core/rhwp_bg.wasm');
const publicDir = resolve(here, '../public');
const destination = resolve(publicDir, 'rhwp_bg.wasm');

await mkdir(publicDir, { recursive: true });
await copyFile(packageWasm, destination);
console.log('Copied @rhwp/core WebAssembly runtime to web/public/rhwp_bg.wasm');
