# MiniApp Audio: Physical iPhone Verification

This checklist records the device-only evidence required for the iOS
`AVAudioEngine`/`AVAudioSourceNode` sink. Native tests and simulator framework
linking verify API compatibility, Metro aggregation and state transitions; they
do not prove audible output, realtime latency or click-free teardown.

Automated coverage currently verifies lazy engine activation, injected PCM
rendering, background/foreground transitions, interruption pause/resume, route
reset, media-services rebuild, callback-failure containment and idempotent
engine/observer teardown.

## Physical iPhone checklist

- [ ] Music is audible and remains stable for at least five minutes.
- [ ] Several distinct SFX are audible while Music continues.
- [ ] Disabling Music silences only Music; SFX remain audible.
- [ ] Disabling SFX rejects new SFX; Music remains audible.
- [ ] Opening a host sheet ducks Music and suppresses new SFX.
- [ ] Backgrounding pauses playback; foregrounding resumes the same program.
- [ ] An incoming phone or FaceTime call pauses playback and a resumable interruption end restores it safely.
- [ ] An interruption that does not grant `shouldResume` remains silent until a new explicit play request.
- [ ] Connecting and disconnecting wired headphones resets the route without a crash, duplicate playback or stale audio.
- [ ] Connecting, disconnecting and changing a Bluetooth route recovers without restarting the MiniApp program.
- [ ] Playback remains correct after an iOS media-services reset or equivalent development fault injection.
- [ ] The app mixes with other audio according to the ambient game-audio policy and respects the silent switch.
- [ ] Closing a MiniApp releases playback without an audible click.
- [ ] Repeated play/background/interruption/route cycles do not retain duplicate engines or notification observers.
- [ ] Diagnostic output shows no repeated callback failures or underruns during normal play.

iPhone model, iOS version, output route, build SHA and observations must be
added when the checklist is executed. No physical-device item is verified by
the current automated test run.
