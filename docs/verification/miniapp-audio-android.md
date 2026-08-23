# MiniApp Audio: Physical Android Verification

This checklist records the device-only evidence required for the Android
`AudioTrack` sink. Robolectric and compilation verify configuration and state
transitions; they do not prove audible output, latency or click-free teardown.

Automated coverage currently verifies native-rate/minimum-buffer selection,
float-to-PCM16 fallback, PCM16 conversion, pause/flush/resume, focus loss,
duck/gain recovery, lazy focus acquisition, callback-failure diagnostics and
synchronous idempotent writer teardown.

## Physical device checklist

- [ ] Music is audible and remains stable for at least five minutes.
- [ ] Several distinct SFX are audible while Music continues.
- [ ] Disabling Music silences only Music; SFX remain audible.
- [ ] Disabling SFX rejects new SFX; Music remains audible.
- [ ] Opening a host sheet ducks Music and suppresses new SFX.
- [ ] Backgrounding pauses playback; foregrounding resumes the same program.
- [ ] Audio-focus duck lowers output and gain restores it without restarting the program.
- [ ] Audio-focus loss pauses/flushes and later gain resumes safely.
- [ ] Closing a MiniApp releases playback without an audible click.
- [ ] Repeated play/background/focus cycles do not leak a `miniapp-audio-writer` thread.
- [ ] Diagnostic output shows no repeated underruns during normal play.
- [ ] A PCM16-fallback device remains audible and free of obvious distortion.

Device model, Android version, build SHA and observations must be added when
the checklist is executed. No physical-device item is verified by the current
automated test run.
