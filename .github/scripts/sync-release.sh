#!/usr/bin/env bash
# 把 GitHub 上的 Release（标题、正文、APK）同步到目标 Gitea / Forgejo 实例。
# 用法: sync-release.sh <api_base> <owner> <repo> <token> [tag] [mode]
#   tag  : 指定要同步的 tag；mode=one 且 tag 为空时，按 build.gradle 的 versionName 推断
#   mode : one（默认）只同步一个 tag；all 遍历目标仓库已有的全部 Release 逐个对齐
set -euo pipefail

API="$1"
OWNER="$2"
REPO="$3"
TOKEN="$4"
TAG="${5:-}"
MODE="${6:-one}"

GH_REPO="${GH_REPO:-starclimber/TinyMCServer}"
AUTH=(-H "Authorization: token ${TOKEN}")

# ---------------- 单个 tag ----------------
# allow=1：目标端没有该 Release 时新建，并补齐缺失的 APK
# allow=0：只对齐目标端**已存在**的 Release 的标题/正文，不新建、不下载大文件
sync_one() {
  local tag="$1"
  local allow="${2:-1}"
  local rel_json title body

  if ! rel_json=$(gh release view "$tag" --repo "$GH_REPO" --json name,body 2>/dev/null); then
    echo "::notice::GitHub 上没有 Release $tag，跳过"
    return 0
  fi
  title=$(python3 -c 'import json,sys;d=json.loads(sys.argv[1]);print(d.get("name") or sys.argv[2])' "$rel_json" "$tag")
  body=$(python3 -c 'import json,sys;d=json.loads(sys.argv[1]);print(d.get("body") or "")' "$rel_json")

  # 目标端是否已有同名 Release
  local rel_id
  rel_id=$(curl -sS "${AUTH[@]}" "${API}/repos/${OWNER}/${REPO}/releases?limit=100" | python3 -c '
import json,sys
tag=sys.argv[1]
try:
    d=json.load(sys.stdin)
except Exception:
    d=[]
print(next((str(r["id"]) for r in d if r.get("tag_name")==tag), ""))
' "$tag" 2>/dev/null || true)

  if [ -n "$rel_id" ]; then
    echo "  更新已有 release id=$rel_id（$tag）"
    local patch
    patch=$(TITLE="$title" BODY="$body" python3 -c '
import json,os
print(json.dumps({"name": os.environ["TITLE"], "body": os.environ["BODY"]}))
')
    curl -sS -f -X PATCH "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}" \
      "${AUTH[@]}" -H "Content-Type: application/json" -d "$patch" > /dev/null
  else
    if [ "$allow" != "1" ]; then
      echo "  目标端没有 $tag，跳过（全量模式不新建）"
      return 0
    fi
    echo "  创建 release $tag"
    local create
    create=$(TAG="$tag" TITLE="$title" BODY="$body" python3 -c '
import json,os
print(json.dumps({"tag_name": os.environ["TAG"], "name": os.environ["TITLE"],
                  "body": os.environ["BODY"], "draft": False, "prerelease": False}))
')
    rel_id=$(curl -sS -f -X POST "${API}/repos/${OWNER}/${REPO}/releases" \
      "${AUTH[@]}" -H "Content-Type: application/json" -d "$create" \
      | python3 -c 'import json,sys;print(str(json.load(sys.stdin).get("id","")))')
    if [ -z "$rel_id" ]; then
      echo "::error::release 创建失败 ($API $tag)"
      return 1
    fi
  fi

  # 全量模式到此为止：只对齐标题 / 正文，避免每次 push 重复搬运上百 MB 的 APK
  if [ "$allow" != "1" ]; then
    return 0
  fi

  # 目标端已有的资产名
  local existing
  existing=$(curl -sS "${AUTH[@]}" "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}/assets" \
    | python3 -c 'import json,sys
d=json.load(sys.stdin)
print(" ".join(a["name"] for a in d))' 2>/dev/null || true)

  # GitHub 上该 Release 的资产名；只有目标端缺的才下载上传，避免每次全量拉 100MB+
  local wanted
  wanted=$(gh release view "$tag" --repo "$GH_REPO" --json assets \
    --jq '.assets[].name' 2>/dev/null || true)

  local missing=""
  for name in $wanted; do
    case " $existing " in *" $name "*) ;; *) missing="$missing $name" ;; esac
  done
  if [ -z "${missing// /}" ]; then
    echo "  资产已齐全，跳过下载"
    return 0
  fi

  local tmp
  tmp=$(mktemp -d)
  for name in $missing; do
    echo "  下载并上传 $name"
    gh release download "$tag" --repo "$GH_REPO" --pattern "$name" --dir "$tmp" 2>/dev/null || {
      echo "::warning::下载 $name 失败，跳过"; continue; }
    curl -sS -f -X POST \
      "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}/assets?name=${name}" \
      "${AUTH[@]}" -F "attachment=@${tmp}/${name}" > /dev/null
  done
  rm -rf "$tmp"
}

# ---------------- 入口 ----------------
if [ "$MODE" = "all" ]; then
  echo "全量对齐所有 Release → $API/$OWNER/$REPO"
  tags=$(gh release list --repo "$GH_REPO" --limit 100 --json tagName --jq '.[].tagName' 2>/dev/null || true)
  if [ -z "$tags" ]; then echo "::warning::GitHub 上没有 Release"; exit 0; fi
  fail=0
  for t in $tags; do
    sync_one "$t" 0 || fail=1
  done
  if [ "$fail" -ne 0 ]; then echo "::error::有 Release 同步失败"; exit 1; fi
  echo "完成：全部 Release → $API"
else
  if [ -z "$TAG" ]; then
    VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)
    TAG="v${VERSION}"
  fi
  echo "单个同步 $TAG → $API/$OWNER/$REPO"
  sync_one "$TAG"
  echo "完成：$TAG → $API"
fi
