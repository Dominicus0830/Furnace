# T-122 엔티티 손잡이 — Paper 원문 대조 기록 (2026-11-15)

- 원문: Paper 1.21.1 패치(커밋 33ff3dff — `Cap-Entity-Collisions`, `Tracking-Range-Improvements`, `Paper-config-files` 의 SpigotWorldConfig 기본값 줄),
  Spigot 본문은 1.21.1 패치에 없어 Paper `ver/1.21.4` 의 `org/spigotmc/TrackingRange.java`·`ItemEntity.java.patch`·`ChunkMap.java.patch`·`CraftEventFactory.java`(경험치 합치기)로 봤다. 동작 대조용으로만 읽었다.
- 밀기 상한: `numCollisions = max(0, numCollisions - max)` 는 후보 목록이 비지 않았을 때만 줄인다. 민 상대도 하나 올린다 — 밀린 몹은 다음 제 차례에 덜 민다.
  Paper 에서 `max <= 0` 은 "밀지 않음"이라 Furnace 는 `-1` 을 "제한 없음(바닐라)"로 따로 두고, 가져오기는 Paper 의 0 이하를 0 으로 옮긴다.
- Furnace 후보 목록은 엔티티 다음 플레이어 순이다(바닐라 getEntities 는 구역 목록 순서로 섞음). 상한에 닿은 틱에 플레이어가 끼면 누가 밀리는지가 다를 수 있다.
- 관전자 플레이어는 Paper 에서 `isPushable` 거짓이라 밀기 루프에 오지 않는다 → Furnace 도 관전자는 횟수를 세지 않는다.
- T122-1(위임 결정): Paper 고정 변경 둘도 기본 켬 — `item-merge.always-into-ticking`(Spigot `if (true || …)`), `collisions.skip-unpushable`(Paper "Don't run entity collision code": `isPushable` 거짓이면 cramming 도 없음. 같은 패치의 팀 NEVER·"cramming·상한 둘 다 0 이하" 조건은 바닐라에서도 결과가 같아 생략).
- 추적 거리: `activationType`(활성 범위 종류)으로 고른다. 엔더 드래곤은 `Enemy` 라 MONSTER 에서 끝나 Paper 의 "드래곤은 서버 시야" 예외 줄에 닿지 않는다(죽은 코드).
  거리 0 은 그 종류를 아예 추적하지 않는다(Paper 와 같음). 플레이어끼리는 Region 의 보임 판정이 `min(시야, 플레이어 거리 × 방송 비율)` — 방송 비율도 이번에 넣었다(전엔 빠져 있었다).
- 바닐라 `getEffectiveRange` 의 "승객 중 가장 긴 거리" 규칙은 Furnace 에 아직 없다(손잡이와 별개 구멍 — 탄 플레이어가 있는 탈것이 바닐라는 512, Furnace 는 탈것 거리). 후속으로 남긴다.
- 시험 JVM 은 `EntityHandlesVanillaExtension` 으로 바닐라 값, 장면은 `TickScene.entityHandles()`(기본 바닐라)로 못 박는다 — 새 JVM 장면은 확장이 없어 운영 기본(Paper 값)이 들어오기 때문이다.
- 검토(PR #395) 반영: ① Furnace `AABB` 는 바닐라 생성자처럼 min/max 를 정렬하지 않는다 — Spigot 아이템 상자 inflate(r, r-0.5, r) 는 r < 0.375 면 y 가 뒤집혀 아무것도 못 찾았다. `AABB.of` 로 정렬(바닐라에서 음수 inflate 를 옮길 때 같은 함정). ② `PushScratch` 후보 플레이어는 차원 전체 색인에서 오므로 다른 region 소유일 수 있다 — 상한 횟수는 그 플레이어의 소유 스레드일 때만 센다.
- `collisions.allow-player-cramming-damage`(Paper 기본 false): Paper 는 `ServerPlayer.isInvulnerableTo` 에서 캐시된 cramming 피해 원본과 **같은 객체**일 때만 막는다 — 1/4 굴림은 그대로이고, `/damage … minecraft:cramming` 은 새 원본이라 막히지 않는다. Furnace 는 플레이어 cramming 호출 자리에서만 건너뛴다.
