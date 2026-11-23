#!/usr/bin/env python3
"""두 마크 판 사이 바닐라 차이를 기계로 뽑는다(버전업 지도). 읽기 전용 분석이며 서버 실행 경로와 이어진 곳이 없다.

입력은 모두 개발자 캐시(`~/.cache/furnace-vanilla-extract/<판>/`)에 vanilla-extract 가 만든 것이다:
  sig-table.tsv.gz   선언 표(T-227, `-Pmode=sig-table`)         → 클래스·멤버 추가/삭제/서술자 변경
  body-table.tsv.gz  본문 지문표(`-Pmode=body-table`)           → 같은 서명인데 본문이 달라진 메서드(동작 변경 후보)·상수 변경
  generated/         데이터 생성기 출력(`-Pmode=reports`)       → 패킷·레지스트리·블록·아이템·명령·데이터팩 파일
  work/server-inner.jar                                       → jar 안 클래스 아닌 자원(구조물 등)

사용법:
  tools/compat/version-diff.py 1.21.1 1.21.2 [--out 폴더] [--shim-table sig-table-shim.tsv.gz] [--safe 요약.md]

만드는 것:
  <out>/version-diff-<A>-<B>.md      전체 보고서(Mojang 이름이 들어 있다 — 커밋·공유 금지)
  <out>/version-diff-<A>-<B>.tsv     세부 목록(종류·영역·소유자·멤버·자세히) — 같은 이유로 커밋 금지
  <out>/version-diff-<A>-<B>.json    개수만(커밋해도 되는 숫자)
  --safe 로 준 파일                  개수만 적은 커밋용 요약(Mojang 이름 없음, 영역 이름·숫자만)

주의: 본문 지문은 "달라졌는가"만 알려 주는 후보다. 타입 이름이 바뀌면 그 타입을 부르는 모든 본문이 달라지므로 과대 계수된다.
표·보고서는 Mojang 대응표에서 나온 이름을 담으므로 저장소·공유 폴더·서버 jar 에 두지 않는다(대응표 머리말: 완전·미수정 재배포 금지).
"""
import argparse
import collections
import gzip
import hashlib
import json
import os
import sys
import zipfile

CACHE = os.environ.get("FURNACE_VANILLA_CACHE", os.path.expanduser("~/.cache/furnace-vanilla-extract"))

# 영역: 앞에서부터 첫 일치. (접두, 영역 이름)
AREAS = [
    ("net/minecraft/network/", "프로토콜"),
    ("net/minecraft/world/level/levelgen/", "월드 생성"),
    ("net/minecraft/world/level/biome/", "월드 생성"),
    ("net/minecraft/world/level/dimension/", "월드 생성"),
    ("net/minecraft/world/level/storage/", "저장·데이터 올리기"),
    ("net/minecraft/world/level/chunk/storage/", "저장·데이터 올리기"),
    ("net/minecraft/nbt/", "저장·데이터 올리기"),
    ("net/minecraft/util/datafix/", "저장·데이터 올리기"),
    ("net/minecraft/world/entity/", "엔티티"),
    ("net/minecraft/world/level/block/", "블록·아이템"),
    ("net/minecraft/world/item/", "블록·아이템"),
    ("net/minecraft/world/inventory/", "블록·아이템"),
    ("net/minecraft/world/food/", "블록·아이템"),
    ("net/minecraft/world/level/", "월드 규칙(틱·빛·유체·청크)"),
    ("net/minecraft/world/", "월드 규칙(틱·빛·유체·청크)"),
    ("net/minecraft/server/", "서버·명령"),
    ("net/minecraft/commands/", "서버·명령"),
    ("net/minecraft/core/", "데이터·레지스트리"),
    ("net/minecraft/resources/", "데이터·레지스트리"),
    ("net/minecraft/tags/", "데이터·레지스트리"),
    ("net/minecraft/data/", "데이터·레지스트리"),
    ("net/minecraft/advancements/", "데이터·레지스트리"),
    ("net/minecraft/stats/", "데이터·레지스트리"),
    ("net/minecraft/sounds/", "데이터·레지스트리"),
    ("com/mojang/", "라이브러리(브리지)"),
]


