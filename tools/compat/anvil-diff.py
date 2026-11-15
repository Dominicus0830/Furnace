#!/usr/bin/env python3
# Anvil(.mca) 월드 두 개를 청크 범위에서 칸 단위로 비교한다 — N2b-6 FAWE Paper 대조(V6)용.
# 블록 상태(팔레트 순서와 무관하게 풀어서), 바이옴, 하이트맵, 빛 배열, 블록 엔티티, 그 밖의 청크·섹션 태그를 본다.
# 쓰기: python -X utf8 anvil-diff.py A월드 B월드 --chunks x0 z0 x1 z1 [--json 출력.json] [--samples 8]
#   (청크 좌표, 양 끝 포함. 월드는 level.dat 가 있는 폴더 — region/ 을 읽는다.)
import argparse, gzip, io, json, math, os, struct, sys, zlib
from collections import Counter, defaultdict

# ---------------- NBT ----------------
def _read_nbt(buf):
    pos = 0
    def rd(fmt):
        nonlocal pos
        v = struct.unpack_from(fmt, buf, pos); pos += struct.calcsize(fmt); return v[0]
    def rstr():
        nonlocal pos
        n = rd(">H"); s = bytes(buf[pos:pos + n]).decode("utf-8", "replace"); pos += n; return s
    def payload(t):
        nonlocal pos
        if t == 1: return rd(">b")
        if t == 2: return rd(">h")
        if t == 3: return rd(">i")
        if t == 4: return rd(">q")
        if t == 5: return rd(">f")
        if t == 6: return rd(">d")
        if t == 7:
            n = rd(">i"); v = bytes(buf[pos:pos + n]); pos += n; return v
        if t == 8: return rstr()
        if t == 9:
            et = rd(">b"); n = rd(">i"); return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = rd(">b")
                if tt == 0: return d
                k = rstr(); d[k] = payload(tt)
        if t == 11:
            n = rd(">i"); v = list(struct.unpack_from(">%di" % n, buf, pos)); pos += 4 * n; return v
        if t == 12:
            n = rd(">i"); v = list(struct.unpack_from(">%dq" % n, buf, pos)); pos += 8 * n; return v
        raise ValueError("NBT 태그 %d" % t)
    t = rd(">b"); rstr(); return payload(t)

# ---------------- region ----------------
_region_cache = {}
def read_chunk(world, cx, cz):
    rx, rz = cx >> 5, cz >> 5
    path = os.path.join(world, "region", "r.%d.%d.mca" % (rx, rz))
    data = _region_cache.get(path)
    if data is None:
        data = open(path, "rb").read() if os.path.exists(path) else b""
        _region_cache[path] = data
    if not data: return None
    i = 4 * ((cx & 31) + (cz & 31) * 32)
    loc = struct.unpack_from(">I", data, i)[0]
    off, cnt = loc >> 8, loc & 0xFF
    if off == 0 or cnt == 0: return None
    p = off * 4096
    ln = struct.unpack_from(">I", data, p)[0]; ct = data[p + 4]
    raw = data[p + 5:p + 4 + ln]
    if ct & 0x80:  # 외부 .mcc
        raw = open(os.path.join(world, "region", "c.%d.%d.mcc" % (cx, cz)), "rb").read(); ct &= 0x7F
    if ct == 1: raw = gzip.decompress(raw)
    elif ct == 2: raw = zlib.decompress(raw)
    elif ct == 3: pass
    else: raise ValueError("압축 형식 %d (청크 %d,%d)" % (ct, cx, cz))
    return _read_nbt(raw)

# ---------------- 풀기 ----------------
def unpack(longs, bits, n):
    if not longs: return [0] * n
    per = 64 // bits; mask = (1 << bits) - 1; out = []
    for L in longs:
        L &= (1 << 64) - 1
        for k in range(per):
            out.append((L >> (k * bits)) & mask)
            if len(out) == n: return out
    return out + [0] * (n - len(out))

