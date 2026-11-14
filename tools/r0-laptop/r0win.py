# R0 노트북 진행기 (Windows·Linux 공용). 클라우드 R0 도구(srv.sh·r0round.sh·r0misc.sh·sampler.py)를 파이썬 하나로 옮긴 것.
# 봇(o00walk.js·o00hold.js)·시야 판정(정사각∩바닐라 원형)·장면 순서·XOFF·변형(A|Bv|Bd)은 클라우드 판과 같다.
# 사용:
#   python r0win.py check                      → 도구·jar·포트 확인
#   python r0win.py round <판> <변형...>        → ② 새 땅 6구성(구성마다 변형 교대) + ③ 만든 땅(냉+⑦ 힙, chunktrace 냉, 온)
#   python r0win.py walk <판> <변형...>         → ② 만
#   python r0win.py old <판> <변형...>          → ③·⑦ 만 (같은 판의 walk 가 만든 월드 w<판><변형> 이 있어야 함)
#   python r0win.py misc <판> <변형...>         → ① 유휴+⑥ 기동, ④ 소 1000·3000, ⑤ 레드스톤
#   python r0win.py sum                        → runs/summary.json (결과 줄 + 1초 표본 창 요약)
#   python r0win.py agg                        → 모든 jfr 을 JfrAgg 로 묶어 runs/<월드>/agg-<k>.txt
# 변형: A = 07dec3da, Bv = a242b1af + entity-activation-range.enabled=false (바닐라 조건), Bd = a242b1af 기본(D11 켬)
# 환경 변수: R0_JAVA(java 경로, 기본 java), R0_NODE(기본 node), R0_XMX(기본 6G)
import os, sys, time, json, csv, subprocess, threading, urllib.request, statistics as st, shutil, glob

HERE = os.path.dirname(os.path.abspath(__file__))
RUNS = os.path.join(HERE, "runs"); BOT = os.path.join(HERE, "bot"); TOOLS = os.path.join(HERE, "tools")
JARS = {"A": os.path.join(HERE, "jars", "A.jar"), "B": os.path.join(HERE, "jars", "B.jar")}
JAVA = os.environ.get("R0_JAVA", "java"); NODE = os.environ.get("R0_NODE", "node"); XMX = os.environ.get("R0_XMX", "6G")
OUT = os.path.join(RUNS, "results.jsonl"); MISC = os.path.join(RUNS, "misc.jsonl")
os.makedirs(RUNS, exist_ok=True)

def log(*a):
    s = time.strftime("%H:%M:%S") + " " + " ".join(str(x) for x in a)
    print(s, flush=True)
    with open(os.path.join(RUNS, "driver.log"), "a", encoding="utf-8") as f: f.write(s + "\n")

def append(path, line):
    with open(path, "a", encoding="utf-8") as f: f.write(line + "\n")

def jar_of(v): return "A" if v == "A" else "B"
def fprops_of(v): return ["entity-activation-range.enabled=false"] if v == "Bv" else []

