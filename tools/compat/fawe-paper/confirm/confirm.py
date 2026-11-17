# T-204 확정 실험 구동기: 설정 {def,pt1} x 서버 {paper,furnace} 를 N회씩 번갈아 돌린다.
import os, shutil, subprocess, sys, time
N = int(sys.argv[1]); R = r"E:\t198-work\runs"
for i in range(1, N + 1):
    for cfg, pt in (("def", None), ("pt1", "1")):
        for kind, port in (("paper", "25565"), ("furnace", "25567")):
            name = "cf-%s-%s-%02d" % (kind, cfg, i)
            if os.path.exists(os.path.join(R, name, "world", "level.dat")): continue
            args = [sys.executable, "-X", "utf8", "scenario_fix.py", kind, name, port] + ([pt] if pt else [])
            with open("log-%s.txt" % name, "w", encoding="utf-8") as f:
                rc = subprocess.call(args, stdout=f, stderr=subprocess.STDOUT)
            d = os.path.join(R, name)
            for n in os.listdir(d):
                if n not in ("world", "console.log", "plugins"): shutil.rmtree(os.path.join(d, n), ignore_errors=True) if os.path.isdir(os.path.join(d, n)) else None
            print(time.strftime("%H:%M:%S"), name, "rc", rc, flush=True)
open("alldone.txt", "w").write("done")
