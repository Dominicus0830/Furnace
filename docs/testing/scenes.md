# 공용 장면 라이브러리 (T-003 / O15 T-O15-04)

성능 최적화 PR 이 "결과(관측 가능한 동작)를 바꾸지 않았다"는 것을 **저장소 안의 시험으로** 남기고, 같은 장면으로 시간을 재기 위한 장면 모음이다. 장면 자체는 입력이라 정답이 아니다 — 금 파일은 "옛 커밋 == 새 커밋"만 증명한다(바닐라 동일성은 oracle fixture·바닐라 jar 대조의 몫, `docs/testing/identitykit.md` 첫머리).

위치: 장면 코드 `furnace-server/src/test/java/org/domi/furnace/server/scene/`(운영 jar 에 안 들어간다, bench 소스셋 클래스패스에도 들어 있다), 벤치 실행기 `src/bench/java/org/domi/furnace/server/scene/SceneLibraryBench.java`, 금 파일 `src/test/resources/identity/scene-<이름>.txt`.

## 한 장면 = 두 모드, 같은 코드

| 모드 | 누가 돌리나 | 크기 | 하는 일 |
|---|---|---|---|
| **증명** | 평소 시험 `SceneLibraryTest`(CI `test`) | `Size.PROOF`(장면당 1~3초, 공유 파이프라인·노이즈는 약 10초) | 결정성 점검 → 금 파일 줄 단위 비교 |
| **벤치** | `:furnace-server:bench --tests '*SceneLibraryBench*'` | `Size.FULL`(실서버에 가까운 크기) | 걸음(틱·청크)마다 시간·할당 → `build/bench/SceneLibrary-<이름>.json` + `.lines.txt` |

## 장면 목록

| 이름 | 내용 | 기대는 쪽(최적화 TASK) | 모드 |
|---|---|---|---|
| `noise-quick` | 노이즈 7설정 라우터 해시 + 원시 청크(금 파일 `worldgen-noise-quick`) | O04 노이즈·표면·카버 | 같은 JVM |
| `shared-pipeline` | `generateShared` 완성 청크(장식·후처리 포함) 해시, 세 차원 | O04-01~03·08·11 | 같은 JVM |
| `fluid` | 모양 블록이 섞인 땅 위 물·용암 원천 주기, 예약 틱 600 | O07 유체(퍼짐 칸 기억) | 같은 JVM |
| `piston` | 피스톤 기계 40(FULL 200)줄 6종: 보통·끈끈이+점액·꿀·12칸·13칸·막힘 | O08 피스톤, 블록 이벤트 순서 | 같은 JVM |
| `explosion` | TNT 블록 100(FULL 400)개 + 점화 TNT + 몹, 연쇄 폭발 | O05-13·O07-09·T-063 | 새 JVM |
| `mobs` | 몹 120(FULL 1000)마리 8종 + 플레이어 한 명 | O05 전반(충돌·플레이어 근접·속성) | 새 JVM |
| `village` | 침대·직업 블록 11종·종이 있는 마을, 주민 16(FULL 100), 일 시작 직전부터 | O06-01·02·05·08·12 | 새 JVM |
| `chase` | 좀비 24(FULL 200)가 돌 기둥·틈 있는 벽을 지나 플레이어를 쫓음 | O06-05·06·10, 충돌 수집 | 새 JVM |
| `farm` | 문이 20틱마다 열리고 닫히는 울타리 우리 + 밀밭 + 무작위 틱 + 좀비 | O06-06(F5)·05·10, 무작위 틱 | 새 JVM |

PROOF 크기는 위 표의 앞 숫자, FULL 은 괄호. 금 파일의 `...count`·`heads=`·`employed=` 같은 **텍스트 줄**은 사람이 읽는 보조 표시이면서 "장면이 비어 있지 않다"는 확인이다(해시 줄만 있으면 아무것도 안 하는 장면도 통과한다).

### 변이 점검(이 PR 에서 확인함)
운영 코드를 일부러 틀리게 고쳤을 때 해당 장면의 금 파일 시험이 실패하는지 봤다(고친 뒤 되돌림): 피스톤 최대 12→11 → `piston`, 물 틱 지연 5→6 → `fluid`, TNT 중력 0.04→0.05 → `explosion`, `OreFeature` 크기 +1 → `shared-pipeline`(`noise-quick` 은 통과 — 장식 전이라), 좀비 속도 0.23→0.24 → `chase`·`mobs`·`farm`·`explosion`, 주민 일터 접근 반경 9→6 → `village`.

## 새 장면 더하기