# ---------------- 서버 ----------------
class Server:
    def __init__(self, name): self.name = name; self.dir = os.path.join(RUNS, name); self.proc = None

    def new(self, props, fprops):
        if os.path.exists(self.dir): shutil.rmtree(self.dir)
        os.makedirs(os.path.join(self.dir, "cache"))
        # 바닐라 데이터 캐시를 판끼리 같이 쓴다(첫 실행 때 Mojang 에서 받은 것을 복사)
        shared = os.path.join(RUNS, "_vanilla-cache")
        if os.path.isdir(shared):
            for n in os.listdir(shared):
                src = os.path.join(shared, n); dst = os.path.join(self.dir, "cache", n)
                (shutil.copytree if os.path.isdir(src) else shutil.copy2)(src, dst)
        with open(os.path.join(self.dir, "server.properties"), "w", encoding="utf-8") as f:
            f.write("\n".join(["online-mode=false", "spawn-protection=0", "level-seed=20261112", "max-players=100"] + props) + "\n")
        with open(os.path.join(self.dir, "furnace.properties"), "w", encoding="utf-8") as f:
            f.write("\n".join(["metrics.prometheus.enabled=true"] + fprops) + "\n")
        with open(os.path.join(self.dir, "eula.txt"), "w") as f: f.write("eula=true\n")
        return self

    def k_next(self): return len(glob.glob(os.path.join(self.dir, "jfr-*.jfr")))

    def start(self, variant, jvm=(), tag=""):
        self.k = self.k_next(); self.variant = variant
        cmdfile = os.path.join(self.dir, "cmds"); open(cmdfile, "w").close()
        self.logpath = os.path.join(self.dir, f"server-{self.k}.log")
        args = [JAVA, f"-Xmx{XMX}", f"-XX:StartFlightRecording=filename={os.path.join(self.dir, f'jfr-{self.k}.jfr')},settings=profile,dumponexit=true",
                *jvm, "-jar", JARS[jar_of(variant)], "--nogui"]
        t0 = time.time()
        self.logf = open(self.logpath, "wb")
        self.proc = subprocess.Popen(args, cwd=self.dir, stdin=subprocess.PIPE, stdout=self.logf, stderr=subprocess.STDOUT)
        self.stop_ev = threading.Event()
        threading.Thread(target=self._forward, args=(cmdfile,), daemon=True).start()
        done = None
        while time.time() - t0 < 300:
            if self.proc.poll() is not None: raise RuntimeError(f"server exited early, see {self.logpath}")
            txt = open(self.logpath, encoding="utf-8", errors="replace").read()
            i = txt.find("Done (")
            if i >= 0: done = txt[i:txt.find(")", i) + 1]; break
            time.sleep(0.2)
        wall = time.time() - t0
        line = f"{tag} name={self.name} k={self.k} jar={variant} wall_to_done_s={wall:.2f} {done}"
        append(os.path.join(RUNS, "startup-all.txt"), line); log("start", line)
        if done is None: raise RuntimeError("no Done within 300s")
        self._save_vanilla_cache()
        self.sampler = threading.Thread(target=self._sample, args=(os.path.join(self.dir, f"samples-{self.k}.csv"),), daemon=True)
        self.sampler.start()
        return wall

    def _save_vanilla_cache(self):
        shared = os.path.join(RUNS, "_vanilla-cache")
        if os.path.isdir(shared): return
        c = os.path.join(self.dir, "cache")
        if any(n.startswith("vanilla-data") for n in os.listdir(c)):
            shutil.copytree(c, shared)

    def _forward(self, cmdfile):  # 봇이 cmds 파일에 덧붙인 명령을 서버 콘솔로 보낸다(tail -f 대신)
        pos = 0
        while not self.stop_ev.is_set():
            try:
                with open(cmdfile, "rb") as f:
                    f.seek(pos); data = f.read()
                nl = data.rfind(b"\n")
                if nl >= 0:
                    chunk = data[:nl + 1]; pos += nl + 1
                    self.proc.stdin.write(chunk); self.proc.stdin.flush()
            except Exception: pass
            time.sleep(0.05)

    def cmd(self, line): append(os.path.join(self.dir, "cmds"), line)

    def _sample(self, out):
        import psutil
        p = psutil.Process(self.proc.pid)
        cols = ["ts", "t", "cpu_pct", "rss_mb", "players", "chunks", "regions_ow", "mspt_region_ow", "mspt_global", "tps_global", "tps_slow_ow", "heap_mb", "threads"]
        with open(out, "w", encoding="utf-8") as fo:
            fo.write(",".join(cols) + "\n")
            t0 = time.time(); ct = p.cpu_times(); c0 = ct.user + ct.system
            while not self.stop_ev.is_set():
                time.sleep(1)
                try: ct = p.cpu_times(); c1 = ct.user + ct.system; rss = p.memory_info().rss // 1048576
                except Exception: break
                m = metrics(); m["t"] = round(time.time() - t0, 1); m["ts"] = round(time.time(), 1)
                m["cpu_pct"] = round((c1 - c0) * 100, 1); m["rss_mb"] = rss; c0 = c1
                fo.write(",".join(str(m.get(c, "")) for c in cols) + "\n"); fo.flush()

    def stop(self):
        if not self.proc: return
        try: self.proc.stdin.write(b"stop\n"); self.proc.stdin.flush()
        except Exception: pass
        try: self.proc.wait(180)
        except subprocess.TimeoutExpired: log("forced kill", self.name); self.proc.kill(); self.proc.wait()
        self.stop_ev.set(); time.sleep(1.2); self.logf.close(); self.proc = None

    def wait_log(self, token, timeout=900):
        t = time.time()
        while time.time() - t < timeout:
            if token in open(self.logpath, encoding="utf-8", errors="replace").read(): return True
            time.sleep(1)
        return False

