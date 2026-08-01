# D3 Save Editor (Android)

Native Android app (Kotlin + Jetpack Compose, Material You) for editing
**offline** Diablo III (Switch/Yuzu-style) save files: currencies, paragon
level, hero level, inventory/stash items, and a simplified "apply gem to
item" flow. Not affiliated with Blizzard Entertainment — personal-use tool
for save files you already own.

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
- **Hero inventory/equipment**: `hero.dat` field 6 → repeated field 1 item
  entries (`uid`, `slot`, item `blob` with `gbid`/`quality`/`level`).

See `core/*.kt` for the full port with inline comments pointing back at the
reference Python module each piece came from.

### Known simplification: Gems screen

The reference Python tool never reverse-engineered a real per-item "gem
socket" sub-message — its own gem UI works by directly overwriting the
target item's GBID with a gem's GBID. This app ports that same (limited)
behavior honestly rather than inventing an unverified byte layout that
could corrupt a save. The Items screen otherwise supports adding new items
(creates new slot entries — this *is* how the format really represents
"quantity" for stackable materials/gems, there's no separate stack-count
field either).

## Building

CI builds a debug-signed release APK via GitHub Actions
(`.github/workflows/build.yml`) — no keystore secret needed, so the APK can
be installed directly. To build locally you'll need Android Studio /
a JDK 17 + Android SDK (compileSdk 34) environment; there's no committed
Gradle wrapper, so either open in Android Studio (which provisions Gradle
itself) or install Gradle 8.7 and run `gradle assembleRelease`.

## License

App code: no explicit license set (personal project). Bundled font:
`app/src/main/res/font/cinzel.ttf` is the "Cinzel" variable font,
© The Cinzel Project Authors, licensed under the SIL Open Font License 1.1
(`licenses/OFL-Cinzel.txt`).
