# Logica Store Screenshot Design

## Objective

Create a conversion-focused English screenshot set for Logica and prepare it for:

- Apple App Store, iPhone 6.5-inch display class
- Google Play, phone listing

The creative should position Logica as a calm, thoughtful alternative to louder block-puzzle games. It must use real application UI, exclude ad banners and test-mode overlays, and remain legible at store-thumbnail size.

## Audience

The primary audience is broad casual-puzzle players looking for a relaxing daily game. The creative should not frame Logica as a difficult specialist puzzle or a competitive arcade game.

## Value Proposition

The screenshot sequence communicates three approved benefits:

1. Calm play without pressure
2. Simple rules with strategic depth
3. Satisfying block placement and line clearing

Offline play, settings, audio, vibration, and theme controls are valid product features but are outside this first screenshot story.

## Visual Direction

Use the approved **Quiet Editorial** direction:

- Near-black background with restrained warm terracotta and muted sage atmosphere
- Large cream editorial serif headlines
- Terracotta emphasis on the key phrase in each headline
- Small uppercase sans-serif supporting line
- Real game UI presented as a large, clean crop with subtle depth
- Premium, calm composition rather than bright arcade styling

Generative imagery may be used only for a subtle abstract background texture. It must not generate, redraw, or alter the game interface.

## Screenshot Story

### Slot 1 — Hook

- Headline: **A quiet puzzle / with real depth**
- Supporting line: **CALM TO PLAY · SMART TO MASTER**
- Source screen: dark-theme active gameplay, score 28
- Purpose: establish the emotional promise and differentiate Logica from loud category conventions

### Slot 2 — Mechanic

- Headline: **Place. Fit. / Clear.**
- Supporting line: **SIMPLE FROM THE FIRST MOVE**
- Source screen: light-theme placement tutorial
- Purpose: make the drag-and-place mechanic understandable at a glance

### Slot 3 — Strategy

- Headline: **Plan ahead. / Clear more.**
- Supporting line: **EVERY MOVE OPENS A POSSIBILITY**
- Source screen: light-theme active gameplay, score 59
- Purpose: communicate strategic depth and satisfying board development

### Slot 4 — Brand Close

- Headline: **Your next / quiet challenge**
- Supporting line: **PLAY AT YOUR OWN PACE**
- Source screen: dark-theme Logica home screen
- Purpose: close on the brand and a calm invitation to play

## Source Mapping

Use these source captures:

- `Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.42.45.png` for Slot 1
- `Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.42.24.png` for Slot 2
- `Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.43.09.png` for Slot 3
- `Simulator Screenshot - iPhone 11 Pro Max - 2026-07-29 at 22.41.43.png` for Slot 4

The settings capture is intentionally excluded because it does not reinforce the approved value proposition.

## Platform Deliverables

### App Store

- Four PNG files
- 1242 × 2688 pixels
- RGB, opaque
- English, `en-US`

### Google Play

- Four PNG files
- 1080 × 1920 pixels
- 9:16 portrait
- RGB, opaque
- English, `en-US`

Use a shared visual system but recompose each platform separately. Do not stretch or blindly crop one platform's final master into the other.

## Cropping and Content Rules

- Exclude the bottom ad banner from every source capture.
- Exclude the “Test mode” overlay from the visible crop.
- Preserve the application UI without generative reconstruction.
- Keep important UI, headline text, and brand elements inside comfortable safe margins.
- Do not use ratings, awards, download counts, or comparative claims.
- Do not include the settings sheet in the final four-shot set.

## Output Structure

Save the final assets under:

```text
store-assets/screenshots/en-US/
├── appstore-6.5/
│   ├── 01-quiet-depth.png
│   ├── 02-place-fit-clear.png
│   ├── 03-plan-clear-more.png
│   └── 04-quiet-challenge.png
└── google-play-phone/
    ├── 01-quiet-depth.png
    ├── 02-place-fit-clear.png
    ├── 03-plan-clear-more.png
    └── 04-quiet-challenge.png
```

## Quality and Acceptance Criteria

- Every file has the exact required dimensions.
- Every file is an opaque RGB PNG.
- No source ad banner or test-mode overlay is visible.
- All four headlines are readable in a small thumbnail preview.
- Typography, palette, spacing, and UI treatment are consistent across the series.
- The first three screenshots communicate calm play, ease of learning, and strategic depth without relying on their supporting lines.
- The App Store and Google Play sets are individually composed and visually equivalent.
- Final files pass visual inspection before any store upload.

## Store Upload Scope

After asset approval:

1. Upload the App Store set to the in-flight iOS version for app `6765924581`.
2. Upload the Google Play set to the phone screenshots for package `ge.yet.blokblast`.
3. Stop before any release submission, rollout, or publication action unless separately authorized.
