# Instruments and shared presets

Use presets before writing a new synthesizer voice. They are original, reviewed declarations from `:miniapp:audio-presets` and can be renamed so several instances coexist.

| Preset | Character | Useful controls |
|---|---|---|
| `SoftPad` | slow, warm harmonic bed | `name`, `gain` |
| `ChipLead` | bright pulse/square lead | `name`, `gain` |
| `AnalogBass` | filtered saw/sine bass | `name`, `gain` |
| `GlassBell` | additive glass-like bell | `name`, `gain` |
| `OceanBreeze` | wind, water, waves and sparse chimes | `seed`, `gain`, `density`, `stereo`, four `AudioParameter` layers |
| `SoftRain` | rain bed and droplets | `seed`, `gain`, `density`, `stereo` |
| `ForestNight` | air and insect gestures | `seed`, `gain`, `density`, `stereo` |
| `DeepSpace` | pad, dust and sparse signals | `seed`, `gain`, `density`, `stereo` |

Compose several renamed fragments instead of copying their implementation. Names generated inside a soundscape are derived from its `name`, so two instances must not share the same name.

When no preset fits, create a game-owned `AudioProgramFragment` or declarations inside the game's program. Keep the result original and focused on a reusable sonic role, not a transcription of a commercial track or another library's demo.
