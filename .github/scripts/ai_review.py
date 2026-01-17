# .github/ai_review.py
import os, re, json, requests, textwrap

# ====== Env ======
GITHUB_TOKEN   = os.environ["GITHUB_TOKEN"]
OPENAI_API_KEY = os.environ["OPENAI_API_KEY"]          # required
OPENAI_ORG     = os.getenv("OPENAI_ORG", "").strip()   # optional
OPENAI_PROJECT = os.getenv("OPENAI_PROJECT", "").strip()  # optional
REPO           = os.environ["REPO"]                    # e.g., Calus14/AiAntFarm
PR_NUMBER      = os.environ["PR_NUMBER"]               # e.g., "12"

# ====== GitHub helpers ======
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

# ====== OpenAI headers (supports org/project) ======
oai_headers = {
    "Authorization": f"Bearer {OPENAI_API_KEY}",
    "Content-Type": "application/json",
}
if OPENAI_ORG:
    oai_headers["OpenAI-Organization"] = OPENAI_ORG
if OPENAI_PROJECT:
    oai_headers["OpenAI-Project"] = OPENAI_PROJECT

def call_responses(model, prompt):
    data = {
        "model": model,
        "input": [
            {"role": "system", "content": "Be direct, specific, and actionable. Prefer minimal diffs using ```suggestion blocks```."},
            {"role": "user",   "content": prompt}
        ]
    }
    return requests.post("https://api.openai.com/v1/responses",
                         headers=oai_headers, data=json.dumps(data))

def call_chat(model, prompt):
    data = {
        "model": model,
        "messages": [
            {"role": "system", "content": "Be direct, specific, and actionable. Prefer minimal diffs using ```suggestion blocks```."},
            {"role": "user",   "content": prompt}
        ]
    }
    return requests.post("https://api.openai.com/v1/chat/completions",
                         headers=oai_headers, data=json.dumps(data))

# ====== Fetch PR + diffs ======
pr = gh_get(f"{GH}/repos/{REPO}/pulls/{PR_NUMBER}")
pr_title = pr.get("title") or ""
pr_body  = pr.get("body") or ""
files = gh_get(f"{GH}/repos/{REPO}/pulls/{PR_NUMBER}/files")

changed = []
total = 0
MAX_PATCH = 120000  # guardrail for context size

for f in files:
    patch = f.get("patch") or ""
    if total + len(patch) > MAX_PATCH:
        patch = patch[: max(0, MAX_PATCH - total)]
    total += len(patch)
    changed.append({"filename": f["filename"], "status": f["status"], "patch": patch})

if not changed:
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments",
            {"body": "<!-- ai-review-sticky -->\nAI Review: No code changes detected."})
    raise SystemExit(0)

# ====== Pull linked issues from PR body and include their bodies ======
issue_ids = list({int(m) for m in re.findall(r"#(\d+)", pr_body)})
issue_chunks = []
for iid in issue_ids[:8]:  # cap for sanity
    try:
        issue = gh_get(f"{GH}/repos/{REPO}/issues/{iid}")
        title = issue.get("title") or ""
        body  = issue.get("body")  or ""
        issue_chunks.append(f"• #{iid} {title}\n{body}\n")
    except Exception:
        pass
linked_issue_context = "\n".join(issue_chunks) if issue_chunks else "None."

# ====== Best-idea project description (concise, from context of AI Ant Farm) ======
project_description = (
    "a public “AI Ant Farm” website: topic-based text rooms (“ant farms”) where humans and user-provided AI agents "
    "(“AI Ants”) converse. Each Ant has a personality prompt and optional cadence. Stack: React frontend, Spring Boot "
    "backend, SSE for realtime, DynamoDB on AWS, JWT auth, and simple downloads endpoint for a Python desktop client."
)

# ====== Build the exact prompt you requested ======
intro = textwrap.dedent(f"""
You are asked to review code for a personal side project that is going to be a website with {project_description}
This is a pull request that is meant to solve an issue with:

{linked_issue_context}

Please leave review comments and what you think of this code for solving this issue.
Also provide concrete code suggestions using GitHub suggestion blocks.
""").strip()

diff_block = ""
for c in changed:
    diff_block += f"\n### {c['filename']} ({c['status']})\n```diff\n{c['patch']}\n```\n"

prompt = textwrap.dedent(f"""
Repository: {REPO}
PR #{PR_NUMBER}: {pr_title}

{intro}

Changed files (unified diff context):
{diff_block}
""").strip()

# ====== Call OpenAI with fallback ======
resp = call_responses("gpt-4.1-mini", prompt)
if resp.status_code == 403:
    # fall back to broadly available chat endpoint/model
    resp = call_chat("gpt-4o-mini", prompt)

if resp.status_code >= 300:
    raise SystemExit(f"OpenAI error {resp.status_code}: {resp.text}")

try:
    js = resp.json()
    if "output" in js:   # Responses API
        review_text = js["output"][0]["content"][0]["text"]
    else:                # Chat Completions
        review_text = js["choices"][0]["message"]["content"]
except Exception as e:
    raise SystemExit(f"Could not parse OpenAI response: {e}\nRaw: {resp.text}")

# ====== Post/Update sticky comment on PR ======
marker = "<!-- ai-review-sticky -->"
body = f"{marker}\n### 🤖 AI Review\n\n{review_text}"

comments = gh_get(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments")
sticky = next((c for c in comments if c.get("body","").startswith(marker)), None)

if sticky:
    gh_patch(sticky["url"], {"body": body})
else:
    gh_post(f"{GH}/repos/{REPO}/issues/{PR_NUMBER}/comments", {"body": body})
