# 폭발 레벨 난수·부술 칸 순서 바닐라 맞춤 (2026-11-13)

PR #318(T-040a) 검토 4장 범위 밖 발견("폭발 소리 서버 재생 = 바닐라 난수 어긋남 의심")을 바닐라 1.21.1 소스로 확인했다. 의심이 맞았고, 같은 자리에서 세 군데가 더 바닐라와 달랐다.

## 바닐라 1.21.1 (Mojang 이름 디컴파일, extracted_minecraft_data client1.21.1)
- `Explosion.finalizeExplosion`: 소리(`playLocalSound`, 높이에 `level.random.nextFloat()` 두 번)는 `if (this.level.isClientSide)` 안에만 있다. 서버는 `ServerLevel.explode` 가 `ClientboundExplodePacket` 에 소리를 실어 보내고, 클라이언트 `ClientPacketListener.handleExplosion` 이 `new Explosion(...).finalizeExplosion(true)` 로 소리를 낸다.
- `interactsWithBlocks()` = `blockInteraction != KEEP`. 이것이 참이면 **TRIGGER_BLOCK(윈드 차지)도** `Util.shuffle(toBlow, level.random)` 뒤 각 칸 `onExplosionHit`.
- `explode()` 는 칸을 `Sets.newHashSet()` 에 모은 뒤 `toBlow.addAll(set)` — 섞기 전 순서 = `BlockPos` 해시(`Vec3i.hashCode = (y + z*31)*31 + x`)로 정해지는 HashSet 반복 순서.
- 불: `this.random.nextInt(3)` — 레벨 난수가 아니라 폭발 자신의 `RandomSource.create()`.
- 서버 폭발이 레벨 난수를 쓰는 곳은 광선마다 `nextFloat` 한 번(1352개)과 섞기뿐이다(드롭·전리품·`popResource` 는 별도).

## Furnace 에서 달랐던 것 → 고친 것 (`block/Explosion`)
1. 서버에서 `level.playSound` 로 소리를 따로 재생(레벨 난수 2번 + 소리 난수 1번) → 삭제. 섞기 순서·이후 모든 레벨 난수가 밀렸고, 클라이언트는 소리를 두 번 들었으며(패킷 소리 + 소리 패킷), 플러그인이 폭발 이벤트를 취소해도 소리가 났다(Paper 는 취소면 패킷도 없음).
2. 섞기를 `breaking`(DESTROY 계열)일 때만 → `KEEP` 이 아니면 언제나.
3. 섞기 전 순서가 광선 삽입 순서(`ordered` 목록) → 바닐라 해시를 가진 열쇠(`VanillaPos`)의 `HashSet` 반복 순서. `Pos` 는 레코드라 해시가 바닐라와 다르다. 열쇠는 일부러 `Comparable` 이 아니다: `BlockPos` 는 `Comparable<Vec3i>` 를 물려받을 뿐이라 HashMap 의 `comparableClassFor` 가 null → 트리 칸도 같은 길(해시 → tieBreakOrder). 해시가 같은 칸은 반경 12 부터 흔하지만 목록 칸에서는 삽입 순서라 결정적이고 바닐라와 같다. 비결정은 해시가 같은 칸이 트리 칸(한 칸 8개 이상, 표 64 이상)에 들어갈 때만이고, 그때는 바닐라 자신도 identityHashCode 로 실행마다 다르다(PR #327 검토 측정: 반경 48 까지 트리 칸 없음).
4. 불 붙이기 `level.random()` → 폭발마다 만드는 난수(`RandomSeeds.Purpose.EXPLOSION`, 불 폭발일 때만 생성).

## 시험
`ExplosionVanillaRandomTest`(5개): 바닐라 소스를 옮긴 모형(java.util.Random 으로 따로 돌림)과 공중 폭발의 섞은 뒤 `toBlow` 순서·폭발 뒤 레벨 난수 다음 값을 비교한다(DESTROY·KEEP·TRIGGER_BLOCK·불). 수정 전 코드에서는 5개 모두 실패한다.
- 모형 함정: 공기 칸 저항은 `Optional.empty()`(감쇠 없음)이지 `Optional.of(0)` 이 아니다 — 공기에서 광선은 걸음마다 0.225 씩만 준다.

## 검토 반영(PR #327 R1·R2)
- `ChunkLevel.isInWorldBounds` 가 `Math.abs(x) < 30000000` 이라 x·z = −30,000,000 칸을 월드 밖으로 봤다 → 바닐라 `isInWorldBoundsHorizontal`(`>= -30000000 && < 30000000`). 폭발 광선뿐 아니라 이 메서드를 쓰는 모든 자리에 해당.
- 폭발 패킷: KEEP 이면 빈 목록(바닐라 `ServerLevel.explode` 의 `clearToBlow()` 뒤 전송), `toBlow` 는 불 처리 뒤에 비운다. 두 번째 입자 칸은 폭발의 large 입자 그대로(바닐라는 클라이언트가 반경·KEEP 으로 고른다).
- 블록이 섞인 장면(저항·광선 끊김)은 검토 하네스가 바닐라 jar 로 60경우 확인했다(`furnace/reviews/explosion-random-pr327-repro/`).

## 남은 것(이 묶음 밖)
- **Paper 와 다름은 의도다.** Paper 1.21.1(Moonrise 패치)은 HashSet 을 지우고 광선에서 처음 만난 순서로 넣으며, 불 폭발이 아니면 공기 칸을 넣지 않아 섞기 길이(레벨 난수 소비)도 바닐라와 다르다. Furnace 는 바닐라 기준이므로 Paper 쪽으로 "고치지" 않는다.
- `Pos` 레코드 해시가 바닐라 `BlockPos` 와 달라, 다른 `HashSet<Pos>`/`HashMap<Pos,…>` 반복 순서가 바닐라 결과에 닿는 자리가 더 있을 수 있다(점검 후보).
- 바닐라는 패킷을 블록을 부순 뒤 보내고 Furnace 는 앞서 보낸다(난수 영향 없음, 기존 주석).
