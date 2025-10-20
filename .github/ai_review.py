import os, json, requests, textwrap

GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]
OPENAI_API_KEY = os.environ["OPENAI_API_KEY"]
REPO = os.environ["REPO"]
PR_NUMBER = os.environ["PR_NUMBER"]

GH = "https://api.github.com"
headers = {"Authorization": f"token {GITHUB_TOKEN}",
           "Accept": "application/vnd.github+json"}

def gh(url, **kw):
    r = requests.get(url, headers=headers, **kw)
    r.raise_for_status()
    return r.json()

def gh_post(url, payload):
    r = requests.post(url, headers=headers, json=payload)
    r.raise_for_status()
    return r.json()

# 1) Gather changed files + patches
files = gh(f"{GH}/repos/{REPO}/pulls/{PR_NUMBER}/files")
changed = []
total_patch_chars = 0
MAX_PATCH = 120000  # guardrails for context size

for f in files:
    patch = f.get("patch") or ""
    # trim giant patches but keep context
    if total_patch_chars + len(patch) > MAX_PATCH:
        patch = patch[: max(0, MAX_PATCH - total_patch_chars)]
    total_patch_chars += len(patch)
    changed.append({
        "filename": f["filename"],
        "status": f["status"],
        "patch": patch
    })

if not changed:
    body = "AI Review: No code changes detected."
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments", {"body": body})
    raise SystemExit(0)

# 2) Build prompt
prompt = textwrap.dedent(f"""
You are a senior backend reviewer. Provide:
- High-signal review comments (bugs, security, concurrency, perf, API contracts, logging, tests).
- Concrete code suggestions using GitHub suggestion blocks.
- A brief summary and risk level.

Repo: {REPO}
PR: #{PR_NUMBER}

Changed files (filename + unified diff hunks):

""")
for c in changed:
    prompt += f"\n### {c['filename']} ({c['status']})\n"
    # fence the patch for clarity; model sees unified diff
    prompt += f"```diff\n{c['patch']}\n```\n"

# 3) Call OpenAI (Responses API style; switch to your preferred model)
oai_headers = {
    "Authorization": f"Bearer {OPENAI_API_KEY}",
    "Content-Type": "application/json",
}
data = {
  "model": "gpt-4.1-mini",
  "input": [
    {"role":"system","content":"Be direct, specific, and actionable. Prefer minimal diffs with ```suggestion blocks```."},
    {"role":"user","content": prompt}
  ]
}
resp = requests.post("https://api.openai.com/v1/responses",
                     headers=oai_headers, data=json.dumps(data))
resp.raise_for_status()
review_text = resp.json()["output"][0]["content"][0]["text"]

# 4) Post sticky/replaceable review comment
# Use a single top-level comment that we update on each run.
marker = "<!-- ai-review-sticky -->"
body = f"{marker}\n### 🤖 AI Review\n\n{review_text}"
# try to find existing sticky comment
comments = gh(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments")
sticky = next((c for c in comments if c["body"].startswith(marker)), None)

if sticky:
    # update
    r = requests.patch(sticky["url"], headers=headers, json={"body": body})
    r.raise_for_status()
else:
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments", {"body": body})
