#!/usr/bin/env bash
# Resolve VERSION_NAME + VERSION_CODE from a git tag ref or defaults.
# Usage:
#   source scripts/resolve-version.sh           # uses GITHUB_REF or defaults
#   source scripts/resolve-version.sh v1.2.3
#   ./scripts/resolve-version.sh v1.2.3        # prints export lines
set -euo pipefail

input="${1:-${GITHUB_REF_NAME:-}}"

# Strip refs/tags/ if present
case "$input" in
  refs/tags/*) input="${input#refs/tags/}" ;;
esac

if [[ "$input" =~ ^v([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  VERSION_NAME="${BASH_REMATCH[1]}.${BASH_REMATCH[2]}.${BASH_REMATCH[3]}"
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[2]}"
  PATCH="${BASH_REMATCH[3]}"
  VERSION_CODE=$((MAJOR * 1000000 + MINOR * 1000 + PATCH))
  IS_RELEASE=true
elif [[ "$input" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
  VERSION_NAME="$input"
  MAJOR="${BASH_REMATCH[1]}"
  MINOR="${BASH_REMATCH[2]}"
  PATCH="${BASH_REMATCH[3]}"
  VERSION_CODE=$((MAJOR * 1000000 + MINOR * 1000 + PATCH))
  IS_RELEASE=true
else
  VERSION_NAME="${VERSION_NAME:-1.0.0}"
  VERSION_CODE="${VERSION_CODE:-1}"
  IS_RELEASE=false
fi

export VERSION_NAME VERSION_CODE IS_RELEASE

if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  echo "VERSION_NAME=$VERSION_NAME"
  echo "VERSION_CODE=$VERSION_CODE"
  echo "IS_RELEASE=$IS_RELEASE"
fi
