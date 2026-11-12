#!/usr/bin/env python3
"""프로토콜 767(1.21.1) 패킷 번호 표를 바닐라 클래스에서 뽑는다 — 로컬 도구(바닐라 jar 는 저장소에 없다, 결과 표만 커밋).

쓰는 법:  protocol-ids-extract.py <압축 푼 server-1.21.1.jar 폴더> > protocol-767-ids.txt
(바닐라 번들러 jar 안 META-INF/versions/1.21.1/server-1.21.1.jar 를 풀어 둔 폴더. javap 가 PATH 에 있어야 한다.)

읽는 것: <상태>PacketTypes 의 static 초기화(필드 ← 와이어 이름·방향), <상태>Protocols 의 방향별 등록 람다(등록 순서 = 번호).
난독화 이름은 아래 표(1.21.1)에 고정 — 다른 판이면 표를 다시 맞춘다.
출력 한 줄: <protocol> <flow> <id> <holder>:<wire_name>   (holder = 그 패킷 종류가 선언된 PacketTypes 클래스의 상태 이름)
"""
import re, subprocess, sys

PACKET_TYPES = {"zy": "common", "abi": "configuration", "abq": "cookie", "agg": "game", "aik": "handshake",
                "aiu": "login", "ajk": "ping", "ajt": "status"}
PROTOCOLS = {"abj": "configuration", "agh": "game", "ail": "handshake", "aiv": "login", "aju": "status"}
ZI = "zi"       # PacketType
ZL = "zl"       # ProtocolInfoBuilder
ZH = {"a": "serverbound", "b": "clientbound"}      # PacketFlow 상수 SERVERBOUND, CLIENTBOUND (열거 순서)


def javap(root, cls):
    out = subprocess.run(["javap", "-p", "-c", "-constants", cls], cwd=root, capture_output=True, text=True).stdout
    return out


def types_of(root, cls):
    """필드 이름 -> (flow, 와이어 이름)"""
    text = javap(root, cls + ".class")
    # 팩토리 메서드 a/b 가 어느 방향 상수를 쓰는지(클래스마다 글자가 다르다)
    flow_of = {}
    for m in re.finditer(r'private static <T extends \S+> ' + ZI + r'<T> (\w)\(java/lang/String;\)|private static <T extends [^\n]*> ' + ZI + r'<T> (\w)\(java\.lang\.String\);\n    Code:\n(.*?)(?=\n\n)', text, re.S):
        if m.group(2):
            g = re.search(r'getstatic\s+#\d+\s+// Field zh\.(\w):', m.group(3))
            flow_of[m.group(2)] = ZH[g.group(1)]
    body = text[text.index("static {};"):]
    res, pending = {}, None
    for line in body.splitlines():
        m = re.search(r'ldc(?:_w)?\s+#\d+\s+// String (\S+)', line)
        if m:
            pending = [m.group(1), None]
            continue
        m = re.search(r'invokestatic\s+#\d+\s+// Method (\w):\(Ljava/lang/String;\)L' + ZI + ';', line)
        if m and pending:
            pending[1] = flow_of[m.group(1)]
            continue
        m = re.search(r'putstatic\s+#\d+\s+// Field (\w+):L' + ZI + ';', line)
        if m and pending and pending[1]:
            res[m.group(1)] = (pending[1], pending[0])
            pending = None
    return res


def delimiter_field(root, cls):
    """번들 구분자 클래스(zd 구현)의 type() 이 가리키는 PacketTypes 필드"""
    text = javap(root, cls + ".class")
    m = re.search(r'getstatic\s+#\d+\s+// Field (\w+):L' + ZI + ';', text)
    return m.group(1)


def main():
    root = sys.argv[1]
    types = {c: types_of(root, c) for c in PACKET_TYPES}
    print("# 프로토콜 767(Minecraft 1.21.1) 패킷 종류·번호 표 — tools/compat/protocol-ids-extract.py 로 바닐라 클래스에서 뽑은 사실(번호·이름)만 담았다.")
    print("# type <PacketTypes 상태> <방향> <와이어 이름>   : 그 <상태>PacketTypes 클래스가 선언한 종류(선언 순서)")
    print("# id <프로토콜> <방향> <번호> <PacketTypes 상태>:<와이어 이름> : 프로토콜 등록부의 번호(등록 순서). bundle 종류는 번호가 없다")
    for holder, name in sorted(PACKET_TYPES.items(), key=lambda kv: kv[1]):
        for fld, (flow, wire) in types[holder].items():     # 사전은 삽입(=선언) 순서
            print(f"type {name} {flow} {wire}")
    for proto_cls, proto in sorted(PROTOCOLS.items(), key=lambda kv: kv[1]):
        text = javap(root, proto_cls + ".class")
        # 등록 메서드 본문(람다) — static 초기화가 아닌 private static void x(zl)
        for m in re.finditer(r'private static void (\w+)\(' + ZL + r'\);\n    Code:\n(.*?)(?=\n  \S|\Z)', text, re.S):
            ids = []
            pending = None
            for line in m.group(2).splitlines():
                g = re.search(r'getstatic\s+#\d+\s+// Field (\w+)\.(\w+):L' + ZI + ';', line)
                if g:
                    pending = (g.group(1), g.group(2))
                    continue
                if re.search(r'invokevirtual\s+#\d+\s+// Method ' + ZL + r'\.a:\(L' + ZI + r';Ljava/util/function/Function;L\w+;\)L' + ZL + ';', line):
                    # 번들: 구분자 종류가 번호를 받는다(번들 종류 자체는 와이어 번호 없음)
                    n = re.search(r'new\s+#\d+\s+// class (\w+)', "\n".join(m.group(2).splitlines()[max(0, 0):]))
                    pending = ("BUNDLE", pending)
                    ids.append(pending)
                    pending = None
                    continue
                if re.search(r'invokevirtual\s+#\d+\s+// Method ' + ZL + r'\.a:\(L' + ZI + r';L\w+;\)L' + ZL + ';', line):
                    ids.append(pending)
                    pending = None
            # 방향 = 첫 종류의 방향
            first = ids[0]
            if first[0] == "BUNDLE":
                holder, fld = first[1]
                flow = types[holder][fld][0]
            else:
                flow = types[first[0]][first[1]][0]
            idx = 0
            for e in ids:
                if e[0] == "BUNDLE":
                    holder, fld = e[1]
                    # 구분자 종류: 클래스 acf(바닐라 ClientboundBundleDelimiterPacket) 가 가리키는 필드 — 번들 종류 다음 필드
                    names = sorted(types[holder].items(), key=lambda kv: kv[0])
                    wire = "bundle_delimiter"
                    print(f"id {proto} {flow} {idx} {PACKET_TYPES[holder]}:{wire}")
                else:
                    holder, fld = e
                    f, wire = types[holder][fld]
                    assert f == flow, (proto, holder, fld)
                    print(f"id {proto} {flow} {idx} {PACKET_TYPES[holder]}:{wire}")
                idx += 1


main()
