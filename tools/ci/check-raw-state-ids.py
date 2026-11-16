#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""서버 코드에 블록 상태 번호가 숫자로 박혀 있는지 검사한다 — AGENTS.md 규칙 6("상태 번호를 새로 늘리지 않는다")을 CI 에서 강제.

상태 번호는 판이 바뀔 때마다 밀린다. 코드는 숫자 대신 StateIds(gradle/state-ids.spec 에서 생성)를 써야 한다.
  python3 -I tools/ci/check-raw-state-ids.py            # 검사(종료 코드 0 = 통과, 1 = 위반)
  python3 -I tools/ci/check-raw-state-ids.py --self-test # 이 검사 자신의 시험

위반으로 치는 것(주석·문자열 안은 보지 않는다):
  (levelEvent·globalLevelEvent 호출 줄은 사건 번호라 보지 않는다.)
  A. StateIds 에 있는 값과 같은 정수 리터럴. 값이 1000 이상이면 어디서든, 64 이상 1000 미만이면 같은 줄에 state·water·lava·bedrock·leaves·vine·lichen·magma 중 하나가 있을 때만
     (80·130·237 같은 작은 수는 다른 뜻으로도 흔해서, 상태 번호를 다루는 줄에서만 본다. 63 이하(0 공기, 잎 보폭 4, 잎 블록당 28칸 같은 값)는 보지 않는다).
  B. StateIds 에 아직 없는 새 번호(16진수 0x329E·밑줄 12_958 도 값으로 읽는다. 이름에 state 가 없어도 block·state 가 보이는 줄에서 10000~26683 숫자와 견주면 잡는다): 이름에 "state" 가 들어 있는 식이 ==, !=, <, <=, >, >= 로 3자리 이상 정수와 견주어지거나
     "...state... = <4~5자리 정수>" 로 값이 정해지는 줄. 새 번호가 필요하면 gradle/state-ids.spec 에 한 줄 더하고 이름으로 쓴다.
알려진 한계(근사 검사다 — 코드 리뷰를 대신하지 않는다):
  - 새 번호 검사(B)는 "비교 연산자 옆의 숫자"와 "state 가 이름에 든 대입"만 본다. 비교가 없는 형태 — `case 15555 ->`, `table[15555]`,
    `setBlock(x, y, z, 15555)`, `return 15555;` — 는 StateIds 에 이미 있는 값(A)일 때만 잡히고 새 번호는 놓친다.
  - 줄에 block·state 글자가 있는지는 부분 문자열(`lock`)로 보므로 `Block`·`block`·`clock` 같은 이름도 걸릴 수 있다(10000 이상 숫자와의 비교일 때만).
  - 4자리 수(1000~9999)의 새 번호는 이름에 state 가 든 식에서만 본다.
예외는 tools/ci/raw-state-ids.allow 에 "경로<탭>줄에 들어 있는 글귀<탭>이유" 로 적는다(이유 필수 — 예외가 늘면 눈에 띄게).
"""
import os, re, sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(ROOT, "furnace-server", "src", "main", "java")
GEN = os.path.join(ROOT, "furnace-server", "build", "generated", "stateIds", "org", "domi", "furnace", "server", "world", "StateIds.java")
ALLOW = os.path.join(ROOT, "tools", "ci", "raw-state-ids.allow")

INT = re.compile(r"(?<![\w.$])(0[xX][0-9a-fA-F_]+|\d[\d_]*)(?![\w.])")
STR = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'')
CMP_STATE = re.compile(r"\b\w*[sS]tate\w*\b\s*(?:==|!=|<=|>=|<|>)\s*(\d{3,})\b|\b(\d{3,})\s*(?:==|!=|<=|>=|<|>)\s*\w*[sS]tate\w*\b")
SMALL_CTX = re.compile(r"state|water|lava|bedrock|leaves|vine|lichen|magma", re.I)
# 이름에 state 가 없어도 블록을 다루는 줄에서 상태 번호 범위(10000~26683)의 숫자와 견주는 것
BLOCK_LINE = re.compile(r"lock|state|State|STATE")
CMP_ANY = re.compile(r"(?:==|!=|<=|>=|<|>)\s*(0[xX][0-9a-fA-F_]+|\d[\d_]*)\b|\b(0[xX][0-9a-fA-F_]+|\d[\d_]*)\s*(?:==|!=|<=|>=|<|>)")
MAX_STATE = 26683
MIN_BIG = 10000   # 4자리 수(4096·1200 같은 칸 수·틱 수)는 너무 흔해서 이름에 state 가 있을 때(규칙 B 앞부분)만 본다
ASSIGN_STATE = re.compile(r"\b\w*(?:STATE|State|state)\w*\s*=\s*(\d{4,5})\b")


def lit_value(text):
    """정수 리터럴(10진·16진·밑줄 12_958)의 값."""
    t = text.replace("_", "")
    return int(t, 16) if t[:2] in ("0x", "0X") else int(t)


def load_values(path):
    vals = {}
    for m in re.finditer(r"public static final int (\w+) = (-?\d+);", open(path, encoding="utf-8").read()):
        vals.setdefault(int(m.group(2)), []).append(m.group(1))
    return vals


def code_lines(text):
    """(줄 번호, 주석·문자열을 지운 줄) — 블록 주석(/* */, /** */)과 // 주석을 걷어 낸다."""
    in_block = False
    for n, raw in enumerate(text.splitlines(), 1):
        line = STR.sub('""', raw)
        out, i = [], 0
        while i < len(line):
            if in_block:
                j = line.find("*/", i)
                if j < 0: i = len(line)
                else: in_block, i = False, j + 2
            else:
                j, k = line.find("/*", i), line.find("//", i)
                if k >= 0 and (j < 0 or k < j):
                    out.append(line[i:k]); i = len(line)
                elif j >= 0:
                    out.append(line[i:j]); in_block, i = True, j + 2
                else:
                    out.append(line[i:]); i = len(line)
        yield n, "".join(out), raw


