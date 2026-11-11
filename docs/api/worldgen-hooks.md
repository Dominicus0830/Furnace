# 월드젠 단계 훅·청크 재생성 (W3, 불만 표 40·41행)

Paper 에서 오래 요청된 두 가지를 받는다: 바닐라 생성 단계를 골라 끄거나 바꾸기(`createVanillaChunkData` 요청 #3250, Bukkit 생성기는 거친 단위),
그리고 청크 하나를 처음부터 다시 만들기(Paper 는 사실상 미지원, #2551). 훅이 없는 월드는 바닐라와 바이트 단위로 같다 — 생성 경로에
null 검사 하나씩만 더했다.

## 1. 훅 등록 (부트스트랩)

```java
public final class IslandsBootstrap implements PluginBootstrap {
    @Override public void bootstrap(BootstrapContext ctx) {
        ctx.lifecycle().register(LifecycleEvents.WORLD_GENERATION, reg ->
                reg.register("islands", new IslandHooks()));          // → "<플러그인 namespace>:islands"
    }
}

final class IslandHooks implements ChunkGenerationHooks {
    @Override public boolean runVanilla(GenerationStage stage, int cx, int cz) {
        return stage != GenerationStage.NOISE;                         // 바닐라 지형 대신 직접 만든다
    }
    @Override public void noise(GenerationChunk c) {                    // 여기 놓은 돌은 바닐라 표면 규칙(잔디·모래)을 받는다
        if (Math.floorMod(c.chunkX(), 8) == 0 && Math.floorMod(c.chunkZ(), 8) == 0) {
            c.fill(2, 50, 2, 13, 64, 13, BlockData.of("minecraft:stone"));
        }
    }
    @Override public boolean structure(String id, int cx, int cz) { return !id.startsWith("minecraft:village"); }
    @Override public boolean feature(String id, int cx, int cz) { return !id.equals("minecraft:ore_diamond"); }
    @Override public void decorate(DecorationRegion r) {             // 원점 장식 뒤, 둘레 3×3 청크에 쓸 수 있다
        var rnd = new java.util.SplittableRandom(r.decorationSeed());
        int x = r.originChunkX() * 16 + rnd.nextInt(16), z = r.originChunkZ() * 16 + rnd.nextInt(16);
        int y = r.highestBlockY(x, z) + 1;
        r.setBlock(x, y, z, BlockData.of("minecraft:lantern"));
    }
}
```

| 단계(`GenerationStage`) | 바닐라를 끄면 | 훅 | 쓸 수 있는 곳 |
|---|---|---|---|
| `NOISE` | 공기에서 시작(바이옴은 그대로 샘플) | `noise(GenerationChunk)` | 그 청크 |
| `SURFACE` | 표면 규칙·기반암 없음 | `surface(GenerationChunk)` | 그 청크 |
| `CARVERS` | 동굴·협곡 깎기 없음 | `carvers(GenerationChunk)` | 그 청크 |
| `STRUCTURES` | 그 청크를 시작점으로 하는 구조물 없음 | `structure(id, cx, cz)` — 바닐라가 고른 뒤 거른다(Paper `AsyncStructureGenerateEvent` 취소와 같다) | — |
| `FEATURES` | 지형지물(나무·광석·꽃·호수…)만 빠지고 구조물 조각은 그대로(구조물은 `STRUCTURES` 로 따로 끈다. CraftBukkit `shouldGenerateDecorations=false` 는 구조물 조각까지 건너뛴다고 알려져 있다(소스 미확인) — 호환 어댑터는 FEATURES 와 그 원점의 구조물 조각을 함께 끄는 쪽으로 맞춘다) | `feature(placedFeatureId, ox, oz)`, `decorate(DecorationRegion)` | 원점 둘레 3×3 청크(바닐라 `WorldGenRegion` 쓰기 범위). 읽기는 5×5 |

- `runVanilla` 는 청크마다 묻는다. 끈 단계의 훅도 불린다.
- `DecorationRegion.canRead` 가 참인 칸만 믿는다. 보통 생성은 원점 둘레 5×5 를 읽을 수 있지만, 청크 재생성(독립 경로)은 재생성 대상 청크 둘레 5×5 만 들고 있어 이웃 원점의 장식 훅에서는 원점에서 2칸 떨어진 일부 청크가 `canRead=false`(공기)다 — 그런 칸을 읽어 결정하는 훅은 재생성 결과가 보통 생성과 다를 수 있다.
- 좌표: `GenerationChunk` 는 청크 안 0..15 + 월드 y, `DecorationRegion` 은 월드 좌표. `setBlock` 은 이웃 갱신·물리·빛 없이 놓는다(바닐라 생성 단계와 같다). 상자 같은 블록 엔티티 블록은 청크가 올라갈 때 빈 블록 엔티티가 생긴다.
- `decorationSeed()` 는 바닐라 장식 씨앗(`WorldgenRandom.setDecorationSeed(seed, ox*16, oz*16)`). 이걸로 난수를 만들면 같은 시드·같은 청크에서 늘 같다.
- 지형지물을 하나 거절해도 다른 지형지물의 배치 난수는 그대로다(지형지물마다 씨앗을 다시 정한다). 바닐라 `WorldGenRegion` 공용 난수를 이어 쓰는 몇몇은 달라질 수 있다.

### 규칙

- **스레드**: 모든 훅은 생성 스레드(여러 개가 동시에)에서 불린다. 스레드 안전해야 하고 서버 API(월드·엔티티·스케줄러)를 부르면 안 된다.
- **결정성**: 같은 청크에 대해 여러 번 불릴 수 있다(스폰 찾기·청크 재생성). 결과는 (시드, 청크 좌표)만의 함수여야 한다 — 아니면 청크 경계가 어긋난다.
- **구조물 배치 높이는 바닐라 노이즈로 판정한다.** 바닐라 `getBaseHeight`·`getBaseColumn`(마을·폐허 포털·네더 화석 등의 높이·바닥 찾기)은
  노이즈 단계만으로 계산하고 `NOISE`·`SURFACE` 훅을 보지 않는다. 그래서 플러그인이 지형을 크게 바꾸면 구조물이 바닐라 지형 높이에 놓인다
  (떠 있거나 묻힐 수 있다). CraftBukkit `CustomChunkGenerator` 도 플러그인이 `getBaseHeight` 를 구현하지 않으면(`UnsupportedOperationException`) 바닐라 높이를 쓰고, `getBaseColumn` 은 늘 바닐라에 맡긴다(Paper 1.21.1 원문으로 확인, PR #232 검토).
  플러그인 지형 높이를 구조물에 반영하는 훅은 아직 없다(`docs/user-todo/entries/2026-11-11-worldgen-hooks-base-height.md`).
- **예외**: 훅이 던지면(`RuntimeException`, 핫 언로드 중의 `NoClassDefFoundError` 같은 `LinkageError`) 기록하고(처음 10번, 그 뒤 1000번마다) 그 훅만 건너뛴다. 청크는 계속 만든다.
- **플러그인이 꺼지면**: 저장소에서 그 키가 빠지고 그 월드의 새 청크는 바닐라로 만든다(Paper 에서 생성기 플러그인이 빠졌을 때와 같다). 이미 만든 청크는 그대로다. 훅이 바뀌거나 빠지면 다음 청크 생성 때 훅 결과가 든 캐시(지형·높이·구조물 시작점)를 비운다 — 그때 이미 만들던 청크와 장식을 기다리던 청크는 옛 훅 결과일 수 있다. 월드는 키만 쥐므로(캐시 무효화 표식도 약한 참조) 플러그인 클래스를 붙들지 않는다(핫 리로드 때 새지 않는다). 핫 리로드로 다시 켜면 그 플러그인의 `WORLD_GENERATION` 처리기만 다시 돌아 같은 키의 훅이 바뀐다.
- 다른 플러그인이 쓰는 키는 거절한다. 키의 namespace 는 그 플러그인의 namespace 여야 한다.

## 2. 월드에 걸기

- **추가 월드**: `WorldCreator.named("islands").generator(WorldGenerator.VOID).generationHooks("islands:islands").build()`. 키는 level.dat 의 `furnace:world` 설명서(`generation_hooks`)에 남아 `/worlds load`·`worlds.autoload` 도 같은 키로 연다.
  노이즈 생성기(DEFAULT·LARGE_BIOMES·AMPLIFIED)와 평지·빈 월드(FLAT·VOID) 모두 된다. 평지·빈 월드는 NOISE(층 쌓기)만 바닐라 단계가 있고, 장식 훅은 그 청크 안에만 쓸 수 있다.
- **기본 세 차원**: `furnace.properties` 의 `worldgen-hooks.overworld`, `worldgen-hooks.the_nether`, `worldgen-hooks.the_end`(기본 없음 = 바닐라). 부트스트랩 처리기가 등록한 뒤, 스폰 계산·첫 청크 전에 건다.
- 이미 만든 청크는 바뀌지 않는다. 훅을 도중에 걸면 경계가 생긴다 — 필요하면 아래 재생성을 쓴다.

## 3. 청크 재생성

```java
world.regenerateChunk(cx, cz);                                  // 엔티티는 그대로
world.regenerateChunk(cx, cz, ChunkRegeneration.FRESH);          // 엔티티를 지우고 생성 때 엔티티·처음 동물까지
```

운영자는 `/worlds regen [반경]`(실행 위치 청크 둘레) 또는 `/worlds regen <청크x> <청크z> [반경]`(반경 0..8, 권한 3).

| 항목 | 규칙 |
|---|---|
| 결과 | 같은 시드의 새 월드에서 그 청크가 처음 만들어질 때와 같은 블록·바이옴(노이즈 생성기는 공유 장식 상태를 쓰지 않는 독립 경로 — 5×5 pristine + 둘레 원점 9개를 행 순서로 장식). 이웃 원점에서 넘어오는 나무·구조물 조각도 들어간다. 지금 걸린 월드젠 훅을 쓴다(훅이 바뀌었으면 캐시를 비우고 새 훅으로) |
| 바꾸는 것 | 그 청크 하나의 블록·바이옴·블록 엔티티·예약 틱·구조물 시작점/참조·POI·높이 지도. 이 청크에서 이웃으로 넘치는 장식은 이웃을 바꾸지 않는다 |
| 이어 가는 것 | 엔티티(기본 — 워든·알레이의 진동 리스너는 새 청크에 다시 건다), 플러그인 영속 데이터(`ChunkBukkitValues`)·모르는 최상위 NBT 키, 머문 시간(InhabitedTime) |
| 빛 | 옛 빛 배열을 이어받아 바뀐 칸만 다시 계산한다(블록을 하나씩 바꾼 것과 같은 결과) — 이웃으로 번졌던 옛 빛도 지워지고, 이웃의 빛 변화는 평소처럼 틱 끝에 보낸다 |
| 스레드 | 생성은 생성 풀, 교체·재전송은 그 청크를 가진 region 스레드에서만 — 다른 region 의 청크는 건드리지 않는다. 아무 스레드에서 불러도 되고 future 를 돌려준다 |
| 재전송 | 그 청크를 보는 플레이어마다 구독을 새로 만들고(그 사이 블록 변경은 청크 뒤로 미룬다) 새 청크 패킷을 보낸다(Paper `refreshChunk` 처럼 배치 밖). 옛 청크를 직렬화하던 전송은 구독이 바뀌어 버려진다 |
| 로드 안 된 청크 | 먼저 불러온 뒤(디스크 엔티티 읽기가 끝날 때까지 기다린다) 바꾼다. 바꾸기 전에 내려가면 다시 불러와 최대 3번 |
| 동시 수 | 생성은 월드마다 한 번에 하나씩 줄을 선다(하나가 이미 생성 풀을 병렬로 쓴다) — `/worlds regen 8` 이 플레이어 청크 생성을 밀어내지 않게 |
| 저장 | 바꾼 청크는 dirty — 다음 저장에 디스크에 쓴다 |

## 4. 바닐라·Paper 와 같은 것 / 다른 것

- 훅이 없으면 바닐라와 같다(시험 `GenHooksTest.defaultHooksGiveVanillaBlocks`: 아무것도 안 하는 훅을 단 청크도 블록이 같다).
- `/locate` 는 바닐라처럼 구조물 거르기를 보지 않는다(바닐라 `/locate` 가 `generate-structures` 를 보지 않는 것과 같다, Paper 이벤트 취소도 마찬가지).
- 바닐라에는 청크 재생성이 없다. 결과 기준은 "같은 시드 새 월드의 그 청크"로 잡았다. 서버의 보통 생성 경로는 요청 순서대로 원점을 한 번씩 장식하므로, 겹치는 장식(경계의 나무 두 그루 등)은 원래 청크와 재생성 청크가 다를 수 있다 — 바닐라도 생성 순서에 따라 그렇다.
- Bukkit `ChunkGenerator`·`BlockPopulator`·`BiomeProvider` 이름은 호환 어댑터(H1~H4)가 이 훅 위에 맞춘다. 바이옴 바꾸기 훅(BiomeProvider)은 이번 묶음에 없다 — 바이옴은 W2 의 등록 바이옴 + `World.setBiome` 으로.
