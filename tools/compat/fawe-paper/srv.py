# N2b-6 노트북 대조용 서버 조종(콘솔 stdin). Paper·Furnace 둘 다 같은 방식.
import os, re, shutil, subprocess, sys, time

ROOT = os.environ.get("N2B6_ROOT", os.path.dirname(os.path.abspath(__file__)))   # jars/·base/·runs/ 가 놓이는 작업 폴더
JAVA = os.environ.get("N2B6_JAVA", "java")
JARS = {"paper": os.path.join(ROOT, "jars", "paper-1.21.1-133.jar"),
        "furnace": os.path.join(ROOT, "jars", "furnace-server.jar")}
MOJANG = os.environ["N2B6_VANILLA_CACHE"]   # cache/ 에 넣을 mojang_1.21.1.jar·vanilla-data-1.21.1 가 든 폴더
SEED = "20261115"

def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)

class Server:
    def __init__(self, name, kind):
        self.name, self.kind = name, kind
        self.dir = os.path.join(ROOT, "runs", name)
        self.proc = None

    def fresh(self, world_from=None, plugins=(), extra_props=()):
        if os.path.exists(self.dir): shutil.rmtree(self.dir)
        os.makedirs(os.path.join(self.dir, "cache"))
        for n in os.listdir(MOJANG):
            if self.kind == "paper" and n != "mojang_1.21.1.jar": continue
            s = os.path.join(MOJANG, n); d = os.path.join(self.dir, "cache", n)
            (shutil.copytree if os.path.isdir(s) else shutil.copy2)(s, d)
        props = ["online-mode=false", "spawn-protection=0", "level-seed=" + SEED, "allow-nether=false",
                 "view-distance=6", "simulation-distance=6", "enable-rcon=false", "max-tick-time=-1", *extra_props]
        with open(os.path.join(self.dir, "server.properties"), "w", encoding="utf-8") as f: f.write("\n".join(props) + "\n")
        with open(os.path.join(self.dir, "eula.txt"), "w") as f: f.write("eula=true\n")
        if self.kind == "paper":
            with open(os.path.join(self.dir, "bukkit.yml"), "w") as f: f.write("settings:\n  allow-end: false\n")
        if world_from:
            shutil.copytree(world_from, os.path.join(self.dir, "world"))
            for lock in ("session.lock",):
                p = os.path.join(self.dir, "world", lock)
                if os.path.exists(p): os.remove(p)
        if plugins:
            os.makedirs(os.path.join(self.dir, "plugins"))
            for p in plugins: shutil.copy2(p, os.path.join(self.dir, "plugins"))
        return self

    def start(self, jvm=(), timeout=600):
        self.logpath = os.path.join(self.dir, "console.log")
        self.logf = open(self.logpath, "ab")
        args = [JAVA, "-Xmx4G", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", *jvm, "-jar", JARS[self.kind], "--nogui"]
        self.proc = subprocess.Popen(args, cwd=self.dir, stdin=subprocess.PIPE, stdout=self.logf, stderr=subprocess.STDOUT)
        self.pos = 0
        t0 = time.time()
        self.wait(r"Done \(", timeout)
        log(self.name, "기동", round(time.time() - t0, 1), "s")

    def text(self):
        return open(self.logpath, encoding="utf-8", errors="replace").read()

    def wait(self, pattern, timeout=300, since=None):
        rx = re.compile(pattern); t0 = time.time()
        start = self.pos if since is None else since
        while time.time() - t0 < timeout:
            txt = self.text()
            m = rx.search(txt, start)
            if m:
                self.pos = m.end(); return m
            if self.proc.poll() is not None: raise RuntimeError("%s 종료됨: %s" % (self.name, self.logpath))
            time.sleep(0.2)
        raise TimeoutError("%s: '%s' 를 %ds 안에 못 봄" % (self.name, pattern, timeout))

    def cmd(self, line, wait=None, timeout=300, settle=0.0):
        mark = len(self.text())
        log(self.name, ">", line)
        self.proc.stdin.write((line + "\n").encode("utf-8")); self.proc.stdin.flush()
        m = None
        if wait: m = self.wait(wait, timeout, since=mark)
        if settle: time.sleep(settle)
        return m

    def stop(self, timeout=300):
        if not self.proc: return
        try: self.proc.stdin.write(b"stop\n"); self.proc.stdin.flush()
        except Exception: pass
        try: self.proc.wait(timeout)
        except subprocess.TimeoutExpired:
            log(self.name, "강제 종료"); self.proc.kill(); self.proc.wait()
        self.logf.close(); self.proc = None
        log(self.name, "종료")
