# Releasing WaveTop

Releases are built by GitHub Actions ([`.github/workflows/android.yml`](../.github/workflows/android.yml)),
the same way NetSeer and NineLives ship: a `v*` tag builds, signs and publishes. The in-app updater
(Settings → Updates) reads those GitHub Releases, so **the repository must be public** for it to see them.

## What CI does

| Trigger | Result |
| --- | --- |
| Pull request, push to `main` | Unit tests + a debug APK (download it from the run's artifacts). |
| Tag `vX.Y.Z` | Tests, then a signed release APK, verified with `apksigner`, published as a GitHub Release with `WaveTop-X.Y.Z.apk` and `SHA256SUMS.txt`. |

## Cutting a release

1. Bump `versionName` in [`version.properties`](../version.properties) (e.g. `1.1.0`) on a branch; merge the PR.
   `versionCode` is derived from it (`major*10000 + minor*100 + patch`), so versions must only go up.
2. Tag `main` and push the tag:
   ```bash
   git tag v1.1.0
   git push origin v1.1.0
   ```
   CI refuses a tag that doesn't match `version.properties`.
3. When the run finishes, the release has `WaveTop-1.1.0.apk` and `SHA256SUMS.txt`. Installed copies
   offer the update under Settings → Updates (and light the dot on the cog within a day).

## The signing key

Android installs an update only if it's signed with **the same key** as the installed app. WaveTop's
key is a PKCS12 keystore (`wavetop-release.jks`, alias `wavetop`) kept **outside the repo** together
with its password. CI gets it from four repository secrets:

| Secret | Value |
| --- | --- |
| `WAVETOP_KEYSTORE_BASE64` | The keystore file, base64-encoded |
| `WAVETOP_KEYSTORE_PASSWORD` | Keystore password |
| `WAVETOP_KEY_ALIAS` | `wavetop` |
| `WAVETOP_KEY_PASSWORD` | Key password (same as the keystore's for PKCS12) |

The maintainer's copy has a script that sets all four with the GitHub CLI without printing them
(`add-github-secrets.ps1`, next to the keystore).

> **Back the keystore and its password up somewhere safe.** If they're lost, no future release can
> update existing installs: everyone would have to uninstall (losing their drives unless backed up) and
> install again.

For a local signed build, put a gitignored `keystore.properties` at the repo root:

```properties
storeFile=C:/path/to/wavetop-release.jks
storePassword=…
keyAlias=wavetop
keyPassword=…
```

then `./gradlew :app:assembleRelease`. Without it (and without the CI variables) release builds come
out unsigned.
