#!/usr/bin/env python3
# Paper 1.21.1 NMS·CraftBukkit 멤버 이름표 생성(T-205): 호환 계층이 가진 NMS·CraftBukkit 클래스(와 그 조상)마다 진짜 Paper 1.21.1 클래스가 선언한
# 필드·메서드 이름만 모아 gzip 한 줄 형식 자원으로 쓴다. 리플렉션으로 멤버를 찾다 못 찾았을 때 "Paper 에도 없는 이름"(예: 1.21.1 에는 MinecraftServer.nextTickTime 이
# 없고 nextTickTimeNanos 만 있다)이면 경고하지 않으려고 쓴다 — 내용은 이름뿐이라 바닐라 코드·데이터는 들어 있지 않다.
#
# 사용(저장소 루트에서): python3 -I tools/compat/gen-paper-member-names.py <paper-1.21.1.jar> [호환 클래스 폴더]
#   <paper-1.21.1.jar> = PaperMC paperclip 을 한 번 돌려 만들어지는 versions/1.21.1/paper-1.21.1.jar(Mojang 이름).
#   [호환 클래스 폴더] 기본 = furnace-compat-paper/build/classes/java/main(먼저 :furnace-compat-paper:compileJava).
# 줄 형식: 클래스(내부 이름) TAB 상위 클래스 TAB 인터페이스(쉼표) TAB 필드(쉼표) TAB 메서드(쉼표) — 이름이 같은 오버로드는 한 번.
import gzip
import os
import re
import subprocess
import sys

OUT = 'furnace-compat-paper/src/main/resources/furnace-compat-nms/paper-1.21.1-members.txt.gz'

jar = sys.argv[1]
classes_dir = sys.argv[2] if len(sys.argv) > 2 else 'furnace-compat-paper/build/classes/java/main'


def ours():
    out = set()
    for root, _, files in os.walk(classes_dir):
        for f in files:
            if not f.endswith('.class'):
                continue
            rel = os.path.relpath(os.path.join(root, f), classes_dir)[:-6].replace(os.sep, '/')
            if rel.startswith('net/minecraft/') or rel.startswith('org/bukkit/craftbukkit/'):
                out.add(rel)
    return out


HEAD = re.compile(r'^(?:public |protected |private |abstract |final |static |sealed |non-sealed )*(?:class|interface|enum|record) ([\w.$]+)(?:<.*?>)?(?: extends ([\w.$<>,? ]+?))?(?: implements ([\w.$<>,? ]+?))?(?: permits .*)? \{$')


def split_types(s):
    # 제네릭 인자 속 쉼표는 건너뛰고 이름만 낸다
    out, depth, cur = [], 0, ''
    for ch in s:
        if ch == '<':
            depth += 1
        elif ch == '>':
            depth -= 1
        elif ch == ',' and depth == 0:
            out.append(cur.strip())
            cur = ''
            continue
        if depth == 0 and ch != '>':
            cur += ch
    if cur.strip():
        out.append(cur.strip())
    return [re.sub(r'<.*', '', x).strip() for x in out if x.strip()]


def javap(names):
    res = {}
    for i in range(0, len(names), 150):
        chunk = [n.replace('/', '.') for n in names[i:i + 150]]
        p = subprocess.run(['javap', '-p', '-cp', jar] + chunk, capture_output=True, text=True)
        cur = None
        for line in p.stdout.splitlines():
            if line.startswith(('Compiled from', 'Picked up')):
                continue
            m = HEAD.match(line)
            if m:
                name = m.group(1).replace('.', '/')
                cur = res[name] = {'super': '', 'ifaces': [], 'f': set(), 'm': set()}
                if m.group(2) and 'interface' not in line.split(' ' + m.group(1))[0]:
                    cur['super'] = split_types(m.group(2))[0].replace('.', '/')
                elif m.group(2):
                    cur['ifaces'] += [t.replace('.', '/') for t in split_types(m.group(2))]
                if m.group(3):
                    cur['ifaces'] += [t.replace('.', '/') for t in split_types(m.group(3))]
                continue
            if cur is None or not line.startswith('  '):
                continue
            body = line.strip()
            if not body.endswith(';'):
                continue
            body = body[:-1]
            if '(' in body:
                mm = re.search(r'([\w$<>]+)\(', body)
                if mm and not mm.group(1).startswith('<'):
                    cur['m'].add(mm.group(1))
            else:
                mm = re.search(r'([\w$]+)$', body.split('=')[0].strip())
                if mm:
                    cur['f'].add(mm.group(1))
    return res


todo = sorted(ours())
table = {}
while todo:
    got = javap(todo)
    for k, v in got.items():
        table[k] = v
    nxt = set()
    for v in got.values():
        for a in [v['super']] + v['ifaces']:
            # 조상 중 NMS·CraftBukkit·Moonrise·Mojang 클래스만(자바·라이브러리는 표에 넣지 않는다)
            if a and a not in table and (a.startswith('net/minecraft/') or a.startswith('org/bukkit/craftbukkit/') or a.startswith('ca/spottedleaf/')):
                nxt.add(a)
    todo = sorted(nxt - set(table))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
with gzip.GzipFile(OUT, 'wb', mtime=0) as gz:
    for k in sorted(table):
        v = table[k]
        gz.write(('\t'.join([k, v['super'], ','.join(v['ifaces']), ','.join(sorted(v['f'])), ','.join(sorted(v['m']))]) + '\n').encode('utf-8'))
print(len(table), 'classes ->', OUT, os.path.getsize(OUT), 'bytes')
