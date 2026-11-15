#!/usr/bin/env python3
"""tools/ci/test-impact.py 시험(표준 라이브러리 unittest). CI light 작업이 `python3 tools/ci/test_test_impact.py` 로 돌린다.

선택 도구는 "잘못 고르면 깨진 코드가 병합되는" 자리라서, 안전 쪽 동작(전체로 넘어가는 경우)을 사례로 고정한다.
고르기가 놓친 것이 발견되면 여기에 그 사례를 추가한다(docs/ci.md).
"""
import datetime
import importlib.util
import json
import os
import re
import shutil
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(os.path.dirname(HERE))
_spec = importlib.util.spec_from_file_location("test_impact", os.path.join(HERE, "test-impact.py"))
ti = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ti)

MAIN = "furnace-server/src/main/java/p/"
TEST = "furnace-server/src/test/java/p/"
NOW = datetime.datetime(2026, 11, 16, 12, 0, tzinfo=datetime.timezone.utc)
OPTS = {"max_age_days": 7, "max_files": 400, "max_referrers": 250, "full_ratio": 0.7, "max_shards": 4,
        "shard_seconds": 300, "force_full": "", "now": NOW}


def git(repo, *args):
    subprocess.run(["git", "-C", repo, "-c", "user.name=t", "-c", "user.email=t@t", "-c", "commit.gpgsign=false"] + list(args),
                   check=True, capture_output=True)


def head_sha(repo):
    return subprocess.run(["git", "-C", repo, "rev-parse", "HEAD"], check=True, capture_output=True, text=True).stdout.strip()


class Fixture:
    """A,B,C 세 클래스와 시험 세 개가 있는 작은 저장소. 표는 손으로 만든다(ATest 는 A 만, BTest 는 B 만, CTest 는 C 와 A 를 실행)."""

    def __init__(self, test_case):
        self.dir = tempfile.mkdtemp(prefix="impact-test-")
        test_case.addCleanup(shutil.rmtree, self.dir, True)
        self.write("settings.gradle.kts", "x")
        for n in "ABC":
            self.write(MAIN + n + ".java", "package p;\npublic class %s {\n    void run() {}\n}\n" % n)
            self.write(TEST + n + "Test.java", "package p;\nclass %sTest {}\n" % n)
        self.write("furnace-server/src/main/resources/data.txt", "1\n")
        self.write("docs/a.md", "x\n")
        git(self.dir, "init", "-q")
        git(self.dir, "add", "-A")
        git(self.dir, "commit", "-q", "-m", "base")
        self.base = head_sha(self.dir)
        m = ti.ImpactMap(self.base, "2026-11-16T03:00:00Z")
        for n in "ABC":
            m.file_id(MAIN + n + ".java")
            m.file_id(TEST + n + "Test.java")
        m.add_test("p/ATest", "ok", TEST + "ATest.java", {MAIN + "A.java", TEST + "ATest.java"})
        m.add_test("p/BTest", "ok", TEST + "BTest.java", {MAIN + "B.java", TEST + "BTest.java"})
        m.add_test("p/CTest", "ok", TEST + "CTest.java", {MAIN + "C.java", MAIN + "A.java", TEST + "CTest.java"})
        self.map = m
        self.rules = ti.Rules.read(os.path.join(HERE, "test-selection-rules.txt"))
        self.times = {"p/ATest": 100.0, "p/BTest": 100.0, "p/CTest": 100.0, "p/DTest": 100.0, "p/ETest": 100.0}

    def write(self, rel, text):
        path = os.path.join(self.dir, rel)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as fh:
            fh.write(text)

    def commit(self, msg="change"):
        git(self.dir, "add", "-A")
        git(self.dir, "commit", "-q", "-m", msg)

    def select(self, **over):
        opts = dict(OPTS)
        opts.update(over)
        return ti.select(self.dir, self.map, self.rules, "HEAD", self.times, opts)


class ClassFileTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("javac"), "javac 없음")
    def test_source_file_of_nested_and_anonymous_classes(self):
        d = tempfile.mkdtemp(prefix="impact-cls-")
        self.addCleanup(shutil.rmtree, d, True)
        os.makedirs(os.path.join(d, "src", "a", "b"))
        with open(os.path.join(d, "src", "a", "b", "Foo.java"), "w") as fh:
            fh.write("package a.b; public class Foo { class In {} Object o = new Object() {}; static final long L = 5L; static final double D = 1.5; }\n"
                     "class Other {}\n")
        subprocess.run(["javac", "-d", os.path.join(d, "out"), os.path.join(d, "src", "a", "b", "Foo.java")], check=True, capture_output=True)
        found = {}
        for dirpath, _dirs, files in os.walk(os.path.join(d, "out")):
            for f in files:
                with open(os.path.join(dirpath, f), "rb") as fh:
                    name, src = ti.read_class_source(fh.read())
                found[name] = src
        self.assertEqual({"a/b/Foo": "Foo.java", "a/b/Foo$In": "Foo.java", "a/b/Foo$1": "Foo.java", "a/b/Other": "Foo.java"}, found)

    def test_not_a_class_file(self):
        with self.assertRaises(ValueError):
            ti.read_class_source(b"nope")


class MapFormatTest(unittest.TestCase):
    def test_roundtrip_and_duplicate_test_becomes_unreliable(self):
        m = ti.ImpactMap("abc", "2026-11-16T00:00:00Z")
        m.add_test("p/ATest", "ok", "x/ATest.java", {"x/A.java"})
        m.add_test("p/ATest", "ok", "x/ATest.java", {"x/B.java"})   # 같은 시험이 두 번: 믿을 수 없음
        m.add_test("p/Solo", "unreliable", None, set())
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d, True)
        path = os.path.join(d, "m.tsv")
        m.write(path)
        r = ti.ImpactMap.read(path)
        self.assertEqual("abc", r.commit)
        self.assertEqual(("unreliable", "x/ATest.java", {"x/A.java", "x/B.java"}), r.tests["p/ATest"])
        self.assertEqual(("unreliable", None, set()), r.tests["p/Solo"])

    def test_wrong_format_is_rejected(self):
        d = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, d, True)
        path = os.path.join(d, "m.tsv")
        with open(path, "w") as fh:
            fh.write("#format\tother\n")
        with self.assertRaises(ValueError):
            ti.ImpactMap.read(path)


class GlobTest(unittest.TestCase):
    def test_glob_semantics(self):
        g = ti.glob_regex
        self.assertTrue(g("docs/**").match("docs/a/b.md"))
        self.assertTrue(g("*.md").match("README.md"))
        self.assertFalse(g("*.md").match("sub/README.md"))
        self.assertTrue(g("**/*.kts").match("build.gradle.kts"))
        self.assertTrue(g("**/*.kts").match("furnace-server/build.gradle.kts"))
        self.assertTrue(g("**/src/*/resources/**").match("furnace-server/src/test/resources/a/b.bin"))
        self.assertFalse(g("tools/ci/**").match("tools/cix/a"))


