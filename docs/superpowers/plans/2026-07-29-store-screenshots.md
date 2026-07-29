# Store Screenshots Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce two validated four-image English store screenshot sets for Logica and upload them to the current App Store Connect and Google Play listings without submitting a release.

**Architecture:** Preserve the supplied simulator captures as source-of-truth UI, create one restrained AI-generated abstract background, and use a small Pillow renderer for deterministic typography, cropping, and platform-specific composition. A focused unittest verifies dimensions, color mode, filenames, and safe crop limits; visual contact sheets provide the final human check before browser upload.

**Tech Stack:** Python 3, Pillow 12, built-in image generation, macOS Georgia/SF fonts, unittest, App Store Connect, Google Play Console.

---

## File Map

- Modify: `.gitignore` — keep visual-companion session files out of git.
- Create: `store-assets/screenshots/source/en-US/*.png` — four supplied simulator captures.
- Create: `store-assets/screenshots/backgrounds/quiet-editorial.png` — abstract background only.
- Create: `tools/store_screenshots/generate.py` — deterministic renderer and verifier.
- Create: `tools/store_screenshots/test_generate.py` — output and crop-contract tests.
- Create: `store-assets/screenshots/en-US/appstore-6.5/*.png` — four App Store assets.
- Create: `store-assets/screenshots/en-US/google-play-phone/*.png` — four Google Play assets.
- Create: `store-assets/screenshots/en-US/contact-sheet.png` — review-only composite, not uploaded.

### Task 1: Prepare source assets and repository hygiene

**Files:**
- Modify: `.gitignore`
- Create: `store-assets/screenshots/source/en-US/01-gameplay-dark.png`
- Create: `store-assets/screenshots/source/en-US/02-tutorial-light.png`
- Create: `store-assets/screenshots/source/en-US/03-gameplay-light.png`
- Create: `store-assets/screenshots/source/en-US/04-home-dark.png`
- Create: `store-assets/screenshots/backgrounds/quiet-editorial.png`

- [ ] **Step 1: Ignore visual-companion state**

Append this exact entry to `.gitignore`:

```gitignore
.superpowers/
```

- [ ] **Step 2: Copy the approved source captures**

Run:

```bash
rtk mkdir -p store-assets/screenshots/source/en-US store-assets/screenshots/backgrounds
rtk cp "/Users/yet/Desktop/Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.42.45.png" store-assets/screenshots/source/en-US/01-gameplay-dark.png
rtk cp "/Users/yet/Desktop/Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.42.24.png" store-assets/screenshots/source/en-US/02-tutorial-light.png
rtk cp "/Users/yet/Desktop/Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.43.09.png" store-assets/screenshots/source/en-US/03-gameplay-light.png
rtk cp "/Users/yet/Desktop/Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.41.43.png" store-assets/screenshots/source/en-US/04-home-dark.png
```

Expected: all four source files are 1242 × 2688 RGB/RGBA PNGs.

- [ ] **Step 3: Generate the background with the built-in image tool**

Use this exact prompt:

```text
Use case: ads-marketing
Asset type: reusable portrait app-store screenshot background
Primary request: Create a restrained abstract background for a premium calm block-puzzle game.
Scene/backdrop: near-black matte field with extremely subtle soft atmospheric shapes.
Style/medium: editorial, minimal, tactile paper-like depth, no literal scene.
Composition/framing: portrait-friendly, quiet negative space, no focal object, seamless enough to crop to 1242x2688 and 1080x1920.
Lighting/mood: calm, contemplative, understated.
Color palette: #171715 near-black, restrained terracotta #D36643 glow, muted sage #7A8D69 haze, warm cream only as a very faint highlight.
Constraints: background only; no text, no letters, no logo, no blocks, no device, no UI, no watermark; low contrast so cream typography stays readable.
```

Copy the selected result to:

```text
store-assets/screenshots/backgrounds/quiet-editorial.png
```

- [ ] **Step 4: Inspect the background**

Expected:

- no text, logos, UI, or recognizable objects;
- no bright region behind the future headline;
- enough texture to avoid a flat fill at full size;
- near-black dominant tone.

- [ ] **Step 5: Commit source preparation**

```bash
rtk git add .gitignore store-assets/screenshots/source store-assets/screenshots/backgrounds
rtk git commit -m "assets: add store screenshot sources"
```

### Task 2: Define the renderer contract with tests