def metrics(port=9225):
    try: t = urllib.request.urlopen(f"http://127.0.0.1:{port}/metrics", timeout=1).read().decode()
    except Exception: return {}
    m = {}
    for l in t.splitlines():
        if l.startswith("#") or not l.strip(): continue
        k, v = l.rsplit(" ", 1)
        if k.startswith("furnace_mspt_region_seconds") and 'window="5s"' in k and "overworld" in k: m["mspt_region_ow"] = float(v) * 1000
        elif k.startswith("furnace_mspt_global_seconds") and 'window="5s"' in k: m["mspt_global"] = float(v) * 1000
        elif k.startswith("furnace_tps_global") and 'window="5s"' in k: m["tps_global"] = float(v)
        elif k.startswith("furnace_tps_slowest_region") and "overworld" in k: m["tps_slow_ow"] = float(v)
        elif k.startswith("furnace_loaded_chunks"): m["chunks"] = m.get("chunks", 0) + int(float(v))
        elif k.startswith("furnace_regions") and "overworld" in k: m["regions_ow"] = int(float(v))
        elif k.startswith("furnace_jvm_heap_used_bytes"): m["heap_mb"] = int(float(v)) // 1048576
        elif k.startswith("furnace_jvm_threads"): m["threads"] = int(float(v))
        elif k.startswith("furnace_players_online"): m["players"] = int(float(v))
    return m

def bot(script, args, env_extra, timeout, out_jsonl=None):
    env = dict(os.environ); env.update({k: str(v) for k, v in env_extra.items()})
    try:
        r = subprocess.run([NODE, script, *map(str, args)], cwd=BOT, env=env, capture_output=True, text=True, timeout=timeout, encoding="utf-8", errors="replace")
        out = r.stdout
    except subprocess.TimeoutExpired as e:
        out = (e.stdout or b"").decode("utf-8", "replace") if isinstance(e.stdout, bytes) else (e.stdout or "")
        log("bot timeout", script, args)
    lines = [l for l in out.splitlines() if l.startswith("{")]
    if out_jsonl:
        for l in lines: append(out_jsonl, l)
    if not lines: log("bot gave no JSON", script, args)
    return lines

# ---------------- 장면 ----------------
CFGS = [(1, 10), (8, 10), (32, 10), (1, 16), (8, 16), (32, 16)]

def scene_walk(r, order):
    xo = 200000
    for ci, (n, vd) in enumerate(CFGS):
        for v in order:
            W = f"w{r}{v}"; s = Server(W)
            if ci == 0: s.new(["view-distance=16", "simulation-distance=10"], fprops_of(v))
            s.start(v, ["-Dfurnace.tickstats=true"], tag=f"r={r} v={v} walk"); time.sleep(3)
            append(OUT, f"== r={r} v={v} scene=walk n={n} vd={vd} world={W} jfr={s.k} t={int(time.time())}")
            bot("o00walk.js", [n, vd, 10, 60], {"SRV": s.dir, "XOFF": xo, "NAMEP": f"{v}{r}c{ci}x"}, 320, OUT)
            s.stop(); log("walk done", r, v, n, vd)
        xo += 200000
    append(OUT, f"== r={r} walkdone t={int(time.time())}")

def jcmd_path():
    j = shutil.which(JAVA) or JAVA
    cand = os.path.join(os.path.dirname(j), "jcmd" + (".exe" if os.name == "nt" else ""))
    return cand if os.path.exists(cand) else "jcmd"

