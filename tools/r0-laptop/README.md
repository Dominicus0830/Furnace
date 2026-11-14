# R0 노트북 측정 꾸러미 (2차 CSP R0)

클라우드 R0 도구(공유 폴더 `furnace/reviews/opt-review-r2/R0-tools-cloud/`)를 Windows 에서도 돌도록 파이썬 하나(`r0win.py`)로 옮긴 것.
봇 `bot/o00walk.js` 의 시야 판정은 정사각 r ∩ 바닐라 원형(`ChunkTrackingView.isWithinDistance`, includeOuter=true)으로 A·B 가 같은 잣대다.

준비: `jars/A.jar`(07dec3da fatJar), `jars/B.jar`(a242b1af fatJar), `cd bot && npm install`, `pip install psutil`.
실행: `python r0win.py check` → `round <판> <변형...>` → `misc <판> <변형...>` → `agg` → `sum`.
변형: `A`, `Bv`(B + `entity-activation-range.enabled=false`), `Bd`(B 기본, D11 켬).