**Files:**
- Create: `tools/store_screenshots/test_generate.py`

- [ ] **Step 1: Write the failing test**

Create `tools/store_screenshots/test_generate.py` with:

```python
from pathlib import Path
from tempfile import TemporaryDirectory
import sys
import unittest

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))

from tools.store_screenshots.generate import SHOTS, TARGETS, render_all


class StoreScreenshotRendererTest(unittest.TestCase):
    def test_all_source_crops_end_above_ad_banner(self):
        self.assertEqual(len(SHOTS), 4)
        for shot in SHOTS:
            self.assertLessEqual(shot.crop[3], 2320)

    def test_render_all_creates_exact_opaque_outputs(self):
        source_dir = ROOT / "store-assets/screenshots/source/en-US"
        background = ROOT / "store-assets/screenshots/backgrounds/quiet-editorial.png"

        with TemporaryDirectory() as temp:
            output = Path(temp)
            render_all(source_dir, background, output)

            for target in TARGETS:
                files = sorted((output / target.directory).glob("*.png"))
                self.assertEqual([path.name for path in files], [
                    "01-quiet-depth.png",
                    "02-place-fit-clear.png",
                    "03-plan-clear-more.png",
                    "04-quiet-challenge.png",
                ])
                for path in files:
                    with Image.open(path) as image:
                        self.assertEqual(image.size, target.size)
                        self.assertEqual(image.mode, "RGB")


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
rtk python3 -m unittest tools.store_screenshots.test_generate -v
```

Expected: `ModuleNotFoundError: No module named 'tools.store_screenshots.generate'`.

- [ ] **Step 3: Commit the failing contract**

```bash
rtk git add tools/store_screenshots/test_generate.py
rtk git commit -m "test: define store screenshot contract"
```

### Task 3: Implement deterministic platform rendering

**Files:**
- Create: `tools/store_screenshots/generate.py`

- [ ] **Step 1: Implement the renderer**

Create `tools/store_screenshots/generate.py` with:

