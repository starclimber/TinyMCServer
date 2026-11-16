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

  # 可选：按 RECREATE_TAG 强制重建目标端的同名 Release。
  # 目标站按「发布时间」倒序排列，补建旧版本会把旧版本顶到新版本上面，
  # 用它删掉再建一次即可让新版本回到最前。
  if [ -n "$rel_id" ] && [ "${RECREATE_TAG:-}" = "$tag" ]; then
    echo "  按 RECREATE_TAG 重建 $tag（先删除旧 release id=$rel_id）"
    curl -sS -X DELETE "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}" "${AUTH[@]}" -o /dev/null || true
    rel_id=""
  fi

  if [ -n "$rel_id" ]; then
    echo "  更新已有 release id=$rel_id（$tag）"
    local patch
    patch=$(TITLE="$title" BODY="$body" python3 -c '
import json,os
print(json.dumps({"name": os.environ["TITLE"], "body": os.environ["BODY"]}))
')
    curl -sS -f -X PATCH "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}" \
      "${AUTH[@]}" -H "Content-Type: application/json" -d "$patch" > /dev/null \
      || echo "::warning::更新 release 标题/正文失败（$API $tag）"
  else
    if [ "$allow" != "1" ]; then
      echo "  目标端没有 $tag，跳过（全量模式不新建）"
      return 0
    fi
    # 目标端没有对应 tag 时建不出 Release（早期版本的历史遗留），跳过即可
    local tag_code
    tag_code=$(curl -sS -o /dev/null -w "%{http_code}" "${AUTH[@]}" \
      "${API}/repos/${OWNER}/${REPO}/tags/${tag}" || echo "000")
    if [ "$tag_code" != "200" ]; then
      echo "::warning::${API} 上没有 tag ${tag}，跳过该 Release"
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
      "${AUTH[@]}" -H "Content-Type: application/json" -d "$create" 2>/dev/null \
      | python3 -c 'import json,sys;print(str(json.load(sys.stdin).get("id","")))' 2>/dev/null || true)
    if [ -z "$rel_id" ]; then
      echo "::warning::release 创建失败 ($API $tag)"
      return 0
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
    local code
    code=$(curl -sS -o /dev/null -w "%{http_code}" -X POST \
      "${API}/repos/${OWNER}/${REPO}/releases/${rel_id}/assets?name=${name}" \
      "${AUTH[@]}" -F "attachment=@${tmp}/${name}" || echo "000")
    if [ "$code" = "201" ] || [ "$code" = "200" ]; then
      echo "  已上传 $name"
    else
      # Codeberg 对单个资产有体积上限，112MB 的 APK 会被 413 拒收；
      # 这不该让整次同步失败——Release 的标题/正文仍然要同步过去。
      echo "::warning::上传 $name 到 ${API} 失败（HTTP $code），APK 请从 GitHub Releases 下载"
    fi
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
    # allow=1：目标端缺这个 Release 时也建出来（标题/正文/APK 一起补齐）。
    # 目标端已齐全的 Release 会因为「资产已齐全」直接跳过，不会重复搬大文件。
    sync_one "$t" 1 || fail=1
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