class SelectTest(unittest.TestCase):
    def test_small_change_selects_only_executing_tests(self):
        f = Fixture(self)
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    void run() { int x = 1; }\n}\n")
        f.commit()
        r = f.select()
        self.assertEqual(["p/ATest", "p/CTest"], r["skip"])        # B 를 안 쓰는 시험은 건너뜀
        self.assertEqual(["p/BTest"], r["selected"])
        self.assertEqual(1, r["shards"])

    def test_change_to_shared_class_selects_all_who_ran_it(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    void run() { int x = 1; }\n}\n")
        f.commit()
        r = f.select()
        self.assertEqual(["p/BTest"], r["skip"])
        self.assertEqual(["p/ATest", "p/CTest"], r["selected"])

    def test_changes_merged_after_the_map_count_too(self):
        # 표를 만든 뒤 기준 브랜치에 들어온 B 변경(이 PR 이 아님)이 있으면 BTest 도 고른다 — 그 변경이 BTest 의 실행 경로를 바꿨을 수 있다
        f = Fixture(self)
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    void run() { int merged = 1; }\n}\n")
        f.commit("merged by someone else")
        f.write(MAIN + "C.java", "package p;\npublic class C {\n    void run() { int mine = 1; }\n}\n")
        f.commit("this pr")
        r = f.select()
        self.assertEqual(["p/ATest"], r["skip"])
        self.assertEqual(["p/BTest", "p/CTest"], r["selected"])

    def test_changed_test_runs_itself(self):
        f = Fixture(self)
        f.write(TEST + "BTest.java", "package p;\nclass BTest { /* edit */ }\n")
        f.commit()
        self.assertEqual(["p/BTest"], f.select()["selected"])

    def test_new_tests_are_never_skipped(self):
        f = Fixture(self)
        f.write(TEST + "DTest.java", "package p;\nclass DTest {}\n")
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        r = f.select()
        self.assertNotIn("p/DTest", r["skip"])           # 표에 없으므로 건너뛰기 목록에 안 들어간다 → Gradle 이 돌린다
        self.assertIn("p/DTest", f.times)               # (시간 추정에 새 시험 몫이 들어감)
        self.assertIn("p/CTest", r["skip"])

    def test_unreliable_tests_always_run(self):
        f = Fixture(self)
        s, src, ex = f.map.tests["p/CTest"]
        f.map.tests["p/CTest"] = ("unreliable", src, ex)
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        r = f.select()
        self.assertEqual(["p/ATest"], r["skip"])
        self.assertIn("p/CTest", r["forced"])

    def test_always_tests_run(self):
        f = Fixture(self)
        f.rules.always.append("p/ATest")
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertEqual(["p/CTest"], f.select()["skip"])

    def test_new_main_class_runs_same_package_tests(self):
        f = Fixture(self)
        f.map.add_test("q/QTest", "ok", "furnace-server/src/test/java/q/QTest.java", {"furnace-server/src/test/java/q/QTest.java"})
        f.times["q/QTest"] = 100.0
        f.write("furnace-server/src/main/java/q/NewThing.java", "package q;\npublic class NewThing {}\n")
        f.commit()
        r = f.select()
        self.assertEqual(["q/QTest"], r["selected"])     # 새 클래스는 어느 시험도 아직 실행하지 않았다 → 같은 패키지의 시험을 돈다
        self.assertEqual(["p/ATest", "p/BTest", "p/CTest"], r["skip"])

    def test_deleted_class_selects_its_tests(self):
        f = Fixture(self)
        os.remove(os.path.join(f.dir, MAIN + "B.java"))
        f.commit()
        self.assertEqual(["p/BTest"], f.select()["selected"])

    def test_constant_inlining_reaches_users_that_did_not_change(self):
        # A 의 공개 상수를 바꾸면 값이 B 에 박혀 있으므로 B 를 실행한 BTest 도 돈다(B 소스는 안 바뀌었다). A→ATest·CTest + B→BTest = 전부 → 전체
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 1;\n    void run() {}\n}\n")
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    void run() { int x = A.LIMIT; }\n}\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 2;\n    void run() {}\n}\n")
        f.commit()
        with self.assertRaises(ti.FullRun) as cm:
            f.select()
        self.assertIn("건너뛸 시험이 없다", str(cm.exception))

    def test_non_constant_change_does_not_reach_users(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 1;\n    void run() {}\n}\n")
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    void run() { int x = A.LIMIT; }\n}\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 1;\n    void run() { int y; }\n}\n")
        f.commit()
        self.assertEqual(["p/ATest", "p/CTest"], f.select()["selected"])

    def test_private_constant_does_not_expand(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    private static final int LIMIT = 1;\n    void run() {}\n}\n")
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    void run() { int LIMIT = 5; }\n}\n")   # 같은 이름이 B 에도 있다
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    private static final int LIMIT = 2;\n    void run() {}\n}\n")
        f.commit()
        r = f.select()
        self.assertEqual(["p/BTest"], r["skip"])         # private 상수는 같은 파일 안에서만 박힌다 — 사용처 확장 없음

    def test_constant_user_selected_even_if_it_never_runs_the_holder(self):
        f = Fixture(self)
        f.write(MAIN + "Holder.java", "package p;\npublic final class Holder {\n    public static final int N = 1;\n}\n")
        f.write(MAIN + "User.java", "package p;\npublic class User {\n    int f() { return Holder.N; }\n}\n")
        f.write(TEST + "UserTest.java", "package p;\nclass UserTest {}\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.map.add_test("p/UserTest", "ok", TEST + "UserTest.java", {MAIN + "User.java", TEST + "UserTest.java"})
        f.map.file_id(MAIN + "Holder.java"); f.map.file_id(MAIN + "User.java")
        f.times["p/UserTest"] = 50.0
        f.write(MAIN + "Holder.java", "package p;\npublic final class Holder {\n    public static final int N = 2;\n}\n")
        f.commit()
        r = f.select()
        self.assertIn("p/UserTest", r["selected"])      # Holder 는 어떤 시험도 실행하지 않았지만 N 을 쓰는 User 를 실행한 시험이 돈다
        self.assertEqual(["p/ATest", "p/BTest", "p/CTest"], r["skip"])

    def test_type_never_executed_falls_back_to_its_users(self):
        f = Fixture(self)
        f.write(MAIN + "Shape.java", "package p;\npublic interface Shape { int sides(); }\n")
        f.write(MAIN + "B.java", "package p;\npublic class B {\n    Shape s;\n    void run() {}\n}\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.map.file_id(MAIN + "Shape.java")
        f.write(MAIN + "Shape.java", "package p;\npublic interface Shape { long sides(); }\n")
        f.commit()
        r = f.select()
        self.assertEqual(["p/BTest"], r["selected"])    # Shape 를 쓰는 B 를 실행한 시험


class FullRunTest(unittest.TestCase):
    def assertFull(self, f, needle, **over):
        with self.assertRaises(ti.FullRun) as cm:
            f.select(**over)
        self.assertIn(needle, str(cm.exception))

    def test_everything_selected_is_full(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A { void run() { int x; } }\n")
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertFull(f, "건너뛸 시험이 없다")

    def test_large_selection_is_full(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A { void run() { int x; } }\n")
        f.commit()
        self.assertFull(f, "나누는 이득이 없다", full_ratio=0.5)   # 선택 ATest·CTest = 40%... 비율 한도를 낮춰 확인

    def test_resource_change_is_full(self):
        f = Fixture(self)
        f.write("furnace-server/src/main/resources/data.txt", "2\n")
        f.commit()
        self.assertFull(f, "전체 시험 대상")

    def test_test_resource_change_is_full(self):
        f = Fixture(self)
        f.write("furnace-server/src/test/resources/golden/x.bin", "2\n")
        f.commit()
        self.assertFull(f, "전체 시험 대상")

    def test_build_and_ci_files_are_full(self):
        for path in (".github/workflows/ci.yml", "build.gradle.kts", "furnace-server/build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties",
                     "tools/ci/test-impact.py", "gradle.properties"):
            f = Fixture(self)
            f.write(path, "x\n")
            f.commit()
            self.assertFull(f, "전체 시험 대상")

    def test_concurrency_core_is_full(self):
        for path in ("furnace-server/src/main/java/org/domi/furnace/server/sched/GlobalThread.java",
                     "furnace-server/src/main/java/org/domi/furnace/server/world/Regionizer.java",
                     "furnace-server/src/main/java/org/domi/furnace/server/world/ChunkColumn.java"):
            f = Fixture(self)
            f.write(path, "package x;\nclass X {}\n")
            f.commit()
            self.assertFull(f, "전체 시험 대상")

    def test_unknown_file_is_full(self):
        f = Fixture(self)
        f.write("some-new-folder/thing.dat", "x")
        f.commit()
        self.assertFull(f, "어느 규칙에도 없는 파일")

    def test_docs_only_ignored(self):
        f = Fixture(self)
        f.write("docs/a.md", "changed\n")
        f.write("docs/feature-parity/context-notes/n.md", "n\n")
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertEqual(["p/BTest"], f.select()["selected"])

    def test_reads_rule_without_a_reader_is_full(self):
        f = Fixture(self)
        f.write("docs/api/x.md", "x\n")
        f.commit()
        self.assertFull(f, "규칙이 낡음")

    def test_reads_rule_selects_readers(self):
        f = Fixture(self)
        f.write(TEST + "ATest.java", "package p;\nclass ATest { java.nio.file.Path p = java.nio.file.Path.of(\"..\", \"docs\", \"api\", \"c.md\"); }\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.write("docs/api/c.md", "changed\n")
        f.commit()
        self.assertEqual(["p/ATest"], f.select()["selected"])

    def test_map_problems_are_full(self):
        f = Fixture(self)
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertFull(f, "일 지났다", now=NOW + datetime.timedelta(days=30))
        f.map.commit = "0" * 40
        self.assertFull(f, "기록에 없다")
        f.map.commit = ""
        self.assertFull(f, "커밋이 없다")

    def test_too_many_changed_files_is_full(self):
        f = Fixture(self)
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertFull(f, "한도", max_files=0)

    def test_force_full(self):
        f = Fixture(self)
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        self.assertFull(f, "라벨", force_full="라벨 full-tests")

    def test_constant_with_too_many_users_is_full(self):
        f = Fixture(self)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 1;\n}\n")
        git(f.dir, "add", "-A"); git(f.dir, "commit", "-q", "-m", "setup")
        f.map.commit = head_sha(f.dir)
        f.write(MAIN + "A.java", "package p;\npublic class A {\n    public static final int LIMIT = 2;\n}\n")
        f.commit()
        f.write(MAIN + "B.java", "package p;\npublic class B { int f() { return A.LIMIT; } }\n")
        f.commit()
        self.assertFull(f, "상수", max_referrers=0)


class CommandTest(unittest.TestCase):
    def run_cmd(self, f, map_path, **extra):
        out = f.dir + "-out"
        self.addCleanup(shutil.rmtree, out, True)
        gh = f.dir + "-gh-output"
        self.addCleanup(lambda: os.path.exists(gh) and os.remove(gh))
        args = ["python3", "-I", os.path.join(HERE, "test-impact.py"), "select", "--repo", f.dir, "--rules", os.path.join(HERE, "test-selection-rules.txt"),
                "--times", f.dir + "-times.tsv", "--out-dir", out, "--github-output", gh, "--github-summary", ""]
        for k, v in extra.items():
            args += ["--" + k.replace("_", "-"), str(v)]
        if map_path:
            args += ["--map", map_path]
        subprocess.run(args, check=True, capture_output=True)
        with open(gh) as fh:
            outputs = dict(line.split("=", 1) for line in fh.read().splitlines())
        return outputs, out

    def test_outputs_and_failures_fall_back_to_full(self):
        f = Fixture(self)
        self.addCleanup(lambda: os.path.exists(f.dir + "-times.tsv") and os.remove(f.dir + "-times.tsv"))
        with open(f.dir + "-times.tsv", "w") as fh:
            fh.write("".join("%s\t%s\n" % (k, v) for k, v in f.times.items()))
        f.write(MAIN + "B.java", "package p;\npublic class B { void run() { int x; } }\n")
        f.commit()
        mp = f.dir + "-map.tsv"
        self.addCleanup(lambda: os.path.exists(mp) and os.remove(mp))
        f.map.generated = "2999-01-01T00:00:00Z"
        f.map.write(mp)
        o, out = self.run_cmd(f, mp, max_shards=4, full_ratio=0.9)
        self.assertEqual("selected", o["mode"])
        self.assertEqual("1", o["shards"])
        self.assertEqual("[1]", o["matrix"])
        skip = open(os.path.join(out, "skip-tests.txt")).read().split("\n")
        self.assertIn("p/ATest", skip)
        self.assertNotIn("p/BTest", skip)
        # 표가 없다 / 깨졌다 / 푸시 이벤트 → 전체, 조각 수는 최대
        o, _ = self.run_cmd(f, "", max_shards=6)
        self.assertEqual(("full", "6"), (o["mode"], o["shards"]))
        with open(f.dir + "-broken.tsv", "w") as fh:
            fh.write("쓰레기\n")
        self.addCleanup(os.remove, f.dir + "-broken.tsv")
        o, out = self.run_cmd(f, f.dir + "-broken.tsv", max_shards=6)
        self.assertEqual("full", o["mode"])
        self.assertEqual("# 이번 변경과 상관없다고 판단한 시험 클래스(-PtestSkip). 이 목록에 없는 시험은 모두 돈다.\n", open(os.path.join(out, "skip-tests.txt")).read())
        o, _ = self.run_cmd(f, mp, event="push", max_shards=6)
        self.assertEqual(("full", "6"), (o["mode"], o["shards"]))


class RepoAuditTest(unittest.TestCase):
    """실제 저장소: 규칙 파일이 읽히고, 시험이 저장소 밖 파일을 읽는 곳마다 규칙이 있다."""

    def test_rules_parse(self):
        rules = ti.Rules.read(os.path.join(HERE, "test-selection-rules.txt"))
        self.assertTrue(rules.paths)

    def test_every_external_folder_a_test_mentions_has_a_reads_rule(self):
        """자바 소스(시험·도우미)에 저장소 밖 폴더 이름(docs·tools·LLM-Wiki·licenses)이 경로 꼴 문자열로 나오면, 그 폴더에 reads/full 규칙이 있어야 한다.
        없으면 시험이 읽는 파일이 바뀌어도 'ignore' 로 넘어가 시험이 안 돈다. 주석 줄은 건너뛴다(문구 안 설명)."""
        rules = ti.Rules.read(os.path.join(HERE, "test-selection-rules.txt"))
        pat = re.compile(r'"(?:\.\./)?(docs|tools|LLM-Wiki|licenses|graphify-out)(?:/[^"]*)?"')
        found = {}
        for module in os.listdir(REPO):
            src = os.path.join(REPO, module, "src")
            if not os.path.isdir(src):
                continue
            for dirpath, _d, files in os.walk(src):
                for fn in files:
                    if not fn.endswith(".java"):
                        continue
                    with open(os.path.join(dirpath, fn), encoding="utf-8", errors="replace") as fh:
                        for line in fh:
                            if line.lstrip().startswith(("//", "*", "/*")):
                                continue
                            for m in pat.finditer(line):
                                found.setdefault(m.group(1), os.path.join(module, fn))
        for top, where in sorted(found.items()):
            covered = any(kind in ("reads", "full") and glob.startswith(top + "/") for kind, _rx, glob, _arg in rules.paths)
            self.assertTrue(covered, "%s 폴더를 경로로 쓰는 시험·소스(%s)가 있는데 test-selection-rules.txt 에 %s/ 아래 reads/full 규칙이 없다" % (top, where, top))


class SubprocessTestsLintTest(unittest.TestCase):
    """에이전트는 시험 JVM 에만 붙는다. 서버 코드를 자식 JVM 에서 돌리는 시험은 영향 표에 안 잡히므로 항상 돌려야 한다(규칙 파일 always)."""

    EXEMPT = {"org/domi/furnace/server/config/ConfigMigrationSafetyTest"}   # chattr 만 부른다(서버 코드를 자식 JVM 에서 돌리지 않음)

    def test_tests_that_fork_jvms_are_always_run(self):
        rules = ti.Rules.read(os.path.join(HERE, "test-selection-rules.txt"))
        root = os.path.join(REPO, "furnace-server", "src", "test", "java")
        pat = re.compile(r"ProcessBuilder|\bSceneHash\b|\bForkMain\b|Runtime\.getRuntime\(\)\.exec")
        missing = []
        for dirpath, _d, files in os.walk(root):
            for fn in files:
                if not fn.endswith("Test.java"):
                    continue
                with open(os.path.join(dirpath, fn), encoding="utf-8", errors="replace") as fh:
                    text = fh.read()
                if pat.search(text):
                    name = os.path.relpath(os.path.join(dirpath, fn), root)[:-5].replace(os.sep, "/")
                    if name not in rules.always and name not in self.EXEMPT:
                        missing.append(name)
        self.assertEqual([], missing, "자식 JVM 을 띄우는 시험은 test-selection-rules.txt 의 always 에 넣는다")

    def test_always_names_exist(self):
        rules = ti.Rules.read(os.path.join(HERE, "test-selection-rules.txt"))
        for name in rules.always:
            self.assertTrue(os.path.isfile(os.path.join(REPO, "furnace-server", "src", "test", "java", name + ".java")), name + " 시험 소스가 없다")


class WorkflowLintTest(unittest.TestCase):
    """워크플로에 같은 수가 여러 곳에 흩어져 있어(strategy 안에서는 env 를 못 쓴다) 어긋나면 조각이 빠진 채 초록이 될 수 있다 — 같은 수인지 확인한다."""

    def read(self, name):
        with open(os.path.join(REPO, ".github", "workflows", name), encoding="utf-8") as fh:
            return fh.read()

    def test_ci_max_shards_matches_fallbacks(self):
        ci = self.read("ci.yml")
        max_shards = int(re.search(r"^  MAX_SHARDS: (\d+)$", ci, re.M).group(1))
        literal = re.search(r"needs\.select\.outputs\.matrix \|\| '\[([\d,]+)\]'", ci).group(1)
        self.assertEqual(list(range(1, max_shards + 1)), [int(x) for x in literal.split(",")])
        counts = {int(n) for n in re.findall(r"needs\.select\.outputs\.shards \|\| (\d+)", ci)}
        self.assertEqual({max_shards}, counts)
        code = "\n".join(l for l in ci.splitlines() if not l.lstrip().startswith("#")).replace("needs.select.outputs.shards || 6", "")
        self.assertNotRegex(code, r"/6\b", "조각 수를 숫자로 박은 곳이 남았다")

    def test_impact_workflow_shards_match(self):
        wf = self.read("test-impact.yml")
        n = int(re.search(r"^  IMPACT_SHARDS: (\d+)$", wf, re.M).group(1))
        matrix = re.search(r"shard: \[([\d, ]+)\]", wf).group(1)
        self.assertEqual(list(range(1, n + 1)), [int(x) for x in matrix.split(",")])
        self.assertIn("(${{ matrix.shard }}/%d)" % n, wf)

    def test_ci_server_tests_runs_without_a_selection(self):
        ci = self.read("ci.yml")
        # select 가 못 돌아도 서버 시험이 돌아야 한다(always) — 빠지면 select 실패가 "시험 건너뜀"으로 초록이 된다
        self.assertRegex(ci, r"server-tests:\n    needs: \[changes, select\]\n(?:    #.*\n)*    if: always\(\) && needs\.changes\.result == 'success'")


if __name__ == "__main__":
    unittest.main(verbosity=1)
