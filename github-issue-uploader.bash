#!/usr/bin/env bash
set -euo pipefail

OWNER="Calus14"
REPO="AiAntFarm"
API="https://api.github.com"
TOKEN="$(tr -d '\r' < ../.git_creds/pat_issues.txt)"
ISSUES_JSON="backend_issues.json"
JQ=${JQ:-jq}
hdr=(-H "Authorization: token $TOKEN" -H "Accept: application/vnd.github+json")

require_tools() {
  for b in curl "$JQ" awk sed tr; do
    command -v "$b" >/dev/null 2>&1 || { echo "Missing tool: $b"; exit 1; }
  done
}

ensure_labels() {
  echo "→ Ensuring labels exist…"
  while IFS=' ' read -r name color; do
    curl -s -X POST "${hdr[@]}" "$API/repos/$OWNER/$REPO/labels" \
      -d "{\"name\":\"$name\",\"color\":\"$color\"}" >/dev/null || true
  done <<'LABELS'
backend 0e8a16
Epic 5319e7
Ticket bfd4f2
security d73a4a
realtime 1d76db
observability a2eeef
aws ffea7f
ci-cd c5def5
E1 eeeeee
E2 eeeeee
E3 eeeeee
E4 eeeeee
E5 eeeeee
E6 eeeeee
E7 eeeeee
E8 eeeeee
E9 eeeeee
E10 eeeeee
LABELS
}

# Normalize helper used everywhere
norm_line() {
  # 1) drop real CR, 2) drop literal "\r", 3) drop NBSP, 4) trim
  sed -e $'s/\r//g' -e 's/\\r//g' -e $'s/\302\240/ /g' -e 's/^[[:space:]]\+//' -e 's/[[:space:]]\+$//'
}

build_issue_map() {
  echo "→ Fetching issues…"
  : > .issue_map.tsv
  for page in 1 2 3 4 5 6 7 8 9 10; do
    resp="$(curl -s "${hdr[@]}" "$API/repos/$OWNER/$REPO/issues?state=all&per_page=100&page=$page")"
    echo "$resp" | "$JQ" -e . >/dev/null 2>&1 || { echo "Non-JSON from GitHub"; echo "$resp"; exit 1; }
    count="$(printf '%s' "$resp" | "$JQ" -r 'length' | tr -d '\r')"
    [ "${count:-0}" = "0" ] && break

    printf '%s' "$resp" \
      | "$JQ" -r '.[] | [.title, .number] | @tsv' \
      | norm_line >> .issue_map.tsv
  done
}

# Exact-title lookup; non-zero exit if not found
issue_num_by_title() {
  local t_norm
  t_norm="$(printf '%s' "$1" | norm_line)"

  # Normalize the TSV before matching (safety for old files)
  # shellcheck disable=SC2002
  out="$(cat .issue_map.tsv | norm_line | awk -v FS='\t' -v want="$t_norm" '
    { if ($1 == want) { print $2; found=1; exit } }
    END { if (!found) exit 1 }
  ')" || return 1

  [ -n "$out" ] || return 1
  printf '%s' "$out"
}

apply_labels() {
  echo "→ Applying labels to existing issues…"
  "$JQ" -c '.[]' "$ISSUES_JSON" | while IFS= read -r row; do
    title=$(printf '%s' "$row" | "$JQ" -r '.title' | norm_line)
    labels_csv=$(printf '%s' "$row" | "$JQ" -r '.labels | join(",")' | norm_line)

    if ! num="$(issue_num_by_title "$title")"; then
      echo "  • SKIP (not found by title): $title"
      prefix="${title%% *}"
      echo "    └─ debug: candidates with prefix [$prefix]"
      # show normalized candidates
      awk -F'\t' '{print $1 " | #" $2}' .issue_map.tsv | norm_line | grep -a -F "$prefix" | head -n 8 || true
      continue
    fi

    IFS=',' read -ra arr <<< "$labels_csv"
    payload='{"labels":['
    for i in "${!arr[@]}"; do
      [ $i -gt 0 ] && payload+=','
      lab="${arr[$i]//\"/\\\"}"
      payload+="\"$lab\""
    done
    payload+=']}'

    curl -s -X PATCH "${hdr[@]}" "$API/repos/$OWNER/$REPO/issues/$num" -d "$payload" >/dev/null
    echo "  • Labeled #$num ← $title"
  done
}

link_epics() {
  echo "→ Linking epics to tickets…"
  build_issue_map  # refresh after labeling

  for e in 1 2 3 4 5 6 7 8 9 10; do
    epic_title=$("$JQ" -r --arg e "$e" '
      [.[] | select(.title|startswith("E"+$e+". ")) | .title][0] // empty
    ' "$ISSUES_JSON" | norm_line)
    [ -z "$epic_title" ] && { echo "  • E$e epic title not in JSON, skip"; continue; }

    epic_num="$(issue_num_by_title "$epic_title" || true)"
    if [ -z "${epic_num:-}" ]; then
      echo "  • E$e epic not found in repo, skip"
      continue
    fi

    mapfile -t ticket_titles < <("$JQ" -r --arg tag "E$e" '
      [.[] | select((.labels|index("Ticket")) and (.labels|index($tag))) | .title] | .[]
    ' "$ISSUES_JSON" | norm_line)

    checklist=""
    for tt in "${ticket_titles[@]}"; do
      [ -z "$tt" ] && continue
      tnum="$(issue_num_by_title "$tt" || true)"
      [ -n "${tnum:-}" ] && checklist+="- [ ] #$tnum"$'\n'
    done
    [ -z "$checklist" ] && { echo "  • E$e no tickets to link"; continue; }

    body=$(curl -s "${hdr[@]}" "$API/repos/$OWNER/$REPO/issues/$epic_num" | "$JQ" -r '.body // ""')
    new_body="$body"$'\n---\n**Tracks**\n'"$checklist"
    curl -s -X PATCH "${hdr[@]}" "$API/repos/$OWNER/$REPO/issues/$epic_num" \
      -d "$(printf '%s' "$new_body" | "$JQ" -Rs '{body: .}')" >/dev/null
    echo "  • E$e (#$epic_num) linked $(printf '%s' "$checklist" | grep -c '^-' || true) tickets"
  done
}

main() {
  require_tools
  ensure_labels
  build_issue_map
  apply_labels
  link_epics
  echo "✓ Sync complete."
}
main "$@"
