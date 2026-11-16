## 2026-11-16 플러그인 다른 월드 텔레포트 — 조인 이벤트 안·밖 (join-world-teleport)

**증상(고치기 전).** `PlayerHandle.teleportNow`·`teleport` 가 다른 차원이면 곧바로 `false` 였다. PlayerJoinEvent 안에서 로비·스폰 월드로 보내는
플러그인(Paper 에서는 흔하다)이 아무 일도 못 했다. #462 는 같은 차원 조인 텔레포트만 고쳤다.

**Paper 1.21.1 가 하는 일(paper-1.21.1-133 jar 를 `javap -c -p` 로 확인, 디컴파일 소스는 커밋하지 않음).**
- `CraftPlayer#teleport(Location, cause, flags)`: 승객 있음·죽음·연결 없음이면 false → PlayerTeleportEvent(도착이 다른 월드) → 취소면 false →
  탈것 내리기·깨우기 → 열린 창 TELEPORT 로 닫기 → 같은 월드면 `internalTeleport`, 다른 월드면 `portalProcess = null` 뒤
  `PlayerList.respawn(플레이어, true, CHANGED_DIMENSION, null, 목적지)` → true.
- `PlayerList.respawn(…, 위치)`: 같은 ServerPlayer 를 다시 쓴다. 패킷 순서 — Respawn(목적 월드, **dataToKeep=1 속성만**) → SetChunkCacheRadius(월드
  시야) → SetSimulationDistance → 위치(teleport) → SetDefaultSpawnPosition(목적 월드) → ChangeDifficulty → SetExperience → 효과 →
  sendLevelInfo(목적 월드) → 권한 → addRespawnedPlayer(players 끝으로) → sendAllPlayerInfo(인벤토리 전체·updateScaledHealth·refreshEntityData·든 칸·
  reducedDebugInfo/immediateRespawn 게임 규칙 패킷) → 능력 → 효과 → `triggerDimensionChangeTriggers(목적 월드)` → PlayerChangedWorldEvent.
- 조인 이벤트 안이면 위가 이벤트 중(접속 탭 앞)에 일어나고, 이벤트 뒤 `placeNewPlayer` 는 탭 → `sendLevelInfo(접속한 월드)`(목적 월드가 아니다)
  → 효과 → 접속 마무리로 이어진다. 청크는 새 월드의 청크 로더가 보낸다.

**Furnace 구현.**
- `PlayerHandle.teleportNow/teleport` → `Player.pluginTeleportToWorld`(이벤트·창 닫기 → 연결의 `worldTeleporter`).
- 조인 이벤트 안(연결 스레드): `ConnectionHandler.joinWorldChange` 가 그 자리에서 Paper 순서 패킷을 보내고 world·차원·서버 위치를 바꾼다. 접속
  순서의 뒤(청크 중심·스폰 청크 배치·region 편입)는 바뀐 world·자리로 이어진다. 이벤트 뒤 레벨 정보(비·틱 상태)는 Paper 처럼 접속한 월드 것.
  PlayerChangedWorldEvent 는 이벤트 안에서 곧바로, 차원 발전 과제는 편입 뒤 소유 스레드에서.
- 그 밖(접속 뒤 아무 스레드): `Player.requestWorldTeleport` → 소유 스레드에서 내리기·깨우기 → region 틱이 플레이어 작업 뒤 꺼내
  `Region.sendAcrossForPlugin`(떼기 → 목적 청크 준비 → 목적 region 스레드) → `Player.changeWorldForPlugin` → 연결 `applyPluginWorldChange`(같은
  Paper 순서 + 청크 중심) → 넘기기판으로 목적 region 편입 → 발전 과제·PlayerChangedWorldEvent → `teleport()` future 참.
