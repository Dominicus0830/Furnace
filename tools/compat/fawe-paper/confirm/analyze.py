# 각 실행을 기준(paper-pt1-01)과 블록 칸 단위로 대조해 갈린 칸 수와 칸 좌표를 표로 낸다.
import glob, json, os, subprocess, sys
R = r"E:\t198-work\runs"; ref = "cf-paper-pt1-01"
out = {}
for d in sorted(glob.glob(os.path.join(R, "cf-paper-*-[0-9][0-9]")) + glob.glob(os.path.join(R, "cf-furnace-*-[0-9][0-9]"))):
    n = os.path.basename(d)
    if n == ref: out[n] = (0, []); continue
    subprocess.run([sys.executable, "-X", "utf8", r"E:\t198-work\anvil-diff.py", os.path.join(R, ref, "world"), os.path.join(d, "world"),
                    "--chunks", "-4", "-1", "-2", "0", "--ignore", "sectionKeys", "--samples", "50", "--json", "tmp.json"], capture_output=True)
    j = json.load(open("tmp.json", encoding="utf-8"))
    cells = []
    for c in j["chunks"]:
        b = c.get("blocks")
        if b: cells.append(b)
    out[n] = (j["summary"]["byKind"].get("blockCells", 0), cells)
    print(n, out[n][0], json.dumps(cells, ensure_ascii=False), flush=True)
json.dump({k: v[0] for k, v in out.items()}, open("result.json", "w"), indent=1)
