#!/usr/bin/env python3
"""@VanillaPort 대응표 생성기: 서버 소스를 훑어 (바닐라 클래스, Furnace 클래스, 파일) 표를 만든다.

사용: python3 tools/vanilla-port/vanilla-port-map.py [--out build/vanilla-port-map.tsv] [--verify 바닐라클래스목록.txt]
  --verify: 한 줄에 클래스 하나(예: net.minecraft.world.level.block.Block, '$' 또는 '.' 구분 모두 허용)인 파일.
            바닐라 jar 를 풀어 만든 목록(jar tf 결과에서 .class 제거)을 주면, 목록에 없는 대응을 오류로 알린다.
            바닐라 jar 는 커밋하지 않으므로(규칙 7) 목록은 로컬에서 만든다.
출력(TSV, 바닐라 이름순): vanilla_class <TAB> furnace_class <TAB> file
"""
import argparse, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "furnace-server/src/main/java"
ANN = re.compile(r'@(?:org\.domi\.furnace\.server\.)?VanillaPort\s*\(\s*(\{[^)]*\}|"[^"]*")\s*\)')
TYPE = re.compile(r'^(?:\s*(?:public|protected|private|abstract|final|sealed|static|non-sealed)\s+)*\s*(?:class|interface|enum|record)\s+(\w+)', re.M)
PKG = re.compile(r'^package\s+([\w.]+);', re.M)

def scan():
    rows = []
    for f in sorted(SRC.rglob("*.java")):
        text = f.read_text(encoding="utf-8")
        if "VanillaPort" not in text or f.name == "VanillaPort.java":
            continue
        pkg = PKG.search(text).group(1)
        for m in ANN.finditer(text):
            t = TYPE.search(text, m.end())
            name = t.group(1) if t else f.stem
            for v in re.findall(r'"([^"]+)"', m.group(1)):
                rows.append((v, f"{pkg}.{name}", str(f.relative_to(ROOT))))
    return sorted(rows)

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(ROOT / "build/vanilla-port-map.tsv"))
    ap.add_argument("--verify")
    a = ap.parse_args()
    rows = scan()
    out = Path(a.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("".join("\t".join(r) + "\n" for r in rows), encoding="utf-8")
    print(f"{len(rows)}건 -> {out}")
    if a.verify:
        known = {l.strip().replace("/", ".").replace("$", ".") for l in Path(a.verify).read_text(encoding="utf-8").splitlines() if l.strip()}
        bad = [r for r in rows if r[0].replace("$", ".") not in known]
        for r in bad:
            print(f"없는 바닐라 클래스: {r[0]}  ({r[2]})", file=sys.stderr)
        sys.exit(1 if bad else 0)

if __name__ == "__main__":
    main()
