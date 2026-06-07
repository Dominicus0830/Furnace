# Furnace M3c — 교차지역 가시성 (스냅샷) 설계서

> 부모 설계서: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` (§4.5 교차지역).
> 선행: M3a(플레이어 이동·가시성), M3b(region N분할 병렬틱·핸드오프). 전부 main 머지.
> M3 분해: M3a·M3b(완료) · **M3c**(본 문서, 교차region 가시성).

## 1. 목표

서로 다른 region에 있는 플레이어가 **view거리 안이면 region 무관하게 서로 보인다**(스폰·이동·디스폰). region 소유권을 깨지 않고 — 각 region이 매 틱 불변 스냅샷을 공개하고, 가시성 계산이 인접 region 스냅샷을 락프리로 읽는다. M3b의 "경계서 깜빡임" 한계도 해소.

## 2. 비목표

- **교차지역 원자 블록 연산**(폭발·피스톤이 두 region 가로지름) → 가변 블록이 없으면 操作 대상이 없다. M4(블록 상호작용)가 가변 블록을 들이면 그때 §4.5(메시지패싱 vs Global 직렬화)를 확정한다. M3c 범위 밖.
- 엔티티 메타데이터(웅크리기 등 포즈) → 별도.
- 동적 region·서버측 물리 → 후속.

## 3. 확정된 핵심 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| 교차region 읽기 | 불변 스냅샷 + volatile/concurrent 공개 | 타 region Player(다른 스레드 소유) 직접 접근 금지. 불변 스냅샷은 락프리 안전 |
| 가시성 입력 | live Player → 불변 `EntityView` | 관찰 대상이 교차region이라 소유 객체 못 만짐. 스냅샷 뷰로 통일 |
| 스냅샷 신선도 | 1틱 stale 허용 | 인접 region은 직전 틱 스냅샷. ~50ms 지연은 가시성에 무관 |
| 읽을 범위 | `ceil(viewDistance/regionSize)+1` 반경 region | view 원이 닿는 인접 region만 |

## 4. 아키텍처

```
[region 스레드 i] Region.tick():
   per-player: drain/핸드오프/청크 (M3b 그대로)
   publishSnapshot:  자기 players → List<EntityView> 불변 → World.snapshots[pos]
   per-player a: updateVisibility(a, world.candidatesAround(pos, radius))
                   후보 = 반경 내 region 스냅샷 union(자기 포함), self(entityId) 제외
                   든 자 spawn / 난 자 remove / 유지 이동 — EntityView 기준
```

**소유권 불변식 유지.** Region은 여전히 자기 Player만 변경. 교차region은 **읽기 전용 불변 스냅샷**만. 관찰자 `a`는 자기 region 소유라 패킷 전송(채널)은 안전. 후보 `EntityView`는 불변값이라 어느 스레드서 읽어도 안전.

## 5. 컴포넌트 상세

### 5.1 EntityView (`entity/EntityView.java`, 신규)
불변 record: `int entityId, UUID uuid, String name, double x,y,z, float yaw,pitch, boolean onGround`. 스냅샷 단위.

### 5.2 World 스냅샷 레지스트리 (`world/World.java` 수정)
- `ConcurrentHashMap<RegionPos, List<EntityView>> snapshots`.
- `publishSnapshot(RegionPos pos, List<EntityView> views)`: 덮어쓰기(불변 리스트 발행).
- `List<EntityView> candidatesAround(RegionPos center, int radius)`: center±radius region들의 스냅샷을 모아 반환(없는 region은 건너뜀).
- `neighborRadius()`: `Math.max(1, (int) Math.ceil((double) viewDistance / regionSize) + 1)` 사전계산.

### 5.3 Region.tick 수정
1. (기존) per-player drain/핸드오프/청크.
2. **공개:** 현재 players → `List<EntityView>` 만들어 `world.publishSnapshot(pos, views)`.
3. **가시성:** `List<EntityView> cands = world.candidatesAround(pos, world.neighborRadius())`. per-player a: `updateVisibility(a, cands)`.
- 빈 region도 매 틱 빈 스냅샷 공개 → 떠난 플레이어가 관찰자 후보서 사라짐(자연 despawn).

### 5.4 updateVisibility 리팩터 (`world/Region.java`)
시그니처: `updateVisibility(Player a, List<EntityView> candidates)`.
```
for EntityView b in candidates:
    if b.entityId == a.entityId: continue          // self 제외
    near = 거리(a, b) <= viewDistance*16
    tracking = a.tracked.containsKey(b.entityId)
    near&&!tracking: PlayerInfoUpdate(b)+SpawnEntity(b)+SetHeadRotation(b); tracked.put(b, {b.x..pitch})
    !near&&tracking:  RemoveEntities([b]); tracked.remove
    near:            broadcastMove(a, b, last)       // b는 EntityView
