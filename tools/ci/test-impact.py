#!/usr/bin/env python3
"""V-08 시험 영향 선택 — "바뀐 코드를 실제로 실행한 시험만" PR 에서 돌리기 위한 표 만들기·읽기·선택 도구(표준 라이브러리만).

세 하위 명령이 있다(전체 설명: docs/ci.md "시험 영향 선택").

  partial  시험 조각 하나의 원자료(TestImpactListener 가 남긴 build/test-impact/raw/*.imp)와 컴파일된 클래스 폴더를 읽어 "조각 표"를 쓴다.
  merge    조각 표들을 한 장의 영향 표(test-impact.tsv)로 합친다.
  select   영향 표와 PR 의 파일 차이로 "건너뛸 시험 클래스" 목록과 조각 수를 정한다(CI 의 select 작업).

영향 표 형식(탭 구분, UTF-8):
  #format<TAB>test-impact-1
  #commit<TAB><표를 만든 커밋>
  #generated<TAB><UTC 시각>
  F<TAB><번호><TAB><저장소 기준 소스 경로>            컴파일된 모든 소스 파일(실행되지 않은 것 포함)
  T<TAB><시험 클래스 a/b/FooTest><TAB>ok|unreliable<TAB><시험 소스 번호 또는 -1><TAB><실행한 소스 번호,…>

선택의 원칙(안전이 먼저다 — 틀리게 고르면 깨진 코드가 병합된다):
  1) 표를 만든 커밋 X 의 트리와 지금 검사하는 트리 HEAD 의 파일 차이 전체를 "바뀐 것"으로 본다. 이 PR 이 바꾼 것뿐 아니라 표를 만든 뒤 기준 브랜치에 들어온
     변경도 포함한다(그 변경이 시험의 실행 경로를 바꿨을 수 있다). 두 트리의 차이라서 병합 기준이 어디든 성립한다.
  2) 바뀐 소스 파일 → 그 파일을 실행한 시험 + 바뀐 시험 자신 + 표에 없는 시험(Gradle 이 건너뛰기 목록에 없는 모든 클래스를 돌린다).
  3) 표로 못 따지는 것(빌드·리소스·동시성 핵심·규칙에 없는 파일·표가 낡았음 등)이 하나라도 있으면 전체를 돈다. 오류가 나도 전체를 돈다.
규칙 파일: tools/ci/test-selection-rules.txt.
"""
import argparse
import datetime
import json
import math
import os
import re
import subprocess
import sys

FORMAT = "test-impact-1"
UNRELIABLE = "unreliable"
OK = "ok"


# --------------------------------------------------------------------------------------------
# 클래스 파일에서 SourceFile 읽기(JaCoCo 실행 데이터에는 소스 파일 이름이 없다)
# --------------------------------------------------------------------------------------------

