# Iekcorbar mobile app

Companion Expo (React Native) app for the [iekcorbar website](../) — same steampunk/sakura
themed Minecraft server, now with push notifications. Talks to the existing Next.js API
routes (`../src/app/api/**`) directly; no separate backend.

## Get started

```bash
npm install
npx expo start
```

By default the app calls `https://iekcorbar.com/api`. While developing against a local
`npm run dev` on the website, point it at your machine's LAN IP instead (not `localhost` —
from a phone/emulator that resolves to the device itself):

```bash
EXPO_PUBLIC_API_BASE_URL=http://192.168.1.x:3000/api npx expo start
```

## Push notifications — before they'll actually work

The app calls `Notifications.getExpoPushTokenAsync({ projectId })`, which needs a real EAS
project id. Until you run this once (needs a free Expo account), push registration silently
no-ops (logs a warning, doesn't crash):

```bash
npx eas init
```

That writes `extra.eas.projectId` into `app.json`. After that, logging in on a physical
device (push tokens don't reliably work on simulators/emulators) registers the device via
`POST /api/push/register`, and the website backend can send notifications with
`sendPushToUser(email, { title, body })` from `../src/lib/push.js`.

## What's wired up vs. stubbed

- **Login** (`src/app/login.tsx`) — calls the real `/api/auth/login`, persists the user in
  `AsyncStorage` (same trust model as the website's `localStorage` — see the root `CLAUDE.md`
  note on client-asserted roles; this app inherits that, doesn't fix it).
- **Server tab** — live `/api/status` polling (players, Discord, online/offline).
- **Wiki tab** — real wiki list + detail via `/api/wiki`, with a small Markdown-subset
  renderer (`src/app/wiki/[id].tsx`) ported from `../src/lib/markdown.js` for RN — headers,
  lists, blockquotes. No images/tables yet.
- **Events tab** — real list + register/unregister via `/api/events` + `/api/events/register`.
- **Chat tab** — real recent-chats list + threaded messaging via `/api/chat` (text only, no
  image attachments yet). Polls every 5s; the chat backend has no websocket/push-driven
  live updates on the website either.
- **Profile tab** — shows the logged-in user, logout (also unregisters the push token).

Not built yet (see the "Fase 2" feature list from planning): community projects, polls,
voting sites, member list/leaderboards, push-on-moderation-action / push-on-new-chat-message
triggers wired into the relevant website routes (the `sendPushToUser` helper exists, it's
just not called from anywhere yet).

## Project structure

File-based routing via `expo-router`, root at `src/app`:

```
src/app/
  _layout.tsx          root stack (auth-gated: (tabs) vs login)
  login.tsx
  (tabs)/
    _layout.tsx         tab bar (redirects to /login if logged out)
    index.tsx            Server status
    wiki.tsx
    events.tsx
    chat.tsx
    profile.tsx
  wiki/[id].tsx
  chat/[contact].tsx
src/context/auth-context.tsx   useAuth() — mirrors the website's useUser()
src/lib/api.ts                  fetch wrapper + API_BASE_URL
src/lib/push-notifications.ts   Expo push permission/token/registration flow
src/constants/brand.ts          sp-*/sakura-* color tokens ported from globals.css
```
