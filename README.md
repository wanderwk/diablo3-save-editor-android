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

### 2026-08-08 incident: edited saves recognized as "old"/invalid by the game

**Symptom**: after editing a save with this app (most recently reproduced
right after adding items via the Gems/Items screens), the game refused to
load it — no crash, just treated as an old/invalid save.

**Investigation**: with no faulty save sample available to byte-diff
directly, the fix was derived from first principles by cross-referencing the
real Blizzard `.proto` schema (the same
[GoobyCorp/D3Edit](https://github.com/GoobyCorp/D3Edit) source already used
to fix the item/gem layout above) field-by-field against every write path in
this app. Two fields our code writes are declared `sint32` in the real
schema (`Items.proto`'s `SavedItem.square_index` and `Hero.proto`'s
`Digest.level`) — proto's `sint32` uses **zigzag encoding**, a different
transform from a plain varint (`item_quality_level`, field 10, was already
handled correctly this way; these two were not). A real protobuf parser
*always* zigzag-decodes a field declared `sint32`, regardless of how the
bytes were written — so a plain varint we intended to mean "16" silently
became "8" once the real game read it back.

- `addItemsToHero`/`addItemsToStash` picked new inventory positions
  (`square_index`) starting at 16, incrementing by 2, specifically to avoid
  the low range used by equipped items and existing inventory — but wrote
  them unencoded. The real game would decode our intended 16/18/20/... as
  8/9/10/..., landing new items much closer to (and risking colliding with)
  real occupied grid cells — a plausible trigger for a save-integrity
  rejection.
- `writeHeroLevel` had the same gap on the hero's actual level field:
  setting level 70 in the app would load in-game as level 35.

Two fields that look similar were deliberately **left untouched** after
closer inspection, to avoid trading a real bug for a regression:
`Handle.game_balance_type` and `SavedItem.item_slot` (544) are also `sint32`
in the real schema, but their values in this codebase were originally
copied verbatim from a real save's raw wire bytes (not chosen by us as a
semantic value) — they're already correctly encoded as-is, and applying
zigzag encoding to them again would have corrupted them.

**Fix**: `zigzagEncode32`/`zigzagDecode32` now wrap every `square_index`
read and write (`ItemRepository`) and every hero-level read and write
(`ParagonRepository`) — `highest_solo_rift_completed` (field 25, plain
`uint32`) is explicitly kept un-transformed alongside it, to make sure the
two don't get conflated in a future edit. Verified with an independent
from-scratch zigzag decoder (not the code under test) confirming a real
protobuf `sint32` parser now reads back exactly the values this app writes
— see `ItemRepositoryTest.kt` and the new `ParagonRepositoryTest.kt`.

**Caveat**: this fix was built and verified statically (byte-level, without
a real save sample or physical device/emulator) — please report back if the
"recognized as old" symptom persists after this update, ideally with a
sample save, so it can be byte-diffed directly against a known-good save
the way earlier bugs in this project were.

## Building

CI builds a release APK via GitHub Actions (`.github/workflows/build.yml`),
signed with `app/ci-release.keystore` — a keystore committed to the repo
(not a real secret: no Play Store distribution, sideloaded APK only), so
the APK can be installed directly with no GitHub secret needed. This
replaced signing with AGP's auto-managed `~/.android/debug.keystore` after
that caused a real bug (see "Release-build integrity check" below) — a
*stable*, known-ahead-of-time keystore is required for the anti-tamper
hash to ever be correct, and an ambiguous auto-generated one isn't
guaranteed to be. To build locally you'll need Android Studio / a JDK 17 +
Android SDK (compileSdk 34) + NDK (26.1.10909125) environment; there's no
committed Gradle wrapper, so either open in Android Studio (which
provisions Gradle itself) or install Gradle 8.7 and run
`gradle assembleRelease`.

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

Both layers compare against a SHA-256 hash of `app/ci-release.keystore`'s
certificate (the same keystore the release build type actually signs
with — see "Building" above for why this has to be a fixed, committed
keystore rather than AGP's auto-managed debug one), computed once at
Gradle configuration time (`app/build.gradle.kts`) and baked in via a C++
compiler define + a `BuildConfig` field. Both layers fail *open* (do
nothing) on any unexpected JNI/PackageManager anomaly, and only act on a
cleanly computed, confirmed mismatch — this is a personal-use tool, so a
false positive bricking a legitimate install is worse than a missed edge
case.

**2026-08-01 incident**: the very first release build of this feature
self-killed on launch for a real, legitimately-signed install. Root cause:
`keytool -genkeypair` without `-storetype` defaults to PKCS12 on modern
JDKs, but AGP's internal debug-keystore creator expects/writes JKS —
finding a PKCS12 file where it expected JKS, AGP silently discarded it and
generated its own JKS keystore with a fresh random keypair for the actual
signing step, so the hash baked into the native lib (read before that
silent regeneration) no longer matched the real signing certificate.
Fixed by switching signing to `ci-release.keystore` entirely, removing
AGP's debug-keystore machinery from the picture.

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