1. `scene/` 에 `TickScene` 을 상속하는 `public final class`(public 이고 인자 없는 생성자 — 새 JVM 모드가 이름으로 만든다)를 둔다: `setup(size, round)`, `ticks`, `checkEvery`, `tick(t)`, `checkpoint(rec, label)`. 틱이 아닌 장면(청크 만들기)은 `LibraryScene` 을 직접 구현하고 걸음마다 `probe.before/after` 를 부른다.
2. `SceneLibrary.all()` 에 한 줄.
3. 금 파일 만들기: `./gradlew -PskipOracle=true :furnace-server:test --tests '*SceneLibraryTest*' -Dfurnace.identity.bless=scene-<이름>` (결정성 점검이 먼저 돈다. 새 파일이라도 PR 본문 "금 파일 갱신" 절과 독립 검토 승인이 필요하다 — `docs/testing/identitykit.md`).
4. 변이 점검 한 번: 그 장면이 의존하는 운영 코드를 일부러 틀려 시험이 실패하는지 확인.

## 규칙·한계

- **엔티티가 들었으면 `freshJvm() = true`.** 엔티티 id 카운터(`ChunkLevel.FALLBACK_IDS`)·정적 난수가 JVM 전역이라 같은 JVM 두 번째 실행이 달라진다. 새 JVM 에서 `-Dfurnace.test.seeded=true` 로 돈다(IdentityKit). 블록만 다루는 장면만 같은 JVM.
- **region 하나, 시험 스레드가 직접 틱** — `SceneLevel` 은 예약 틱 → 블록 이벤트 → 엔티티(사라짐 검사 포함) → 블록 엔티티 → 빛 순으로 돈다. 자연 스폰·추적기·네트워크·여러 region 스레드·구역 합치기는 안 돈다(그쪽은 `RegionTickBench`·`MultiDimensionTickBench`). 무작위 틱이 필요한 장면(`farm`)은 직접 `tickChunk` 를 부른다.
- 엔티티 상태 해시는 위치·속도·회전·땅 여부·틱 수 + **저장 NBT 전체**(체력·효과·두뇌 기억·속성) 바이트다(`SceneKit.entity`). 필드 하나만 달라도 잡힌다. 해시에 정체 해시·`HashMap<객체>` 순회·시각을 넣지 않는다 — `NbtCompound` 에는 `toString` 이 없어 `String.valueOf` 로 해시하면 정체 해시가 들어간다(옛 `SharedPipelineBench` 시제품의 `extraNbt` 가 그랬다: 같은 JVM 이 같은 순서로 할당하는 새 JVM 에서만 우연히 안정). 
- `mobs`·`chase` 의 플레이어는 자기 틱을 안 돈다(레벨이 틱하는 몹이 `forEachPlayer` 로 보는 표적일 뿐). 좀비가 붙어도 체력은 그대로다 — 피해 처리는 region 소유 경로라서(후속 장면 후보).
- 금 파일이 환경에 따라 달라질 수 있다는 가설(aarch64 `Math.sin`·`Math.log`)은 `identitykit.md` 의 "금 파일 안정성"과 같다. 이 장면들의 금 파일이 CI 러너(x86_64)에서 같은지는 이 PR 의 CI 로 확인한다.
- **FULL 크기는 금 파일이 없다.** 같은 커밋·같은 JVM 옵션이면 `SceneLibrary-<이름>.json` 의 `hash`(TOTAL)가 같다 — 옛·새 커밋의 `.lines.txt` 를 diff 하면 어느 점검에서 갈렸는지 보인다.

## 벤치 실행

```
./gradlew -PskipOracle=true :furnace-server:bench --tests '*SceneLibraryBench*'                                 # 전부
./gradlew -PskipOracle=true :furnace-server:bench --tests '*SceneLibraryBench*' -Dfurnace.bench.scenes=village,chase
```
JSON 칸: `unit`(tick·chunk·scene)·`steps`·`skip`(앞 4분의 1 은 통계에서 뺌, 걸음 8개 미만이면 0)·`step_mean/p50/p95/max_ms`·`total_ms`·`allocBytesPerStep`(틱 장면만, 장면 스레드 할당 — 결정적 값 아님, `docs/testing/bench.md`)·`hash`·`lines`. **절대 시간으로 CI 를 막지 않는다.** 같은 `hash` 일 때 옛·새 커밋을 번갈아 재서 비교한다.

## 같이 옮긴 것
`NoiseIdentityBench`·`PristineHashBench` 는 `src/test` 에서 `src/bench` 로 옮겼다(`docs/testing/bench.md`). `NoiseIdentityBench` 의 장면 부분(설정·위치·해시·`record`)은 시험 소스 `world/gen/NoiseIdentityScene` 로 떼었고, 평소 시험이던 `NoiseIdentityGoldenTest` 는 `SceneLibraryTest` 의 `noise-quick` 으로 합쳤다(같은 금 파일 `worldgen-noise-quick` 그대로).
