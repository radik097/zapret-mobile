/**
 * Behaviour checks for the log relay, run against a stubbed `fetch` so no
 * GitHub call is ever made. Run with: node test/worker.test.mjs
 */
import worker from "../src/index.js";

const env = {
  GITHUB_TOKEN: "gh-secret",
  UPLOAD_TOKEN: "shared-secret",
  GITHUB_REPO: "radik097/zapret-mobile",
};

let captured = null;
globalThis.fetch = async (url, init) => {
  captured = { url, init };
  return new Response(JSON.stringify({ html_url: "https://github.com/x/y/issues/1" }), {
    status: 201,
    headers: { "Content-Type": "application/json" },
  });
};

function post(body, token) {
  const headers = { "Content-Type": "application/json" };
  if (token !== undefined) {
    headers["X-Zapret-Token"] = token;
  }
  return new Request("https://relay.test/", {
    method: "POST",
    headers,
    body: JSON.stringify(body),
  });
}

const checks = [];
function check(name, condition) {
  checks.push({ name, ok: !!condition });
}

let res = await worker.fetch(
  post({ version: "0.1.7 (build 8)", reason: "auto-test finished", log: "line one\nline two" }, "shared-secret"),
  env,
);
let json = await res.json();
check("201 on valid upload", res.status === 201);
check("returns the issue url", json.issue === "https://github.com/x/y/issues/1");
check("posts to the configured repo", captured.url === "https://api.github.com/repos/radik097/zapret-mobile/issues");

const issue = JSON.parse(captured.init.body);
check("labels the issue", issue.labels.includes("autotest-log"));
check("body contains the log", issue.body.includes("line two"));
check("title carries the version", issue.title.includes("0.1.7 (build 8)"));

check("401 with a wrong token", (await worker.fetch(post({ log: "x" }, "nope"), env)).status === 401);
check("401 with no token", (await worker.fetch(post({ log: "x" }), env)).status === 401);
check("401 on a prefix of the token", (await worker.fetch(post({ log: "x" }, "shared"), env)).status === 401);

check("405 on GET", (await worker.fetch(new Request("https://relay.test/"), env)).status === 405);
check("400 on a whitespace-only log", (await worker.fetch(post({ log: "   " }, "shared-secret"), env)).status === 400);
check("400 on a missing log", (await worker.fetch(post({ version: "x" }, "shared-secret"), env)).status === 400);
check(
  "500 when a secret is unset",
  (await worker.fetch(post({ log: "x" }, "shared-secret"), { UPLOAD_TOKEN: "shared-secret" })).status === 500,
);

// A log line must not be able to close the markdown fence early, and a
// caller-supplied version must not be able to break out of the issue title.
await worker.fetch(
  post({ version: "1.0\n# injected", reason: "r", log: "before\n``````\nafter" }, "shared-secret"),
  env,
);
const tricky = JSON.parse(captured.init.body);
const fencedBody = tricky.body.split("\n").slice(1, -1).join("\n");
check("collapses long backtick runs inside the log", !/(^|\n)`{5,}\s*$/m.test(fencedBody));
check("strips newlines from the title", !tricky.title.includes("\n"));

globalThis.fetch = async () => new Response("boom", { status: 403 });
check("502 when GitHub rejects", (await worker.fetch(post({ log: "x" }, "shared-secret"), env)).status === 502);

let failed = 0;
for (const { name, ok } of checks) {
  if (!ok) {
    failed += 1;
  }
  console.log(`${ok ? "ok  " : "FAIL"} ${name}`);
}
console.log(`\n${checks.length - failed}/${checks.length} passed`);
process.exit(failed === 0 ? 0 : 1);
