# D3 Save Editor (Android)

Native Android app (Kotlin + Jetpack Compose, Material You) for editing
**offline** Diablo III (Switch/Yuzu-style) save files: currencies, paragon
level, hero level, inventory/stash items (including stack quantity), and
real per-item gem sockets. Not affiliated with Blizzard Entertainment —
personal-use tool for save files you already own. No license/unlock gate:
launch straight into the app.

This is a from-scratch native rewrite of a reference Python CLI/Tk toolset
(`diablo_code/`), following the visual design handoff in
`design_handoff_d3_save_editor/` (colors, typography, shape, motion tokens —
Cinzel + Roboto, dark Diablo III mood).

## Save format (ported from the reference Python tool)

- **Cipher**: `account.dat` / `profile.dat` / `heroes/*.dat` are XOR-stream
  encrypted with a 64-bit key and rotating feedback
  (see `core/SaveCipher.kt`, based on
  [GoobyCorp/D3Edit](https://github.com/GoobyCorp/D3Edit)).
- **Encoding**: once decrypted, the payload is generic Protobuf wire format
  (no public `.proto` schema from Blizzard is available). `core/Protobuf.kt`
  is a permissive field-by-field reader/writer that round-trips every field
  byte-identical except the ones a given repository intentionally edits.
- **Currencies**: `account.dat` field 20 → field 9 → repeated field 1
  `{value: field 2, currency_id: field 3}`.
- **Paragon level**: `account.dat` field 21 → field 1 (varint).
- **Shared stash**: `account.dat` field 21 → field 2 → repeated field 1 item
  entries (same entry layout as hero items).
- **Hero name/level/class/highest Greater Rift**: `hero.dat` field 2 →
  fields 3/5/4/25.
- **Hero inventory/equipment**: `hero.dat` field 6 → repeated field 1
  `SavedItem` entries. Real field layout (not the reference Python tool's
  guess — see "Item/gem field layout" below):
  `id` (field 1), `used_socket_count` (field 7), `generator` (field 8,
  a `Generator` message carrying `seed`/`gb_handle{gbid}`/`stack_size`/
  `item_quality_level`/`contents` (socketed gems)/`legendary_item_level`).

See `core/*.kt` for the full port with inline comments pointing back at the
reference Python module each piece came from.

### Item/gem field layout

The reference Python tool's `protobuf_handler.py` guessed at the item blob
layout and got it wrong (it read a per-instance random `seed` as if it were
the item's `gbid`, among other mistakes) — every item/gem used to show up
as "Item Desconhecido". This was fixed by cross-referencing the real
`Items.proto` (compiled Python descriptors from
[GoobyCorp/D3Edit](https://github.com/GoobyCorp/D3Edit)) and validating the
corrected layout byte-for-byte against a real Switch save sample.

Real gem sockets were found the same way: a socketed gem is **not** a
separate top-level item, and applying a gem does **not** overwrite the host
item's own `gbid` (that was the old, admittedly-hacky behavior). It's an
`EmbeddedGenerator{id, generator}` entry inside the host item's
`Generator.contents` (field 13, repeated) — its own nested `Generator` has
the exact same `gb_handle{gbid}` structure as a top-level item. `used_socket_count`
(`SavedItem` field 7) tracks how many are filled. The save does **not**
store an item's total socket *capacity* (that's static game-balance data
looked up by `gbid` at runtime, same as damage/armor), so this app doesn't
enforce a socket cap — see `core/ItemRepository.kt`'s top-of-file doc
comment and `addGemToItem`/`removeGemFromItem` for the implementation, and
`ItemRepositoryTest.kt` for a synthetic-data regression test covering both
the item-field fix and the gem-socket read/write path.

Stackable item quantity (`Generator.stack_size`, field 8) is a real field
too — editable directly in the Items screen, no need to fake it with
duplicate slot entries.

## Building

CI builds a debug-signed release APK via GitHub Actions
(`.github/workflows/build.yml`) — no keystore secret needed, so the APK can
be installed directly. To build locally you'll need Android Studio /
a JDK 17 + Android SDK (compileSdk 34) environment; there's no committed
Gradle wrapper, so either open in Android Studio (which provisions Gradle
itself) or install Gradle 8.7 and run `gradle assembleRelease`.

## Release-build integrity check

Release builds only (never debug) carry a two-layer check that the APK is
still signed with the certificate it was actually built with, to raise the
bar against casual repackaging/resigning:

- **Native layer** (`app/src/main/cpp/storage_sync.cpp`, a small NDK/CMake
  module): runs from `JNI_OnLoad` (fires automatically on
  `System.loadLibrary`, no exported function to grep for), hashes the APK's
  own signing certificate via JNI reflection, and on a confirmed mismatch
  calls `kill(getpid(), SIGKILL)` directly — no exception, no tombstone.
- **Java layer** (`util/CacheWarmup.kt`): a second, independent check via
  the normal typed `PackageManager` API, fired a few seconds after startup
  (not at the obvious boot moment), throwing a generic `RuntimeException`
  on mismatch. Deliberately decoupled from the native layer in code path,
  timing, and naming, so defeating one doesn't imply the other was too.

Both layers compare against a SHA-256 hash of the actual debug-keystore
signing certificate, computed once at Gradle configuration time
(`app/build.gradle.kts`) and baked in via a C++ compiler define + a
`BuildConfig` field — so it always matches whatever really signs that
build, on any machine/CI runner. Both layers fail *open* (do nothing) on
any unexpected JNI/PackageManager anomaly, and only act on a cleanly
computed, confirmed mismatch — this is a personal-use tool, so a false
positive bricking a legitimate install is worse than a missed edge case.

## Testing

- **Unit tests** (`gradle testDebugUnitTest`) run on every CI build — plain
  JUnit tests for the `core.*` save-format logic (`ItemRepositoryTest.kt`,
  etc.) plus Robolectric tests that need a real `Context`/`AssetManager`
  (`ItemCatalogTest.kt`).
- **Instrumented Compose UI tests** (`app/src/androidTest/.../ScreensUiTest.kt`)
  cover all 7 main screens (Home, Coins, Items, Gems, Paragon, Export,
  Support) plus a bottom-nav navigation walkthrough (`AppRootNavigationTest`).
  These need a real device/emulator, so they're **not** part of the main
  push pipeline (GitHub's free-tier hosted runners only get hardware
  emulator acceleration on public repos) — run them manually via the
  "Instrumented UI Tests" workflow (`workflow_dispatch`,
  `.github/workflows/instrumented-tests.yml`) or locally with
  `gradle connectedDebugAndroidTest` against a running emulator/device.

## License

App code: no explicit license set (personal project). Bundled font:
`app/src/main/res/font/cinzel.ttf` is the "Cinzel" variable font,
© The Cinzel Project Authors, licensed under the SIL Open Font License 1.1
(`licenses/OFL-Cinzel.txt`).
