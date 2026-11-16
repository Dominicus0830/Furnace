# T-198 블록 엔티티 데스크탑 FAWE 실측 (2026-11-16)

PR #447 브랜치로 만든 Furnace 서버에 FAWE 2.16.2 를 올려 `tools/compat/fawe-paper/scenario_t198.py` 를 Paper(133)와 같은 기준 월드에서 돌려 대조했다(`anvil-diff.py --ignore sectionKeys`).

## 결과

- 블록 칸 차이 0(빛·`keepPacked`·`Paper.*` 필드 제외). 줄 네 개(상자·덫 상자·스포너·화로·신호기)의 `//set stone`·`//set air`+`//undo`·`//copy`+`//stack`·`//move`, 다른 region 으로 `//stack`, 상자 512개 `//set`·`//undo`·`//copy`+`//stack` 모두 Paper 와 같은 블록. 내용물(`Items`)·스포너 몹 종류(`SpawnData`)·좌표·id 도 같다.
- 틱 시간(평균·최대 기준, 최근 5초/10초/1분 창의 `mspt` 한 번 — p95 가 아니다): 512 상자 `//set` 직후 Furnace region 틱 평균 0.33 ms·최대 4.25 ms(편집 전 0.36/4.92), Paper 평균 0.2~0.4 ms·최대 2.8 ms. 악화 없음.
- 빛 차이는 T-197 몫(이 장면의 수용 조건 밖).

## 실측에서 찾아 고친 것

- 신호기 `//set` 에서 Furnace 만 `Error removing beacon`: FAWE `removeBeacon` 이 `Level#isClientSide` 를 **필드**로 읽는데 shim 에는 메서드만 있었다(`UnsupportedNmsLink$Field`). 필드 추가 + `NmsBlockEntityWriteTest.beaconRemovalPath` 에 확인. 같은 장면을 다시 돌려 오류 0.
- 시나리오 B6~B9 가 보기 거리 밖이라 `That position is not loaded` 로 멈췄다(Paper 도 동일) — `forceload` 추가. B9 의 `pos1/pos2` 재지정은 "Position already set" 응답이라 스크립트가 멈춰 `//copy` 만 남겼다.

## 남은 차이(수용 조건 밖, 후속 과제 후보)

- **`keepPacked`**: 바닐라 서버 jar 에 이 키를 쓰는 클래스가 있고(문자열 확인 — 정확한 메서드는 미확인) Paper 는 저장하는 모든 블록 엔티티에 `keepPacked:0b` 를 넣는다. Furnace 는 자기가 저장한 타일에 넣지 않는다(FAWE 와 무관 — `setblock` 상자도 같음). Furnace 에 있는 11개는 Paper 가 만든 기준 월드에서 읽어 와 그대로 되쓴 던전·광산 타일이다. 바닐라 대조 항목.
- `Paper.Range`(신호기)·`Paper.CookSpeedMultiplier`(화로)는 Paper 전용 필드라 Furnace 에 없는 게 맞다.
- **FAWE 편집 시간**: 상자 512개 `//set` 이 Paper 는 약 0.2 s 인데 Furnace 는 FAWE 보고 12.2 s(42 blocks/s), `//stack` 512개는 약 26 s. `//undo` 는 둘 다 약 0.2 s. 틱에는 영향이 없다.
  - **원인(스레드 덤프 12번, `jstack -l`)**: FAWE 작업 스레드가 `ChunkAccess.furnaceBlockEntitySave` 의 `CompletableFuture.get` 에서 기다린다 — 타일 하나를 저장할 때마다 주인 region 스케줄러에 작업을 맡기고 끝날 때까지 막는다(Paper 는 메인 스레드라 기다림 없음). `//set` 은 작업 스레드 3개(`FAWE QueueHandler Blocking Executor`)가 각자 기다려 3개/틱 ≈ 최대 60개/초(실측 42), `//stack` 은 스레드 1개(`AsyncNotifyKeyedQueue`)라 1개/틱 = 20개/초 → 512개 25.6 s(실측 25.7 s). "맡긴 작업을 주인이 다음 틱에 처리한다"는 스케줄러 코드로 확인했다 — `Region.runTick` 이 작업 큐를 틱 머리에서만 비운다.
  - **고침(같은 PR)**: 저장 왕복을 명령 큐 문(`CommandDrainGate`)과 같은 drain 대상으로 넣어(`ChunkInternals.submitAwaited`) 주인이 틱 사이에 쉬면 다음 틱을 기다리지 않고 곧바로 돈다(같은 예산 한 전역 틱 20 ms, 넘으면 예전처럼 다음 틱 머리). 클라우드 시험 서버에서 상자 24개 지우기+저장 73 ms → 8 ms.
  - **재측정(데스크탑, c6f7a055, Furnace 만, 상자 512개)**: `//set stone` 12.2 s → 0.8 s, `//stack` 25.7 s → 0.2 s(Paper 약 0.2 s). 512개 정상 변경, 신호기 오류 0. 틱(평균/최대, `mspt` 창 — p95 아님): `//set` 직전 0.11/0.27 ms, 직후 5초 창 0.80/15.83 ms(편집 구간 포함, 50 ms 안), `//stack` 7초 뒤 0.34/7.78 ms.
- **후속 과제 후보(한 줄)**: NMS shim 전체에서 "바닐라는 필드인데 shim 은 메서드만 있는 곳"(`Level#isClientSide` 같은 꼴)을 일괄 점검한다.
