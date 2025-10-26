import os
import re
from datetime import datetime, timezone
from github import Github

token = os.environ["GITHUB_TOKEN"]
repo_full = os.environ["REPO"]
pr_number = int(os.environ["PR_NUMBER"])

gh = Github(token)
repo = gh.get_repo(repo_full)
pr = repo.get_pull(pr_number)

# 1) Collect candidate issues from PR body + commit messages via closing keywords
closing_kw = r"(?i)(?:close[sd]?|fix(e[sd])?|resolve[sd]?)\s+#(\d+)"
issue_nums = set(int(n) for n in re.findall(closing_kw, pr.body or ""))

for commit in pr.get_commits():
    issue_nums.update(int(n) for n in re.findall(closing_kw, commit.commit.message or ""))

if not issue_nums:
    print("No referenced issues found via closing keywords. Exiting gracefully.")
    raise SystemExit(0)

merged_at = pr.merged_at or datetime.now(timezone.utc)
merged_date_str = merged_at.strftime("%Y-%m-%d")

summary = (
    f"Closed by PR #{pr.number} ({pr.html_url})\n\n"
    f"**Date closed:** {merged_date_str}"
)

for num in sorted(issue_nums):
    try:
        issue = repo.get_issue(number=num)
        issue.create_comment(summary)
        if issue.state != "closed":
            issue.edit(state="closed")
        print(f"Issue #{num} updated and closed.")
    except Exception as e:
        print(f"Failed to update issue #{num}: {e}")
