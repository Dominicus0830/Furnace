# 컨텍스트 노트 — 원격 PR #1 선별 포팅

## 2026-10-07 결정과 근거
- 원격 `origin/main`은 PR #1(`claude/work-continuation-u597cw`, 4커밋)이 올라가 있고 공통조상이 2026-06-08이다. 로컬은 그 뒤 182커밋 동안 같은 파일(Region/World/ChunkColumn/ConnectionHandler)을 크게 바꿨다.
- 통머지를 시도하니 9개 파일에서 충돌(핵심 코드 6 + 테스트 3). 임의 해결은 위험해 중단했고, 항목별로 대조했다.
- 직접 재현으로 확인한 사실 — 청크 전달 레이스(전달된 청크가 편집 전 본문과 바이트 일치, BlockUpdate가 먼저 도착), 먼 좌표 파괴가 거부 없이 청크 생성(~0.5초, 첫 호출 ~1.9초), y=1000 파괴는 AIOOBE가 로그 없이 사라짐, 델타 7.9999블록이 short -32768로 오버플로, 유령 엔티티는 로컬이 이미 해결.
- 전략 — `git merge -s ours`로 이력만 합쳐 push를 빨리감기로 만들고, 필요한 수정을 로컬 구조에 맞게 포팅. 단 push하면 원격 main 트리에서 PR #1 변경이 사라진다.
- 주의 — 원격 입력 검증은 기준 위치가 (0,-60,0)이라 로컬 임의 스폰과 충돌한다. 포팅 시 스폰으로 초기화하고 `pendingTeleportId` 게이트와 맞물려야 한다. 원격 테스트는 `World` 생성자 시그니처가 달라(9 대 10 인자) 그대로는 안 돌아간다.

## 1단계 — 청크 전달 레이스 (구현 메모)
- 원격은 구독·스냅샷·전송을 Region 한 곳으로 옮겼지만, 로컬은 청크 큐·틱당 전송 제한·직렬화 캐시·스폰 청크 배치(ConnectionHandler)가 있어 구조를 유지하고 `World.Subscription`(ready/pending)만 도입했다.
- 프로토콜 — 구독은 ready=false로 시작, 청크 패킷을 채널에 넘긴 직후 `chunkDelivered`가 소유 region 스레드로 돌아가 ready로 바꾸고 보류분을 flush. 브로드캐스트와 flush가 같은 스레드라 직렬화된다. 보류분은 항상 청크 뒤로 나간다(채널 큐 FIFO).
- 스폰 경로는 레이스보다 심했다 — 청크를 보낸 **뒤** 구독해서 스냅샷~구독 사이 편집이 아예 전달되지 않았다. 청크 요청 전에 구독하도록 바꾸고, 채널 비활성·청크 실패 시 구독을 해제해 누수를 막았다(adopt 전이라 region이 정리해주지 않음).
- 알려진 한계 — 청크 직렬화가 실패하면 구독이 ready=false로 남아 그 청크의 보류분이 언로드까지 쌓인다(드묾, 언로드 시 정리).
- 검증 — 수정 전 같은 시나리오가 [SetCenterChunk, BatchStart, BlockUpdate, LevelChunk…]로 재현됨. 수정 후 ChunkDeliveryOrderTest 3개 + 기존 월드 테스트 통과.

## 2단계 — 델타 이동 short 오버플로
- 로컬은 |delta|>=8만 텔레포트로 보냈는데, 7.9999블록은 `Math.round(d*4096)`=32768이라 `short`가 -32768이 된다(반대 방향 이동). 원격 `fitsDelta`(인코딩 결과가 short 범위인지)를 그대로 채택.
- red-green 확인 — 수정 전 로직으로 되돌리면 `nearEightBlocks_isSentAsTeleport…`가 실패, 수정 후 통과. 경계는 +7.9999가 아니라 음수 쪽 -8.0001이 아직 표현 가능(-32768)이고 -8.0002부터 범위 밖이라는 점에 주의(테스트 기대값을 처음에 잘못 써서 고침).
- 실제 발생은 드묾(틱당 이동량이 8블록 근처가 되는 경우 — 렉·고속 이동).