```python
from dataclasses import dataclass
from pathlib import Path
import argparse

from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageOps


ROOT = Path(__file__).resolve().parents[2]
GEORGIA = Path("/System/Library/Fonts/Supplemental/Georgia Bold.ttf")
SFNS = Path("/System/Library/Fonts/SFNS.ttf")


@dataclass(frozen=True)
class Shot:
    source: str
    output: str
    first_line: str
    second_line: str
    support: str
    crop: tuple[int, int, int, int]


@dataclass(frozen=True)
class Target:
    directory: str
    size: tuple[int, int]
    headline_size: int
    support_size: int
    header_height: int
    side_margin: int
    card_radius: int


SHOTS = (
    Shot("01-gameplay-dark.png", "01-quiet-depth.png",
         "A quiet puzzle", "with real depth",
         "CALM TO PLAY · SMART TO MASTER", (24, 80, 1218, 2320)),
    Shot("02-tutorial-light.png", "02-place-fit-clear.png",
         "Place. Fit.", "Clear.",
         "SIMPLE FROM THE FIRST MOVE", (24, 80, 1218, 2320)),
    Shot("03-gameplay-light.png", "03-plan-clear-more.png",
         "Plan ahead.", "Clear more.",
         "EVERY MOVE OPENS A POSSIBILITY", (24, 80, 1218, 2320)),
    Shot("04-home-dark.png", "04-quiet-challenge.png",
         "Your next", "quiet challenge",
         "PLAY AT YOUR OWN PACE", (24, 80, 1218, 2320)),
)

TARGETS = (
    Target("appstore-6.5", (1242, 2688), 106, 32, 640, 78, 42),
    Target("google-play-phone", (1080, 1920), 76, 25, 430, 62, 34),
)


def centered_text(draw, y, text, font, fill, canvas_width):
    box = draw.textbbox((0, 0), text, font=font)
    width = box[2] - box[0]
    draw.text(((canvas_width - width) / 2, y), text, font=font, fill=fill)


def rounded_card(image, size, radius):
    fitted = ImageOps.fit(image, size, Image.Resampling.LANCZOS)
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, size[0], size[1]), radius, fill=255)
    fitted.putalpha(mask)
    return fitted


def render_one(shot, target, source_dir, background_path):
    width, height = target.size
    background = Image.open(background_path).convert("RGB")
    canvas = ImageOps.fit(background, target.size, Image.Resampling.LANCZOS)
    shade = Image.new("RGBA", target.size, (23, 23, 21, 128))
    canvas = Image.alpha_composite(canvas.convert("RGBA"), shade)

    draw = ImageDraw.Draw(canvas)
    headline = ImageFont.truetype(str(GEORGIA), target.headline_size)
    support = ImageFont.truetype(str(SFNS), target.support_size)
    cream = (248, 245, 238, 255)
    terracotta = (211, 102, 67, 255)
    muted = (210, 207, 198, 205)

    first_y = int(target.header_height * 0.20)
    line_gap = int(target.headline_size * 1.02)
    centered_text(draw, first_y, shot.first_line, headline, cream, width)
    centered_text(draw, first_y + line_gap, shot.second_line, headline, terracotta, width)
    centered_text(draw, first_y + line_gap * 2 + 22, shot.support, support, muted, width)

    source = Image.open(source_dir / shot.source).convert("RGB").crop(shot.crop)
    card_x = target.side_margin
    card_y = target.header_height
    card_w = width - target.side_margin * 2
    card_h = height - card_y + int(target.card_radius * 0.45)

    shadow = Image.new("RGBA", target.size, (0, 0, 0, 0))
    shadow_draw = ImageDraw.Draw(shadow)
    shadow_draw.rounded_rectangle(
        (card_x, card_y + 18, card_x + card_w, card_y + card_h + 18),
        target.card_radius,
        fill=(0, 0, 0, 150),
    )
    shadow = shadow.filter(ImageFilter.GaussianBlur(target.card_radius))
    canvas = Image.alpha_composite(canvas, shadow)

    card = rounded_card(source, (card_w, card_h), target.card_radius)
    canvas.alpha_composite(card, (card_x, card_y))
    return canvas.convert("RGB")


def render_all(source_dir, background_path, output_dir):
    source_dir = Path(source_dir)
    background_path = Path(background_path)
    output_dir = Path(output_dir)

    for target in TARGETS:
        target_dir = output_dir / target.directory
        target_dir.mkdir(parents=True, exist_ok=True)
        for shot in SHOTS:
            image = render_one(shot, target, source_dir, background_path)
            image.save(target_dir / shot.output, "PNG", optimize=True)


def build_contact_sheet(output_dir):
    output_dir = Path(output_dir)
    thumbs = []
    for target in TARGETS:
        for path in sorted((output_dir / target.directory).glob("*.png")):
            image = Image.open(path).convert("RGB")
            image.thumbnail((270, 520), Image.Resampling.LANCZOS)
            thumbs.append((target.directory, path.name, image.copy()))

    sheet = Image.new("RGB", (1240, 1160), "#efede8")
    draw = ImageDraw.Draw(sheet)
    label = ImageFont.truetype(str(SFNS), 22)
    for index, (target_name, file_name, thumb) in enumerate(thumbs):
        row, column = divmod(index, 4)
        x = 30 + column * 300
        y = 35 + row * 560
        sheet.paste(thumb, (x, y))
        draw.text((x, y + 520), f"{target_name}\n{file_name}", font=label, fill="#171715")
    sheet.save(output_dir / "contact-sheet.png", "PNG", optimize=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--source-dir",
        default=ROOT / "store-assets/screenshots/source/en-US",
        type=Path,
    )
    parser.add_argument(
        "--background",
        default=ROOT / "store-assets/screenshots/backgrounds/quiet-editorial.png",
        type=Path,
    )
    parser.add_argument(
        "--output-dir",
        default=ROOT / "store-assets/screenshots/en-US",
        type=Path,
    )
    args = parser.parse_args()
    render_all(args.source_dir, args.background, args.output_dir)
    build_contact_sheet(args.output_dir)


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Run the contract tests**

Run:

```bash
rtk python3 -m unittest tools.store_screenshots.test_generate -v
```

Expected: two tests pass.

- [ ] **Step 3: Generate both store sets**

Run:

```bash
rtk python3 tools/store_screenshots/generate.py
```

Expected: eight store PNGs plus `store-assets/screenshots/en-US/contact-sheet.png`.

- [ ] **Step 4: Verify file metadata**

Run:

```bash
rtk sips -g pixelWidth -g pixelHeight -g space \
  store-assets/screenshots/en-US/appstore-6.5/*.png \
  store-assets/screenshots/en-US/google-play-phone/*.png
```

Expected:

- App Store files: 1242 × 2688
- Google Play files: 1080 × 1920
- all files: RGB

- [ ] **Step 5: Commit the renderer**

```bash
rtk git add tools/store_screenshots
rtk git commit -m "feat: add store screenshot renderer"
```

### Task 4: Visual quality pass and final assets

**Files:**
- Modify: `tools/store_screenshots/generate.py` only if one focused layout correction is required.
- Create: `store-assets/screenshots/en-US/appstore-6.5/*.png`
- Create: `store-assets/screenshots/en-US/google-play-phone/*.png`
- Create: `store-assets/screenshots/en-US/contact-sheet.png`

- [ ] **Step 1: Inspect the contact sheet at full detail**

Open:

```text
store-assets/screenshots/en-US/contact-sheet.png
```

Check:

- no ad banner or “Test mode” overlay is visible;
- each title is readable at contact-sheet size;
- no title collides with the UI card;
- board, score, tutorial hand, and Logica home identity remain recognizable;
- all four files feel like one series.

- [ ] **Step 2: Inspect all eight masters**

Check each PNG at original resolution for:

- clean rounded edges;
- no source distortion;
- no accidental crop of essential controls;
- no generated text or generated UI;
- no banding or visible image-generation artifact in the background.

- [ ] **Step 3: Apply at most one focused correction**

If inspection reveals a concrete defect, change only the corresponding crop, margin, font size, or background shade in `generate.py`, then rerun:

```bash
rtk python3 tools/store_screenshots/generate.py
rtk python3 -m unittest tools.store_screenshots.test_generate -v
```

Expected: the defect is removed and tests still pass.

- [ ] **Step 4: Commit the final assets**

```bash
rtk git add store-assets/screenshots/en-US
rtk git commit -m "assets: add English store screenshots"
```

### Task 5: Upload the App Store screenshot set

**Files:**
- Read: `store-assets/screenshots/en-US/appstore-6.5/*.png`

- [ ] **Step 1: Open the in-flight iOS version**

Navigate to:

```text
https://appstoreconnect.apple.com/apps/6765924581/distribution/ios/version/inflight
```

Expected: the editable version page for Logica is visible.

- [ ] **Step 2: Select the English (U.S.) localization and 6.5-inch display**

Expected: the screenshot manager shows the current English iPhone screenshot set.

- [ ] **Step 3: Replace the four screenshots in order**

Upload:

```text
01-quiet-depth.png
02-place-fit-clear.png
03-plan-clear-more.png
04-quiet-challenge.png
```

Expected: all four previews finish processing without a dimension or format error.

- [ ] **Step 4: Save metadata changes**

Expected: App Store Connect confirms the version metadata was saved.

- [ ] **Step 5: Stop before submission**

Do not click “Add for Review”, “Submit for Review”, or any release/publication control.

### Task 6: Upload the Google Play screenshot set

**Files:**
- Read: `store-assets/screenshots/en-US/google-play-phone/*.png`

- [ ] **Step 1: Open the package listing in Google Play Console**

Use package:

```text
ge.yet.blokblast
```

Navigate to the English (United States) main store listing and its phone screenshots section.

- [ ] **Step 2: Replace the phone screenshots in order**

Upload:

```text
01-quiet-depth.png
02-place-fit-clear.png
03-plan-clear-more.png
04-quiet-challenge.png
```

Expected: all four 1080 × 1920 previews finish processing without an aspect-ratio, dimension, or file-format error.

- [ ] **Step 3: Save the listing draft**

Expected: Google Play Console shows the listing changes as saved.

- [ ] **Step 4: Stop before publication**

Do not send changes for review, publish, create a rollout, or alter any release track.

### Task 7: Final verification and handoff

**Files:**
- Read: `store-assets/screenshots/en-US/appstore-6.5/*.png`
- Read: `store-assets/screenshots/en-US/google-play-phone/*.png`

- [ ] **Step 1: Run local verification again**

Run:

```bash
rtk python3 -m unittest tools.store_screenshots.test_generate -v
```

Expected: two tests pass.

- [ ] **Step 2: Verify remote order**

Confirm both stores show the same story order:

1. quiet depth
2. place, fit, clear
3. plan and clear more
4. quiet challenge

- [ ] **Step 3: Report the outcome**

Report:

- exact local asset directories;
- test result;
- App Store upload/save status;
- Google Play upload/save status;
- any authentication or store-processing blocker;
- confirmation that no release was submitted or published.
