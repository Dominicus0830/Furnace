#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""javap -v -p -c 출력(여러 클래스 연속)을 읽어 클래스마다 "명령까지 같은지" 견줄 수 있는 꼴로 정리해 <출력 폴더>/<클래스 경로 / → __>.txt 로 쓴다.
(tools/ci/javap-same.sh 가 부른다.)

지우는 것 — 의미가 아니라 "상수 풀 크기"에서 오는 차이뿐이다:
  · 상수 풀 표, 상수 풀 번호(#12), 체크섬·수정 시각·파일 경로, 줄 번호 표, 지역 변수 표
  · ldc_w ↔ ldc 차이(상수 풀 번호가 255 를 넘으면 명령이 한 바이트 길어져 뒤 명령 위치가 모두 밀린다)
  · 그래서 명령 위치(바이트 오프셋)는 "몇 번째 명령"으로, 분기 목표·switch 표·예외 표 범위도 같은 방식으로 바꿔 쓴다
  · StackMapTable(위치 차이만 담긴 파생 정보)
남기는 것: 필드 서술자·ConstantValue, 메서드 명령과 피연산자(ldc 값 주석 포함), BootstrapMethods(문자열 이어 붙이기 재료 포함).
"""
import re, sys, os

BRANCH = re.compile(r'^(if\w+|goto|goto_w|jsr|jsr_w)$')
INSN = re.compile(r'^(\s+)(\d+): ([a-z][a-z_0-9]*)(.*)$')
SWENT = re.compile(r'^(\s+)(-?\d+|default): (\d+)\s*$')
EXC = re.compile(r'^(\s+)(\d+)(\s+)(\d+)(\s+)(\d+)(\s+)(.*)$')

def norm_code(block):
    # block: Code: 한 덩어리의 줄들(들여쓰기 유지). 1차 — 명령 오프셋 → 순번
    order = {}
    n = 0
    for ln in block:
        m = INSN.match(ln)
        if m:
            order[int(m.group(2))] = n; n += 1
    end = n
    def o(x): return order.get(int(x), end)
    out = []
    skip_indent = None
    for ln in block:
        if skip_indent is not None:
            if ln.strip() and (len(ln) - len(ln.lstrip())) > skip_indent: continue
            skip_indent = None
        s = ln.strip()
        if s.startswith('StackMapTable') or s.startswith('LineNumberTable') or s.startswith('LocalVariableTable') or s.startswith('LocalVariableTypeTable'):
            skip_indent = len(ln) - len(ln.lstrip()); continue
        m = INSN.match(ln)
        if m:
            _, off, mn, rest = m.groups()
            if mn == 'ldc_w': mn = 'ldc'
            if BRANCH.match(mn):
                rest = ' ' + str(o(rest.strip()))
            out.append('  %d: %s%s' % (order[int(off)], mn, rest)); continue
        m = SWENT.match(ln)
        if m:
            out.append('    %s: %d' % (m.group(2), o(m.group(3)))); continue
        m = EXC.match(ln)
        if m and 'Exception table' not in ln:
            g = m.groups()
            out.append('  exc %d %d %d %s' % (o(g[1]), o(g[3]), o(g[5]), g[7])); continue
        out.append(ln)
    return out

def main(outdir, root):
    cur = None; code = None; fh = None; const_pool = False
    def flush_code():
        nonlocal code
        if code is not None:
            for l in norm_code(code): emit(l)
            code = None
    def emit(l):
        l = re.sub(r'#\d+', '#', l)
        l = re.sub(r' +', ' ', l)
        fh.write(l + '\n')
    for raw in sys.stdin:
        line = raw.rstrip('\n')
        if line.startswith('Classfile '):
            flush_code()
            if fh: fh.close()
            name = line.split(' ', 1)[1].strip()
            name = name[len(root):] if name.startswith(root) else name
            fh = open(os.path.join(outdir, name.replace('/', '__') + '.txt'), 'w', encoding='utf-8')
            const_pool = False
            continue
        if fh is None: continue
        if re.match(r'^  (Last modified|SHA-256|Compiled from)', line): continue
        if line.startswith('Constant pool:'): const_pool = True; continue
        if const_pool:
            if line.startswith('{'): const_pool = False
            else: continue
        if line.strip() == 'Code:':
            flush_code(); emit(line); code = []; continue
        if code is not None:
            # Code 블록 끝 = 들여쓰기 4칸 이하의 비어 있지 않은 줄(Exceptions:, 다음 메서드, 닫는 중괄호) 또는 빈 줄
            if line.strip() == '' or (len(line) - len(line.lstrip())) <= 4:
                flush_code(); emit(line); continue
            code.append(line); continue
        emit(line)
    flush_code()
    if fh: fh.close()

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
