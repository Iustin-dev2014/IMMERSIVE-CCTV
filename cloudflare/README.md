# Web confirmation endpoint setup

The custom confirmation page is hosted by GitHub Pages. The button must call a server-side endpoint so no GitHub token is exposed in the browser.

## One-time setup

1. In the repository settings, open **Pages** and choose **GitHub Actions** as the build/deployment source. The deployment workflow is `.github/workflows/deploy-confirm-page.yml`.
2. Create a Cloudflare Worker and paste in `cloudflare/confirm-worker.js`.
3. Add Worker variables/secrets:
   - `GITHUB_REPOSITORY` = `Iustin-dev2014/IMMERSIVE-CCTV`
   - `GITHUB_TOKEN` = a fine-grained GitHub personal access token restricted to this repository, with **Issues: Read and write** permission. Keep it secret; never put it in the page or repository.
4. Deploy the Worker and copy its HTTPS URL, adding `/confirm` if needed.
5. In `docs/confirm-test/index.html`, replace `https://YOUR-CLOUDFLARE-WORKER.workers.dev/confirm` with that deployed endpoint and commit the change.
6. Wait for **Deploy Web Confirmation Page** to succeed. The page URL will be:
   `https://iustin-dev2014.github.io/IMMERSIVE-CCTV/confirm-test/`
7. Run **Web Confirmation Test** from the Actions tab. Open the link in its run summary/logs and press **CONFIRMĂ TESTUL** within five minutes.

The test workflow creates a temporary issue, waits up to 300 seconds for the exact session token to arrive as a comment, and closes the issue whether the test is confirmed or times out. This test workflow is separate from the mod build workflow.

## Security notes

- The page contains only a short-lived random session token; the GitHub credential stays in the Worker secret.
- The Worker accepts requests only from the GitHub Pages origin and only confirms open issues with the expected session title.
- Anyone holding the generated confirmation link can confirm that session, so do not share the link.
- Rotate/revoke the fine-grained token in GitHub if it is ever exposed.