## 3단계 — 입력 검증 (원격 5247d60 포팅 + 확장)
- 원격과 다른 점 ①: **기준 위치를 스폰으로 초기화**. 원격은 (0,-60,0)에서 시작해 100블록 넘는 이동을 거부하는데, 로컬 스폰은 저장 위치/기후 기반 임의 좌표라 첫 이동부터 거부된다. 원격식 기준 위치로 되돌리면 `farFromOriginSpawn_normalMoveIsAccepted`가 실패함을 확인(red-green).
- ② **보정 텔레포트 게이팅**. 거부 시 보정을 한 번만 보내고(`correctionTpId`), 클라가 confirm(0x00)할 때까지 위치 패킷을 버린다. 안 그러면 옛 위치 패킷마다 보정이 연쇄로 나간다. 보정 id는 스폰 게이트(1·2) 뒤인 3부터.
- ③ **블록 요청 3경로 모두 검증** — 원격은 파괴(0x24)만 다뤘지만 로컬에는 설치/상자 열기(0x38)도 `chunkAt`으로 임의 좌표 청크를 생성했다. y 범위·도달 거리(눈 높이 8블록)·로드된 청크만 허용하고, 거부해도 BlockChangedAck를 보내며 로드된 청크면 서버의 실제 블록 상태를 되돌려 보낸다(`resendBlock`) — 클라 예측이 어긋나지 않게.
- ④ 설치 대상 위치도 높이 범위·로드 여부를 검사. 로드 여부는 `Region.chunkLoaded`(소유 스레드 전용). 기존 `breakBlock`/`placeBlock` 시그니처는 유지(기존 테스트 호환) — 게이트는 ConnectionHandler 쪽.
- ⑤ region 작업은 `World.runOn`으로 실행해 예외가 로그로 남는다(스케줄러는 future에만 담고 버렸다). 회전 값은 NaN/무한대면 0으로 정규화.
- 남은 것 — Container Click(0x0E)의 상자 좌표는 `openChest` 시점에 검증된 클릭에서 오므로 별도 검증 안 함. 이동 패킷 병합(회전 전용이 위치를 덮어씀)은 6단계.

## 4단계 — ConfigLoader 값 검증
- 원격은 7개 키만 검증했지만 로컬에는 io-threads·autosave-ticks·chunk-eviction-grace-ticks·spawn-chunk-radius·chunks-per-tick·level-name이 더 있어 함께 검증(빈 level-name은 작업 디렉터리를 월드로 쓰게 됨).
- **동작 변경 주의** — `view-distance`가 2~32 밖이면 기동이 거부된다. 과거에 `view-distance=64`로 테스트한 적이 있다(메모리 기록). 서버 값이 32를 넘어도 클라이언트 최대가 32라 `effectiveViewDistance`가 32로 잘려 효과가 없었으므로 거부가 맞다고 판단. 현재 저장소의 furnace.properties(16)는 통과하며 테스트로 고정.

## 5단계 — PlayerInfoRemove (탭 목록 제거)
- 패킷 ID 0x3D는 바닐라 datagen(`build/generated/reports/packets.json`, player_info_remove=61)으로 확인.
- **원격과 다른 설계**: 원격은 엔티티가 시야 범위 밖으로 나갈 때마다 PlayerInfoRemove를 보내는데, 바닐라는 접속 중인 플레이어를 거리와 무관하게 탭에 둔다. 고칠 버그는 "퇴장한 플레이어가 탭에 영구히 남는 것"이므로, 후보에서 완전히 사라졌을 때(퇴장·유예 틱 경과)만 제거한다. 시야 밖 이동은 엔티티만 디스폰하고 탭 항목은 유지(`outOfEntityRange_keepsTabEntry_thenRemovedOnDisconnect`).
- 이를 위해 `Player.listed`(탭에 올린 항목 → uuid)를 `tracked`와 분리했다. 유예 디스폰 루프를 `listed` 기준으로 돌려, 시야 밖이라 엔티티는 이미 사라졌어도 탭 항목은 남은 상태에서 퇴장한 경우도 정리된다.
- 한계 — 이 서버의 탭 목록은 "후보 region 안에서 한 번 이상 본 플레이어"만 담는다(전체 접속자 탭이 아님). 접속자 전체 탭은 World 수준 목록이 필요해 이번 범위 밖. 후보 범위를 벗어난 접속자도 같은 경로로 제거된다.

