#!/usr/bin/env python3
"""@VanillaPort 소스 주석을 모아 "바닐라 클래스 → Furnace 클래스" 대응표(TSV)를 만든다.

사용: tools/ci/vanilla-port-map.py [--out 파일] [--prefix 패키지경로조각]
  --prefix  furnace-server 소스 아래 이 경로 조각이 든 파일만(예: server/world/ → 월드·월드젠 조각)
  --out     기본 build/vanilla-port-map.tsv
열: 바닐라 클래스 \t Furnace 클래스(FQN) \t 소스 경로. 바닐라 이름은 net.minecraft. 로 시작해야 한다(아니면 실패).
"""
import argparse, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "furnace-server", "src", "main", "java")
ANN = re.compile(r"@(?:org\.domi\.furnace\.server\.)?VanillaPort\(\s*(\{[^}]*\}|\"[^\"]*\")\s*\)")
VAL = re.compile(r'"([^"]*)"')
PKG = re.compile(r"^package\s+([\w.]+);", re.M)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(ROOT, "build", "vanilla-port-map.tsv"))
    ap.add_argument("--prefix", default="")
    a = ap.parse_args()
    rows, bad = [], []
    for d, _, fs in os.walk(SRC):
        for f in fs:
            if not f.endswith(".java"):
                continue
            p = os.path.join(d, f)
            rel = os.path.relpath(p, SRC).replace(os.sep, "/")
            if a.prefix not in rel:
                continue
            text = open(p, encoding="utf-8").read()
            m = ANN.search(text)
            if not m:
                continue
            pk = PKG.search(text)
            fq = (pk.group(1) + "." if pk else "") + f[:-5]
            for v in VAL.findall(m.group(1)):
                if not v.startswith("net.minecraft."):
                    bad.append((rel, v))
                rows.append((v, fq, "furnace-server/src/main/java/" + rel))
    if bad:
        for rel, v in bad:
            print("net.minecraft. 로 시작하지 않음: %s: %s" % (rel, v), file=sys.stderr)
        sys.exit(1)
    rows.sort()
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, "w", encoding="utf-8", newline="\n") as o:
        o.write("vanilla\tfurnace\tpath\n")
        for r in rows:
            o.write("\t".join(r) + "\n")
    print("%d행 → %s" % (len(rows), a.out))

if __name__ == "__main__":
    main()