def scene_old(r, order):
    for v in order:
        W = f"w{r}{v}"; s = Server(W)
        if not os.path.isdir(s.dir): log("missing world", W); continue
        # ③ 냉 8×16 + ⑦ 힙
        s.start(v, ["-Dfurnace.tickstats=true"], tag=f"r={r} v={v} oldcold"); time.sleep(3)
        append(OUT, f"== r={r} v={v} scene=oldcold n=8 vd=16 world={W} jfr={s.k} t={int(time.time())}")
        flag = os.path.join(s.dir, "hold.flag")
        if os.path.exists(flag): os.remove(flag)
        res = {}
        th = threading.Thread(target=lambda: res.setdefault("o", bot("o00walk.js", [8, 16, 10, 60, "old"], {"SRV": s.dir, "XOFF": 1000000, "NAMEP": f"{v}{r}k", "HOLD": 25000}, 200, OUT)))
        th.start()
        for _ in range(150):
            if os.path.exists(flag): break
            time.sleep(1)
        time.sleep(2)
        m = metrics()
        with open(os.path.join(s.dir, f"histo-{s.k}-chunks.txt"), "w") as f: f.write(f"furnace_loaded_chunks_sum {m.get('chunks')}\n")
        h = subprocess.run([jcmd_path(), str(s.proc.pid), "GC.class_histogram"], capture_output=True, text=True, encoding="utf-8", errors="replace")
        with open(os.path.join(s.dir, f"histo-{s.k}.txt"), "w", encoding="utf-8") as f: f.write(h.stdout)
        th.join(); s.stop()
        # ③ 냉 8×16, chunktrace
        s.start(v, ["-Dfurnace.tickstats=true", "-Dfurnace.chunktrace=true"], tag=f"r={r} v={v} oldcold-trace"); time.sleep(3)
        append(OUT, f"== r={r} v={v} scene=oldcold-trace n=8 vd=16 world={W} jfr={s.k} t={int(time.time())}")
        bot("o00walk.js", [8, 16, 10, 60, "old"], {"SRV": s.dir, "XOFF": 1000000, "NAMEP": f"{v}{r}t"}, 200, OUT)
        s.stop()
        # ③ 온
        s.start(v, ["-Dfurnace.tickstats=true"], tag=f"r={r} v={v} oldwarm"); time.sleep(3)
        bot("o00walk.js", [8, 16, 10, 60, "old"], {"SRV": s.dir, "XOFF": 1000000, "NAMEP": f"{v}{r}u"}, 200)  # 데우기(버림)
        time.sleep(30)
        for n, vd, xo in [(1, 10, 200000), (8, 10, 400000), (1, 16, 800000), (8, 16, 1000000)]:
            append(OUT, f"== r={r} v={v} scene=oldwarm n={n} vd={vd} world={W} jfr={s.k} t={int(time.time())}")
            bot("o00walk.js", [n, vd, 10, 60, "old"], {"SRV": s.dir, "XOFF": xo, "NAMEP": f"{v}{r}w{n}_{vd}"}, 200, OUT)
            time.sleep(30)
        s.stop(); log("old done", r, v)
    append(OUT, f"== r={r} done t={int(time.time())}")

def mark(r, v, W, k, m): append(MISC, json.dumps({"r": int(r), "v": v, "world": W, "jfr": k, "mark": m, "ts": time.time()}))

