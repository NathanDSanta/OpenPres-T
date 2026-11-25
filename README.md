# OpenPres Translation (Android)

Live translation of the service on phones: the app finds the OpenPres server on the Wi-Fi and
plays the language track chosen (Spanish for now), also with the screen off.

One of the OpenPres repositories: the server is [OpenPres](https://github.com/NathanDSanta/OpenPres)
(docs/TRANSLATION.md there explains how translation is set up).

## Using it

1. Get `OpenPres-Translation-X.Y.Z.apk` from the
   [latest release](https://github.com/NathanDSanta/OpenPres-T/releases/latest) and open it on
   the phone (Android 7 or newer; allow installing apps when Android asks).
2. Connect the phone to the same Wi-Fi as the OpenPres server and open the app. It lists the
   servers it finds (or type the server's address); the server used last opens by itself.
3. Tap **Escuchar: Español**. It keeps playing with the screen off; the notification has a
   **Stop** button, and the phone's volume buttons set the volume.

While the translation is off, the app says so and starts playing again by itself when it's
back. The app is in Spanish on Spanish phones and in English otherwise.

On the server: remote access on (Settings → Server & security), and the Spanish track on in
the Translation page. No password is needed to listen.

The app looks for a newer version when it opens: stable releases, and betas when the server
follows the beta program.

## Development

Kotlin, no libraries beyond Android's own, for Android 7 or newer. Work on a branch named
`feature/…`, `fix/…`, `docs/…`, `chore/…` or `ci/…`, and open the pull request into `develop`
with a title like `feat: …` or `fix: …`. The checks (lint and the unit tests) run on every pull
request; an APK is built only for releases. Betas and stable releases:
[docs/RELEASING.md](docs/RELEASING.md).

A release build is signed with the key in `ANDROID_KEYSTORE_FILE` (with
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD`) when that is set:
the release workflow takes them from the repository's secrets (`ANDROID_KEYSTORE` holds the
keystore in base64). Without it, the APK is signed with a throwaway key, and a new version can
only be installed after removing the old one.
