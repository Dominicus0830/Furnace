# Furnace M3b — region 분할 + 병렬 틱 설계서

> 부모 설계서: `docs/superpowers/specs/2026-06-06-furnace-multithreaded-mc-server-design.md` (§4 동시성 모델, S2→S3).
> 선행: M0(스레딩 골격·RegionThread), M1(네트워킹), M2(청크), M3a(플레이어 이동·멀티플레이, region 1개). 전부 main 머지.
> M3 분해: M3a(완료) · **M3b**(본 문서, region N분할 병렬틱) · M3c(교차지역 가시성·원자연산).

## 1. 목표

월드를 고정 그리드 region으로 나누고 **N개 region 스레드 풀**에서 병렬로 틱한다. 서로 다른 region의 플레이어가 다른 스레드에서 틱하고(진짜 Folia식 병렬), 플레이어가 region 경계를 넘으면 소유권이 이양된다. 잘못된 스레드의 region 접근은 assert로 터진다 — **설계서 S2→S3 전환, 소유권 불변식 실가동.**

## 2. 비목표 (M3c 이후)

- **교차지역 가시성**(경계 너머 플레이어 서로 보임) → M3c. 버전드 스냅샷 패턴.
- **다중 region 원자연산**(폭발·피스톤이 동시에 두 region) → M3c. §4.5 프로젝트 최대 난제.
- **동적 region**(Folia식 핫스팟 분할/병합) → 별도 최적화 마일스톤. M0 비동기 API 고정 덕에 `RegionPos→스레드` 매핑만 바꿔 후일 교체(시그니처 무변경).
- EntityScheduler 라우팅 — M3b는 이동을 락프리 위치 발행으로 처리, 라우팅 불필요. 플러그인용 EntityScheduler는 M6.
- 블록 가변상태·영속성 → M4.

## 3. 확정된 핵심 결정

| 항목 | 결정 | 근거 |
|---|---|---|
| region 분할 | 고정 그리드(설정가능 크기) | 단순·결정적 매핑. 동적은 별도 최적화(API 고정으로 후일 교체) |
| region 소유 대상 | 플레이어/엔티티(가변)만 | 청크는 superflat 순수생성이라 교차region 안전. 가변상태만 소유권 필요 |
| 이동 처리 | 락프리 volatile 위치 발행 | region 옮겨다녀 대상 스레드가 바뀌므로 execute 라우팅은 레이스. 단일writer/단일reader volatile이 핸드오프-안전 |
| 핸드오프 | 단일 엔티티 비동기 이양 | 경계 못 넘으면 서버 망가짐. 단일 이양은 M3c 다중원자연산보다 쉬움 |
| 교차 가시성 | M3c로 미룸 | M3b는 병렬 인프라+소유권+핸드오프 집중. 경계 가시성은 교차region 읽기(스냅샷)라 M3c |

## 4. 아키텍처

```
RegionPos = (floorDiv(chunkX, regionSize), floorDiv(chunkZ, regionSize))
chunkX = floor(blockX / 16)
스레드 매핑 = regionThreads[floorMod(pos.x*31 + pos.z, N)]   // 결정적, 고정
```

```
[IO 스레드 N개]  이동패킷 파싱 → player.publishPosition(스냅샷)   (volatile write)
                                          │
[region 스레드 i] ──매 틱──> 자기에 매핑된 Region들 각각 tick():
   players 순회:
     player.drainPending() → 위치 적용                          (volatile read)
     현재 RegionPos != 이 region? → 핸드오프 큐에 수집
     updateChunks(player)        (M3a 로직, 청크는 순수생성)
     updateVisibility(player)    (이 region 내 플레이어만)
   수집된 핸드오프 처리: 제거 + scheduler.region(newPos).execute(adopt)
   각 전송 → player.channel.eventLoop().execute(send)
```

**소유권 불변식.** `Region`·`Player`는 자기 region 스레드만 변경. region 생성·플레이어 add/remove/handoff는 `region(pos).execute(...)`로 그 스레드에서. 디버그 가드: Region 변경 진입에 `assert owningThread.isOwnerThread()`.

## 5. 컴포넌트 상세

### 5.1 RegionPos 매핑 (`world/RegionGrid` 또는 정적 유틸)
- `RegionPos forBlock(double x, double z, int regionSize)`: 블록 → 청크(floor/16) → region(floorDiv/regionSize).
- `RegionPos`는 furnace-api의 `record(int x,int z)` 재사용.

### 5.2 Region (`world/Region.java`, 신규)
- 필드: `RegionPos pos`, `Map<Integer,Player> players`, 공유 자원 참조(World).
- `tick()`: M3a `World.tick`의 per-player 로직(drain 위치 → 핸드오프 판정 → updateChunks → updateVisibility). 핸드오프는 순회 후 일괄(ConcurrentModification 회피).
- `adopt(Player)` / `release(int eid)`: 소유 스레드서 호출(add/remove).
- updateVisibility는 **이 region players만** 순회(교차 가시성 미포함).

