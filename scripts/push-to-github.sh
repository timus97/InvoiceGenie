#!/usr/bin/env bash
set -euo pipefail

# Push current repo changes to GitHub.
# Usage:
#   GITHUB_TOKEN=ghp_xxx ./scripts/push-to-github.sh <github-repo-url> [branch]
#
# Example:
#   GITHUB_TOKEN=ghp_abc123 ./scripts/push-to-github.sh \
#     https://github.com/timus97/InvoiceGenie.git main
#
# Notes:
# - Token is read from GITHUB_TOKEN and never written to git config.
# - This script sets author identity only for this commit operation.

if [[ $# -lt 1 ]]; then
  echo "Usage: GITHUB_TOKEN=<token> $0 <github-repo-url> [branch]"
  exit 1
fi

if [[ -z "${GITHUB_TOKEN:-}" ]]; then
  echo "Error: GITHUB_TOKEN is not set."
  exit 1
fi

REPO_URL="$1"
BRANCH="${2:-$(git branch --show-current)}"
REMOTE_NAME="github"

GIT_NAME="timus97"
GIT_EMAIL="sumitbhardwaj2411@gmail.com"

# GitHub PAT auth format.
AUTH_URL="${REPO_URL/https:\/\//https:\/\/x-access-token:${GITHUB_TOKEN}@}"
MASKED_URL="${REPO_URL/https:\/\//https:\/\/x-access-token:***@}"

echo "Preparing push to ${MASKED_URL} on branch ${BRANCH}..."

if git remote get-url "${REMOTE_NAME}" >/dev/null 2>&1; then
  git remote set-url "${REMOTE_NAME}" "${AUTH_URL}"
else
  git remote add "${REMOTE_NAME}" "${AUTH_URL}"
fi

git add .

if git diff --cached --quiet; then
  echo "No staged changes to commit. Proceeding to push existing branch state."
else
  git -c user.name="${GIT_NAME}" -c user.email="${GIT_EMAIL}" commit -m "chore: sync latest local changes"
fi

git push -u "${REMOTE_NAME}" "${BRANCH}"

# Remove tokenized URL after push.
git remote set-url "${REMOTE_NAME}" "${REPO_URL}"

echo "Done: pushed ${BRANCH} to ${REPO_URL}"
