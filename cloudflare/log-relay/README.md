# Zapret Mobile log relay

A Cloudflare Worker that accepts a diagnostics log from the app and files it as
a GitHub issue.

## Why a relay instead of posting to GitHub directly

The app is distributed as an APK. Anything inside it — including a GitHub token —
can be extracted by anyone who downloads a release. The Worker holds the token as
a Cloudflare secret instead, so the app only carries the Worker URL and a shared
secret whose sole power is "file a log".

## Deploy

Requires a Cloudflare account and `wrangler` (`npm install -g wrangler`).

```bash
cd cloudflare/log-relay
wrangler login
```

Create a **fine-grained** GitHub personal access token scoped to this repository
only, with `Issues: read and write` and nothing else
(<https://github.com/settings/personal-access-tokens>). Then:

```bash
wrangler secret put GITHUB_TOKEN
```

Pick a shared secret for the app — any long random string. Generate one with:

```bash
openssl rand -hex 32
```

Give it to the Worker:

```bash
wrangler secret put UPLOAD_TOKEN
```

Deploy:

```bash
wrangler deploy
```

`wrangler deploy` prints the Worker URL, e.g.
`https://zapret-log-relay.<your-subdomain>.workers.dev`.

## Configure the app

Settings → **Send logs automatically**:

- endpoint: the Worker URL from `wrangler deploy` (must be `https://`)
- shared secret: the same value you gave `UPLOAD_TOKEN`
- turn the switch on, then **Save upload settings**

From then on, each time the strategy auto-test finishes, the day's log is posted
and appears as an issue labelled `autotest-log`. Whether it was sent, and any
failure, is recorded in the log itself.

## Check it works

```bash
curl -X POST https://zapret-log-relay.<your-subdomain>.workers.dev \
  -H "Content-Type: application/json" \
  -H "X-Zapret-Token: <your UPLOAD_TOKEN>" \
  -d '{"version":"test","reason":"manual curl","log":"hello"}'
```

Expected: `{"ok":true,"issue":"https://github.com/.../issues/N"}`.
A wrong or missing token returns `401`.

## What ends up on GitHub

The app's own diagnostics log: version, strategy profile, auto-test results per
domain, TLS/certificate errors for the two hard-coded test domains, and socket
lifecycle events. It does not contain browsing history — the app never logs which
sites the user visits.

**The issues are as private as the repository.** If `radik097/zapret-mobile` is
ever made public, every previously filed log becomes public with it. If you
distribute builds to other people and turn this on for them, that is their data
going to your repo — which is why the switch is off by default and the settings
screen says plainly what gets sent.

## Cost

Cloudflare's free tier covers 100,000 Worker requests per day. One auto-test
produces one request.
