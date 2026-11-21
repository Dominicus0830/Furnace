# M-22 스폰 청크 A/B(게임 규칙 spawnChunkRadius 0 대 2) 측정 진행기. r0win.py 의 서버·표본·봇 도우미를 재사용한다.
# 사용: python m22.py tpl                  → 규칙 0·2 템플릿 level.dat 만들기(한 번)
#       python m22.py idle <판> <규칙...>   → (가) 새 월드 기동 + 접속자 0 유휴 IDLE_S 초(기본 300)
#       python m22.py walk <판> <시야> <규칙...> → (나) 1명 새 땅 걷기(o00walk2.js, 속도 10, 60초)
#       python m22.py sum                   → runs-m22/summary.json 과 표
# 환경 변수: M22_JAVA(java 경로), M22_JAR(서버 jar), M22_RUNS(결과 폴더, 기본 저장소 밖 runs-m22)
import os, sys, json, time, shutil, subprocess, statistics as st, csv, glob
import importlib.util

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("r0win", os.path.join(HERE, "r0win.py"))
r0 = importlib.util.module_from_spec(spec)
RUNS = os.environ.get("M22_RUNS", os.path.join(HERE, "runs-m22"))
JAR = os.environ.get("M22_JAR", os.path.abspath(os.path.join(HERE, "..", "..", "furnace-server", "build", "libs", "furnace-server.jar")))
JAVA = os.environ.get("M22_JAVA", os.path.expanduser("~/.jdks/corretto-21.0.12.1/bin/java.exe"))
os.makedirs(RUNS, exist_ok=True)
spec.loader.exec_module(r0)
r0.RUNS = RUNS; r0.OUT = os.path.join(RUNS, "results.jsonl"); r0.MISC = os.path.join(RUNS, "misc.jsonl")
r0.JAVA = JAVA; r0.JARS = {"A": JAR, "B": JAR}
os.makedirs(RUNS, exist_ok=True)
IDLE_S = int(os.environ.get("IDLE_S", "300"))
UTF8 = ["-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-XX:+UnlockDiagnosticVMOptions", "-XX:+DebugNonSafepoints"]
TPL = os.path.join(RUNS, "_tpl")
log = r0.log

def monitor_window():
    # 보이는 콘솔 창(UTF-8): 서버 로그를 따라 보여 준다. 측정 서버 자체는 로그 파일로 보낸다(시험 방해 최소).
    if os.name != "nt": return
    lg = os.path.join(RUNS, "live.log"); open(lg, "a").close()
    cmd = f'chcp 65001 >nul & powershell -NoProfile -Command "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-Content -Wait -Tail 30 -Encoding UTF8 \'{lg}\'"'
    subprocess.Popen(["cmd", "/c", "start", "M22 server log", "cmd", "/k", cmd])

def mirror(srv):
    # 서버 로그 파일의 새 줄을 live.log 로 복사하는 스레드
    import threading
    def run():
        pos = 0
        while srv.proc is not None:
            try:
                with open(srv.logpath, "rb") as f: f.seek(pos); d = f.read(); pos += len(d)
                if d:
                    with open(os.path.join(RUNS, "live.log"), "ab") as o: o.write(d)
            except Exception: pass
            time.sleep(1)
    threading.Thread(target=run, daemon=True).start()

def start(s, rule, tag, extra=()):
    w = s.start("A", [*UTF8, *extra], tag=tag)
    mirror(s)
    return w

def make_world(name, rule):
    s = r0.Server(name).new(["view-distance=16", "simulation-distance=10"], [])
    if rule is not None:
        wd = os.path.join(s.dir, "world"); os.makedirs(wd, exist_ok=True)
        shutil.copy2(os.path.join(TPL, f"level{rule}.dat"), os.path.join(wd, "level.dat"))
    return s

def tpl():
    shutil.rmtree(TPL, ignore_errors=True); os.makedirs(TPL)
    s = r0.Server("_tplsrv").new(["view-distance=16", "simulation-distance=10"], [])
    start(s, 2, "tpl")
    time.sleep(3); s.cmd("save-all"); time.sleep(5); s.stop()
    lv = glob.glob(os.path.join(s.dir, "**", "level.dat"), recursive=True); log("level.dat", lv)
    shutil.copy2(lv[0], os.path.join(TPL, "level2.dat"))
    s.start("A", UTF8, tag="tpl0"); mirror(s)
    s.cmd("gamerule spawnChunkRadius 0"); time.sleep(3); s.cmd("save-all"); time.sleep(5); s.stop()
    shutil.copy2(lv[0], os.path.join(TPL, "level0.dat"))
    log("templates ok", os.listdir(TPL))

def rec(**kw): r0.append(r0.OUT, json.dumps(kw))

def idle(r, rules):
    for rule in rules:
        W = f"m22i{r}r{rule}"; s = make_world(W, rule)
        wall = start(s, rule, f"m22 idle r={r} rule={rule}")
        done_line = open(s.logpath, encoding="utf-8", errors="replace").read()
        t0 = time.time(); time.sleep(30)   # 기동 직후 30초는 안정화(요약에서 버림)
        a = time.time()
        time.sleep(IDLE_S)
        b = time.time()
        rec(scene="idle", r=r, rule=rule, world=W, jfr=s.k, wall_to_done_s=round(wall, 2), a=a, b=b)
        s.stop(); log("idle done", r, rule)

def walk(r, vd, rules):
    for rule in rules:
        W = f"m22w{r}v{vd}r{rule}"; s = make_world(W, rule)
        wall = start(s, rule, f"m22 walk r={r} vd={vd} rule={rule}", ["-Dfurnace.tickstats=true"]); time.sleep(3)
        lines = r0.bot("o00walk2.js", [1, vd, 10, 60], {"SRV": s.dir, "XOFF": 200000, "NAMEP": f"m{r}{rule}x"}, 340)
        d = json.loads(lines[0]) if lines else {}
        rec(scene="walk", r=r, rule=rule, vd=vd, world=W, jfr=s.k, wall_to_done_s=round(wall, 2), bot=d)
        s.stop(); log("walk done", r, vd, rule)

def stats(world, k, a, b):
    return r0.window(world, k, a, b)

def summarize():
    rows = [json.loads(l) for l in open(r0.OUT, encoding="utf-8") if l.startswith("{")]
    out = []
    for d in rows:
        if d["scene"] == "idle": w = stats(d["world"], d["jfr"], d["a"], d["b"])
        else:
            b = d["bot"]; w = stats(d["world"], d["jfr"], b["walkStart"], b["walkEnd"]) if b else {}
        out.append({**{k: v for k, v in d.items() if k != "bot"}, **w, "bot": d.get("bot")})
    json.dump(out, open(os.path.join(RUNS, "summary.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    for o in out:
        print(o["scene"], o.get("vd", ""), "r", o["r"], "rule", o["rule"], "start_s", o["wall_to_done_s"], "cpu%avg", o.get("cpu_pct_avg"), "rss", o.get("rss_mb_avg"), "heap", o.get("heap_mb_avg"), "chunks", o.get("chunks_avg"), "mspt", o.get("mspt_region_ow_avg"))

if __name__ == "__main__":
    c = sys.argv[1]
    if c == "tpl": monitor_window(); tpl()
    elif c == "idle": monitor_window(); idle(sys.argv[2], [int(x) for x in sys.argv[3:]])
    elif c == "walk": monitor_window(); walk(sys.argv[2], int(sys.argv[3]), [int(x) for x in sys.argv[4:]])
    elif c == "sum": summarize()