def check_text(rel, text, vals, allow, used=None):
    bad = []
    used = used if used is not None else set()
    for n, line, raw in code_lines(text):
        if not line.strip() or "evelEvent(" in line: continue   # levelEvent(소리·입자 사건 번호 2000~3000대)는 상태 번호가 아니다
        hits = []
        for m in INT.finditer(line):
            v = lit_value(m.group(1))
            if v in vals and (v >= 1000 or (v >= 64 and SMALL_CTX.search(line))):
                hits.append(f"StateIds.{vals[v][0]} 와 같은 숫자 {v}")
        if not hits:
            for m in CMP_STATE.finditer(line):
                v = int(m.group(1) or m.group(2)); hits.append(f"상태 번호로 보이는 새 숫자 {v}(gradle/state-ids.spec 에 이름을 더해 쓴다)")
            if BLOCK_LINE.search(line):
                for m in CMP_ANY.finditer(line):
                    v = lit_value(m.group(1) or m.group(2))
                    if MIN_BIG <= v <= MAX_STATE: hits.append(f"블록 상태 번호 범위의 새 숫자 {v}(gradle/state-ids.spec 에 이름을 더해 쓴다)")
            for m in ASSIGN_STATE.finditer(line):
                hits.append(f"상태 번호로 보이는 새 숫자 {m.group(1)}(gradle/state-ids.spec 에 이름을 더해 쓴다)")
        if hits:
            ex = [a for a in allow if a[0] == rel and a[1] in raw]
            if ex: used.update(ex)
            else: bad.append((rel, n, hits[0], raw.strip()))
    return bad


def load_allow(path):
    out = []
    if os.path.exists(path):
        for ln, line in enumerate(open(path, encoding="utf-8"), 1):
            if not line.strip() or line.startswith("#"): continue
            p = line.rstrip("\n").split("\t")
            if len(p) < 3 or not p[2].strip():
                print(f"{path}:{ln}: 예외에는 경로, 글귀, 이유 셋이 탭으로 필요하다"); sys.exit(1)
            out.append((p[0], p[1]))
    return out


def run():
    if not os.path.exists(GEN):
        print(f"{GEN} 가 없다 — 먼저 ./gradlew :furnace-server:generateStateIds"); return 1
    vals, allow, bad = load_values(GEN), load_allow(ALLOW), []
    used = set()
    for d, _, files in os.walk(SRC):
        for f in sorted(files):
            if f.endswith(".java"):
                p = os.path.join(d, f); rel = os.path.relpath(p, SRC).replace(os.sep, "/")
                bad += check_text(rel, open(p, encoding="utf-8").read(), vals, allow, used)
    stale = [a for a in allow if a not in used]
    for p, frag in stale:
        print(f"낡은 예외(이제 걸리지 않는다 — raw-state-ids.allow 에서 지운다): {p}\t{frag}")
    for rel, n, why, raw in bad:
        print(f"{rel}:{n}: {why}\n    {raw}")
    if stale: return 1
    if bad:
        print(f"\nraw 상태 번호 검사: 위반 {len(bad)}곳 — StateIds 상수를 쓰세요(규칙 6, tools/ci/check-raw-state-ids.py 머리말 참고).")
        return 1
    print(f"raw 상태 번호 검사: 통과 (StateIds {len(vals)}개 값, 예외 {len(allow)}개)")
    return 0


def self_test():
    vals = {0: ["AIR"], 80: ["WATER_FIRST"], 12958: ["VOID_AIR"], 237: ["LEAVES_FIRST"], 0x329E: ["X"]}
    def n(src, allow=()): return len(check_text("X.java", src, vals, list(allow)))
    ok = True
    def expect(name, got, want):
        nonlocal ok
        if got != want: ok = False; print(f"자체 시험 실패: {name}: {got} != {want}")
    expect("큰 값 직접", n("int a = x == 12958;"), 1)
    expect("큰 값 주석", n("int a = 1; // 12958 void_air\n/* 12958 */\n/**\n * 12958\n */"), 0)
    expect("큰 값 문자열", n('String s = "12958";'), 0)
    expect("작은 값 state 줄", n("return stateId >= 80;"), 1)
    expect("작은 값 다른 뜻", n("int max = 80;"), 0)
    expect("0 은 안 본다", n("return stateId == 0;"), 0)
    expect("새 번호 비교", n("return stateId == 5555;"), 1)
    expect("새 번호 대입", n("private static final int SOME_STATE = 7777;"), 1)
    expect("상수 쓰기", n("return stateId == StateIds.VOID_AIR;"), 0)
    expect("예외", n("return stateId == 12958;", [("X.java", "== 12958")]), 0)
    expect("levelEvent", n("level.levelEvent(null, 12958, p, 0);"), 0)
    expect("16진수", n("return stateId == 0x329E;"), 1)
    expect("16진수 StateIds 값", n("return x == 0x329E;"), 1)
    expect("밑줄", n("return stateId == 12_958;"), 1)
    expect("state 없는 이름 새 번호", n("if (view.getBlock(x, y, z) == 15555) return;"), 1)
    expect("블록과 무관한 큰 수", n("if (count > 15555) return;"), 0)
    expect("실수 아님", n("double d = 12958.5; long l = 12958L;"), 0)
    expect("블록 주석 걸침", n("a = 1; /* 12958\n 12958 */ b = 2;"), 0)
    print("자체 시험: " + ("통과" if ok else "실패")); return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(self_test() if "--self-test" in sys.argv[1:] else run())
