## 2026-11-16 `Player#setPlayerProfile` (T-141 E11c-2b)

설계 `docs/feature-parity/compat-e11c-set-player-profile-design-2026-11-16.md` §4~§7 대로 구현. 2a(#454)의 엔티티 UUID·프로필 나누기 위에 올린다.

- 진입: furnace-api `Player#profile(ProfileData)`(이름 16자 초과는 IllegalArgumentException), 호환 `CraftPlayer.setPlayerProfile`(Paper `asAuthlibCopy` 처럼 빈 id → NIL UUID, 빈 이름 → "", 넘긴 객체는 복사). 서버 `Player.changeProfile`: 접속 탭 전 구간(`tabListingHeld`, Paper `!sentListPacket`)이면 프로필만 바꾼다. 그 밖에는 `RegionBorrow.claim` 뒤 `runOnOwner` — 소유 스레드·빌린 동기 문맥이면 그 자리, 다른 스레드면 플레이어를 따라가는 대기열로 다음 틱.
- 같은 region(`Region.applyProfileChange`): 대상을 볼 수 있는 모두(본인 포함, 숨긴 관찰자·접속 처리 중인 관찰자 제외)에게 보고 있었으면 RemoveEntities, 늘 PlayerInfoRemove(엔티티 UUID) → 프로필 교체(세대+1) → 같은 사람들에게 새 프로필 id 로 PlayerInfoUpdate, 시야 안이면 엔티티 UUID 로 스폰 → `PlayerShowEntityEvent`(관찰자마다, 본인 포함) → 본인 화면 갱신.
- 다른 region·월드 관찰자: `listed` 가 `(UUID, 세대)` 를 기억하고 `syncTabList` 가 세대 차이를 보면 같은 순서(엔티티 해제·Remove → 새 id 로 Update → 이벤트)로 다시 올린다. 한 틱 안 지연(탭만이라 눈에 띄지 않는다).
- 본인 갱신(Paper `refreshPlayer`, 연결 스레드에서 한 덩어리): Respawn(같은 차원, KEEP_ALL, 관문 대기) → 능력치 → 위치(region 이 검증한 위치·회전 — Paper getLocation. 서버 텔레포트(스폰 게이트·보정·플러그인)가 확인을 기다리는 동안만 연결의 목적지 값. 연결의 수락 위치를 그대로 쓰면 region 검증 전 이동을 확정해 노클립이 된다(#456 검토 R1), region 위치만 쓰면 막 보낸 텔레포트를 되돌린다) → 권한 레벨 엔티티 이벤트만(명령 트리 재전송 없음) → 레벨 정보 → 인벤토리 전체 → 속성(syncable, 최대 체력은 수정자 없는 기본값으로 끝에) → 체력 → 엔티티 데이터 전부 → 손 슬롯 → 22/23 → 즉시 리스폰 게임 이벤트 → 경험치 → 효과(blend=false). 시험이 패킷 id 순서를 고정한다.
- 숨김 이벤트: `EntityViewsImpl.hide/show/setVisibleByDefault` 가 관찰자에게 실제로 보임이 바뀔 때만(판정은 `EntityViewRules.Change` 의 원자적 갱신 앞뒤 묶음 — 동시 호출에도 전이마다 한 번) `PlayerHideEntityEvent`/`PlayerShowEntityEvent`(CraftBukkit untrackAndHideEntity/trackAndShowEntity, 취소 불가). 호환 다리·EventBridgeTest 포함.
- NMS `Player.getGameProfile()` 은 핸들 프로필이 바뀌면 다시 만든다(volatile 캐시).

시험: `ProfileChangeTest`(서버, 실제 소켓, region 스레드 둘 — UUID 같음 패킷 순서·권한 값·최대 체력 속성·엔티티 데이터 21 항목, UUID 바뀜, 조인 이벤트 안, 다른 region 관찰자, 숨긴 관찰자, 연속 두 번, 관찰자 region 을 붙잡은 채 바꾸고 퇴장, 막 보낸 텔레포트 목적지 유지, 검증 대기 이동을 확정하지 않음), `EntityViewsApiTest#hideAndShowEventsFireOnlyWhenVisibilityChanges`, 호환 `PlayerProfileSplitTest#setPlayerProfileSwapsTheProfileOnly`·`EventBridgeTest`.

2c(실서버 Paper 대조)에서 확인할 추론:
- `updateScaledHealth` 속성 목록 순서(syncable 집합의 순회 순서)와 엔티티 데이터 전부(packAll)의 기본값 항목.
- 다른 region 관찰자의 탭 갱신 한 틱 지연은 Furnace 구조상 남는다(의도한 차이에 가까움 — 엔티티를 볼 수 있는 관찰자는 늘 같은 region 이라 화면 차이는 탭뿐).

해당 없음(Furnace 에 아직 그 API 가 없다): 플레이어별 시간·날씨(`setPlayerTime`/`setPlayerWeather`), 목록 숨김(`unlistPlayer`) — 그 API 가 생기면 재등록 경로(`applyProfileChange`·`syncTabList`)에서 그 상태를 함께 지킬 것.

의도한 차이(설계 §8): 접속자 이름 조회는 새 이름으로 찾는다(Paper 는 접속 때 이름).

미세 차이(#456 검토 N5):
- Paper 는 아직 조인 이벤트 중인 관찰자(`players` 에는 있음)에게도 해제·재등록을 보낸다. Furnace 는 그 관찰자의 접속 탭에 새 프로필 항목 하나로 올린다 — UUID 를 바꾼 경우에만 보인다.
- 관찰자별 `PlayerShowEntityEvent` 순서: Paper 는 접속 순, Furnace 는 region 의 플레이어 맵 순.
- 소유 작업 드레인 안에서 앞선 작업이 플레이어를 다른 region 으로 옮겼으면 프로필 적용은 엔티티 작업 대기열로 넘겨 소유 region 에서 다시 본다(그 드레인 끝에 한 번, 아니면 다음 틱 새 region 에서).

함정:
- 시험 클라이언트는 텔레포트를 확인하지 않아 스폰 게이트가 300ms 뒤 대체 해제되며 스폰 자리로 다시 고정한다(SyncPlayerPosition id 2). 그 전에 보낸 서버 텔레포트는 이 재고정에 덮인다 — 멀리 보내는 시험은 게이트 해제를 기다린 뒤 옮긴다(`ProfileChangeTest.awaitSpawnGate`). 같은 까닭으로 **PlayerJoinEvent 안에서 한 텔레포트도 되돌려진다**(확인: 조인 이벤트에서 (300.5,120,300.5)로 보내면 클라이언트가 id 1 스폰 → id 3 목적지 → id 2 스폰 순으로 받고 서버 위치도 스폰. 실제 클라이언트는 id 1 확인 + 스폰 청크 전송 완료 때 `releaseSpawnGate` 가 같은 재고정을 한다). Paper 에는 이 게이트가 없어 조인 이벤트 텔레포트가 남는다 — 이 단계 밖의 별도 작업으로 넘김.
- 프레임을 읽을 때는 `TestClient.poll` 로(소켓 시간 제한이 프레임 중간에서 끊지 않게).
