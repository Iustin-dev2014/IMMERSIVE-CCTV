/**
 * Cloudflare Worker endpoint for the IMMERSIVE CCTV web-confirmation page.
 *
 * Configure these Worker environment variables/secrets:
 *   GITHUB_REPOSITORY = Iustin-dev2014/IMMERSIVE-CCTV
 *   GITHUB_TOKEN      = fine-grained GitHub PAT with Issues: read/write on this repo
 *
 * Deploy this Worker, then replace CONFIRM_ENDPOINT in docs/confirm-test/index.html
 * with the deployed Worker URL ending in /confirm.
 */
const ALLOWED_ORIGIN = "https://iustin-dev2014.github.io";
const SESSION_PREFIX = "[CCTV TEST CONFIRM] Run ";

function json(data, status, origin = ALLOWED_ORIGIN) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Access-Control-Allow-Origin": origin,
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type",
      "Cache-Control": "no-store",
      "Vary": "Origin"
    }
  });
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    if (origin !== ALLOWED_ORIGIN) return json({error:"Origin not allowed"}, 403);
    if (request.method === "OPTIONS") return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": ALLOWED_ORIGIN,
        "Access-Control-Allow-Methods": "POST, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type",
        "Access-Control-Max-Age": "600",
        "Vary": "Origin"
      }
    });
    if (request.method !== "POST") return json({error:"Method not allowed"}, 405);
    if (!env.GITHUB_TOKEN || !env.GITHUB_REPOSITORY) {
      return json({error:"Worker is not configured yet"}, 503);
    }

    let payload;
    try { payload = await request.json(); }
    catch { return json({error:"Invalid JSON"}, 400); }

    const issue = String(payload.issue || "");
    const token = String(payload.token || "");
    if (!/^\d{1,10}$/.test(issue) || !/^[a-f0-9]{64}$/.test(token)) {
      return json({error:"Invalid confirmation data"}, 400);
    }

    const apiBase = "https://api.github.com/repos/" + env.GITHUB_REPOSITORY;
    const headers = {
      "Authorization": "Bearer " + env.GITHUB_TOKEN,
      "Accept": "application/vnd.github+json",
      "X-GitHub-Api-Version": "2022-11-28",
      "User-Agent": "IMMERSIVE-CCTV-confirmation"
    };

    try {
      const issueResponse = await fetch(apiBase + "/issues/" + issue, {headers});
      if (!issueResponse.ok) return json({error:"Session not found or expired"}, 404);
      const issueData = await issueResponse.json();
      if (issueData.state !== "open" || !String(issueData.title || "").startsWith(SESSION_PREFIX)) {
        return json({error:"This confirmation session is already closed or invalid"}, 410);
      }

      const commentsResponse = await fetch(apiBase + "/issues/" + issue + "/comments?per_page=100", {headers});
      if (!commentsResponse.ok) return json({error:"Could not verify session"}, 502);
      const comments = await commentsResponse.json();
      if (comments.some(c => c.body === "CCTV_TEST_CONFIRM:" + token)) {
        return json({ok:true, message:"Already confirmed"});
      }

      const commentResponse = await fetch(apiBase + "/issues/" + issue + "/comments", {
        method:"POST",
        headers:{...headers, "Content-Type":"application/json"},
        body:JSON.stringify({body:"CCTV_TEST_CONFIRM:" + token})
      });
      if (!commentResponse.ok) return json({error:"GitHub rejected the confirmation"}, 502);
      return json({ok:true});
    } catch {
      return json({error:"Could not reach GitHub"}, 502);
    }
  }
};
