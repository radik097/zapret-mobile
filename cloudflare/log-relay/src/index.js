/**
 * Zapret Mobile log relay.
 *
 * Takes a diagnostics log posted by the app and files it as a GitHub issue.
 * The point of the relay is that the GitHub token lives here, as a Worker
 * secret, instead of inside a distributed APK where anyone could extract it.
 *
 * Request:  POST / with JSON {version, reason, truncated, log}
 *           header X-Zapret-Token must equal the UPLOAD_TOKEN secret
 * Response: 201 {ok: true, issue: <url>}
 *
 * Secrets (wrangler secret put ...):
 *   GITHUB_TOKEN  fine-grained PAT, "Issues: read and write" on the repo only
 *   UPLOAD_TOKEN  shared secret, must match what the app is configured with
 * Vars (wrangler.jsonc):
 *   GITHUB_REPO   e.g. "radik097/zapret-mobile"
 */

const MAX_BODY_BYTES = 256 * 1024;
const MAX_LOG_CHARS = 60000;

export default {
  async fetch(request, env) {
    if (request.method !== "POST") {
      return json({ error: "Only POST is accepted" }, 405);
    }
    for (const name of ["GITHUB_TOKEN", "UPLOAD_TOKEN", "GITHUB_REPO"]) {
      if (!env[name]) {
        // Never report which one is missing to the caller.
        console.error(`Missing configuration: ${name}`);
        return json({ error: "Relay is not configured" }, 500);
      }
    }

    // Constant-time-ish compare: bail on length first, then accumulate.
    const presented = request.headers.get("X-Zapret-Token") ?? "";
    if (!secretsMatch(presented, env.UPLOAD_TOKEN)) {
      return json({ error: "Unauthorized" }, 401);
    }

    const declared = Number(request.headers.get("Content-Length") ?? "0");
    if (declared > MAX_BODY_BYTES) {
      return json({ error: "Payload too large" }, 413);
    }

    let payload;
    try {
      payload = await request.json();
    } catch {
      return json({ error: "Body must be JSON" }, 400);
    }

    const log = typeof payload.log === "string" ? payload.log : "";
    if (!log.trim()) {
      return json({ error: "Missing log" }, 400);
    }

    const version = sanitize(payload.version, 64) || "unknown";
    const reason = sanitize(payload.reason, 120) || "manual";
    const truncatedByApp = payload.truncated === true;
    const body = log.length > MAX_LOG_CHARS ? log.slice(-MAX_LOG_CHARS) : log;
    const truncated = truncatedByApp || body.length < log.length;

    const issue = {
      title: `Auto-test log ${version} — ${new Date().toISOString()}`,
      labels: ["autotest-log"],
      body: [
        `**Version:** ${version}`,
        `**Trigger:** ${reason}`,
        truncated ? "**Note:** truncated to the most recent entries." : null,
        "",
        // Five backticks, and any run of five or more inside the log is
        // collapsed to four -- so no log line can close the fence early and
        // have the rest of itself rendered as markdown.
        "`````text",
        body.replace(/`{5,}/g, "````"),
        "`````",
      ]
        .filter((line) => line !== null)
        .join("\n"),
    };

    const response = await fetch(
      `https://api.github.com/repos/${env.GITHUB_REPO}/issues`,
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${env.GITHUB_TOKEN}`,
          Accept: "application/vnd.github+json",
          "X-GitHub-Api-Version": "2022-11-28",
          "User-Agent": "zapret-mobile-log-relay",
          "Content-Type": "application/json",
        },
        body: JSON.stringify(issue),
      },
    );

    if (!response.ok) {
      // Log upstream detail server-side; don't leak it to the caller.
      console.error(`GitHub rejected the issue: ${response.status} ${await response.text()}`);
      return json({ error: "Upstream rejected the log" }, 502);
    }

    const created = await response.json();
    return json({ ok: true, issue: created.html_url }, 201);
  },
};

function secretsMatch(presented, expected) {
  if (presented.length !== expected.length) {
    return false;
  }
  let diff = 0;
  for (let i = 0; i < expected.length; i += 1) {
    diff |= presented.charCodeAt(i) ^ expected.charCodeAt(i);
  }
  return diff === 0;
}

/** Keeps caller-supplied strings out of the issue title as anything but plain text. */
function sanitize(value, maxLength) {
  if (typeof value !== "string") {
    return "";
  }
  return value.replace(/[\r\n`]/g, " ").trim().slice(0, maxLength);
}

function json(payload, status) {
  return new Response(JSON.stringify(payload), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
