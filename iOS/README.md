# MeloRemote

Unofficial native SwiftUI remote for self-hosted Music Assistant on iPhone, iPad, and Mac.

The app supports:

- Music Assistant long-lived token sign-in with Keychain storage
- Music Assistant username/password login fallback
- Live player and queue updates over the Music Assistant websocket API
- Play, pause, previous, next, seek, volume, mute, shuffle, and repeat
- Player selection for Snapcast, Home Assistant, AirPlay, and other providers
- Library search, playlists, playlist tracks, queue playback, and queue editing
- Album and playlist artwork through the Music Assistant image proxy

## Now on Testflight!
Use this invite link, https://testflight.apple.com/join/C8WZ9Dnp


## Run

1. Open `MeloRemote.xcodeproj` in Xcode.
2. Choose an iPhone, iPad, or `My Mac (Mac Catalyst)` destination.
3. Press Run.
4. Enter your Music Assistant server URL and a long-lived token.

For a physical device or Mac Catalyst build, select your Apple development team under the app target's Signing & Capabilities settings.

MeloRemote connects directly to your Music Assistant server, so the device must be able to reach that server through the local network, VPN, or another configured network route.

## Authentication

Using a Music Assistant long-lived token is recommended for remote apps. In
the Music Assistant web interface, open your profile settings, create a long
lived token, copy it once, and paste it into MeloRemote.

## Security

MeloRemote does not include personal server addresses, usernames, passwords,
or tokens. User-entered long-lived tokens are stored in the Apple Keychain. Do not
commit generated build products, screenshots with private server details, or
local `.xcuserdata` files.

MeloRemote is an unofficial client and is not affiliated with, endorsed by, or
sponsored by Music Assistant or the Open Home Foundation.

## Demo UI

The Debug build supports the launch argument `-demo` to load representative
players, queue items, playlists, and search results without signing in.
Optional tab arguments are `-demo-library`, `-demo-queue`, `-demo-speakers`,
and `-demo-settings`.

The app stores the long-lived Music Assistant token in the Apple Keychain. It
does not save your password.

## Verify

```sh
xcodebuild \
  -project MeloRemote.xcodeproj \
  -scheme MeloRemote \
  -configuration Debug \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=26.2' \
  CODE_SIGNING_ALLOWED=NO \
  test
```

