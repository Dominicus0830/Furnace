## 2026-11-16 접속 직후 텔레포트가 스폰 게이트에 되돌려지던 문제 (join-teleport-gate)

**증상(고치기 전).** `ConnectionHandler.releaseSpawnGate` 가 게이트를 풀 때(스폰 텔레포트 확인 + 스폰 청크 전송 완료, 또는 300ms 대체 해제)
무조건 스폰 자리로 `SyncPlayerPosition` id 2 를 보내고 서버 위치도 되돌렸다. 그 사이 서버 텔레포트(PlayerJoinEvent 안, 접속 직후 플러그인
작업)는 덮였다 — 클라이언트는 id 1 스폰 → id 3 목적지 → id 2 스폰 순으로 받았다. Paper 에는 이 게이트가 없어 조인 텔레포트가 남는다.

**고친 것.**
- 텔레포트 id 는 보낸 순서대로 커진다(`teleportId` 는 스폰의 1 에서 시작, 게이트 재고정도 `++teleportId`). 게이트 해제 때 `teleportId` 가
  아직 1 이면(그 사이 서버 텔레포트 없음) 예전처럼 스폰으로 재고정(id 2 — 청크 사이 낙하 보정). 아니면 재고정하지 않는다 — 마지막 서버
  텔레포트가 최종 위치이고, 클라 이동은 그 텔레포트 확인(`correctionTpId`) 뒤부터 받는다. 모든 서버 텔레포트 경로(`teleportPlayer`,
  `teleportPlayerRelative`, `placeAfterDimensionChange`, `finishRespawn`, #456 의 프로필 갱신)가 `++teleportId` 를 쓰므로 특정 플러그인·경로를 따로
  보지 않는다.
- 조인 이벤트 안에서 이 연결 스레드가 부른 텔레포트는 대기열에 넣지 않고 그 자리에서 한다(`runTeleport`). Paper 처럼 텔레포트 패킷이
  이벤트 중(접속 탭 앞)에 나가고, 뒤의 청크 중심·스폰 청크·region 편입이 옮긴 자리(`ix`·`iz`)를 쓴다 — 스폰 자리 청크를 먼저 보내지 않는다.
- 조인 이벤트 때 서버 위치가 (0,0,0) 이던 것도 고쳤다 — region 편입 전(아직 어느 region 에도 없을 때) 연결 스레드가 `setPosRaw`·`setRotRaw`
  로 스폰 자리를 먼저 쓴다. 이벤트 안 `location()`·상대 텔레포트가 스폰 자리를 본다(Paper 는 이미 그 자리의 ServerPlayer).

**관측 결과(Paper 와 같음).** 조인 이벤트에서 옮기면: id 1 스폰 → id 2 목적지, 그 뒤 재고정 없음, 서버 위치 목적지(다른 region 이어도).
접속 직후 다른 스레드에서 옮겨도 같다. 텔레포트가 없으면 예전대로 id 2 로 스폰 재고정(이것은 Furnace 만의 보정으로 Paper 에는 없다 — 그대로 둔다).

**시험.** `JoinTeleportGateTest`(실제 소켓): 조인 이벤트 텔레포트(대체 해제·확인 해제 두 경로), 접속 직후 텔레포트, 텔레포트 없을 때 재고정.
고치기 전에는 앞의 셋이 실패(`[id 1 스폰, id 3 목적지, id 2 스폰]`), 고친 뒤 모두 통과.

**시험 함정 갱신.** 접속 직후 멀리 옮기는 시험이 게이트 해제(id 2)를 기다릴 필요는 이제 없다. 다만 텔레포트하지 않은 접속자의 게이트
해제 신호로 id 2 를 기다리는 도우미(`ProfileChangeTest.awaitSpawnGate`)는 그대로 맞다.