def state_str(e):
    props = e.get("Properties") or {}
    if not props: return e["Name"]
    return e["Name"] + "[" + ",".join("%s=%s" % (k, props[k]) for k in sorted(props)) + "]"

def decode_blocks(sec):
    bs = sec.get("block_states")
    if bs is None: return None
    pal = [state_str(e) for e in bs["palette"]]
    if len(pal) == 1: return [pal[0]] * 4096
    bits = max(4, math.ceil(math.log2(len(pal))))
    return [pal[i] if i < len(pal) else "?%d" % i for i in unpack(bs.get("data"), bits, 4096)]

def decode_biomes(sec):
    b = sec.get("biomes")
    if b is None: return None
    pal = list(b["palette"])
    if len(pal) == 1: return [pal[0]] * 64
    bits = math.ceil(math.log2(len(pal)))
    return [pal[i] if i < len(pal) else "?%d" % i for i in unpack(b.get("data"), bits, 64)]

def sections_by_y(ch):
    return {s["Y"]: s for s in ch.get("sections", [])}

def nib(arr, i):
    if arr is None: return None
    b = arr[i >> 1]
    return (b >> 4) & 15 if i & 1 else b & 15

# ---------------- 비교 ----------------
SKIP_TOP = {"sections", "Heightmaps", "block_entities", "LastUpdate", "InhabitedTime", "DataVersion"}

def short(v, n=120):
    s = json.dumps(v, ensure_ascii=False, default=lambda o: "<%d bytes>" % len(o) if isinstance(o, bytes) else str(o))
    return s if len(s) <= n else s[:n] + "…"

