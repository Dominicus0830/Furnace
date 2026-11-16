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
