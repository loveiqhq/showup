# Android's Manrope: the apostrophe curls at a word end, as Chrome and iOS draw it

Android's five Manrope files (`android-preview-project/app/src/main/res/font/manrope_*.ttf`) are
**the upstream fonts plus one OpenType lookup**. iOS keeps the upstream files unchanged
(`ios-app/ShowUpWelcome/Fonts/Manrope-*.ttf`). `_patch_manrope_android_apostrophe.py` writes the
Android files from the iOS ones.

## Why

Every copy string carries a straight apostrophe, U+0027. Manrope's own `calt` feature draws it as the
curly one (the `quoteright` glyph, U+2019's) in contexts: inside a word -- "you're", "I'm", "don't",
"I've", "we'll" -- and in two contexts that need to **see a space after them**: `'s ` and `em' `.

| | "It's now closed." | "you're free" | "you've" |
|---|---|---|---|
| Spec sheet (Chrome) | It’s (curly) | you’re | you've (straight) |
| iOS (Core Text) | It’s | you’re | you've |
| Android, upstream font | **It's (straight)** | you’re | you've |

Chrome and Core Text shape a whole run of text at once, so HarfBuzz sees the space after "It's" and the
rule matches. **Android's text engine, Minikin, shapes word by word**: it cuts the text before and after
every space (`LayoutSplitter`, for its layout cache) and hands HarfBuzz "It's" alone. With no space in
the buffer, the two space rules can never match on Android. Seen on Date 08 (SHOWUP-199, 27 Nov 2026):
"It’s now closed.", "each other’s time.", "where it’s needed." were curly in the spec and on iOS and
straight on Android, both under Robolectric and on a Pixel 7 emulator. Every other apostrophe already
agreed.

The font files are not the difference -- they were byte-identical on both platforms. The shaping is.

## What changed

One lookup, appended to the end of GSUB's lookup list and to the end of the `calt` feature (which every
script and language system lists, so it applies everywhere `calt` does). For each of the two space
rules it holds two chained-context subtables, tried in this order:

| Subtable | Context | Effect |
|---|---|---|
| ignore | `'` then `s` then **any glyph** | nothing -- so "it's," and "it's." stay straight, as in Chrome, where no space follows either |
| substitute | `'` then `s`, and the word ends | `'` becomes `’`, through the font's own nested lookup |
| ignore | `e m` before, `'`, then **any glyph** | nothing |
| substitute | `e m` before, `'`, and the word ends | `'` becomes `’` |

Inside a Minikin word the glyph after the `s` is never a space, so "the word ends here" is exactly
"a space came next" -- the original condition, read from the only side Android can see. Where the
upstream rule already fired (a shaper that does see the space), the apostrophe is already `’` and the
new lookup has nothing to match. HarfBuzz applies it straight after `calt`'s own lookup: every lookup
in between is nested, not a feature of its own.

Nothing else moved:

- **GSUB is edited in place, never rebuilt from a feature file.** feaLib's builder replaces the whole
  table and would drop `liga`, `locl`, the numeral features and the other `calt` rules. The script
  decompiles the patched file, removes the new lookup and its `calt` entry, and requires the result to
  decompile identically to the original GSUB -- on every run, before it writes anything.
- **Every other table is written back byte for byte** (also checked on every run), except `head`
  (its checksum) and `name` ID 5, the version string, which gains
  `; ShowUp Android: calt apostrophe at a word end` so a patched file can be told from an upstream one.
- Glyph outlines, metrics, kerning and the cmap are untouched. The only visible change is the glyph
  chosen, and `quoteright` is a little wider than `quotesingle` (2000 units to the em): +16 Regular,
  +27 Medium, +38 SemiBold, +47 Bold, +58 ExtraBold -- under half a point at body sizes. A line can
  re-wrap where it was within that of the edge, towards what the spec and iOS already show.

Checked against the 234 apostrophes in the 210 string literals under `app/src/main/java`, all five
weights, shaping each literal with HarfBuzz both ways (whole, as Chrome does; cut at every space, as
Minikin does): Android picked Chrome's glyph for 172 of them before the patch, and for all 234 after.
The only difference left is by construction: an `'s` that ends a paragraph or a styled span with
nothing after it ("Kim's" as the very last word) curls on Android and stays straight in Chrome. No
literal ends that way.

`ManropeApostropheTest` draws the cases with Robolectric's native graphics (the real Minikin and
HarfBuzz) for all five weights and compares pixels.

### Not patched

- **Lora.** None of its five files substitutes the apostrophe at all -- no lookup maps `quotesingle`,
  and no contextual rule mentions it or a space -- so Lora's apostrophe is straight everywhere already
  ("didn't show up." in Date 08's headline), and Android matches.
- **Manrope's arrow rule.** `calt` also swaps `→` and `←` for their capital-height forms between two
  capitals with spaces either side ("A → B"). Minikin isolates that arrow between two spaces, so no
  word-end lookup can see both sides; it would have to guess. No copy string puts an arrow between
  capitals ("End date → Review" has a lowercase `e` before it, which the rule does not take in Chrome
  either).

## Re-running it, and when

Run it whenever the Manrope files change -- a new upstream version, a new weight. It reads iOS's copies,
so running it twice gives the same bytes, and pointing it at an already patched file stops with an error
rather than adding the lookup twice.

```
python -m venv .venv
.venv/Scripts/pip install fonttools==4.69.1          (.venv/bin/pip on macOS / Linux)
.venv/Scripts/python mobile/welcome-screen/fonts/_patch_manrope_android_apostrophe.py
```

The virtualenv is throwaway; do not commit it. fontTools 4.69.1 is the version these files were written
with: another version may lay GSUB out differently -- same rules, different bytes -- and the script's
check holds either way. It packs GSUB with fontTools' own packer even when `uharfbuzz` is installed, so
the bytes do not depend on that.

**If it stops with "Manrope's space rules changed"**, upstream has changed which contexts need a space.
Read the new rules (the message lists them), decide whether each one is safe to apply at a word end the
same way, and update `EXPECTED` in the script. Then run `ManropeApostropheTest`.

**When adding a weight**, add it to `FONTS` in the script and to the `weights` map in the test.

**Do not copy a Manrope file from `fonts/` or `ios-app/` into Android's `res/font/` directly** -- that
silently removes the patch. `ManropeApostropheTest` fails if it happens.

## Licence

SIL Open Font License 1.1, same as the upstream files -- see `OFL-Manrope.txt`. The OFL allows modified
versions to be distributed under the same licence. Manrope declares no Reserved Font Name, so the family
name stays "Manrope". Each patched file keeps the upstream copyright notice, licence description and
licence URL in its `name` table (IDs 0, 13, 14), unchanged.
