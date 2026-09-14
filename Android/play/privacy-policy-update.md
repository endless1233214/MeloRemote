# MeloRemote Privacy Policy

Effective date: September 13, 2026

MeloRemote is an unofficial native remote for self-hosted Music Assistant servers. It is distributed for Android through Google Play and for Apple platforms through the App Store. This policy explains what the app handles when you connect it to your own server.

## Summary

MeloRemote connects directly from your device to the Music Assistant server address you enter. VersaRepair LLC does not operate your Music Assistant server and does not receive your server address, music library, queue, playback activity, password, or token during normal app use.

## Store Distribution

Google and Apple distribute MeloRemote through their respective app stores. They may process download, installation, purchase, account, device, usage, diagnostic, and crash information under their own privacy policies and device settings. VersaRepair may receive aggregated store metrics or diagnostic reports made available by those platforms.

## Information Stored on Your Device

- **Server address:** The Music Assistant server URL you enter may be saved locally so the app can reconnect.
- **Username:** If you use account login, the username may be saved locally for convenience.
- **Authentication token:** Long-lived or generated Music Assistant tokens are stored using platform-provided secure storage. On Android, the token is encrypted with a key held by Android Keystore. On Apple platforms, it is stored in Keychain.
- **Password:** Your password is used to request a Music Assistant token when you choose account login. MeloRemote does not save your password.
- **App preferences:** Basic settings such as the selected authentication mode and selected output or player may be stored locally.

MeloRemote disables Android cloud backup and device-transfer backup for its app data.

## Information Processed From Your Server

After you connect, MeloRemote may display data provided by your Music Assistant server, such as player names, selected output, now-playing state, queue items, playlist names, search results, tracks, albums, artists, radio items, podcasts, audiobooks, artwork URLs or proxy identifiers, volume state, shuffle or repeat state, favorite or library status, user details, and server compatibility details. This information is used only to show and control your Music Assistant setup.

## Remote Commands Sent to Your Server

MeloRemote may send commands to your Music Assistant server when you use app controls. These commands can play or pause media, move between tracks, seek, change volume or mute state, select speakers, change shuffle or repeat state, edit or clear the queue, start supported media, add items to playlists, favorite or unfavorite items, and add or remove supported items from your Music Assistant library.

These actions affect your own Music Assistant server and connected providers according to your server's configuration.

## Network Access

MeloRemote uses network access to reach Music Assistant servers and players on your home network. The app communicates with your server using HTTP or HTTPS requests and WebSocket messages. Your device must be able to reach the server through a local network, VPN, or another route you configure. Artwork may load through your server's Music Assistant image proxy.

If you use an unencrypted HTTP connection, data sent between your device and server—including authentication information—may be visible to others with access to that network. Use HTTPS or a trusted private network whenever possible.

## Audio, Media, Advertising, and Tracking

MeloRemote is a remote-control app. It does not provide music, operate a streaming service, download songs, intentionally cache audio files, include third-party advertising or analytics SDKs, use advertising tracking, or sell personal information. The app does not request location, contacts, microphone, camera, or photo-library access for its normal remote-control features. Operating systems may temporarily cache ordinary network responses or artwork as part of normal system behavior.

## VersaRepair Support

If you contact VersaRepair LLC for support, the information you choose to send through the support form or email may be used to answer your request. Avoid sending passwords, long-lived tokens, or private server URLs unless specifically requested for troubleshooting and you understand the risk.

## Your Controls

- Delete MeloRemote or clear its app storage to remove locally stored app data.
- Revoke or delete Music Assistant tokens from your Music Assistant server.
- Control the Music Assistant server, music providers, playlists, users, connected players, and retention of server-side logs or data.
- Manage Google or Apple account, diagnostic, and privacy settings through the relevant platform.

## Children

MeloRemote is a general music-control utility and is not specifically directed to children under 13. VersaRepair does not knowingly collect personal information from children through the app.

## Changes to This Policy

This policy may be updated if MeloRemote's features, supported platforms, or data practices change. The effective date at the top will identify the current version.

## Unofficial Client Notice

MeloRemote is not affiliated with, endorsed by, or sponsored by Music Assistant, the Open Home Foundation, or any related project.

## Contact

For privacy or support questions, use the VersaRepair app support page at https://versarepair.com/app-development/support/.
