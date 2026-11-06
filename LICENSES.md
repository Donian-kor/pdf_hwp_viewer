# Open-source notices

This application combines Android platform APIs and third-party open-source libraries.
Check each upstream project before redistributing a modified build.

| Component | License | Use |
|---|---|---|
| Kotlin | Apache-2.0 | Application language |
| Jetpack Compose / AndroidX Activity | Apache-2.0 | UI and activity integration |
| AndroidX Room | Apache-2.0 | Local metadata database |
| `@rhwp/core` 0.8.7 | MIT | HWP/HWPX parsing and SVG rendering |
| Vite | MIT | Bundles the HWP WebAssembly viewer during development |

The `@rhwp/core` package contains Rust/WebAssembly dependencies. Its upstream project lists third-party notices and license details in its repository/package. Keep those notices with redistributed builds.

HWP and HWPX are trademarks of Hancom Inc. This independent project is not affiliated with, sponsored by, or endorsed by Hancom Inc.

No HWP/HWPX fonts owned by third parties are bundled by default. The viewer uses device/browser fallback fonts unless separately licensed fonts are added.
