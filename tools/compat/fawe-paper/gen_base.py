# Paper 로 기준 월드를 만든다: 시드 고정, (-128..127)² 블록 = 16×16 청크를 forceload 로 끝까지 생성.
import os, shutil, sys, time
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from srv import Server, ROOT, log

s = Server("base-gen", "paper").fresh()
s.start()
s.cmd("forceload add -128 -128 127 127", wait=r"Marked|forceloaded|chunks", timeout=60)
time.sleep(150)
s.cmd("save-all flush", wait=r"Saved the game", timeout=300)
s.cmd("forceload remove all", wait=r"Unmarked|forceload", timeout=60)
s.cmd("save-all flush", wait=r"Saved the game", timeout=300)
s.stop()
dst = os.path.join(ROOT, "base", "world")
if os.path.exists(dst): shutil.rmtree(dst)
shutil.copytree(os.path.join(s.dir, "world"), dst)
log("기준 월드:", dst)