def area(cls):
    for p, a in AREAS:
        if cls.startswith(p):
            return a
    if cls.startswith(("org/bukkit/", "io/papermc/", "com/destroystokyo/")):
        parts = cls.split("/")
        depth = 3 if cls.startswith("org/bukkit/") else 4
        return "API " + ("/".join(parts[:depth]) if len(parts) > depth else "/".join(parts[:depth - 1]) + " (루트)")
    return "기타"


# ───────────────────────── 읽기 ─────────────────────────

def read_tsv(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            yield line.rstrip("\n").split("\t")


def acc(x):
    """접근 플래그 문자열에서 ACC_DEPRECATED(0x20000, 애노테이션이 켜는 asm 가상 플래그)를 뺀다."""
    return str(int(x) & ~0x20000) if x.lstrip("-").isdigit() else x


class Sig:
    def __init__(self, path):
        self.classes = {}      # name -> (access, super, interfaces, signature, inner)
        self.members = collections.defaultdict(dict)  # owner -> {(kind,name,desc): (access, signature)}
        for r in read_tsv(path):
            if r[0] == "C":
                self.classes[r[2]] = (acc(r[1]), r[3], r[4], r[5], acc(r[6]))
            else:
                self.members[r[2]][(r[0], r[3], r[4])] = (acc(r[1]), r[5])


class Body:
    def __init__(self, path):
        self.methods = {}   # (owner,name,desc) -> hash
        self.lambdas = {}   # owner -> (count, hash)
        self.consts = {}    # (owner,name,desc) -> value
        for r in read_tsv(path):
            if r[0] == "M":
                self.methods[(r[1], r[2], r[3])] = (r[4], r[5])   # (모양 해시, 참조 해시)
            elif r[0] == "L":
                self.lambdas[r[1]] = (r[2], r[3], r[4])           # (개수, 모양 묶음, 참조 묶음)
            elif r[0] == "K":
                self.consts[(r[1], r[2], r[3])] = r[4]


def vdir(v):
    return os.path.join(CACHE, v)


def jload(p):
    with open(p, encoding="utf-8") as f:
        return json.load(f)


def sha1(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()


def nbt_fingerprint(raw):
    """구조물 NBT: gzip 을 풀고 최상위 DataVersion(int) 4바이트만 0 으로 가린 뒤 해시한다 — 판이 바뀌면 파일마다 이 값만 올라 모든 파일이 '변경'으로 보이므로."""
    try:
        b = gzip.decompress(raw)
    except OSError:
        b = raw
    key = b"\x03\x00\x0bDataVersion"
    i = b.find(key)
    if i >= 0:
        j = i + len(key)
        b = b[:j] + b"\0\0\0\0" + b[j + 4:]
    return hashlib.sha1(b).hexdigest()


def canon(x):
    return json.dumps(x, sort_keys=True, separators=(",", ":"))


# ───────────────────────── 비교 ─────────────────────────

class Out:
    def __init__(self):
        self.rows = []              # (종류, 영역, 소유자, 멤버, 자세히)
        self.counts = collections.Counter()           # (영역, 종류) → 개수
        self.top = collections.defaultdict(collections.Counter)  # 종류 → 패키지 → 개수

    def add(self, kind, ar, owner, member="", detail=""):
        self.rows.append((kind, ar, owner, member, detail))
        self.counts[(ar, kind)] += 1
        pkg = "/".join(owner.split("/")[:5]) if "/" in owner else owner
        self.top[kind][pkg] += 1


def diff_code(a, b, ba, bb, shim, out):
    ca, cb = set(a.classes), set(b.classes)
    removed, added = sorted(ca - cb), sorted(cb - ca)
    simple_added = collections.defaultdict(list)
    for n in added:
        simple_added[n.rsplit("/", 1)[-1]].append(n)
    for n in removed:
        s = n.rsplit("/", 1)[-1]
        cand = [x for x in simple_added.get(s, []) if x != n]
        out.add("클래스 삭제", area(n), n, "", ("이동·이름 변경 후보: " + cand[0]) if cand else "")
    for n in added:
        out.add("클래스 추가", area(n), n)
    shared = sorted(ca & cb)
    changed_decl_classes = set(removed) | set(added)
    for n in shared:
        x, y = a.classes[n], b.classes[n]
        if x != y:
            parts = []
            if x[0] != y[0]:
                parts.append(f"접근 {x[0]}→{y[0]}")
            if x[1] != y[1]:
                parts.append(f"상위 {x[1]}→{y[1]}")
            if x[2] != y[2]:
                parts.append(f"인터페이스 {x[2]}→{y[2]}")
            if x[3] != y[3] and not parts:
                parts.append("제네릭 서명")
            if x[4] != y[4] and not parts:
                parts.append(f"안쪽접근 {x[4]}→{y[4]}")
            if parts:
                out.add("클래스 선언 변경", area(n), n, "", "; ".join(parts))
                changed_decl_classes.add(n)
        ma, mb = a.members.get(n, {}), b.members.get(n, {})
        ga, gb = collections.defaultdict(set), collections.defaultdict(set)
        for (k, nm, d) in ma:
            ga[(k, nm)].add(d)
        for (k, nm, d) in mb:
            gb[(k, nm)].add(d)
        for key in sorted(set(ga) | set(gb)):
            da, db = ga.get(key, set()), gb.get(key, set())
            if da == db:
                for d in da:
                    if ma[(key[0], key[1], d)][0] != mb[(key[0], key[1], d)][0]:
                        out.add("멤버 접근 변경", area(n), n, f"{key[1]}{d}", f"{ma[(key[0], key[1], d)][0]}→{mb[(key[0], key[1], d)][0]}")
                        changed_decl_classes.add(n)
                continue
            changed_decl_classes.add(n)
            kind = "필드" if key[0] == "F" else "메서드"
            if da and db:
                out.add(f"{kind} 서술자 변경", area(n), n, key[1], f"{sorted(da)} → {sorted(db)}")
            elif da:
                out.add(f"{kind} 삭제", area(n), n, key[1], str(sorted(da)))
            else:
                out.add(f"{kind} 추가", area(n), n, key[1], str(sorted(db)))
    # 삭제된 클래스의 멤버는 클래스 삭제에 포함해 세지 않는다(멤버 수는 요약에만)
    # 본문
    body_changed_plain = 0
    for key in sorted(set(ba.methods) & set(bb.methods)):
        (sa, ra), (sb, rb) = ba.methods[key], bb.methods[key]
        if (sa, ra) != (sb, rb):
            o = key[0]
            if o not in ca or o not in cb:
                continue
            if sa != sb:
                out.add("본문 변경 후보(흐름)", area(o), o, f"{key[1]}{key[2]}")
                if o not in changed_decl_classes:
                    body_changed_plain += 1
            else:
                out.add("본문 참조만 변경(이름 바뀜·옮김 후보)", area(o), o, f"{key[1]}{key[2]}")
    for o in sorted(set(ba.lambdas) & set(bb.lambdas)):
        la, lb = ba.lambdas[o], bb.lambdas[o]
        if la != lb and o in ca and o in cb:
            if la[:2] != lb[:2]:
                out.add("람다 묶음 변경 후보(흐름)", area(o), o, "", f"{la[0]}→{lb[0]}개")
            else:
                out.add("람다 묶음 참조만 변경", area(o), o, "", f"{la[0]}개")
    for o in sorted(set(bb.lambdas) - set(ba.lambdas)):
        if o in ca and o in cb:
            out.add("람다 묶음 생김·사라짐", area(o), o, "", "람다 생김")
    for o in sorted(set(ba.lambdas) - set(bb.lambdas)):
        if o in ca and o in cb:
            out.add("람다 묶음 생김·사라짐", area(o), o, "", "람다 사라짐")
    for key in sorted(set(ba.consts) | set(bb.consts)):
        va, vb = ba.consts.get(key), bb.consts.get(key)
        if va == vb:
            continue
        o = key[0]
        if va is None:
            out.add("상수 추가", area(o), o, key[1], vb)
        elif vb is None:
            out.add("상수 삭제", area(o), o, key[1], va)
        else:
            out.add("상수 값 변경", area(o), o, key[1], f"{va} → {vb}")

    stats = {
        "classes_a": len(ca), "classes_b": len(cb), "classes_removed": len(removed), "classes_added": len(added),
        "methods_a": sum(1 for _ in ba.methods), "methods_b": sum(1 for _ in bb.methods),
        "body_changed_in_decl_stable_classes": body_changed_plain,
    }
    if shim is not None:
        stats["shim"] = shim_impact(shim, a, b, ba, bb, out)
    return stats


def shim_impact(shim, a, b, ba, bb, out):
    """손으로 만든 shim 이 선언한 바닐라 멤버가 새 판에서 어떻게 되는지(shim 표의 멤버를 이전 판 표와 새 판 표에 대고)."""
    res = collections.Counter()
    for n in shim.classes:
        if n not in a.classes:
            continue  # 바닐라에 없는(Furnace·CraftBukkit 쪽) 클래스
        res["classes_vanilla"] += 1
        if n not in b.classes:
            res["classes_gone"] += 1
            out.add("shim 영향: 클래스 사라짐", area(n), n)
            continue
        if a.classes[n] != b.classes[n]:
            res["classes_decl_changed"] += 1
        for (k, nm, d) in shim.members.get(n, {}):
            if (k, nm, d) not in a.members.get(n, {}):
                continue  # 이전 판 바닐라 멤버가 아님(Furnace 도우미·Paper 가 더한 멤버)
            res["members_vanilla"] += 1
            if (k, nm, d) in b.members.get(n, {}):
                x1, x2 = ba.methods.get((n, nm, d)), bb.methods.get((n, nm, d))
                if k == "M" and x1 is not None and x2 is not None and x1[0] != x2[0]:
                    res["members_same_flow_changed"] += 1
                    out.add("shim 영향: 흐름 변경(동작 확인)", area(n), n, f"{nm}{d}")
                elif k == "M" and x1 is not None and x2 is not None and x1 != x2:
                    res["members_same_refs_changed"] += 1
                else:
                    res["members_same"] += 1
            else:
                same_name = [dd for (kk, nn, dd) in b.members.get(n, {}) if kk == k and nn == nm]
                if same_name:
                    res["members_desc_changed"] += 1
                    out.add("shim 영향: 서술자 변경", area(n), n, nm, f"{d} → {same_name}")
                else:
                    res["members_gone"] += 1
                    out.add("shim 영향: 멤버 사라짐", area(n), n, f"{nm}{d}")
    return dict(res)


def diff_data(A, B, out):
    ga, gb = os.path.join(vdir(A), "generated"), os.path.join(vdir(B), "generated")
    st = {}
    # 패킷
    pa, pb = jload(f"{ga}/reports/packets.json"), jload(f"{gb}/reports/packets.json")
    pk = collections.Counter()
    for state in sorted(set(pa) | set(pb)):
        for dr in ("clientbound", "serverbound"):
            xa, xb = pa.get(state, {}).get(dr, {}), pb.get(state, {}).get(dr, {})
            for n in sorted(set(xa) | set(xb)):
                if n not in xa:
                    out.add("패킷 추가", "프로토콜", f"{state}/{dr}", n, f"id {xb[n]['protocol_id']}"); pk["added"] += 1
                elif n not in xb:
                    out.add("패킷 삭제", "프로토콜", f"{state}/{dr}", n, f"id {xa[n]['protocol_id']}"); pk["removed"] += 1
                elif xa[n] != xb[n]:
                    out.add("패킷 번호 변경", "프로토콜", f"{state}/{dr}", n, f"{xa[n]['protocol_id']}→{xb[n]['protocol_id']}"); pk["id_changed"] += 1
                else:
                    pk["same"] += 1
    st["packets"] = dict(pk)
    # 레지스트리
    ra, rb = jload(f"{ga}/reports/registries.json"), jload(f"{gb}/reports/registries.json")
    rk = collections.Counter()
    for r in sorted(set(ra) | set(rb)):
        if r not in ra:
            out.add("레지스트리 추가", "데이터·레지스트리", r, "", f"항목 {len(rb[r]['entries'])}"); rk["registries_added"] += 1; continue
        if r not in rb:
            out.add("레지스트리 삭제", "데이터·레지스트리", r, "", f"항목 {len(ra[r]['entries'])}"); rk["registries_removed"] += 1; continue
        ea, eb = ra[r]["entries"], rb[r]["entries"]
        for n in sorted(set(ea) | set(eb)):
            if n not in ea:
                out.add("레지스트리 항목 추가", "데이터·레지스트리", r, n); rk["entries_added"] += 1
            elif n not in eb:
                out.add("레지스트리 항목 삭제", "데이터·레지스트리", r, n); rk["entries_removed"] += 1
            elif ea[n] != eb[n]:
                rk["entries_id_shifted"] += 1
        if ra[r].get("protocol_id") != rb[r].get("protocol_id"):
            rk["registry_id_changed"] += 1
    st["registries"] = dict(rk)
    # 블록
    ba, bb = jload(f"{ga}/reports/blocks.json"), jload(f"{gb}/reports/blocks.json")
    bk = collections.Counter()
    for n in sorted(set(ba) | set(bb)):
        if n not in ba:
            out.add("블록 추가", "블록·아이템", n, "", f"상태 {len(bb[n]['states'])}개"); bk["added"] += 1; continue
        if n not in bb:
            out.add("블록 삭제", "블록·아이템", n); bk["removed"] += 1; continue
        x, y = ba[n], bb[n]
        if x.get("properties") != y.get("properties"):
            out.add("블록 속성 변경", "블록·아이템", n, "", f"{canon(x.get('properties'))[:80]} → {canon(y.get('properties'))[:80]}"); bk["properties_changed"] += 1
        elif x.get("definition") != y.get("definition"):
            out.add("블록 정의 변경", "블록·아이템", n); bk["definition_changed"] += 1
        else:
            bk["same_props"] += 1
        ia = min(s["id"] for s in x["states"])
        ib = min(s["id"] for s in y["states"])
        if ia != ib:
            bk["first_state_id_moved"] += 1
    bk["total_a"], bk["total_b"] = len(ba), len(bb)
    bk["states_a"] = sum(len(v["states"]) for v in ba.values())
    bk["states_b"] = sum(len(v["states"]) for v in bb.values())
    st["blocks"] = dict(bk)
    # 아이템
    ia, ib = jload(f"{ga}/reports/items.json"), jload(f"{gb}/reports/items.json")
    ik = collections.Counter()
    for n in sorted(set(ia) | set(ib)):
        if n not in ia:
            out.add("아이템 추가", "블록·아이템", n); ik["added"] += 1
        elif n not in ib:
            out.add("아이템 삭제", "블록·아이템", n); ik["removed"] += 1
        elif ia[n] != ib[n]:
            ca, cb = ia[n].get("components", {}), ib[n].get("components", {})
            keys = sorted(k for k in set(ca) | set(cb) if ca.get(k) != cb.get(k))
            out.add("아이템 기본 컴포넌트 변경", "블록·아이템", n, "", ",".join(keys)); ik["components_changed"] += 1
    ik["total_a"], ik["total_b"] = len(ia), len(ib)
    st["items"] = dict(ik)
    # 명령 트리
    def flat(node, path, d):
        d[path] = canon({k: v for k, v in node.items() if k != "children"})
        for k, v in (node.get("children") or {}).items():
            flat(v, path + "/" + k, d)
    fa, fb = {}, {}
    flat(jload(f"{ga}/reports/commands.json"), "", fa)
    flat(jload(f"{gb}/reports/commands.json"), "", fb)
    ck = collections.Counter()
    for p in sorted(set(fa) | set(fb)):
        if p not in fa:
            out.add("명령 노드 추가", "서버·명령", p); ck["added"] += 1
        elif p not in fb:
            out.add("명령 노드 삭제", "서버·명령", p); ck["removed"] += 1
        elif fa[p] != fb[p]:
            out.add("명령 노드 변경", "서버·명령", p); ck["changed"] += 1
    st["command_nodes"] = dict(ck)
    # 데이터팩 레지스트리 표
    def opt(p):
        return jload(p) if os.path.isfile(p) else None
    da, db = opt(f"{ga}/reports/datapack.json"), opt(f"{gb}/reports/datapack.json")
    if (da is None) != (db is None):
        out.add("데이터팩 레지스트리 표", "데이터·레지스트리", "reports/datapack.json", "", "한쪽 판에만 있음(새 보고서)")
        da = db = None
    for sect in ("registries", "others") if da is not None else ():
        for n in sorted(set(da.get(sect, {})) | set(db.get(sect, {}))):
            if da[sect].get(n) != db[sect].get(n):
                out.add("데이터팩 레지스트리 변경", "데이터·레지스트리", n, "", f"{canon(da[sect].get(n))} → {canon(db[sect].get(n))}")
    # 생성된 data/assets 파일
    def tree(root):
        d = {}
        for base in ("data", "assets"):
            for dp, _, fs in os.walk(os.path.join(root, base)):
                for f in fs:
                    p = os.path.join(dp, f)
                    d[os.path.relpath(p, root).replace(os.sep, "/")] = sha1(p)
        return d
    ta, tb = tree(ga), tree(gb)
    fk = collections.Counter()
    def group(p):
        s = p.split("/")
        if len(s) > 3 and s[2] == "worldgen":
            return "/".join(s[:4])
        return "/".join(s[:3])
    for p in sorted(set(ta) | set(tb)):
        g = group(p)
        if p not in ta:
            out.add("데이터 파일 추가", "데이터·레지스트리", g, p); fk["added"] += 1
        elif p not in tb:
            out.add("데이터 파일 삭제", "데이터·레지스트리", g, p); fk["removed"] += 1
        elif ta[p] != tb[p]:
            out.add("데이터 파일 변경", "데이터·레지스트리", g, p); fk["changed"] += 1
        else:
            fk["same"] += 1
    st["data_files"] = dict(fk)
    # jar 안 클래스 아닌 자원(구조물 NBT 등)
    ja, jb = {}, {}
    for v, d in ((A, ja), (B, jb)):
        with zipfile.ZipFile(os.path.join(vdir(v), "work", "server-inner.jar")) as z:
            for i in z.infolist():
                if i.is_dir() or i.filename.endswith(".class") or i.filename.startswith("META-INF/"):
                    continue
                if i.filename.endswith(".nbt"):
                    d[i.filename] = nbt_fingerprint(z.read(i.filename))
                else:
                    d[i.filename] = i.CRC
    jk = collections.Counter()
    for p in sorted(set(ja) | set(jb)):
        g = "/".join(p.split("/")[:3])
        if p not in ja:
            out.add("jar 자원 추가", "데이터·레지스트리", g, p); jk["added"] += 1
        elif p not in jb:
            out.add("jar 자원 삭제", "데이터·레지스트리", g, p); jk["removed"] += 1
        elif ja[p] != jb[p]:
            out.add("jar 자원 변경", "데이터·레지스트리", g, p); jk["changed"] += 1
    st["jar_resources"] = dict(jk)
    # 판 상수(jar 의 version.json)
    ver = {}
    for v in (A, B):
        with zipfile.ZipFile(os.path.join(vdir(v), "work", "server-inner.jar")) as z:
            try:
                ver[v] = json.loads(z.read("version.json"))
            except KeyError:
                ver[v] = {}
    st["version_json"] = ver
    return st


DECL_KINDS = ("클래스 삭제", "클래스 선언 변경", "메서드 삭제", "메서드 서술자 변경", "필드 삭제", "필드 서술자 변경", "멤버 접근 변경")
ADD_KINDS = ("메서드 추가", "필드 추가")
BODY_KINDS = ("본문 변경 후보(흐름)", "람다 묶음 변경 후보(흐름)", "상수 값 변경", "상수 추가", "상수 삭제")
WEAK_KINDS = ("본문 참조만 변경(이름 바뀜·옮김 후보)", "람다 묶음 참조만 변경")


def furnace_area(path):
    """Furnace 파일 경로 → 영역 이름(furnace-server/.../server/<패키지>/...)."""
    seg = path.replace("\\", "/").split("/server/", 1)
    if len(seg) < 2:
        return "기타"
    p = seg[1].split("/")
    if p[0] == "world" and len(p) > 2 and p[1] == "gen":
        return "world/gen"
    return p[0] if len(p) > 1 else "(루트)"


def furnace_impact(port_map, out):
    """바닐라 클래스(바깥 클래스 기준) ↔ Furnace 파일 대응표(V-06)와 이어 '고칠 Furnace 파일' 후보를 센다."""
    by_van = collections.defaultdict(set)   # 바닐라 바깥 클래스(점 표기) → {(Furnace 클래스, 파일)}
    all_files = {}
    with open(port_map, encoding="utf-8") as f:
        for line in f:
            r = line.rstrip("\n").split("\t")
            if len(r) >= 3:
                by_van[r[0]].add((r[1], r[2]))
                all_files[r[2]] = r[1]
    tier = {}  # 파일 → 'A'(선언·삭제 변경) 또는 'B'(본문 변경 후보만)
    why = collections.defaultdict(set)
    for kind, ar, owner, member, detail in out.rows:
        if kind in DECL_KINDS:
            t = "A"
        elif kind in ADD_KINDS:
            t = "C"
        elif kind in BODY_KINDS:
            t = "B"
        elif kind in WEAK_KINDS:
            t = "D"
        else:
            continue
        outer = owner.split("$")[0].replace("/", ".")
        for fc, fp in by_van.get(outer, ()):
            if "ACBD".index(t) < "ACBD".index(tier.get(fp, "D")) or fp not in tier:
                tier[fp] = t
            why[fp].add(outer.rsplit(".", 1)[-1] + ":" + kind)
    counts = collections.Counter()
    per_area = collections.defaultdict(collections.Counter)
    total_area = collections.Counter(furnace_area(p) for p in all_files)
    for fp, t in tier.items():
        counts[t] += 1
        per_area[furnace_area(fp)][t] += 1
    # 바닐라에서 사라진 클래스를 이식한 Furnace 파일은 따로
    gone = sorted({fp for k, a, o, m, d in out.rows if k == "클래스 삭제" for _, fp in by_van.get(o.split("$")[0].replace("/", "."), ()) if "$" not in o})
    return {
        "ported_files": len(all_files), "files_A_decl_changed": counts["A"], "files_C_additions_only": counts["C"], "files_B_flow_only": counts["B"], "files_D_refs_only": counts["D"],
        "files_whose_vanilla_class_removed": len(gone),
        "by_area": {a: {"ported": total_area[a], "A": per_area[a]["A"], "C": per_area[a]["C"], "B": per_area[a]["B"], "D": per_area[a]["D"]} for a in sorted(total_area)},
    }, tier, why


# ───────────────────────── 보고서 ─────────────────────────

def write_reports(A, B, out, stats, outdir, safe, tbl=None):
    os.makedirs(outdir, exist_ok=True)
    base = os.path.join(outdir, f"version-diff-{tbl + '-' if tbl else ''}{A}-{B}")
    kinds = sorted({k for (_, k) in out.counts})
    areas = sorted({a for (a, _) in out.counts})
    total = collections.Counter()
    for (a, k), n in out.counts.items():
        total[k] += n
    with open(base + ".tsv", "w", encoding="utf-8") as f:
        f.write("종류\t영역\t소유자\t멤버\t자세히\n")
        for r in out.rows:
            f.write("\t".join(x.replace("\t", " ").replace("\n", " ") for x in r) + "\n")
    summary = {"from": A, "to": B, "stats": stats, "totals": dict(total),
               "by_area": {a: {k: out.counts[(a, k)] for k in kinds if out.counts[(a, k)]} for a in areas}}
    with open(base + ".json", "w", encoding="utf-8") as f:
        json.dump(summary, f, ensure_ascii=False, indent=1, sort_keys=True)
    def table(include_names):
        L = []
        L.append("| 종류 | 합계 | " + " | ".join(areas) + " |")
        L.append("|---|---:|" + "---:|" * len(areas))
        for k in kinds:
            L.append(f"| {k} | {total[k]} | " + " | ".join(str(out.counts[(a, k)] or "") for a in areas) + " |")
        return "\n".join(L)
    head = f"# {tbl or '바닐라'} {A} → {B} 차이 (자동 생성)\n\n"
    s = stats
    body = [head, f"클래스 {s['classes_a']} → {s['classes_b']} (삭제 {s['classes_removed']}, 추가 {s['classes_added']}). 메서드 본문 지문 {s['methods_a']} → {s['methods_b']}.\n",
            "## 영역별 개수\n", table(True), "\n"]
    for key in ("packets", "registries", "blocks", "items", "command_nodes", "data_files", "jar_resources"):
        if key in stats:
            body.append(f"- {key}: {canon(stats[key])}")
    if not tbl:
        body.append(f"- 같은 선언 클래스 안에서 흐름이 바뀐 메서드: {s['body_changed_in_decl_stable_classes']}")
    if "shim" in s:
        body.append(f"- shim 영향: {canon(s['shim'])}")
    if "furnace_impact" in s:
        fi = s["furnace_impact"]
        body.append(f"- Furnace 이식 파일 {fi['ported_files']}개 중 바닐라 선언이 삭제·서술자·상위가 바뀐 것(A) {fi['files_A_decl_changed']}, 멤버 추가만(C) {fi['files_C_additions_only']}, 본문 흐름만 바뀐 후보(B) {fi['files_B_flow_only']}, 참조만 바뀐 후보(D) {fi['files_D_refs_only']}, 바닐라 클래스가 사라진 것 {fi['files_whose_vanilla_class_removed']}")
        for a, v in fi["by_area"].items():
            body.append(f"  - {a}: 이식 {v['ported']} / A {v['A']} / C {v['C']} / B {v['B']} / D {v['D']}")
    if "version_json" in stats:
        body.append(f"- 판 상수(jar version.json): {canon(stats['version_json'])}\n")
    safe_text = "".join(x if x.endswith("\n") else x + "\n" for x in body)
    with open(base + ".md", "w", encoding="utf-8") as f:
        f.write(safe_text)
        f.write("\n## 종류별 패키지 상위\n")
        for k in kinds:
            f.write(f"\n### {k}\n")
            for p, n in out.top[k].most_common(25):
                f.write(f"- {p}: {n}\n")
    if safe:
        with open(safe, "w", encoding="utf-8") as f:
            f.write("<!-- version-diff.py 가 만든 개수 요약. Mojang 이름 없음 -->\n")
            f.write(safe_text)
    print(f"[version-diff] {base}.md / .tsv / .json ({len(out.rows)}줄)")
    return base


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("a")
    ap.add_argument("b")
    ap.add_argument("--out", default=os.path.join(CACHE, "diffs"))
    ap.add_argument("--shim-table", help="컴파일한 shim 의 서명표(sig-table-shim.tsv.gz)")
    ap.add_argument("--safe", help="개수만 적은 커밋용 요약을 쓸 파일")
    ap.add_argument("--port-map", help="tools/vanilla-port/vanilla-port-map.py 가 만든 build/vanilla-port-map.tsv — 고칠 Furnace 파일 후보를 센다")
    ap.add_argument("--table", help="서명표 이름(예: paper-api → sig-table-paper-api.tsv.gz). 주면 선언 차이만 낸다(본문·데이터 없음)")
    ap.add_argument("--no-data", action="store_true")
    o = ap.parse_args()
    tname = f"sig-table-{o.table}.tsv.gz" if o.table else "sig-table.tsv.gz"
    need = (tname,) if o.table else ("sig-table.tsv.gz", "body-table.tsv.gz")
    for v in (o.a, o.b):
        for f in need:
            if not os.path.isfile(os.path.join(vdir(v), f)):
                sys.exit(f"{v}/{f} 없음 — ./gradlew -PskipOracle=true :vanilla-extract:run -Pmc={v} -Pmode=sig-table|body-table|reports (paper-api 는 -PsigPaperApi=true)")
    a, b = Sig(os.path.join(vdir(o.a), tname)), Sig(os.path.join(vdir(o.b), tname))
    if o.table:
        ba = bb = Body.__new__(Body)
        ba.methods, ba.lambdas, ba.consts = {}, {}, {}
        o.no_data = True
    else:
        ba, bb = Body(os.path.join(vdir(o.a), "body-table.tsv.gz")), Body(os.path.join(vdir(o.b), "body-table.tsv.gz"))
    shim = Sig(o.shim_table) if o.shim_table else None
    out = Out()
    stats = diff_code(a, b, ba, bb, shim, out)
    if not o.no_data:
        stats.update(diff_data(o.a, o.b, out))
    if o.port_map:
        stats["furnace_impact"], tier, why = furnace_impact(o.port_map, out)
        os.makedirs(o.out, exist_ok=True)
        with open(os.path.join(o.out, f"furnace-impact-{o.a}-{o.b}.tsv"), "w", encoding="utf-8") as f:
            f.write("등급\tFurnace 파일\t이유(바닐라 클래스:종류)\n")
            for fp in sorted(tier, key=lambda x: (tier[x], x)):
                f.write(f"{tier[fp]}\t{fp}\t{'; '.join(sorted(why[fp]))[:300]}\n")
    write_reports(o.a, o.b, out, stats, o.out, o.safe, o.table)


if __name__ == "__main__":
    main()