## 6단계 — 이동 패킷 병합
- 심각도를 몰라 재현 테스트부터 썼다. 실제 ConnectionHandler를 EmbeddedChannel로 구동해도 3개 시나리오(위치→회전, 회전→위치, pos_rot→회전)가 모두 실패 — 부분 패킷이 `p.x()`/`p.yaw()`(region이 아직 drain 안 한 옛 값)로 나머지를 채워 대기 중인 값을 덮어썼다.
- 수정 — IO 스레드가 마지막 수락 위치·회전·onGround(`ix/iy/iz/iyaw/ipitch/ig`)를 들고 있다가 항상 합친 값을 발행. 3단계에서 도입한 기준 위치 변수를 회전·onGround까지 확장한 것이라 추가 상태는 최소.
- 실제 영향은 한 서버 틱 안에 두 패킷이 겹쳐 올 때(클라 틱과 서버 틱 지터)만. 바닐라 클라가 정지 후 회전만 보내는 패턴에서 서버 위치가 옛 값으로 되돌아가 다음 위치 패킷까지 유지될 수 있었는지는 실클라로 확인하지 못했다.

## 결과 (2026-10-07)
- 전체 407개(스킵 6) 실패 0 — 기준선 388에 새 테스트 19개(ChunkDeliveryOrder 3, DeltaMoveOverflow 3, ConnectionInput 7, ConfigLoader +3, TabList 3).
- **push 주의** — `merge -s ours`로 이력만 합쳤으므로 로컬 main을 push하면 원격 `main` 트리에서 PR #1의 변경 내용이 사라진다(이력은 남음). 필요한 수정은 위 포팅으로 대체했고, 로컬에 이미 있던 유령 엔티티·유휴 청크 퇴출·heightmap 편집 반영은 로컬 구현을 유지했다. 원격 브랜치 `claude/work-continuation-u597cw`는 그대로 남아 있다.
- 포팅하지 않은 원격 변경 — 유령 엔티티 transit 방식(로컬 유예 틱 방식이 교차 region 퇴장을 3틱 만에 처리함을 실행으로 확인), 유휴 청크 퇴출(로컬이 저장과 연동해 이미 구현), heightmap 편집 반영(로컬 이미 구현), gradlew 실행 권한·스케줄러 javadoc(사소).

## 제3자 검토 (push 직전)
머지 구조는 정상이었다 — 머지 커밋 892ed26의 트리는 첫 부모(로컬)와 동일(원격 내용 미유입), origin/main은 main의 조상이라 빨리감기 push 가능. 검토에서 아래 빈틈이 나와 보강했다.
- **gradlew 실행 권한 누락** — 원격은 100755인데 main은 100644였다. push하면 원격이 고친 권한이 되돌아가 Linux(OCI Ubuntu)에서 `./gradlew`가 실패한다. 처음에 "사소"로 분류해 포팅하지 않은 것이 오판이었다. `git update-index --chmod=+x`로 복원.
- **"로컬에 이미 있음"을 코드만 보고 판단한 3건에 테스트가 없었다** — (1) 청크 퇴출: 로컬엔 퇴출 테스트가 아예 없었다. 원격은 "편집 청크 보관", 로컬은 "저장 후 퇴출"이라 의미가 달라 로컬 의미론으로 새로 작성(`ChunkEvictionTest`: 미편집은 저장 안 하고 퇴출, 편집은 저장 후 퇴출·재로딩 보존, 구독 중은 보관). (2) heightmap 편집 반영: `ChunkColumnTest.heightmap_followsEdits`로 MOTION_BLOCKING·WORLD_SURFACE 모두 확인. 처음 실패한 건 새로 만든 빈 컬럼이 WORLD_SURFACE를 라이브 갱신하지 않는 "미초기화" 상태라서이며(운영에선 loadGenerated/fromNbt로 초기화되고 오버월드 컬럼은 기반암 때문에 높이 0이 될 수 없음), 운영 경로(loadGenerated)로 초기화하도록 테스트를 고쳤다. (3) 유령 엔티티: 원격 GhostEntityTest의 "후보 밖으로 순간이동" 시나리오가 로컬 테스트에 없어 `TabListTest.teleportFarAway_isDespawnedForObserver`로 추가.
- `PlayerInfoRemove` 와이어 포맷 테스트(`SpawnPacketsTest`) 추가 — ID 0x3D와 바이트 구성이 하드코딩인데 검증이 없었다.
- push 시 원격에서 사라지는 것 — 원격 전용 `GhostEntityTest`(transit 방식 전용 케이스 포함)·`ChunkEvictionTest`(원격 의미론). 같은 동작은 위 로컬 테스트들이 검증한다.