def diff_chunk(a, b, cx, cz, nsamp):
    r = {"chunk": [cx, cz]}
    if a is None or b is None:
        r["missing"] = "A" if a is None else "B"; return r
    sa, sb = sections_by_y(a), sections_by_y(b)
    ys = sorted(set(sa) | set(sb))
    blk = Counter(); blk_s = []; bio = 0; bio_s = []; light = Counter(); light_s = []; seckeys = []
    for y in ys:
        A, B = sa.get(y, {}), sb.get(y, {})
        ka, kb = set(A) - {"Y"}, set(B) - {"Y"}
        if ka != kb: seckeys.append({"Y": y, "onlyA": sorted(ka - kb), "onlyB": sorted(kb - ka)})
        ba, bb = decode_blocks(A), decode_blocks(B)
        if ba is not None or bb is not None:
            ba = ba or ["minecraft:air"] * 4096; bb = bb or ["minecraft:air"] * 4096
            for i in range(4096):
                if ba[i] != bb[i]:
                    blk[(ba[i], bb[i])] += 1
                    if len(blk_s) < nsamp:
                        blk_s.append({"x": cx * 16 + (i & 15), "y": y * 16 + (i >> 8), "z": cz * 16 + ((i >> 4) & 15), "A": ba[i], "B": bb[i]})
        ia, ib = decode_biomes(A), decode_biomes(B)
        if ia is not None or ib is not None:
            for i in range(64):
                va = ia[i] if ia else None; vb = ib[i] if ib else None
                if va != vb:
                    bio += 1
                    if len(bio_s) < nsamp: bio_s.append({"qx": i & 3, "qy": y * 4 + (i >> 4), "qz": (i >> 2) & 3, "A": va, "B": vb})
        for lk in ("BlockLight", "SkyLight"):
            la, lb = A.get(lk), B.get(lk)
            if la == lb: continue
            if la is None or lb is None:
                light[(lk, "present")] += 1
                if len(light_s) < nsamp: light_s.append({"Y": y, "kind": lk, "A": la is not None, "B": lb is not None})
                continue
            n = sum(1 for i in range(4096) if nib(la, i) != nib(lb, i))
            light[(lk, "cells")] += n
            if len(light_s) < nsamp:
                i = next(i for i in range(4096) if nib(la, i) != nib(lb, i))
                light_s.append({"Y": y, "kind": lk, "cells": n, "x": cx * 16 + (i & 15), "y": y * 16 + (i >> 8), "z": cz * 16 + ((i >> 4) & 15), "A": nib(la, i), "B": nib(lb, i)})
    if blk:
        r["blocks"] = {"cells": sum(blk.values()), "pairs": [[k[0], k[1], v] for k, v in blk.most_common(nsamp)], "samples": blk_s}
    if bio: r["biomes"] = {"cells": bio, "samples": bio_s}
    if light: r["light"] = {"counts": {"%s.%s" % k: v for k, v in light.items()}, "samples": light_s}
    if seckeys: r["sectionKeys"] = seckeys
    # 하이트맵
    ha, hb = a.get("Heightmaps", {}), b.get("Heightmaps", {})
    hm = {}
    for k in sorted(set(ha) | set(hb)):
        va, vb = ha.get(k), hb.get(k)
        if va == vb: continue
        if va is None or vb is None: hm[k] = {"present": [va is not None, vb is not None]}; continue
        ua, ub = unpack(va, 9, 256), unpack(vb, 9, 256)
        d = [i for i in range(256) if ua[i] != ub[i]]
        hm[k] = {"cols": len(d), "samples": [{"x": cx * 16 + (i & 15), "z": cz * 16 + (i >> 4), "A": ua[i], "B": ub[i]} for i in d[:nsamp]]}
    if hm: r["heightmaps"] = hm
    # 블록 엔티티
    def be_map(ch):
        return {(e.get("x"), e.get("y"), e.get("z")): e for e in ch.get("block_entities", [])}
    ea, eb = be_map(a), be_map(b)
    bed = []
    for p in sorted(set(ea) | set(eb), key=lambda t: tuple(v if v is not None else 0 for v in t)):
        if ea.get(p) != eb.get(p): bed.append({"pos": list(p), "A": short(ea.get(p)), "B": short(eb.get(p))})
    if bed: r["blockEntities"] = {"count": len(bed), "samples": bed[:nsamp]}
    # 그 밖의 최상위 태그
    top = {}
    for k in sorted((set(a) | set(b)) - SKIP_TOP):
        if a.get(k) != b.get(k): top[k] = {"A": short(a.get(k)), "B": short(b.get(k))}
    if top: r["top"] = top
    return r

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("a"); ap.add_argument("b")
    ap.add_argument("--chunks", nargs=4, type=int, required=True, metavar=("X0", "Z0", "X1", "Z1"))
    ap.add_argument("--samples", type=int, default=6)
    ap.add_argument("--json")
    ap.add_argument("--ignore", default="", help="쉼표로 뺄 항목: light,top,sectionKeys,blockEntities,heightmaps,biomes")
    o = ap.parse_args()
    ign = {s for s in o.ignore.split(",") if s}
    x0, z0, x1, z1 = o.chunks
    res = []; tot = Counter()
    for cz in range(min(z0, z1), max(z0, z1) + 1):
        for cx in range(min(x0, x1), max(x0, x1) + 1):
            d = diff_chunk(read_chunk(o.a, cx, cz), read_chunk(o.b, cx, cz), cx, cz, o.samples)
            for k in ign: d.pop(k, None)
            if len(d) > 1:
                res.append(d)
                for k in d:
                    if k != "chunk": tot[k] += 1
                tot["blockCells"] += d.get("blocks", {}).get("cells", 0)
                tot["biomeCells"] += d.get("biomes", {}).get("cells", 0)
    n = (abs(x1 - x0) + 1) * (abs(z1 - z0) + 1)
    summary = {"chunks": n, "chunksWithDiff": len(res), "byKind": dict(tot)}
    print(json.dumps(summary, ensure_ascii=False))
    for d in res[:40]:
        print(json.dumps(d, ensure_ascii=False)[:1500])
    if o.json:
        with open(o.json, "w", encoding="utf-8") as f: json.dump({"summary": summary, "chunks": res}, f, ensure_ascii=False, indent=1)
    return 0 if not res else 1

if __name__ == "__main__":
    sys.exit(main())