```
`broadcastMove(Player a, EntityView b, double[] last)`: 기존 로직, `b.x()/yaw()` 등 EntityView 게터로. 델타·텔레포트·헤드회전 동일.

> `within`/`broadcastMove`가 M3b선 `Player b`였다 → `EntityView b`로 변경. 같은 필드(x,z,yaw…) 제공하므로 본문 거의 동일.

### 5.5 핸드오프/디스폰 상호작용
- 디스폰(퇴장/이탈)은 스냅샷서 사라지면 관찰자 updateVisibility가 자동 despawn → M3b의 명시적 `broadcastRemove`는 **자기 region 내**용으로 남기되, 교차region 관찰자는 스냅샷 부재로 처리. (이중 안전: 둘 다 동작해도 멱등 — tracked.remove + RemoveEntities.)
- 경계 넘기(핸드오프): 플레이어가 region 바뀌어도 스냅샷엔 계속 등장(새 region이 공개) → 관찰자가 계속 봄 → **깜빡임 없음.**

## 6. 동시성 안전

- 스냅샷 = 불변 `List<EntityView>`(생성 후 변경 없음). `ConcurrentHashMap`에 put/get. 읽는 스레드는 불변값만 봄 → 락프리.
- 1틱 stale: region B가 직전 틱 공개한 스냅샷을 A가 읽음. 위치 ~50ms 지연 — 가시성 무관.
- 패킷 전송: 관찰자 `a`의 채널로(자기 소유), `channel.eventLoop().execute` 마샬 그대로.

## 7. 위험 / 검증

- **stale 스냅샷로 인한 유령:** 퇴장 직후 1틱 잔상 가능 → 다음 틱 빈 스냅샷으로 정리. 멱등 remove로 안전.
- **반경 계산 오류:** view가 닿는 region을 덜 읽으면 경계서 안 보임. `neighborRadius` 검증 단위 테스트.
- **성능:** 매 틱 스냅샷 생성(플레이어 수 비례) + 후보 union. 소수 플레이어 OK. 대규모는 후속 최적화(공간 인덱스).

✅ **verify:** 두 플레이어를 **다른 region이지만 view거리 안**에 두면 서로 보임. 경계 왕복해도 깜빡임 없음. 멀어지면(view 밖) despawn. 퇴장 시 사라짐.

## 8. 파일 구조

| 파일 | 책임 |
|---|---|
| `entity/EntityView.java` | 교차region 가시성용 불변 엔티티 스냅샷 |
| `world/World.java` | (modify) 스냅샷 레지스트리(publish/candidatesAround/neighborRadius) |
| `world/Region.java` | (modify) tick서 스냅샷 공개 + updateVisibility를 EntityView 후보 기반으로 |

단위 테스트: EntityView 불변, World.candidatesAround 반경 수집, neighborRadius 계산, Region 교차region 스폰(스냅샷 주입 후 관찰자 패킷 확인). 통합: 2클라 다른 region·view내 실접속(verify §7).
