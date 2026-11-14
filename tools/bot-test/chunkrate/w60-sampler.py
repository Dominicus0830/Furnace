# 1초마다 서버 프로세스 CPU·RSS 와 Prometheus 메트릭을 CSV 로 적는다. 사용: python3 w60-sampler.py <pid> <out.csv> [port=9225]
# 리눅스는 /proc(스레드 그룹별 CPU 포함), 그 밖(Windows)은 psutil 이 있으면 CPU·RSS 만, 없으면 메트릭만(CPU 칸 비움).
import sys, time, urllib.request, os
pid, out = int(sys.argv[1]), sys.argv[2]
port = sys.argv[3] if len(sys.argv) > 3 else "9225"
LINUX = os.path.isdir("/proc/self")
try:
    import psutil; PS = psutil.Process(pid)
except Exception: PS = None
if LINUX: hz = os.sysconf("SC_CLK_TCK")
def cpu():
    if LINUX:
        f = open(f"/proc/{pid}/stat").read().rsplit(")", 1)[1].split()
        return (int(f[11]) + int(f[12])) / hz
    if PS: t = PS.cpu_times(); return t.user + t.system
    if not os.path.exists("."): raise OSError
    return 0.0
def rss():
    if LINUX:
        for l in open(f"/proc/{pid}/status"):
            if l.startswith("VmRSS"): return int(l.split()[1]) // 1024
    if PS: return PS.memory_info().rss // 1048576
def metrics():
    try:
        t = urllib.request.urlopen(f"http://127.0.0.1:{port}/metrics", timeout=1).read().decode()
    except Exception: return {}
    m = {}
    for l in t.splitlines():
        if l.startswith("#") or not l.strip(): continue
        k, v = l.rsplit(" ", 1)
        if k.startswith("furnace_mspt_region_seconds") and 'window="5s"' in k and "overworld" in k: m["mspt_region_ow"] = float(v) * 1000
        elif k.startswith("furnace_mspt_global_seconds") and 'window="5s"' in k: m["mspt_global"] = float(v) * 1000
        elif k.startswith("furnace_tps_global") and 'window="5s"' in k: m["tps_global"] = float(v)
        elif k.startswith("furnace_tps_slowest_region") and "overworld" in k: m["tps_slow_ow"] = float(v)
        elif k.startswith("furnace_mspt_region_seconds") and 'window="5s"' in k and "nether" in k: m["mspt_region_ne"] = float(v) * 1000
        elif k.startswith("furnace_tps_slowest_region") and "nether" in k: m["tps_slow_ne"] = float(v)
        elif k.startswith("furnace_loaded_chunks"): m["chunks"] = m.get("chunks", 0) + int(float(v))
        elif k.startswith("furnace_regions") and "overworld" in k: m["regions_ow"] = int(float(v))
        elif k.startswith("furnace_jvm_heap_used_bytes"): m["heap_mb"] = int(float(v)) // 1048576
        elif k.startswith("furnace_jvm_threads"): m["threads"] = int(float(v))
        elif k.startswith("furnace_players_online"): m["players"] = int(float(v))
    return m
import glob, re
def groups():
    g = {}
    if not LINUX: return g
    for d in glob.glob(f"/proc/{pid}/task/*"):
        try:
            comm = open(d + "/comm").read().strip(); f = open(d + "/stat").read().rsplit(")", 1)[1].split()
        except Exception: continue
        key = re.sub(r"[-#]?\d+$", "", comm)
        g[key] = g.get(key, 0) + (int(f[11]) + int(f[12])) / hz
    return g
cols = ["ts", "t", "cpu_pct", "rss_mb", "players", "chunks", "regions_ow", "mspt_region_ow", "mspt_global", "tps_global", "tps_slow_ow", "heap_mb", "threads", "mspt_region_ne", "tps_slow_ne", "grp"]
with open(out, "w") as fo:
    fo.write(",".join(cols) + "\n")
    t0 = time.time(); c0 = cpu(); g0 = groups()
    while True:
        time.sleep(1)
        try: c1 = cpu()
        except Exception: break
        m = metrics(); m["t"] = round(time.time() - t0, 1); m["ts"] = round(time.time(), 1); m["cpu_pct"] = round((c1 - c0) * 100, 1); m["rss_mb"] = rss(); c0 = c1
        g1 = groups(); m["grp"] = "|".join(f"{k}={(g1[k]-g0.get(k,0))*100:.0f}" for k in sorted(g1) if g1[k]-g0.get(k,0) > 0.005); g0 = g1
        fo.write(",".join(str(m.get(c, "")) for c in cols) + "\n"); fo.flush()
