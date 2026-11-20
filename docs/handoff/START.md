# 시작 문서 (요약판, 약 4KB)

새 세션은 `CLAUDE.md` → `AGENTS.md` → 이 문서만 읽고 시작한다. 아래 큰 문서는 **통째로 읽지 않는다.** 필요한 절만 줄 범위로 읽는다(`Read` 의 offset/limit, 또는 `grep -n`). 이유: 전부 합치면 약 200KB(한국어 기준 십수만 토큰)라 세션마다 맥락을 낭비한다.

## 프로젝트 한 줄
Furnace = 처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버(Java 21/Gradle). 바닐라/Paper 와 관측 결과가 같고 더 빠른 것이 목표. 스레드 모델은 전역 스레드 1 + 동적 region 워커 풀(Folia 방식, `.claude/rules/scheduler.md`). 포팅은 난수 호출 순서까지 바닐라 그대로.

## 지금 상태는 어디서 보나 (문서에 현황 숫자를 박지 않는다 — 금방 낡는다)
- 최근 작업: `git log --oneline -20`, 열린 PR 목록.
- 사용자 확인·결정 대기: `tools/user-todo-index.sh 20`(최신 20개를 파일당 한 줄로, 합본 300KB 를 통째로 읽지 않는다) 뒤 필요한 파일만 `cat`. `docs/user-todo.md` 는 34KB 라 맨 위 표 구조만 보고, 필요한 줄만 grep.
- 로드맵 TASK 번호·상태: `python3 tools/ci/roadmap-index.py [T-2]`(한 줄 목록) 뒤 `grep -n "<ID>" docs/roadmap/optimization-tasks.md`. 표의 상태 칸은 낡을 수 있어 진행 여부는 열린 PR·`git log` 로 확인.
- 남은 구멍: `docs/feature-parity/unimplemented-survey-2026-10-31.md` 1장(일부 낡음 — 착수 전 코드로 확인).
- 로드맵: `docs/feature-parity/roadmap-2026-10-09.md`.

## 큰 문서 — 이렇게만 읽는다
| 문서 | 크기 | 읽는 법 |
|---|---|---|
| `docs/handoff/2026-10-31-session-handoff.md` | 24KB | 6장 "작업 방식에서 배운 것"(114줄~끝)만. 4장 "다음 할 일"은 2026-11-04 스냅샷이라 **낡았다**(현황은 위 목록·`git log`). 2~3장은 이력, 필요할 때 grep |
| `docs/handoff/2026-10-18-session-handoff.md` | 약 90KB(6~10장은 archive/) | **통째로 읽지 않는다.** 줄 범위만(색인 아래) |
| `docs/feature-parity/context-notes.md` | 342KB | 절대 통째로 읽지 않는다. `grep -n "<키워드>"` 로 찾은 절만. 새 기록은 `context-notes/` 폴더에 새 파일 |
| `docs/feature-parity/context-notes/` | 약 200개, 770KB | 이름(날짜-묶음 id)으로 고른 파일만 |
| `docs/roadmap/optimization-tasks.md` | 91KB | 위 `roadmap-index.py` 로 목록, 필요한 행만 grep |
| `docs/user-todo/entries/` | 136개, 245KB | 위 `user-todo-index.sh` 로 목록, 필요한 파일만 |
| `docs/ci.md` | 40KB | `grep -n '^#'` 로 절 제목을 보고 필요한 절만 |
| `docs/furnace-community-wishlist.md`, `docs/superpowers/plans/`(역사), `docs/audit/`, `docs/feature-parity/compat-nms-shim-audit-full-run-*-raw.md`(150KB 원자료) | 각 50~150KB | 찾는 주제가 있을 때만 grep. 원자료는 읽지 않는다 |
| `graphify-out/GRAPH_REPORT.md`, `graph.html` | 97KB, 1.3MB | 읽지 않는다. `graphify query/path/explain` 으로 묻는다 |
| `docs/user-todo.md` | 34KB | 위 참고 |
| `docs/feature-parity/*-YYYY-MM-DD.md` 보고서 | 다양 | 제목·머리 20줄을 먼저 보고 필요하면 이어서 |

옛 인수인계서(2026-10-18, 91KB)는 통째로 읽지 않는다. 줄 범위 색인: `docs/handoff/old-handoff-line-index.md`(6~10장은 `archive/` 로 옮김, 낡음).

## 토큰 아끼는 작업 습관
- 코드 위치는 `graphify query` 먼저, 그다음 `Grep` 으로 좁혀 필요한 줄 범위만 `Read`. 1000줄 넘는 파일 전체 읽기 금지.
- 시험·빌드 로그는 통째로 출력하지 않는다: `tools/ci/log-summary.sh <로그>` 로 결과·실패만 본다. 로그를 기다릴 때도 `tail -30` 대신 이 스크립트.
- 바꾼 영역만 `--tests` 로 돌리고 전체는 CI(6조각)에 맡긴다(`docs/ci.md`).
- PR·diff 검토는 `git diff --stat` 로 크기를 보고 파일별로 읽는다. 이미 본 diff 는 다시 읽지 말고 바뀐 커밋만(`git diff <본것>..HEAD`).
- 하위 에이전트에게는 필요한 파일 경로와 줄 범위를 지시에 적는다(그쪽이 다시 탐색하지 않게). 탐색만 시키는 일은 `furnace-scout`(haiku).
- 한 번 읽고 확정한 사실을 되풀이해 재확인하지 않는다.
