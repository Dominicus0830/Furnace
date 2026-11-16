# FAWE · Paper 대조 실측 (N2b-6)

`docs/feature-parity/compat-nms-n2b6-2026-11-16.md` 의 재현 스크립트. jar 는 저장소에 넣지 않는다.

준비(작업 폴더 `$N2B6_ROOT`, 기본은 이 폴더):
- `jars/paper-1.21.1-133.jar`(PaperMC), `jars/FastAsyncWorldEdit-Paper-2.16.2.jar`, `jars/furnace-server.jar`(`:furnace-server:fatJar`)
- `N2B6_VANILLA_CACHE` = `mojang_1.21.1.jar` 와 `vanilla-data-1.21.1/` 가 든 폴더(Furnace 가 처음 켤 때 `cache/` 에 받는 것)

순서:
1. `python -X utf8 gen_base.py` — Paper 로 기준 월드(시드 20261115, 16×16 청크) → `base/world`
2. `python -X utf8 scenario.py paper paper-fawe 25565`, `python -X utf8 scenario.py furnace furnace-fawe 25567` — 같은 편집 7가지 후 저장
3. `python -X utf8 control.py paper 25566`, `python -X utf8 control.py furnace 25568` — 플러그인 없는 대조군
4. `python -X utf8 ../anvil-diff.py runs/paper-fawe/world runs/furnace-fawe/world --chunks -8 -8 7 7 --ignore sectionKeys`

Furnace 쪽 FAWE 어댑터 실패 원인을 보려면 서버 JVM 에 `-Dworldedit.bukkit.adapter=com.sk89q.worldedit.bukkit.adapter.impl.fawe.v1_21_R1.PaperweightFaweAdapter`.

## T-198 블록 엔티티 장면

`scenario_t198.py` — 상자(내용물)·덫 상자·스포너·화로·신호기 줄 네 개를 `setblock` 으로 놓고 B1 `//set stone`, B2 `//set air` → B3 `//undo`, B4 `//copy`·`//stack 2 east`(콘솔에는 위치가 없어 `//paste` 대신 같은 붙여 넣기 길), B5 `//move 10 up`. 검토(PR #447) 위험 항목: B6 다른 region 으로 `//stack`(x<0 → x≥0), B7 상자 512개 `//set`(되돌리기 기록), B8 그 `//undo`, B9 상자 512개 `//copy`·`//stack`. 콘솔의 `TIME` 줄로 Paper 와 걸린 시간을, B7 앞뒤 `mspt` 로 틱 시간을 비교한다(지운 타일 꼴 뜨기가 주인 틱에서 돈다 — 틱 p95 +1 ms 합격선은 서버 측정 도구로 따로 잰다).

1. `python -X utf8 scenario_t198.py paper paper-t198 25565`, `python -X utf8 scenario_t198.py furnace furnace-t198 25567`
2. `python -X utf8 ../anvil-diff.py runs/paper-t198/world runs/furnace-t198/world --chunks -8 -8 7 7 --ignore sectionKeys` — 블록과 `block_entities`(내용물·몹 종류) 0차이가 수용 조건. 빛은 T-197 쪽.

## T-204 놓기 상태·부작용 장면(V5)

`scenario_t204.py` — FAWE `//set` 으로 울타리·계단·레드스톤 가루·판유리·담을 날것(안 이어진 꼴)으로 쓴 뒤 F1~F4 `//fixblocks`(PlacementStateProcessor → `getStateForPlacement`·`canBeReplaced`·`canSurvive`). 바닐라 놓기 계산이 읽기 밖으로 나가는 블록: F5 weeping vines·F6 켈프(나이 난수)·F7 물 없는 산호 블록(예약 틱) — Paper 의 FAWE 프록시는 null 필드라 NullPointerException, Furnace 는 UnsupportedNmsCall 이므로 FAWE 가 둘을 같게 처리하는지(작업 중단 여부·남은 블록)를 본다. N1 `//perf neighbors on` 뒤 `//set`(WNA 이웃 갱신), N2 `//perf update on`·`neighbors on` 뒤 공중 물 `//set`(예약 유체 틱, 흐름이 끝난 모양).

1. `python -X utf8 scenario_t204.py paper paper-t204 25565`, `python -X utf8 scenario_t204.py furnace furnace-t204 25567`
2. `python -X utf8 ../anvil-diff.py runs/paper-t204/world runs/furnace-t204/world --chunks -8 -8 7 7 --ignore sectionKeys` — 블록 0차이가 수용 조건.
3. 각 `runs/*/console.log` 에서 `MARK F5`·`F6`·`F7`·`N1` 사이 예외 줄과 FAWE 응답을 비교한다.