def scene_misc(r, order):
    def feed(s, fname):
        with open(os.path.join(TOOLS, fname), encoding="utf-8") as f: data = f.read()
        with open(os.path.join(s.dir, "cmds"), "a", encoding="utf-8") as f: f.write(data if data.endswith("\n") else data + "\n")
    def waitsay(s, token):
        s.cmd(f"say {token}"); return s.wait_log(token)
    for v in order:
        # ① + ⑥
        W = f"i{r}{v}"; s = Server(W).new(["view-distance=16", "simulation-distance=10"], fprops_of(v))
        s.start(v, tag=f"r={r} v={v} new")
        mark(r, v, W, s.k, "idle_start"); time.sleep(120); mark(r, v, W, s.k, "idle_end"); s.stop()
        for j in range(3):
            s.start(v, tag=f"r={r} v={v} restart"); time.sleep(2); s.stop()
        # ④ 소 농장
        W = f"f{r}{v}"; s = Server(W).new(["view-distance=10", "simulation-distance=10", "level-type=flat"], fprops_of(v))
        s.start(v, ["-Dfurnace.tickstats=true"], tag=f"r={r} v={v} flatnew")
        hp = subprocess.Popen([NODE, "o00hold.js", f"F{r}{v}", "850", "10"], cwd=BOT, env={**os.environ, "SRV": s.dir}, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(6)
        for c in ["gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set 1000", "weather clear", "gamerule doWeatherCycle false"]: s.cmd(c)
        feed(s, "farm1000.txt"); waitsay(s, f"FARM1K_{r}{v}"); time.sleep(30)
        mark(r, v, W, s.k, "farm1000_start"); time.sleep(60); mark(r, v, W, s.k, "farm1000_end")
        feed(s, "farm_more2000.txt"); waitsay(s, f"FARM3K_{r}{v}"); time.sleep(30)
        mark(r, v, W, s.k, "farm3000_start"); time.sleep(60); mark(r, v, W, s.k, "farm3000_end")
        s.cmd("execute if entity @e[type=cow]"); time.sleep(2)
        hp.kill(); s.stop()
        # ⑤ 레드스톤
        W = f"g{r}{v}"; s = Server(W).new(["view-distance=10", "simulation-distance=10", "level-type=flat"], fprops_of(v))
        s.start(v, ["-Dfurnace.tickstats=true"], tag=f"r={r} v={v} rednew")
        hp = subprocess.Popen([NODE, "o00hold.js", f"G{r}{v}", "420", "10"], cwd=BOT, env={**os.environ, "SRV": s.dir}, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(6)
        for c in ["gamerule doMobSpawning false", "gamerule doDaylightCycle false", "time set 1000"]: s.cmd(c)
        mark(r, v, W, s.k, "red_build_start"); feed(s, "redstone.txt"); waitsay(s, f"RED_{r}{v}"); mark(r, v, W, s.k, "red_build_end"); time.sleep(20)
        mark(r, v, W, s.k, "red_start"); time.sleep(60); mark(r, v, W, s.k, "red_end")
        hp.kill(); s.stop(); log("misc done", r, v)
    append(MISC, json.dumps({"r": int(r), "mark": "done"}))

# ---------------- 요약 ----------------
def window(world, k, a, b):
    p = os.path.join(RUNS, world, f"samples-{k}.csv")
    if not os.path.exists(p): return {}
    rs = [x for x in csv.DictReader(open(p, encoding="utf-8")) if x["ts"] and a <= float(x["ts"]) <= b]
    out = {"n": len(rs)}
    for key in ["cpu_pct", "mspt_region_ow", "tps_slow_ow", "heap_mb", "chunks", "rss_mb"]:
        v = sorted(float(x[key]) for x in rs if x.get(key))
        if not v: continue
        out[key + "_avg"] = round(st.mean(v), 2); out[key + "_p95"] = v[min(len(v) - 1, int(len(v) * .95))]; out[key + "_max"] = v[-1]; out[key + "_min"] = v[0]
    return out

def summarize():
    rows = []; head = None
    if os.path.exists(OUT):
        for l in open(OUT, encoding="utf-8"):
            l = l.strip()
            if l.startswith("== ") and "scene=" in l: head = dict(kv.split("=", 1) for kv in l[3:].split() if "=" in kv); continue
            if l.startswith("{") and head:
                d = json.loads(l); d.update(head); rows.append(d); head = None
    for d in rows:
        if d["scene"] == "walk": d.update(window(d["world"], d["jfr"], d["walkStart"], d["walkEnd"]))
        else:
            t0 = float(d["t"]) + 4; dur = (d.get("fullViewMsMax") or 60000) / 1000
            d.update(window(d["world"], d["jfr"], t0, t0 + dur))
    misc = []
    if os.path.exists(MISC):
        ms = [json.loads(l) for l in open(MISC, encoding="utf-8") if l.strip()]
        starts = {}
        for m in ms:
            if "mark" not in m or m["mark"] == "done": continue
            key = (m["r"], m["v"], m["world"], m["jfr"], m["mark"].rsplit("_", 1)[0])
            if m["mark"].endswith("_start"): starts[key] = m["ts"]
            elif m["mark"].endswith("_end") and key in starts:
                misc.append({"r": m["r"], "v": m["v"], "scene": key[4], "world": m["world"], "jfr": m["jfr"], **window(m["world"], m["jfr"], starts[key], m["ts"])})
    # chunktrace 의 ser n 줄(직렬화 횟수)을 서버 로그에서 모은다
    traces = {}
    for d in rows:
        if d["scene"] == "oldcold-trace":
            p = os.path.join(RUNS, d["world"], f"server-{d['jfr']}.log")
            if os.path.exists(p): traces[f"{d['world']}#{d['jfr']}"] = [l.rstrip() for l in open(p, encoding="utf-8", errors="replace") if "ser n" in l or "hop." in l][:80]
    startup = open(os.path.join(RUNS, "startup-all.txt"), encoding="utf-8").read().splitlines() if os.path.exists(os.path.join(RUNS, "startup-all.txt")) else []
    histo = {}
    for p in glob.glob(os.path.join(RUNS, "*", "histo-*.txt")):
        histo[os.path.relpath(p, RUNS)] = open(p, encoding="utf-8", errors="replace").read().splitlines()[:40]
    res = {"walk_old": rows, "misc": misc, "startup": startup, "chunktrace": traces, "histo": histo}
    with open(os.path.join(RUNS, "summary.json"), "w", encoding="utf-8") as f: json.dump(res, f, ensure_ascii=False, indent=1)
    log("summary written", len(rows), "rows", len(misc), "misc")

def agg():
    for p in sorted(glob.glob(os.path.join(RUNS, "*", "jfr-*.jfr"))):
        o = p.replace("jfr-", "agg-").replace(".jfr", ".txt")
        if os.path.exists(o): continue
        r = subprocess.run([JAVA, "-Dstdout.encoding=UTF-8", "-Xmx4G", os.path.join(TOOLS, "JfrAgg.java"), p, "40"], capture_output=True, text=True, encoding="utf-8", errors="replace")
        with open(o, "w", encoding="utf-8") as f: f.write(r.stdout + r.stderr)
        log("agg", o)

def check():
    import psutil
    print("cpu logical", psutil.cpu_count(), "physical", psutil.cpu_count(logical=False), "ram GB", round(psutil.virtual_memory().total / 2**30, 1))
    for k, p in JARS.items(): print(k, p, os.path.exists(p), os.path.getsize(p) if os.path.exists(p) else "")
    print(subprocess.run([JAVA, "-version"], capture_output=True, text=True).stderr.splitlines()[:1])
    print("node", subprocess.run([NODE, "-v"], capture_output=True, text=True).stdout.strip())
    print("node_modules", os.path.isdir(os.path.join(BOT, "node_modules", "minecraft-protocol")))
    print("jcmd", jcmd_path())
    import socket
    for port in (25565, 9225):
        s = socket.socket(); busy = s.connect_ex(("127.0.0.1", port)) == 0; s.close(); print("port", port, "busy" if busy else "free")

if __name__ == "__main__":
    c = sys.argv[1]
    if c == "check": check()
    elif c == "round": scene_walk(sys.argv[2], sys.argv[3:]); scene_old(sys.argv[2], sys.argv[3:]); summarize()
    elif c == "walk": scene_walk(sys.argv[2], sys.argv[3:]); summarize()
    elif c == "old": scene_old(sys.argv[2], sys.argv[3:]); summarize()
    elif c == "misc": scene_misc(sys.argv[2], sys.argv[3:]); summarize()
    elif c == "sum": summarize()
    elif c == "agg": agg()
    else: print(__doc__)
