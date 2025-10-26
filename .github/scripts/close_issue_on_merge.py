#!/usr/bin/env python3
"""
Close the GitHub issue referenced by the PR title prefix "Issue#<number>".

Usage: invoked from a GitHub Action on pull_request closed (merged==true).
Requires env:
  - GITHUB_TOKEN
  - REPO                  (e.g., "owner/repo")
  - PR_NUMBER             (pull request number)
"""

import os
import re
import sys
from github import Github, Auth
from datetime import datetime, timezone

def fail(msg: str, code: int = 1):
    print(f"::error::{msg}")
    sys.exit(code)

def main():
    token = os.getenv("GITHUB_TOKEN")
    repo_full = os.getenv("REPO")
    pr_number = os.getenv("PR_NUMBER")

    if not token:
        fail("GITHUB_TOKEN is not set.")
    if not repo_full:
        fail("REPO (owner/repo) is not set.")
    if not pr_number:
        fail("PR_NUMBER is not set.")

    gh = Github(auth=Auth.Token(token))
    repo = gh.get_repo(repo_full)
    pr = repo.get_pull(int(pr_number))

    # Ensure we're only acting on merged PRs (defense-in-depth; workflow also checks)
    if not pr.merged:
        print("PR is not merged. Exiting gracefully.")
        return

    title = pr.title or ""
    # Match exactly at the start of title: Issue#<digits>
    # Allow optional leading/trailing spaces before actual text
    m = re.match(r"^\s*Issue#(\d+)\b", title)
    if not m:
        print(f"No issue pattern found in PR title: '{title}'. Expected prefix 'Issue#<number>'. Exiting gracefully.")
        return

    issue_number = int(m.group(1))
    try:
        issue = repo.get_issue(number=issue_number)
    except Exception as e:
        fail(f"Unable to fetch issue #{issue_number}: {e}")

    # Compose a concise comment summary
    merged_sha = pr.merge_commit_sha or "n/a"
    actor = pr.user.login if pr.user else "unknown"
    ts = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%SZ")
    comment = (
        f"✅ Closed via merged PR #{pr.number}: {pr.title}\n\n"
        f"- Merged by **{actor}** at {ts}\n"
        f"- Merge SHA: `{merged_sha}`\n"
        f"- Workflow: Close linked issue from PR title prefix `Issue#{issue_number}`"
    )

    # Add comment (safe even if already closed), then close if open
    issue.create_comment(comment)

    if issue.state != "closed":
        issue.edit(state="closed")
        print(f"Issue #{issue_number} closed.")
    else:
        print(f"Issue #{issue_number} already closed; added comment.")

    print("Done.")

if __name__ == "__main__":
    main()
