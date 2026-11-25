# OpenPres Translation (Android)

Live translation of the service on phones: the app finds the OpenPres server on the Wi-Fi and
plays the language track chosen (Spanish for now), also with the screen off.

One of the OpenPres repositories: the server is [OpenPres](https://github.com/NathanDSanta/OpenPres)
(docs/TRANSLATION.md there explains how translation is set up).

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
