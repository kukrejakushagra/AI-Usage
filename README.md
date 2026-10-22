# Claude Usage Widget for Android

Home screen widgets that show your Claude subscription usage: the 5-hour session limit, the
7-day weekly limit, any per-model weekly limits, and when each one resets.

> [!IMPORTANT]
> **Unofficial.** This project is not affiliated with, endorsed by, or supported by Anthropic.
>
> - It relies on **undocumented endpoints** (`claude.ai/oauth/authorize`,
>   `console.anthropic.com/v1/oauth/token`, `api.anthropic.com/api/oauth/usage`) that can change or
>   disappear at any time, which would break the app.
> - Anthropic does not offer OAuth client registration for third-party apps, so this app **borrows
>   Claude Code's public OAuth client ID** (`9d1c250a-e61b-44d9-88ed-5944d1962f5e`). On the consent
>   screen you will therefore see Claude Code as the requesting app.
>
> Use at your own risk.

## Features

- **Two widgets** (Jetpack Glance, Material You colours):
  - *Claude usage*: every limit with a progress bar and "resets in 2h 14m". Resizable; shows as many
    rows as fit.
  - *Claude usage (compact)*: 5h and 7d percentages with countdowns.
- Refreshes about every 15 minutes through WorkManager (only when online), and also when you open
  the app or tap ↻ on the widget.
- Per-model weekly limits are discovered automatically: any `seven_day_*` entry in the response
  becomes a row, so new models show up without an app update.

## How sign-in works

OAuth 2.0 Authorization Code with PKCE (S256), scope **`user:profile` only** (read-only; no
inference, no access to chats).

1. Tap **Sign in with Claude**. The app creates a PKCE verifier, challenge and random `state`.
2. It starts a tiny HTTP server on `127.0.0.1` on a random free port and opens Anthropic's official
   authorize page in the **full Chrome app** (or your default browser if Chrome isn't installed),
   using a plain `ACTION_VIEW` intent. No WebView and no Custom Tab: the app never sees your
   password or claude.ai cookies.
3. While you're in the browser, a short-lived foreground service with a visible
   *"Waiting for sign in"* notification keeps the app from being frozen. It gives up after 5 minutes,
   and you can cancel it from the notification.
4. Anthropic redirects to `http://localhost:<port>/callback`. The server checks `state`, shows
   *"Signed in, return to the app"* with a link back, exchanges the code for tokens, and shuts down.
   Requests with the wrong path or `state` are rejected and ignored.

**Manual fallback.** If the loopback redirect doesn't work on your device, choose *Sign in with a
code instead*. The authorize page then redirects to Anthropic's own code page, which shows a code
like `abc…#xyz…`. Paste it into the app, which checks the `state` part and exchanges the code.

## Security & privacy

- The app talks **only to Anthropic hosts**. No backend, no Firebase, no analytics, no ads.
- Tokens are encrypted with AES-256-GCM using a non-exportable key in the **Android Keystore**, then
  stored in DataStore. Backup and device transfer are disabled.
- Access tokens are refreshed automatically. Signing out deletes the tokens and cached usage.
- The app makes no cleartext HTTP requests. The only plain-HTTP traffic is the browser's redirect to
  the app's own `127.0.0.1` listener.

## Building

Requirements: JDK 21 and the Android SDK (platform 37).

```sh
./gradlew testDebugUnitTest assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Tech: Kotlin, Jetpack Compose (app), Jetpack Glance (widgets), WorkManager, DataStore, OkHttp,
kotlinx.serialization. minSdk 29, targetSdk 37.

## Project layout

```
app/src/main/java/io/github/kukrejakushagra/claudeusage/
  auth/    PKCE, authorize URL, loopback server, foreground sign-in service, token client/store, Keystore cipher
  data/    usage API call and lenient parser, cached snapshot
  work/    periodic and one-shot refresh (WorkManager)
  widget/  Glance widgets and receivers
  ui/      Compose screens, view model, formatting helpers
```

## Prior art

Other people have built similar apps, for example
[timofeykhodykin/android-claude-usage](https://github.com/timofeykhodykin/android-claude-usage) and
[G-biggy/claude-pulse-android](https://github.com/G-biggy/claude-pulse-android). This project is an
independent implementation written from scratch, with no code taken from them.

## License

[MIT](LICENSE)