### 5.3 World (`world/World.java`, 리팩터)
- M3a의 단일 player map·tick 제거. 이제: `Map<RegionPos,Region>`(생성 관리) + 공유 자원(WorldSource/chunkPool/viewDistance/blockDirectBits/biomeDirectBits) + region 크기.
- `regionAt(RegionPos)`: 소유 스레드서 getOrCreate.
- `tickThread(int index)`: 인덱스 i에 매핑된 region들 tick. (region→스레드 매핑은 결정적이므로 i가 소유.)
- 스레드별 region 집합은 각 스레드 소유 자료구조에 두어 공유 가변맵을 피한다(상세는 plan).

### 5.4 region 스레드 풀 (`sched/FurnaceSchedulerImpl.java`)
- `RegionThread[] regions`(크기 N). 각 perTick = `() -> regionTick.accept(myIndex)`(settable `IntConsumer`).
- `region(RegionPos pos)`: `regions[floorMod(pos.x*31 + pos.z, N)]` 반환.
- N = config `region-threads`(기본 `availableProcessors`).
- `setRegionTick(IntConsumer)`로 World가 `i -> world.tickThread(i)` 연결.

### 5.5 Player 위치 발행 (`entity/Player.java`)
- `volatile PosUpdate pending`(불변 record: x,y,z,yaw,pitch,onGround). IO 스레드 `publishPosition(...)`가 새 스냅샷 write.
- `drainPending()`(소유 region 스레드): 읽어 권위 필드에 적용, null로 클리어. 단일 reader.
- `RegionPos regionPos(int regionSize)`: 현재 권위 위치 기준.

### 5.6 핸드오프
`Region.tick` 순회 중 `player.regionPos != this.pos`인 플레이어 수집 → 순회 후:
```
players.remove(eid)
scheduler.region(newPos).execute(() -> world.regionAt(newPos).adopt(player))
```
`loadedChunks`/`tracked` 그대로 따라감. 새 region 틱이 청크/가시성 재계산. 떠난 region의 타 플레이어는 다음 틱 updateVisibility서 이 플레이어 부재 감지 → RemoveEntities(자연 despawn).

### 5.7 청크 = region 무관
`WorldSource.generate`는 순수함수(superflat). 경계 넘는 청크도 어느 스레드서 생성·직렬화 안전. region은 청크 소유 안 함(M3b). M4 가변블록서 청크 소유권 재검토.

## 6. 동시성 안전 점검

- **공유 가변상태:** region별 player map은 그 region 스레드 전용. 스레드 간 통신은 `region(pos).execute` 큐로만. 공유 맵 회피(또는 ConcurrentHashMap + 소유 스레드만 쓰기).
- **위치 발행:** volatile 참조 단일writer/단일reader.
- **채널 전송:** 항상 `channel.eventLoop().execute`로 IO 스레드 마샬.
- **핸드오프 레이스:** 이양 중 IO가 publishPosition해도 volatile이라 다음 소유 region이 읽음(유실 없음). 같은 플레이어를 두 스레드가 동시에 틱하지 않음(제거→add 순차, execute 큐 직렬).

## 7. 위험 / 검증

- **핸드오프 중복/유실:** 한 플레이어가 동시에 두 region에 등록되거나 어디에도 없게 되면 버그. remove→execute(adopt) 순서 + 멱등 처리. 경계 왕복(oscillation) 테스트.
- **소유권 위반:** 잘못된 스레드의 Region 변경. assert 가드 + 단위 테스트(다른 스레드서 접근 시 터짐).
- **병렬성 실증:** 두 region이 실제 다른 스레드서 도는지 스레드 id 단언.
- **결정적 매핑:** 같은 pos는 항상 같은 스레드. 해시 분포 확인.

✅ **verify:** 두 플레이어 멀리(다른 region) 스폰 → 다른 스레드서 틱(스레드 id 단언). 경계 넘어 걸어도 핸드오프·크래시/멈춤 없음. 같은 region 두 플레이어 서로 보임. 잘못된 스레드 접근 시 assert.

## 8. 파일 구조

| 파일 | 책임 |
|---|---|
| `world/Region.java` | 한 그리드 셀: 플레이어 소유 + tick(청크/가시성/핸드오프) |
| `world/World.java` | (리팩터) region 컨테이너 + 공유 자원 + 스레드별 틱 디스패치 |
| `entity/Player.java` | (modify) volatile pending 위치 발행/드레인, regionPos |
| `sched/FurnaceSchedulerImpl.java` | (modify) N region 스레드, pos→스레드 매핑, setRegionTick(IntConsumer) |
| `config/Config.java`, `ConfigLoader.java` | (modify) region-size, region-threads |
| `net/ConnectionHandler.java` | (modify) 이동 → publishPosition, 진입 시 소유 region에 adopt |
| `FurnaceMain.java` | (modify) 배선 |

각 신규/변경은 단위 테스트(TDD): RegionPos 매핑, 핸드오프, 소유권 assert, 위치 발행 라운드트립. 통합은 2클라 멀리/경계 실접속(verify §7).
