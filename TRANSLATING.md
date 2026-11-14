# Translating AeroFlux

Thank you for helping. All of AeroFlux's text (item and block names, tooltips, screens and chat
messages) goes through language keys, so a translation is one JSON
file per language.

## Where the files are

- `lang/en_us.json` is the English reference: every key the mod uses, in every Minecraft version.
  It is generated from the code, so please do not edit it.
- `lang/<locale>.json` is a translation, named with Minecraft's locale code, for example
  `lang/de_de.json`, `lang/pt_br.json` or `lang/ja_jp.json`.

## Adding or updating a language

1. Copy the keys you want to translate from `lang/en_us.json` into `lang/<locale>.json` and translate
   the text. Keys you leave out show in English, so a partial translation is fine.
2. Keep every `%s` (or `%1$s`, `%2$s`) placeholder: the game puts numbers and names there. You may
   move them around in the sentence, and numbered ones may change order.
3. Keep formatting codes such as `§7` where the English text has them.
4. Check the file:

   ```
   python assets-src/tools/lang_sync.py check
   ```

   It lists missing keys (fine), and fails on keys the mod does not use or on placeholders that do
   not match the English line.
5. Open a pull request with just your `lang/<locale>.json`.

## For maintainers

- After changing any English text in a build's `ModLanguageProvider`, run datagen in that build and
  then `python assets-src/tools/lang_sync.py export` to refresh `lang/en_us.json`.
- Before a release, run `python assets-src/tools/lang_sync.py install`. It copies each translation
  into all six builds' `src/main/resources/assets/aeroflux/lang/`, keeping only the keys each build
  has.