- 서버 위치는 패킷을 보낼 때(어느 region 에도 없을 때) 연결이 곧바로 쓴다 — 조인 이벤트 안 `location()`, `teleport()` 완료 때 `location()` 이 목적지
  (Paper 는 동기라 그렇다). 같은 월드 조인 이벤트 텔레포트도 같이 고쳤다(#462 뒤에도 이벤트 안 `location()` 이 스폰 자리였다).

**Paper 를 그대로 따른 묘한 점(관측 결과가 같게).**
- 리스폰이 속성만 유지(1)라 클라이언트가 엔티티 데이터를 기본값으로 새로 만든다 → 본인 엔티티 데이터를 다시 보낸다(`Player.resendOwnEntityData`).
- `triggerDimensionChangeTriggers` 에 목적 월드를 넘긴다(CraftBukkit) — changed_dimension 이 (목적 → 목적)이고 nether_travel 은 없으며, 오버월드 →
  네더여도 enteredNetherPosition 을 적지 않는다. "We Need to Go Deeper" 같은 to 만 보는 발전 과제는 그대로 달성된다.
- 조인 이벤트 뒤 sendLevelInfo 는 접속한 월드 것(비 패킷이 네더로 가도 클라이언트 표시는 없다).

**검토 반영(#467).**
- 접속 뒤 요청은 플레이어의 한 칸(원자 참조)에 걸린다. 퇴장(region 퇴장 가지·`channelInactive`)하면 꺼내 거짓으로 끝낸다 — `teleport()` future 가
  매달리지 않는다. 걸어 둔 사이 죽었으면 region 이 꺼낼 때 거짓(CraftPlayer 의 isDead 검사). 탈것 내리기·깨우기는 꺼낼 때 소유 스레드에서.
- 다른 월드 요청이 걸려 있거나 가는 중(떼어 보내 편입 전)이면 같은 월드 텔레포트도 같은 칸에 건다 — Paper 는 둘 다 그 자리에서 해 마지막 호출의
  자리가 남는다. 덮인 앞 요청은 참으로 끝난다(Paper 에서도 앞 것은 참). 가는 중에 건 것은 도착한 region 이 다음 틱에 꺼낸다.
- 조인 이벤트에서 다른 월드로 옮기면 스트리밍 시야·스폰 청크 모양도 목적 월드 시야로 다시 잡는다(`SetChunkCacheRadius` 와 맞게).

**알려진 차이(후속 후보).**
- 접속 뒤(조인 이벤트 밖) 다른 월드 텔레포트는 다음 region 틱에 옮긴다 — `teleport()`/`teleportNow()` 가 돌아온 직후 `location()`·`getWorld()`
  는 아직 떠나온 월드다(Paper 는 동기라 곧바로 목적지). `teleport()` future 가 끝난 뒤에는 목적지다. 호환 계층의 `Entity#teleport` 문서에도 적었다.
- 조인 경로의 차원 발전 과제(changed_dimension)는 편입 뒤 첫 소유 틱에 돈다(Paper 는 이벤트 안). 결과(달성 여부)는 같고 시점만 한 틱 안쪽 차이.
- Paper 는 승객이 있으면(누가 이 플레이어 위에 탔으면) 거짓이다. Furnace 플레이어 승객(플레이어를 태우는 플러그인 기능)이 아직 없어 검사하지 않는다.
- 덮인 같은 월드 요청의 PlayerTeleportEvent 는 from 이 아직 떠나지 않은 자리다(Paper 는 앞 텔레포트 뒤 자리).

**빼 둔 것.** sendAllPlayerInfo 의 reducedDebugInfo·immediateRespawn 패킷은 보내지 않는다 — 클라이언트 리스폰 처리가 그 값을 새 LocalPlayer 로
옮겨서 다시 보내도 바뀌는 것이 없다. updateScaledHealth 의 속성 패킷도 속성이 유지돼 같다(체력은 다음 틱 resetSentInfo 로 다시 간다).
플레이어가 아닌 엔티티의 다른 월드 플러그인 텔레포트는 여전히 false 다(후속 후보).

**시험.** `JoinWorldTeleportTest`(실제 소켓, 오버월드+시험 네더): 조인 이벤트 안 다른 월드(패킷 순서·리스폰이 탭보다 앞·id 1→2 재고정 없음·목적지
청크·편입·이벤트 한 번·이벤트 안 location), 접속 뒤 다른 월드, `teleport()` 도착 뒤 참, 이벤트 취소, 같은 월드 먼 목적지 청크 도착(조인 이벤트 안·접속 뒤).
고치기 전에는 다른 월드 넷이 실패했고(같은 월드 먼 목적지 청크 도착은 이미 통과), 고친 뒤 모두 통과. 검토 반영 시험 둘(region 스레드를 붙잡아
걸어 둔 채로): 퇴장하면 future 거짓, 다른 월드 → 같은 월드 차례면 리스폰 없이 같은 월드 목적지 — 반영 전 코드에서는 둘 다 실패(매달림·네더로 감).
봇 시험 `tools/bot-test/round-jtp`(mineflayer, 실제 fatJar): 조인 이벤트 네더·먼 곳, 접속 2초 뒤 네더, 네더 → 3초 뒤 오버월드 먼 곳 — 모두 목적
차원·목적지 청크 도착.
