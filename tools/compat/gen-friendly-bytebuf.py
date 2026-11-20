#!/usr/bin/env python3
"""FriendlyByteBuf shim 의 위임 메서드(netty ByteBuf 의 추상 메서드 전부)를 만든다 — 기계적 생성(netty 가 바뀌면 다시).
쓰는 법: gen-friendly-bytebuf.py <netty-buffer jar> > 위임_메서드_조각.txt   (결과를 FriendlyByteBuf.java 의 표시 구간에 붙인다)"""
import re, subprocess, sys

jar = sys.argv[1]
out = subprocess.run(["javap", "-cp", jar, "-public", "io.netty.buffer.ByteBuf"], capture_output=True, text=True).stdout
# 안의 새 버퍼를 돌려주는 메서드: 바닐라(javap vw.copy 등)처럼 안쪽 ByteBuf 를 감싸지 않고 그대로 돌려준다(반환형도 ByteBuf).
# readBytes(int) 도 새 버퍼를 돌려준다(Paper 1.21.1 javap: return source.readBytes(n)).
WRAP = {"copy", "duplicate", "slice", "readSlice", "retainedSlice", "readRetainedSlice", "retainedDuplicate"}
# 그 밖에 ByteBuf 를 돌려주는 것은 바닐라 1.21.1 처럼 반환형을 FriendlyByteBuf 로 좁히고(공변 — 컴파일러가 ByteBuf 다리 메서드를 만든다)
# 안쪽에 위임한 뒤 this 를 돌려준다(체이닝). Paper 1.21.1 jar 를 javap 로 대조: order·unwrap·asReadOnly 만 ByteBuf 그대로 안쪽 것을 돌려준다.
lines = []
for l in out.splitlines():
    l = l.strip()
    m = re.match(r"public abstract (.+?) (\w+)\((.*?)\)( throws [\w., ]+)?;", l)
    if not m:
        continue
    ret, name, params, throws = m.group(1), m.group(2), m.group(3), m.group(4) or ""
    plist = [p.strip() for p in params.split(",")] if params.strip() else []
    decl = ", ".join(f"{t} p{i}" for i, t in enumerate(plist))
    call = ", ".join(f"p{i}" for i in range(len(plist)))
    if ret == "io.netty.buffer.ByteBuf":
        if name in WRAP or (name == "readBytes" and plist == ["int"]) or name in ("unwrap", "asReadOnly", "order"):
            lines.append(f"    @Override public io.netty.buffer.ByteBuf {name}({decl}){throws} {{ return source.{name}({call}); }}")
        else:
            lines.append(f"    @Override public FriendlyByteBuf {name}({decl}){throws} {{ source.{name}({call}); return this; }}")
    elif ret == "void":
        lines.append(f"    @Override public void {name}({decl}){throws} {{ source.{name}({call}); }}")
    else:
        lines.append(f"    @Override public {ret} {name}({decl}){throws} {{ return source.{name}({call}); }}")
# ReferenceCounted 에서 물려받는 refCnt·release·release(int) 는 손으로 둔다(javap 의 ByteBuf 선언에 없다)
print("\n".join(lines))
