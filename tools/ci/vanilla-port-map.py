#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""@VanillaPort 소스 주석을 훑어 "Furnace 클래스 ↔ 바닐라 클래스" 대응표(V-06)를 만든다.

버전업 때 바뀐 바닐라 클래스를 보고 고칠 Furnace 파일을 찾는 용도다. 소스만 읽으므로 빌드가 필요 없고, 결과는 커밋하지 않는다.
  python3 -I tools/ci/vanilla-port-map.py                      # build/vanilla-port-map.tsv 를 만들고 요약을 찍는다
  python3 -I tools/ci/vanilla-port-map.py --by-vanilla          # 바닐라 이름 순으로 정렬해 stdout 에 찍는다
  python3 -I tools/ci/vanilla-port-map.py --verify classes.txt  # 한 줄에 바닐라 클래스 전체 이름 하나인 목록과 대조(없는 이름이 있으면 종료 코드 1)
  python3 -I tools/ci/vanilla-port-map.py --self-test
출력 열: Furnace 클래스 전체 이름 <탭> 바닐라 클래스 전체 이름 <탭> 소스 경로(저장소 기준). 한 Furnace 클래스가 여러 바닐라 클래스를 옮겼으면 바닐라마다 한 줄이다.
목록 파일은 바닐라 jar 에서 뽑는다(jar 는 커밋하지 않는다 — 규칙 7): 예) unzip -Z1 server.jar | grep '\\.class$' | sed 's/\\.class$//; s|/|.|g'
(1.21.1 은 난독화 이름 jar 라 Mojang 이름으로 풀린 jar 가 필요하다.)
"""
import os, re, sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC_DIRS = [os.path.join(ROOT, "furnace-server", "src", "main", "java")]
OUT = os.path.join(ROOT, "build", "vanilla-port-map.tsv")

ANN = re.compile(r"^@(?:org\.domi\.furnace\.server\.)?VanillaPort\(\s*(\{[^}]*\}|\"[^\"]*\")\s*\)\s*$", re.M | re.S)
PKG = re.compile(r"^package\s+([\w.]+)\s*;", re.M)
DECL = re.compile(r"^(?:(?:public|protected|private|final|abstract|sealed|non-sealed|static|strictfp)\s+)*(?:class|interface|enum|record|@interface)\s+(\w+)", re.M)
STRING = re.compile(r'"([^"]*)"')


def scan_text(text, path=""):
    """한 파일에서 (Furnace 클래스 이름, [바닐라 이름…]) 목록을 돌려준다."""
    pkg = PKG.search(text)
    pkg = pkg.group(1) if pkg else ""
    rows = []
    for m in ANN.finditer(text):
        d = DECL.search(text, m.end())
        if not d:
            raise ValueError("%s: @VanillaPort 뒤에 클래스 선언이 없다" % path)
        names = STRING.findall(m.group(1))
        if not names:
            raise ValueError("%s: @VanillaPort 값이 비었다" % path)
        rows.append(((pkg + "." if pkg else "") + d.group(1), names))
    return rows


def scan():
    rows = []
    for base in SRC_DIRS:
        for dp, _, fs in os.walk(base):
            for f in sorted(fs):
                if not f.endswith(".java"):
                    continue
                p = os.path.join(dp, f)
                with open(p, encoding="utf-8") as fh:
                    text = fh.read()
                for cls, names in scan_text(text, p):
                    for n in names:
                        rows.append((cls, n, os.path.relpath(p, ROOT).replace(os.sep, "/")))
    rows.sort()
    return rows


def self_test():
    t = 'package a.b;\n\n/** x */\n@org.domi.furnace.server.VanillaPort({"n.m.A", "n.m.B$C"})\npublic final class Foo {}\n'
    assert scan_text(t) == [("a.b.Foo", ["n.m.A", "n.m.B$C"])], scan_text(t)
    t = 'package a;\nimport x.VanillaPort;\n@VanillaPort("n.m.A")\n@Deprecated\nenum E {}\n'
    assert scan_text(t) == [("a.E", ["n.m.A"])]
    assert scan_text("package a;\nclass Plain {}\n") == []
    print("자체 시험 통과")


def main(argv):
    if "--self-test" in argv:
        self_test()
        return 0
    rows = scan()
    if "--verify" in argv:
        with open(argv[argv.index("--verify") + 1], encoding="utf-8") as fh:
            known = {l.strip() for l in fh if l.strip()}
        missing = [(c, v) for c, v, _ in rows if v not in known]
        for c, v in missing:
            print("없는 바닐라 클래스: %s  (Furnace %s)" % (v, c))
        print("대조 %d줄, 없는 이름 %d" % (len(rows), len(missing)))
        return 1 if missing else 0
    if "--by-vanilla" in argv:
        for c, v, p in sorted(rows, key=lambda r: (r[1], r[0])):
            print("%s\t%s\t%s" % (v, c, p))
        return 0
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        for c, v, p in rows:
            fh.write("%s\t%s\t%s\n" % (c, v, p))
    print("%d 줄(Furnace 클래스 %d개, 바닐라 클래스 %d개) → %s" % (len(rows), len({r[0] for r in rows}), len({r[1] for r in rows}), os.path.relpath(OUT, ROOT)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
