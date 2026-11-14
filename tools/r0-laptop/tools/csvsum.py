# 표본 CSV 요약: python3 csvsum.py <csv> [조건: players>0 | all | tmin-tmax]
import csv, sys, statistics as s
rows = list(csv.DictReader(open(sys.argv[1])))
cond = sys.argv[2] if len(sys.argv) > 2 else "players>0"
if cond == "players>0": rows = [r for r in rows if r["players"] and int(r["players"]) > 0]
elif cond.startswith("ts:"):
    a, b = map(float, cond[3:].split("-")); rows = [r for r in rows if a <= float(r["ts"]) <= b]
elif "-" in cond:
    a, b = map(float, cond.split("-")); rows = [r for r in rows if a <= float(r["t"]) <= b]
out = {"n": len(rows)}
for k in ["cpu_pct", "mspt_region_ow", "mspt_global", "tps_slow_ow", "tps_global", "chunks", "regions_ow", "heap_mb", "rss_mb", "threads"]:
    v = sorted(float(r[k]) for r in rows if r.get(k))
    if not v: continue
    out[k] = f"avg {s.mean(v):.2f} p50 {v[len(v)//2]:.2f} p95 {v[min(len(v)-1, int(len(v)*.95))]:.2f} max {v[-1]:.2f}" if k not in ("chunks","regions_ow","heap_mb","rss_mb","threads") else f"avg {s.mean(v):.0f} max {v[-1]:.0f}"
for k, v in out.items(): print(f"  {k}: {v}")
