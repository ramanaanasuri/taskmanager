#!/usr/bin/env bash
set -euo pipefail

print_header() {
  clear 2>/dev/null || true
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo " Git Helper"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo
}

# ---------- Git helpers ----------
git_in_repo() {
  git rev-parse --is-inside-work-tree >/dev/null 2>&1
}

git_current_branch() {
  git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "(no-branch)"
}

git_require_repo() {
  if ! git_in_repo; then
    echo "❌ Not inside a Git repository."
    read -rp "Press Enter to continue…"
    return 1
  fi
  return 0
}

git_require_remote_origin() {
  local url
  url="$(git remote get-url origin 2>/dev/null || true)"
  if [[ -z "$url" ]]; then
    echo "⚠️  No 'origin' remote set."
    echo "Set SSH remote, e.g.:"
    echo "  git remote add origin git@github.com:<user>/<repo>.git"
    read -rp "Press Enter to continue…"
    return 1
  fi
  if [[ "$url" != git@github.com:* ]]; then
    echo "⚠️  'origin' is not SSH: $url"
    echo "To switch to SSH:"
    echo "  git remote set-url origin git@github.com:<user>/<repo>.git"
    read -rp "Press Enter to continue…"
  fi
  return 0
}

# ---------- Actions ----------
action_git_status() {
  git_require_repo || return
  print_header
  echo "Branch: $(git_current_branch)"
  echo "Remote: $(git remote get-url origin 2>/dev/null || echo '—')"
  echo "────────────────────────────────────────────────"
  git status
  echo "────────────────────────────────────────────────"
  read -rp "Press Enter to continue…"
}

action_git_add_commit_push() {
  git_require_repo || return
  git_require_remote_origin || true

  print_header
  echo "Branch: $(git_current_branch)"
  echo
  echo "Stage changes:"
  echo "  1) Stage ALL (git add -A)"
  echo "  2) Interactive patch (git add -p)"
  echo "  3) Only already-staged (skip add)"
  read -rp "Choose 1/2/3 [1]: " addmode
  addmode="${addmode:-1}"

  case "$addmode" in
    1) git add -A ;;
    2) git add -p ;;
    3) : ;;
    *) git add -A ;;
  esac

  echo
  git status --short || true
  echo
  read -e -p "Commit message: " msg
  msg="${msg:-"update $(date +%F\ %T)"}"

  if git diff --cached --quiet; then
    echo "ℹ️  No staged changes to commit."
  else
    git commit -m "$msg" || { echo "❌ Commit failed."; read -rp "Press Enter…"; return; }
  fi

  echo
  read -rp "Push to origin/$(git_current_branch)? [Y/n]: " pushok
  if [[ "${pushok,,}" != "n" ]]; then
    if ! git rev-parse --abbrev-ref --symbolic-full-name @{u} >/dev/null 2>&1; then
      git push -u origin "$(git_current_branch)" || { echo "❌ Push failed."; read -rp "Press Enter…"; return; }
    else
      git push origin "$(git_current_branch)" || { echo "❌ Push failed."; read -rp "Press Enter…"; return; }
    fi
    echo "✅ Pushed to origin/$(git_current_branch)."
  else
    echo "↩️  Skipped push."
  fi
  read -rp "Press Enter to continue…"
}

action_git_pull_rebase() {
  git_require_repo || return
  git_require_remote_origin || true
  print_header
  echo "Pulling with rebase from origin/$(git_current_branch)…"
  echo
  git pull --rebase origin "$(git_current_branch)" || {
    echo
    echo "⚠️  Pull failed. Resolve conflicts then:"
    echo "    git add -A && git rebase --continue"
    echo "    (or git rebase --abort)"
  }
  echo
  read -rp "Press Enter to continue…"
}

action_git_branch_ops() {
  git_require_repo || return
  print_header
  echo "1) Create & switch to NEW branch"
  echo "2) Switch to EXISTING branch"
  echo "3) Delete local branch"
  read -rp "Choose 1/2/3: " choice

  case "$choice" in
    1)
      read -rp "New branch name: " nb
      [[ -z "${nb:-}" ]] && { echo "No branch provided."; read -rp "Enter…"; return; }
      git checkout -b "$nb" && echo "✅ Now on $nb"
      ;;
    2)
      echo "Existing branches:"
      git branch --all
      read -rp "Branch to switch to: " b
      [[ -z "${b:-}" ]] && { echo "No branch provided."; read -rp "Enter…"; return; }
      git checkout "$b"
      ;;
    3)
      git branch
      read -rp "Local branch to delete: " db
      [[ -z "${db:-}" ]] && { echo "No branch provided."; read -rp "Enter…"; return; }
      read -rp "Force delete? [y/N]: " force
      if [[ "${force,,}" == "y" ]]; then git branch -D "$db"; else git branch -d "$db"; fi
      ;;
    *) echo "Invalid." ;;
  esac
  read -rp "Press Enter to continue…"
}

action_git_stash_menu() {
  git_require_repo || return
  print_header
  echo "1) Stash current changes"
  echo "2) List stashes"
  echo "3) Apply latest stash"
  echo "4) Pop latest stash"
  read -rp "Choose: " s
  case "$s" in
    1) read -rp "Optional stash message: " sm; git stash push -u -m "$sm" ;;
    2) git stash list ;;
    3) git stash apply ;;
    4) git stash pop ;;
    *) echo "Invalid." ;;
  esac
  read -rp "Press Enter to continue…"
}

action_git_log() {
  git_require_repo || return
  print_header
  git --no-pager log --oneline --graph --decorate -n 30
  echo
  read -rp "Press Enter to continue…"
}

action_git_diff_cached() {
  git_require_repo || return
  print_header
  echo "Showing staged diff:"
  echo "────────────────────────────────────────────────"
  git --no-pager diff --cached || true
  echo "────────────────────────────────────────────────"
  read -rp "Press Enter to continue…"
}

action_git_check_ssh_remote() {
  git_require_repo || return
  print_header
  local url; url="$(git remote get-url origin 2>/dev/null || true)"
  if [[ -z "$url" ]]; then
    echo "No origin remote configured."
  else
    echo "origin: $url"
    if [[ "$url" == git@github.com:* ]]; then
      echo "✅ SSH remote is set."
    else
      echo "⚠️  Not an SSH remote. To fix:"
      echo "    git remote set-url origin git@github.com:<user>/<repo>.git"
    fi
  fi
  echo
  echo "SSH test to GitHub (will print a short message):"
  ssh -T git@github.com || true
  echo
  read -rp "Press Enter to continue…"
}

# ---------- Menu ----------
while true; do
  print_header
  echo "Choose a Git action:"
  echo "  1) Status"
  echo "  2) Add + Commit + Push (prompt message)"
  echo "  3) Pull --rebase"
  echo "  4) Branch ops (create/switch/delete)"
  echo "  5) Stash (push/list/apply/pop)"
  echo "  6) Recent log (graph)"
  echo "  7) Show staged diff"
  echo "  8) Check SSH remote & auth"
  echo "  q) Quit"
  read -rp "Select: " ans
  case "$ans" in
    1) action_git_status ;;
    2) action_git_add_commit_push ;;
    3) action_git_pull_rebase ;;
    4) action_git_branch_ops ;;
    5) action_git_stash_menu ;;
    6) action_git_log ;;
    7) action_git_diff_cached ;;
    8) action_git_check_ssh_remote ;;
    q|Q) exit 0 ;;
    *) echo "Invalid choice"; read -rp "Press Enter…";;
  esac
done

