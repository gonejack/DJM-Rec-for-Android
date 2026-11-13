# Privacy Policy — DJM Rec for Android

_Last updated: 13 November 2026_

DJM Rec for Android ("DJM Rec", "the app") records audio from a USB DJ mixer to your
Android phone. This policy explains what data the app handles and why. The app is
open source: you can check everything described here in the source code at
<https://github.com/P2GR/DJM-Rec-for-Android>.

## Summary

- Your recordings, videos and file names stay on your phone. They are never uploaded
  to us.
- The app has no user accounts of its own and shows no ads.
- Diagnostic reports stay on your phone unless you explicitly export and share them.
  The app sends no automatic crash reports, analytics or mixer diagnostics.
- Livestreaming sends your audio (and camera video, if you choose) only to the
  streaming service you select, using your own account or stream key.

## Recordings and files

Audio recordings (WAV, FLAC, MP3) are saved in `Music/DJMRec` and videos in
`Movies/DJMRec` on your phone. They are only shared when you choose to share, export
or stream them yourself.

## Permissions

| Permission | Why the app needs it |
| --- | --- |
| USB host access | To receive audio from your mixer over USB. |
| Microphone (`RECORD_AUDIO`) | Android requires this permission to capture any audio input, including USB audio from your mixer. |
| Camera (optional) | Only for video recording and video livestreams, when you turn them on. |
| Notifications | To show the recording status while recording in the background. |
| Do Not Disturb access (optional) | To silence notifications during a set, if you enable it. |
| Internet | For livestreaming to the streaming service you select. |

## Local diagnostics

**Support & diagnostics** lets you export a report containing the description you
optionally enter, your phone and Android version, the connected mixer and its USB
descriptors, audio devices, app settings and recent app logs. Recorded audio is not
included. URLs, file paths, stream keys, tokens and passwords are redacted from
technical log text and your description.

Reports are saved locally and shared only through the Android share sheet when you
choose a destination. There is no automatic diagnostic upload or remote crash reporting.

## Livestreaming

- **YouTube**: create and manage the broadcast in YouTube Studio, then enter the server
  address and stream key in the app. The app sends the audio and video feed to that
  server; it does not sign in to your Google account.
- **Mixcloud and custom RTMP servers**: enter the server address and stream key from
  your streaming service.

Stream keys stay in memory for the current session and are sent only to the selected
streaming server. Changing the provider or ending the app process clears them.

## Sharing

The app shares recordings and diagnostic reports only when you request it, and sends
livestream audio and video only to the streaming server you select.

## Children

DJM Rec is a tool for DJs and is not directed at children under 13.

## Your choices

- Export and share a diagnostic report only if you choose to.
- Manage YouTube broadcasts in YouTube Studio.
- Uninstalling the app removes its settings and local diagnostic reports from your phone.
  Your recordings in `Music/DJMRec` and `Movies/DJMRec` stay until you delete them.
- To ask a question, open an issue at
  <https://github.com/P2GR/DJM-Rec-for-Android/issues>.

## Changes

If this policy changes, the updated version will be published at this address with a
new "Last updated" date.
