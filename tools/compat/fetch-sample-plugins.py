#!/usr/bin/env python3
"""T-234: 인기 플러그인 표본을 받는다(정적 분석 전용). Modrinth 의 1.21.1 Paper·Spigot·Bukkit 플러그인을 내려받은 수 순으로 N개.

사용: python3 -I tools/compat/fetch-sample-plugins.py <받을 폴더> [개수=100] [판=1.21.1]
- jar 는 받은 폴더에만 두고 저장소에 넣지 않는다(각 플러그인의 라이선스). 분석(NmsShimAudit)은 읽기만 한다.
- 폴더에 sample-manifest.tsv 를 쓴다: 슬러그·제목·프로젝트 내려받은 수·jar 이름·sha1·버전. 인기 가중치의 근거다.
- 서버·플레이어 정보를 보내지 않는다. 사용자 에이전트는 일반 이름이다. 이미 받은 jar(sha1 일치)는 다시 받지 않는다.
"""
import hashlib, json, os, sys, time, urllib.parse, urllib.request

API = "https://api.modrinth.com/v2"
UA = {"User-Agent": "furnace-compat-sample-tool/1"}
LOADERS = ["paper", "spigot", "bukkit"]


def get(url):
    for attempt in range(4):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60) as r:
                return r.read()
        except Exception:
            if attempt == 3:
                raise
            time.sleep(2 * (attempt + 1))


def jget(path):
    return json.loads(get(API + path))


def main():
    out = sys.argv[1]
    want = int(sys.argv[2]) if len(sys.argv) > 2 else 100
    mc = sys.argv[3] if len(sys.argv) > 3 else "1.21.1"
    os.makedirs(out, exist_ok=True)
    facets = json.dumps([["project_type:plugin"], [f"versions:{mc}"], ["categories:" + c for c in LOADERS]])
    hits, offset = [], 0
    while len(hits) < want * 2 and offset < 600:
        q = urllib.parse.urlencode({"limit": 100, "offset": offset, "index": "downloads", "facets": facets})
        page = jget("/search?" + q)["hits"]
        if not page:
            break
        hits += page
        offset += 100
        time.sleep(0.3)
    rows = []
    for h in hits:
        if len(rows) >= want:
            break
        q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps(LOADERS)})
        try:
            vs = jget(f"/project/{h['project_id']}/version?{q}")
        except Exception as e:
            print("건너뜀(버전 조회 실패)", h["slug"], e, file=sys.stderr)
            continue
        if not vs:
            continue
        v = vs[0]   # 최신
        f = next((x for x in v["files"] if x.get("primary")), v["files"][0])
        if not f["filename"].endswith(".jar"):
            continue
        name = f"{h['slug']}__{f['filename']}".replace("/", "_")
        path = os.path.join(out, name)
        sha1 = f["hashes"]["sha1"]
        if not (os.path.isfile(path) and hashlib.sha1(open(path, "rb").read()).hexdigest() == sha1):
            data = get(f["url"])
            if hashlib.sha1(data).hexdigest() != sha1:
                print("sha1 불일치, 건너뜀", name, file=sys.stderr)
                continue
            open(path, "wb").write(data)
        rows.append((h["slug"], h["title"], h["downloads"], name, sha1, v["version_number"]))
        print(len(rows), h["slug"], h["downloads"], f["filename"])
        time.sleep(0.2)
    with open(os.path.join(out, "sample-manifest.tsv"), "w", encoding="utf-8") as m:
        m.write("slug\ttitle\tdownloads\tjar\tsha1\tversion\n")
        for r in rows:
            m.write("\t".join(str(x).replace("\t", " ").replace("\n", " ") for x in r) + "\n")
    print("받은 jar", len(rows), "→", out)


if __name__ == "__main__":
    main()