def read_class_source(data):
    """(내부 클래스 이름, SourceFile 속성 또는 None). 클래스 파일 형식 JVMS §4."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise ValueError("클래스 파일이 아니다")
    pos = 8
    count = int.from_bytes(data[pos:pos + 2], "big")
    pos += 2
    utf8 = {}
    class_name_index = {}
    i = 1
    while i < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            n = int.from_bytes(data[pos:pos + 2], "big")
            utf8[i] = data[pos + 2:pos + 2 + n].decode("utf-8", "replace")
            pos += 2 + n
        elif tag == 7:
            class_name_index[i] = int.from_bytes(data[pos:pos + 2], "big")
            pos += 2
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        elif tag in (5, 6):
            pos += 8
            i += 1
        elif tag in (8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        else:
            raise ValueError("알 수 없는 상수 풀 태그 %d" % tag)
        i += 1
    pos += 2  # access flags
    this_name = utf8[class_name_index[int.from_bytes(data[pos:pos + 2], "big")]]
    pos += 4  # this, super
    n = int.from_bytes(data[pos:pos + 2], "big")
    pos += 2 + 2 * n  # interfaces
    for _ in range(2):  # fields, methods
        n = int.from_bytes(data[pos:pos + 2], "big")
        pos += 2
        for _ in range(n):
            pos += 6
            attrs = int.from_bytes(data[pos:pos + 2], "big")
            pos += 2
            for _ in range(attrs):
                pos += 2
                length = int.from_bytes(data[pos:pos + 4], "big")
                pos += 4 + length
    attrs = int.from_bytes(data[pos:pos + 2], "big")
    pos += 2
    for _ in range(attrs):
        name = utf8.get(int.from_bytes(data[pos:pos + 2], "big"))
        length = int.from_bytes(data[pos + 2:pos + 6], "big")
        pos += 6
        if name == "SourceFile":
            return this_name, utf8.get(int.from_bytes(data[pos:pos + 2], "big"))
        pos += length
    return this_name, None


def scan_classes(repo):
    """컴파일된 모든 클래스(<모듈>/build/classes/java/<소스셋>, 벤치 제외) → {내부 이름: 저장소 기준 소스 경로}. 디스크에 없는 소스는 뺀다."""
    index = {}
    for module in sorted(os.listdir(repo)):
        base = os.path.join(repo, module, "build", "classes", "java")
        if not os.path.isdir(base):
            continue
        for sourceset in sorted(os.listdir(base)):
            if sourceset == "bench":
                continue
            root = os.path.join(base, sourceset)
            for dirpath, _dirs, files in os.walk(root):
                for f in files:
                    if not f.endswith(".class"):
                        continue
                    try:
                        with open(os.path.join(dirpath, f), "rb") as fh:
                            name, source = read_class_source(fh.read())
                    except (ValueError, IndexError, KeyError):
                        continue
                    if not source:
                        continue
                    pkg = name.rsplit("/", 1)[0] + "/" if "/" in name else ""
                    rel = "%s/src/%s/java/%s%s" % (module, sourceset, pkg, source)
                    if os.path.isfile(os.path.join(repo, rel)):
                        index[name] = rel
    return index


# --------------------------------------------------------------------------------------------
# 표 읽기·쓰기
# --------------------------------------------------------------------------------------------

class ImpactMap:
    def __init__(self, commit="", generated=""):
        self.commit = commit
        self.generated = generated
        self.files = []          # 번호 → 경로
        self.index = {}          # 경로 → 번호
        self.tests = {}          # 시험 클래스 → (상태, 시험 소스 경로 또는 None, 실행 소스 경로 집합)

    def file_id(self, path):
        if path not in self.index:
            self.index[path] = len(self.files)
            self.files.append(path)
        return self.index[path]

    def add_test(self, name, status, src, executed):
        if name in self.tests:  # 같은 시험이 두 번 들어오면 믿을 수 없다(합집합으로 남기고 항상 돌린다)
            _s, s0, e0 = self.tests[name]
            status, executed, src = UNRELIABLE, set(executed) | e0, src or s0
        self.tests[name] = (status, src, set(executed))
        for p in executed:
            self.file_id(p)
        if src:
            self.file_id(src)

    def write(self, path):
        for name in self.tests:
            for p in self.tests[name][2]:
                self.file_id(p)
        lines = ["#format\t" + FORMAT, "#commit\t" + self.commit, "#generated\t" + self.generated]
        for i, p in enumerate(self.files):
            lines.append("F\t%d\t%s" % (i, p))
        for name in sorted(self.tests):
            status, src, executed = self.tests[name]
            ids = ",".join(str(i) for i in sorted(self.index[p] for p in executed))
            lines.append("T\t%s\t%s\t%d\t%s" % (name, status, self.index[src] if src else -1, ids))
        with open(path, "w", encoding="utf-8", newline="\n") as fh:
            fh.write("\n".join(lines) + "\n")

    @staticmethod
    def read(path):
        m = ImpactMap()
        header = {}
        files = {}
        raw_tests = []
        with open(path, encoding="utf-8") as fh:
            for line in fh.read().splitlines():
                if not line:
                    continue
                parts = line.split("\t")
                if line.startswith("#"):
                    header[parts[0][1:]] = parts[1] if len(parts) > 1 else ""
                elif parts[0] == "F" and len(parts) == 3:
                    files[int(parts[1])] = parts[2]
                elif parts[0] == "T" and len(parts) == 5:
                    raw_tests.append(parts)
                else:
                    raise ValueError("영향 표 줄을 읽을 수 없다: " + line[:80])
        if header.get("format") != FORMAT:
            raise ValueError("영향 표 형식이 다르다: %r" % header.get("format"))
        m.commit = header.get("commit", "")
        m.generated = header.get("generated", "")
        for i in sorted(files):
            if i != len(m.files):
                raise ValueError("영향 표 파일 번호가 이어지지 않는다")
            m.file_id(files[i])
        for _t, name, status, src, ids in raw_tests:
            executed = {m.files[int(x)] for x in ids.split(",") if x}
            m.tests[name] = (status, m.files[int(src)] if int(src) >= 0 else None, executed)
        return m


# --------------------------------------------------------------------------------------------
# partial · merge
# --------------------------------------------------------------------------------------------

def cmd_partial(args):
    index = scan_classes(args.repo)
    m = ImpactMap(args.commit or "", "")
    plans = 0
    for fname in sorted(os.listdir(args.raw)) if os.path.isdir(args.raw) else []:
        if not fname.endswith(".imp"):
            continue
        plans += 1
        tests, classes = [], []
        with open(os.path.join(args.raw, fname), encoding="utf-8") as fh:
            lines = fh.read().splitlines()
        if not lines or lines[0] != "V1":
            raise SystemExit("원자료 형식이 다르다: " + fname)
        for line in lines[1:]:
            parts = line.split("\t")
            if parts[0] == "T":
                tests.append((parts[1], parts[2]))
            elif parts[0] == "C":
                classes.append(parts[1])
        executed = {index[c] for c in classes if c in index}
        if len(tests) != 1:   # 한 JVM 에 시험이 둘 이상(리스너도 이렇게 적지만 여기서도 확인): 구간을 나눌 수 없다
            tests = [(n, UNRELIABLE) for n, _s in tests]
        for name, status in tests:
            src = index.get(name)
            m.add_test(name, status, src, executed)
    for p in sorted(set(index.values())):
        m.file_id(p)
    m.write(args.out)
    print("조각 표: 원자료 %d 개, 시험 %d 개, 소스 파일 %d 개 → %s" % (plans, len(m.tests), len(m.files), args.out))


def cmd_merge(args):
    out = ImpactMap(args.commit, args.generated or datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"))
    for path in args.parts:
        part = ImpactMap.read(path)
        for p in part.files:
            out.file_id(p)
        for name, (status, src, executed) in part.tests.items():
            out.add_test(name, status, src, executed)
    ok = sum(1 for t in out.tests.values() if t[0] == OK)
    if args.min_ok_ratio and (not out.tests or ok / len(out.tests) < args.min_ok_ratio):
        raise SystemExit("영향 표를 쓰지 않는다: 믿을 수 있는 시험이 %d/%d 개뿐이다(최소 %d%%) — 에이전트·시험 JVM 설정을 확인한다" % (
            ok, len(out.tests), round(args.min_ok_ratio * 100)))
    out.write(args.out)
    print("영향 표: 시험 %d 개, 소스 파일 %d 개, 믿을 수 없는 시험 %d 개 → %s" % (
        len(out.tests), len(out.files), sum(1 for t in out.tests.values() if t[0] != OK), args.out))


# --------------------------------------------------------------------------------------------
# 규칙 파일
# --------------------------------------------------------------------------------------------

def glob_regex(glob):
    """저장소 기준 경로용 글롭: ** 는 / 를 넘고, * 와 ? 는 / 를 넘지 않는다."""
    out = []
    i = 0
    while i < len(glob):
        c = glob[i]
        if glob.startswith("**/", i):
            out.append("(?:.*/)?")
            i += 3
        elif glob.startswith("**", i):
            out.append(".*")
            i += 2
        elif c == "*":
            out.append("[^/]*")
            i += 1
        elif c == "?":
            out.append("[^/]")
            i += 1
        else:
            out.append(re.escape(c))
            i += 1
    return re.compile("^" + "".join(out) + "$")


class Rules:
    def __init__(self):
        self.paths = []      # (종류, 정규식, 글롭, 인자)
        self.always = []     # 항상 돌릴 시험 클래스(a/b/FooTest)

    @staticmethod
    def read(path):
        r = Rules()
        with open(path, encoding="utf-8") as fh:
            for n, line in enumerate(fh, 1):
                line = line.strip()
                if not line or line.startswith("#"):
                    continue
                parts = line.split(None, 2)
                kind = parts[0]
                if kind == "always" and len(parts) == 2:
                    r.always.append(parts[1])
                elif kind in ("full", "ignore") and len(parts) >= 2:
                    r.paths.append((kind, glob_regex(parts[1]), parts[1], None))
                elif kind == "reads" and len(parts) == 3:
                    r.paths.append((kind, glob_regex(parts[1]), parts[1], re.compile(parts[2])))
                else:
                    raise ValueError("%s:%d 규칙을 읽을 수 없다: %s" % (path, n, line))
        return r

    def match(self, path):
        for rule in self.paths:
            if rule[1].match(path):
                return rule
        return None


# --------------------------------------------------------------------------------------------
# 소스 읽기 도우미
# --------------------------------------------------------------------------------------------

JAVA_PATH = re.compile(r"^([^/]+)/src/([^/]+)/java/(.+)\.java$")
FINAL_FIELD = re.compile(r"\bfinal\b[^=;(){}]*?\b([A-Za-z_$][\w$]*)\s*=(?!=)")
INTERFACE_FIELD = re.compile(r"^\s*(?:(?:public|static|final)\s+)*[\w.<>\[\],?]+(?:\s*\[\])*\s+([A-Z][A-Z0-9_]*)\s*=(?!=)")


class Sources:
    """저장소의 모든 자바 소스(<모듈>/src/<소스셋>/java)를 한 번 읽어 둔다."""

    def __init__(self, repo):
        self.repo = repo
        self.text = {}
        for module in sorted(os.listdir(repo)):
            src = os.path.join(repo, module, "src")
            if not os.path.isdir(src):
                continue
            for dirpath, _dirs, files in os.walk(src):
                for f in files:
                    if f.endswith(".java"):
                        full = os.path.join(dirpath, f)
                        rel = os.path.relpath(full, repo).replace(os.sep, "/")
                        with open(full, encoding="utf-8", errors="replace") as fh:
                            self.text[rel] = fh.read()

    def referrers(self, word, exclude):
        pat = re.compile(r"(?<![\w$])" + re.escape(word) + r"(?![\w$])")
        return {p for p, t in self.text.items() if p != exclude and pat.search(t)}

    def matching(self, regex):
        return {p for p, t in self.text.items() if regex.search(t)}


def run_git(repo, *args):
    return subprocess.run(["git", "-C", repo] + list(args), check=True, capture_output=True).stdout


def changed_files(repo, base, head):
    """[(상태, 경로)] — 이름 바꾸기는 삭제+추가로 푼다."""
    out = run_git(repo, "diff", "--name-status", "--no-renames", "-z", base, head).decode("utf-8", "replace")
    tokens = out.split("\0")
    res = []
    i = 0
    while i + 1 < len(tokens):
        if tokens[i]:
            res.append((tokens[i][0], tokens[i + 1]))
        i += 2
    return res


def constant_names(line, is_interface):
    """한 줄의 상수 선언 이름들. `static final int A = 1, B = 2;` 처럼 한 줄에 여럿이면 모두(둘째부터는 초기화 식 안의 쉼표도 이름으로 보는 쪽으로 넉넉히 센다)."""
    names = set()
    m = FINAL_FIELD.search(line)
    if m and not re.search(r"\bprivate\b", line):
        names.add(m.group(1))
        names.update(re.findall(r",\s*([A-Za-z_$][\w$]*)\s*=(?!=)", line[m.end():]))
    if is_interface:
        m = INTERFACE_FIELD.match(line)
        if m:
            names.add(m.group(1))
            names.update(re.findall(r",\s*([A-Z][A-Z0-9_]*)\s*=(?!=)", line[m.end():]))
    return names


def changed_constants(repo, base, head, path, is_interface):
    """이 파일의 바뀐 줄(추가·삭제)에서 상수 선언 이름을 모은다. javac 는 컴파일 시간 상수를 쓰는 쪽에 값으로 박아 넣으므로 쓰는 파일이 안 바뀌어도 동작이 바뀐다."""
    diff = run_git(repo, "diff", "-U0", "--no-renames", base, head, "--", path).decode("utf-8", "replace")
    names = set()
    for line in diff.splitlines():
        if line.startswith(("+++", "---")) or not line or line[0] not in "+-":
            continue
        names |= constant_names(line[1:], is_interface)
    return names


# --------------------------------------------------------------------------------------------
# select
# --------------------------------------------------------------------------------------------

class FullRun(Exception):
    """전체를 돌려야 한다는 신호(이유가 메시지)."""


def read_times(path):
    times = {}
    if path and os.path.isfile(path):
        with open(path, encoding="utf-8") as fh:
            for line in fh:
                if line.strip() and not line.startswith("#"):
                    name, sec = line.rstrip("\n").split("\t")
                    times[name] = float(sec)
    return times


def parse_time(s):
    return datetime.datetime.strptime(s, "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=datetime.timezone.utc)


def select(repo, impact, rules, head, times, opts):
    """선택 결과(dict). 전체를 돌려야 하면 FullRun 을 올린다."""
    if opts.get("force_full"):
        raise FullRun(opts["force_full"])
    if not impact.commit:
        raise FullRun("영향 표에 커밋이 없다")
    now = opts.get("now") or datetime.datetime.now(datetime.timezone.utc)
    try:
        age = now - parse_time(impact.generated)
    except ValueError:
        raise FullRun("영향 표 생성 시각을 읽을 수 없다")
    if age > datetime.timedelta(days=opts["max_age_days"]):
        raise FullRun("영향 표가 %.1f 일 지났다(한도 %d 일)" % (age.total_seconds() / 86400, opts["max_age_days"]))
    if subprocess.run(["git", "-C", repo, "cat-file", "-e", impact.commit + "^{commit}"], capture_output=True).returncode != 0:
        raise FullRun("영향 표의 커밋 %s 가 이 저장소 기록에 없다" % impact.commit[:10])

    diff = changed_files(repo, impact.commit, head)
    if len(diff) > opts["max_files"]:
        raise FullRun("영향 표 이후 바뀐 파일이 %d 개(한도 %d)" % (len(diff), opts["max_files"]))
    sources = Sources(repo)

    effective = {}     # 바뀐 것으로 보는 소스 파일 → 이유
    force_tests = set()
    package_dirs = {}  # 새 소스 파일의 패키지 폴더(시험 클래스 이름 앞부분) → 파일
    notes = []
    pending_constants = []

    def eff(path, why):
        effective.setdefault(path, why)

    for status, path in diff:
        rule = rules.match(path)
        if rule is not None:
            kind, _rx, glob, arg = rule
            if kind == "full":
                raise FullRun("%s 은(는) 전체 시험 대상이다(규칙 `full %s`)" % (path, glob))
            if kind == "ignore":
                continue
            if kind == "reads":
                readers = sources.matching(arg)
                if not readers:
                    raise FullRun("%s 을(를) 읽는 시험을 규칙 `reads %s` 로 찾지 못했다(규칙이 낡음)" % (path, glob))
                for r in readers:
                    eff(r, "%s 을(를) 읽음" % path)
                continue
        m = JAVA_PATH.match(path)
        if m is None:
            raise FullRun("%s 은(는) 어느 규칙에도 없는 파일이다" % path)
        if path in impact.index:
            eff(path, "바뀜(%s)" % status)
            if status in ("M", "D"):
                pending_constants.append((status, path))
        else:
            # 영향 표가 모르는 소스: 새 파일이거나 표가 안 훑은 소스셋. 이 파일이 낳는 동작은 이를 쓰는 바뀐 파일·바뀐 시험으로 잡히고,
            # 반사·등록으로만 닿는 경우를 위해 같은 패키지의 시험 클래스도 돌린다.
            # (시험 소스셋의 새 파일은 시험이거나 시험 도우미다: 시험은 표에 없어 저절로 돌고, 도우미는 그것을 쓰는 바뀐 시험으로 잡힌다)
            if m.group(2) != "test":
                pkg = m.group(3).rsplit("/", 1)[0] if "/" in m.group(3) else ""
                package_dirs.setdefault(pkg, path)

    # 컴파일 시간 상수: 값이 쓰는 쪽에 박히므로 이름을 쓰는 파일도 바뀐 것으로 본다(상수끼리 이어지면 끝까지).
    visited = set()
    work = []
    for status, path in pending_constants:
        is_interface = bool(re.search(r"(?<![\w$])@?interface\s+[A-Za-z_]", sources.text.get(path, "")))
        for n in changed_constants(repo, impact.commit, head, path, is_interface):
            work.append((n, path))
    while work:
        name, origin = work.pop()
        if name in visited:
            continue
        visited.add(name)
        if len(visited) > 60:
            raise FullRun("바뀐 상수 이름이 60 개를 넘는다")
        refs = sources.referrers(name, origin)
        if len(refs) > opts["max_referrers"]:
            raise FullRun("상수 `%s` 를 쓰는 파일이 %d 개(한도 %d)" % (name, len(refs), opts["max_referrers"]))
        for r in refs:
            eff(r, "상수 `%s` 를 씀(컴파일할 때 값으로 박힘)" % name)
            for line in sources.text[r].splitlines():
                if name in line:
                    for other in constant_names(line, False):
                        if other != name:
                            work.append((other, r))

    # 어떤 시험도 실행하지 않은 소스(상수·인터페이스·기록 클래스 등)는 그 타입을 쓰는 파일로 대신한다.
    executed_by = {}
    for tname, (_st, _src, ex) in impact.tests.items():
        for p in ex:
            executed_by.setdefault(p, set()).add(tname)
    queue = [(p, 0) for p in list(effective)]
    seen_uncovered = set()
    while queue:
        p, depth = queue.pop()
        if p in executed_by or p not in impact.index or p in seen_uncovered:
            continue
        if any(src == p for _s, src, _e in impact.tests.values()):
            continue  # 시험 클래스 파일(시험이 따로 선택된다)
        seen_uncovered.add(p)
        simple = p.rsplit("/", 1)[1][:-5]
        refs = sources.referrers(simple, p) if simple not in ("package-info", "module-info") else set()
        if not refs:
            pkg = JAVA_PATH.match(p).group(3).rsplit("/", 1)[0] if "/" in JAVA_PATH.match(p).group(3) else ""
            package_dirs.setdefault(pkg, p)
            continue
        if depth >= 3:
            raise FullRun("실행된 적 없는 소스 %s 의 사용처를 3 단계까지 따라도 끝나지 않았다" % p)
        if len(refs) > opts["max_referrers"]:
            raise FullRun("실행된 적 없는 소스 %s 를 쓰는 파일이 %d 개" % (p, len(refs)))
        for r in refs:
            if r not in effective:
                eff(r, "%s(실행된 적 없는 타입)를 씀" % p)
            queue.append((r, depth + 1))

    selected = set()
    reasons = {}
    for p, why in effective.items():
        ts = executed_by.get(p, set())
        for t in ts:
            selected.add(t)
        reasons[p] = (why, len(ts))
    for tname, (status, src, _ex) in impact.tests.items():
        if status != OK:
            selected.add(tname)
            force_tests.add(tname)
        if src and src in effective:
            selected.add(tname)
    for pkg, origin in package_dirs.items():
        for tname in impact.tests:
            if tname.rsplit("/", 1)[0] == pkg:
                selected.add(tname)
    for a in rules.always:
        if a in impact.tests:
            selected.add(a)

    # 새 시험 파일(표에 없음)은 건너뛰기 목록에 안 들어가므로 저절로 돈다 — 시간 추정에만 평균값으로 넣는다.
    new_tests = sum(1 for s, p in diff if s == "A" and re.search(r"/src/test/java/.*Test\w*\.java$", p))
    known_times = [times[t] for t in impact.tests if t in times]
    avg = (sum(known_times) / len(known_times)) if known_times else 5.0
    total = sum(times.get(t, avg) for t in impact.tests)
    est = sum(times.get(t, avg) for t in selected) + new_tests * avg
    skip = sorted(t for t in impact.tests if t not in selected)
    ratio = est / total if total else 1.0
    if not skip:
        raise FullRun("건너뛸 시험이 없다(선택이 전체)")
    if ratio >= opts["full_ratio"]:
        raise FullRun("선택분이 전체의 %d%% 라 나누는 이득이 없다 — 전체를 돈다" % round(ratio * 100))
    shards = max(1, min(opts["max_shards"], math.ceil(est / opts["shard_seconds"])))
    return {
        "mode": "selected", "shards": shards, "skip": skip, "selected": sorted(selected), "forced": sorted(force_tests),
        "estimateSeconds": round(est), "totalSeconds": round(total), "ratio": round(ratio, 3),
        "mapCommit": impact.commit, "mapGenerated": impact.generated, "diffFiles": len(diff),
        "reasons": {p: [w, n] for p, (w, n) in sorted(reasons.items())},
        "packageDirs": sorted(package_dirs), "notes": notes,
    }


def write_outputs(result, args, full_shards):
    os.makedirs(args.out_dir, exist_ok=True)
    skip = result.get("skip", []) if result["mode"] == "selected" else []
    with open(os.path.join(args.out_dir, "skip-tests.txt"), "w", encoding="utf-8", newline="\n") as fh:
        fh.write("# 이번 변경과 상관없다고 판단한 시험 클래스(-PtestSkip). 이 목록에 없는 시험은 모두 돈다.\n")
        for t in skip:
            fh.write(t + "\n")
    shards = result["shards"] if result["mode"] == "selected" else full_shards
    result = dict(result, shards=shards)
    with open(os.path.join(args.out_dir, "selection.json"), "w", encoding="utf-8") as fh:
        json.dump(result, fh, ensure_ascii=False, indent=1)
    lines = ["## 시험 영향 선택", ""]
    if result["mode"] == "full":
        lines += ["**전체 시험을 돈다** (%d 조각). 이유: %s" % (shards, result["reason"]), ""]
    else:
        lines += [
            "**선택 실행**: 서버 시험 클래스 %d 개 중 %d 개를 건너뛰고 %d 조각으로 돈다(추정 %d 초 ≤ 전체 %d 초의 %d%%)." % (
                len(result["skip"]) + len(result["selected"]), len(result["skip"]), shards, result["estimateSeconds"],
                result["totalSeconds"], round(result["ratio"] * 100)),
            "", "영향 표: 커밋 `%s`, %s 생성. 그 이후 바뀐 파일 %d 개를 모두 바뀐 것으로 봤다." % (
                result["mapCommit"][:10], result["mapGenerated"], result["diffFiles"]),
            "항상 도는 시험·표에 없는(새) 시험·믿을 수 없는 시험 %d 개는 건너뛰지 않는다." % len(result["forced"]), "",
            "| 바뀐 소스(또는 그렇게 본 이유) | 이유 | 그것을 실행한 시험 |", "|---|---|---|",
        ]
        for p, (why, n) in list(result["reasons"].items())[:60]:
            lines.append("| `%s` | %s | %d |" % (p, why, n))
        if len(result["reasons"]) > 60:
            lines.append("| … %d 개 더 | | |" % (len(result["reasons"]) - 60))
        lines += ["", "<details><summary>도는 시험 %d 개</summary>" % len(result["selected"]), ""]
        lines += ["- `%s`" % t for t in result["selected"]]
        lines += ["", "</details>", ""]
    text = "\n".join(lines)
    with open(os.path.join(args.out_dir, "summary.md"), "w", encoding="utf-8") as fh:
        fh.write(text)
    print(text)
    if args.github_output:
        with open(args.github_output, "a", encoding="utf-8") as fh:
            fh.write("mode=%s\nshards=%d\nmatrix=%s\n" % (result["mode"], shards, json.dumps(list(range(1, shards + 1)))))
            fh.write("reason=%s\n" % (result.get("reason", "") or "선택 실행").replace("\n", " "))
    if args.github_summary:
        with open(args.github_summary, "a", encoding="utf-8") as fh:
            fh.write(text + "\n")


def cmd_select(args):
    full_shards = args.max_shards
    try:
        if args.event != "pull_request":
            raise FullRun("%s 실행은 전체를 돈다" % args.event)
        if not args.map or not os.path.isfile(args.map):
            raise FullRun("영향 표가 없다")
        impact = ImpactMap.read(args.map)
        rules = Rules.read(args.rules)
        times = read_times(args.times)
        opts = {
            "max_age_days": args.max_age_days, "max_files": args.max_files, "max_referrers": args.max_referrers,
            "full_ratio": args.full_ratio, "max_shards": args.max_shards, "shard_seconds": args.shard_seconds,
            "force_full": args.force_full,
        }
        result = select(args.repo, impact, rules, args.head, times, opts)
    except FullRun as e:
        result = {"mode": "full", "reason": str(e), "shards": full_shards}
    except Exception as e:  # 도구 오류는 언제나 전체 실행으로 끝난다(안전한 쪽)
        result = {"mode": "full", "reason": "선택 도구 오류 %s: %s — 전체를 돈다" % (type(e).__name__, e), "shards": full_shards}
    write_outputs(result, args, full_shards)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)

    p = sub.add_parser("partial", help="원자료 → 조각 표")
    p.add_argument("--raw", required=True)
    p.add_argument("--repo", default=".")
    p.add_argument("--commit", default="")
    p.add_argument("--out", required=True)
    p.set_defaults(fn=cmd_partial)

    p = sub.add_parser("merge", help="조각 표 → 영향 표")
    p.add_argument("parts", nargs="+")
    p.add_argument("--commit", required=True)
    p.add_argument("--generated", default="")
    p.add_argument("--min-ok-ratio", type=float, default=0.0, help="믿을 수 있는(ok) 시험 비율이 이보다 낮으면 표를 쓰지 않는다")
    p.add_argument("--out", required=True)
    p.set_defaults(fn=cmd_merge)

    p = sub.add_parser("select", help="영향 표 + 파일 차이 → 건너뛸 시험")
    p.add_argument("--map", default="")
    p.add_argument("--repo", default=".")
    p.add_argument("--head", default="HEAD")
    p.add_argument("--rules", default="tools/ci/test-selection-rules.txt")
    p.add_argument("--times", default="furnace-server/test-shard-times.tsv")
    p.add_argument("--out-dir", required=True)
    p.add_argument("--event", default="pull_request")
    p.add_argument("--force-full", default="")
    p.add_argument("--max-shards", type=int, default=4)
    p.add_argument("--shard-seconds", type=int, default=300, help="조각 하나에 맡길 시험 시간 목표(초)")
    p.add_argument("--max-age-days", type=int, default=7)
    p.add_argument("--max-files", type=int, default=400, help="영향 표 이후 바뀐 파일 수 한도")
    p.add_argument("--max-referrers", type=int, default=250)
    p.add_argument("--full-ratio", type=float, default=0.7, help="선택분이 전체 시간의 이 비율 이상이면 그냥 전체")
    p.add_argument("--github-output", default=os.environ.get("GITHUB_OUTPUT", ""))
    p.add_argument("--github-summary", default=os.environ.get("GITHUB_STEP_SUMMARY", ""))
    p.set_defaults(fn=cmd_select)

    args = ap.parse_args()
    args.fn(args)


if __name__ == "__main__":
    sys.exit(main())
