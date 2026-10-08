#!/usr/bin/env bash
# Publish a verified main-branch APK without losing the previous working release on a recoverable error.
set -Eeuo pipefail

: "${GH_TOKEN:?missing GitHub token}"
: "${GITHUB_REPOSITORY:?missing repository}"
: "${GITHUB_SHA:?missing commit SHA}"
: "${APK_PATH:?missing APK path}"
: "${SHA_PATH:?missing checksum path}"
: "${BUILD_INFO_PATH:?missing build-info path}"

for file in "$APK_PATH" "$SHA_PATH" "$BUILD_INFO_PATH"; do
  test -s "$file"
done
sha256sum -c "$SHA_PATH"
grep -Fx "commit=$GITHUB_SHA" "$BUILD_INFO_PATH"

# Do not publish a superseded run (main may advance before this job finishes).
HEAD_SHA="$(gh api "repos/$GITHUB_REPOSITORY/branches/main" --jq '.commit.sha')"
test "$HEAD_SHA" = "$GITHUB_SHA"

tmp_dir="$(mktemp -d)"
had_previous=0
created_release=0
previous_target=""
previous_tag=""
published=0

rollback_on_failure() {
  local exit_code="$?"
  trap - EXIT
  if [[ "$published" != 1 && "$exit_code" -ne 0 ]]; then
    echo "::error::APK publication did not finish; recovering prior latest release."
    if [[ "$had_previous" == 1 ]]; then
      # Continue each recovery step even if the GitHub API is temporarily unavailable.
      gh release upload latest --repo "$GITHUB_REPOSITORY" --clobber \
        "$tmp_dir/old/$APK_PATH" "$tmp_dir/old/$SHA_PATH" \
        "$tmp_dir/old/$BUILD_INFO_PATH" || echo "::error::Previous APK asset recovery failed"
      gh release edit latest --repo "$GITHUB_REPOSITORY" --target "$previous_target" || \
        echo "::error::Previous release target recovery failed"
      gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/latest" \
        -f "sha=$previous_tag" -F force=true >/dev/null || \
        echo "::error::Previous latest tag recovery failed"
    elif [[ "$created_release" == 1 ]]; then
      gh release delete latest --repo "$GITHUB_REPOSITORY" --yes --cleanup-tag || true
    fi
  fi
  rm -rf "$tmp_dir"
  exit "$exit_code"
}
trap rollback_on_failure EXIT

if gh release view latest --repo "$GITHUB_REPOSITORY" >/dev/null 2>&1; then
  had_previous=1
  previous_target="$(gh release view latest --repo "$GITHUB_REPOSITORY" --json targetCommitish --jq '.targetCommitish')"
  previous_tag="$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/latest" --jq '.object.sha')"
  mkdir -p "$tmp_dir/old"
  gh release download latest --repo "$GITHUB_REPOSITORY" \
    --pattern "$APK_PATH" --pattern "$SHA_PATH" --pattern "$BUILD_INFO_PATH" \
    --dir "$tmp_dir/old"
  (cd "$tmp_dir/old" && sha256sum -c "$SHA_PATH")
else
  gh release create latest --repo "$GITHUB_REPOSITORY" \
    --title "Latest APK" --target "$GITHUB_SHA" \
    --notes "Automatically published from successful main CI: $GITHUB_SHA"
  created_release=1
fi

# --clobber replaces only assets of the same names. Keep a verified local copy
# of all previous assets until upload, download, checksum and ref checks succeed.
gh release upload latest --repo "$GITHUB_REPOSITORY" --clobber \
  "$APK_PATH" "$SHA_PATH" "$BUILD_INFO_PATH"

gh release edit latest --repo "$GITHUB_REPOSITORY" \
  --title "Latest APK" --target "$GITHUB_SHA" \
  --notes "Automatically published from successful main CI: $GITHUB_SHA"

# 'gh release edit --target' alone does not move an EXISTING lightweight Git tag.
gh api --method PATCH "repos/$GITHUB_REPOSITORY/git/refs/tags/latest" \
  -f "sha=$GITHUB_SHA" -F force=true >/dev/null

mkdir -p "$tmp_dir/new"
gh release download latest --repo "$GITHUB_REPOSITORY" \
  --pattern "$APK_PATH" --pattern "$SHA_PATH" --pattern "$BUILD_INFO_PATH" \
  --dir "$tmp_dir/new"
(cd "$tmp_dir/new" && sha256sum -c "$SHA_PATH")
cmp -s "$APK_PATH" "$tmp_dir/new/$APK_PATH"
cmp -s "$BUILD_INFO_PATH" "$tmp_dir/new/$BUILD_INFO_PATH"

target="$(gh release view latest --repo "$GITHUB_REPOSITORY" --json targetCommitish --jq '.targetCommitish')"
tag_sha="$(gh api "repos/$GITHUB_REPOSITORY/git/ref/tags/latest" --jq '.object.sha')"
test "$target" = "$GITHUB_SHA"
test "$tag_sha" = "$GITHUB_SHA"
published=1
echo "Verified latest APK, build-info, SHA-256 and Git tag for $GITHUB_SHA"
