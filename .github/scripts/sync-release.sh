#!/usr/bin/env bash
# 把当前版本同步到目标 Gitea/Forgejo 实例（Codeberg 与 gitea.com 均适用）。
# 用法: sync-release.sh <api_base> <owner> <repo> <token>
set -euo pipefail

API="$1"
OWNER="$2"
REPO="$3"
TOKEN="$4"

GH_REPO="${GH_REPO:-starclimber/TinyMCServer}"

VERSION=$(grep -oP 'versionName\s*=\s*"\K[^"]+' app/build.gradle.kts | head -1)
TAG="v${VERSION}"
echo "版本: $VERSION  标签: $TAG  目标: $API/$OWNER/$REPO"

mkdir -p dist
gh release view "$TAG" --repo "$GH_REPO" --json name,body > rel.json 2>/dev/null || echo '{}' > rel.json
gh release download "$TAG" --repo "$GH_REPO" --pattern '*.apk' --dir dist 2>/dev/null || true

TITLE=$(python3 -c "import json;d=json.load(open('rel.json'));print(d.get('name') or '${TAG}')")
BODY=$(python3 -c "import json;d=json.load(open('rel.json'));print(d.get('body') or '')")

REL_ID=$(curl -sS -H "Authorization: token ${TOKEN}" \
  "${API}/repos/${OWNER}/${REPO}/releases" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(next((str(r['id']) for r in d if r.get('tag_name')=='${TAG}'),''))" 2>/dev/null || true)

if [ -n "$REL_ID" ]; then
  echo "更新已有 release id=$REL_ID"
  PAYLOAD=$(TITLE="$TITLE" BODY="$BODY" python3 -c "import json,os;print(json.dumps({'name':os.environ['TITLE'],'body':os.environ['BODY']}))")
  curl -sS -f -X PATCH "${API}/repos/${OWNER}/${REPO}/releases/${REL_ID}" \
    -H "Authorization: token ${TOKEN}" -H "Content-Type: application/json" -d "$PAYLOAD" > /dev/null
else
  echo "创建 release $TAG"
  PAYLOAD=$(TAG="$TAG" TITLE="$TITLE" BODY="$BODY" python3 -c "import json,os;print(json.dumps({'tag_name':os.environ['TAG'],'name':os.environ['TITLE'],'body':os.environ['BODY'],'draft':False,'prerelease':False}))")
  REL_ID=$(curl -sS -f -X POST "${API}/repos/${OWNER}/${REPO}/releases" \
    -H "Authorization: token ${TOKEN}" -H "Content-Type: application/json" -d "$PAYLOAD" \
    | python3 -c "import json,sys;print(str(json.load(sys.stdin).get('id','')))")
fi

if [ -z "$REL_ID" ]; then echo "::error::release 创建失败 ($API)"; exit 1; fi

EXISTING=$(curl -sS -H "Authorization: token ${TOKEN}" \
  "${API}/repos/${OWNER}/${REPO}/releases/${REL_ID}/assets" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print(' '.join(a['name'] for a in d))" 2>/dev/null || true)

for f in dist/*.apk; do
  [ -e "$f" ] || { echo "::notice::没有 APK 可上传"; break; }
  name=$(basename "$f")
  case " $EXISTING " in *" $name "*) echo "跳过已存在的 $name"; continue;; esac
  echo "上传 $name"
  curl -sS -f -X POST \
    "${API}/repos/${OWNER}/${REPO}/releases/${REL_ID}/assets?name=${name}" \
    -H "Authorization: token ${TOKEN}" -F "attachment=@${f}" > /dev/null
done

echo "完成：$TAG → $API"
