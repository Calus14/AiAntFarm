import os, re, json, requests, textwrap

GITHUB_TOKEN = os.environ["GITHUB_TOKEN"]
OPENAI_API_KEY = os.environ["OPENAI_API_KEY"]
REPO = os.environ["REPO"]          # e.g., Calus14/AiAntFarm
PR_NUMBER = os.environ["PR_NUMBER"]

GH = "https://api.github.com"
gh_headers = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json"
}

def gh_get(url):
    r = requests.get(url, headers=gh_headers)
    r.raise_for_status()
    return r.json()

def gh_post(url, payload):
    r = requests.post(url, headers=gh_headers, json=payload)
    r.raise_for_status()
    return r.json()

def gh_patch(url, payload):
    r = requests.patch(url, headers=gh_headers, json=payload)
    r.raise_for_status()
    return r.json()

# --- 1) Get PR body + files (diffs) ---
pr = gh_get(f"{GH}/repos/{REPO}/pulls/{PR_NUMBER}")
pr_title = pr.get("title") or ""
pr_body  = pr.get("body") or ""

files = gh_get(f"{GH}/repos/{REPO}/pulls/{PR_NUMBER}/files")

changed = []
total_patch_chars = 0
MAX_PATCH = 120000  # guardrail: keep the context bounded

for f in files:
    patch = f.get("patch") or ""
    if total_patch_chars + len(patch) > MAX_PATCH:
        patch = patch[: max(0, MAX_PATCH - total_patch_chars)]
    total_patch_chars += len(patch)
    changed.append({
        "filename": f["filename"],
        "status": f["status"],
        "patch": patch
    })

# --- 2) Extract linked issues from PR body (e.g., "Fixes #9", "Implements #48") ---
issue_ids = list({int(m) for m in re.findall(r"#(\d+)", pr_body)})  # unique ints
issue_context_chunks = []
for iid in issue_ids[:8]:  # reasonable cap
    try:
        issue = gh_get(f"{GH}/repos/{REPO}/issues/{iid}")
        title = issue.get("title") or ""
        body  = issue.get("body")  or ""
        issue_context_chunks.append(f"- #{iid} {title}\n{body}\n")
    except Exception:
        pass

issue_context = "\n".join(issue_context_chunks) if issue_context_chunks else "None referenced."

# --- 3) Build prompt for the model ---
if not changed:
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments",
            {"body": "<!-- ai-review-sticky -->\nAI Review: No code changes detected."})
    raise SystemExit(0)

prompt = textwrap.dedent(f"""
You are a senior backend reviewer for a Spring Boot + AWS/DynamoDB + SSE project.
Focus on: correctness, security, concurrency, logging/MDC, HTTP contracts, perf, and tests.
Return:
1) A short **Summary** with risk level.
2) **Findings** as a concise list.
3) **Concrete code suggestions** using GitHub suggestion blocks.
4) A quick **Checklist** for the author.

Repository: {REPO}
PR #{PR_NUMBER}: {pr_title}

Linked issues provided by the author (goal/acceptance criteria):
{issue_context}

Changed files (unified diffs):
""")

for c in changed:
    prompt += f"\n### {c['filename']} ({c['status']})\n```diff\n{c['patch']}\n```\n"

# --- 4) Call OpenAI ---
oai_headers = {"Authorization": f"Bearer {OPENAI_API_KEY}", "Content-Type": "application/json"}
data = {
    "model": "gpt-4.1-mini",
    "input": [
        {"role": "system", "content": "Be direct, specific, and actionable. Show minimal diffs with ```suggestion blocks```."},
        {"role": "user",   "content": prompt}
    ]
}

resp = requests.post("https://api.openai.com/v1/responses", headers=oai_headers, data=json.dumps(data))
resp.raise_for_status()
review_text = resp.json()["output"][0]["content"][0]["text"]

# --- 5) Post or update a single sticky comment on the PR thread ---
marker = "<!-- ai-review-sticky -->"
body = f"{marker}\n### 🤖 AI Review\n\n{review_text}"

comments = gh_get(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments")
sticky = next((c for c in comments if c.get("body","").startswith(marker)), None)

if sticky:
    gh_patch(sticky["url"], {"body": body})
else:
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments", {"body": body})
