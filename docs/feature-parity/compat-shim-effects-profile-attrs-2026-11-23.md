# 효과·프로필·속성 shim 채우기 (2026-11-23)

순위표(`furnace/shim-ranking/ranking-2026-11-23-containers.md`)에서 여러 플러그인이 같이 부르는 구멍 중 효과·프로필·속성 묶음과, FAWE 가 부르는 공통 항목을 채웠다. 방식은 손 shim(`src/main/java`)이고 shim 명세·잠금 파일(`shimLock`)은 건드리지 않았다(이 환경에 Paper 서버 서명표가 없어 서명 관문을 못 돌린다 — 아래 "남은 일").

## 들어간 것
| 클래스 | 내용 |
|---|---|
| `MobEffectInstance` | 값 객체. 생성자 7개(복사 포함), 증폭 0~255 자르기, `update`(센 쪽이 이기고 약한 쪽은 숨은 효과로), `isInfiniteDuration`·`endsWithin`·`mapDuration`, `toString`, `equals`·`hashCode`, `save`·`load`, `compareTo`(32147 규칙). |
| `LivingEntity`(조각) | `getEffect`·`hasEffect`·`getActiveEffects(Map)`·`addEffect`(둘)·`removeEffect`·`removeAllEffects`·`getAttributes`. 플레이어는 새 API `Player.effects/addEffect/removeEffect/clearEffects`(공개 API, 기본은 UnsupportedOperationException)로, 몹은 기존 `EntityStates`로 간다. |
| `ResolvableProfile` | 레코드(이름·UUID·속성·게임 프로필), `isResolved`, `resolve`(CraftPlayerProfile 갱신 경유), `DataComponents.PROFILE`(SNBT 왕복). |
| `AttributeMap` | 엔티티에 붙은 보기: `getInstance`·`hasAttribute`·`hasModifier`·`getValue`·`getBaseValue`·`getModifierValue`·`addTransientAttributeModifiers`·`removeAttributeModifiers`·`getSyncableAttributes`. |
| `Util` | 시간·이름·오류 설명·컬렉션 도우미·`shuffle` 류(바닐라 난수 호출 순서 그대로, javap 로 확인). |
| `WorldData`·`ServerLevelData`·`LevelData`·`WritableLevelData` | 인터페이스(타입이 없는 메서드는 뺌). `PrimaryLevelData` 가 구현, 서버 월드는 살아 있는 값을 읽는다. `MinecraftServer.getWorldData()`. |
| `BlockableEventLoop`·`ReentrantBlockableEventLoop`·`TickTask` | `MinecraftServer` 의 상위. `managedBlock` 은 조건이 될 때까지 1ms 씩 쉬며 기다린다(틱 스레드에서 부르면 ThreadOwnershipException — 데드락 방지). |
| `LevelChunk.setBiome(int,int,int,Holder)` | 4칸 단위 바이옴 칸 쓰기(FAWE 재생성 경로). |

## 알려진 차이·남은 일
- 엔티티가 가진 "숨은 효과"는 엔티티 API 가 안 줘서 엔티티에서 읽은 사본에는 없다.
- 몹 효과 쓰기는 소유 스레드가 아니면 예약하고 true 를 돌려준다(Bukkit `addPotionEffect` 와 같다). `addEffect(효과, 출처)` 의 출처는 무시한다.
- 두지 않은 것: `CODEC`·`STREAM_CODEC`, 효과 틱·시작·제거 알림, 색 섞기, `AttributeMap` 의 저장·불러오기·`AttributeSupplier` 생성자.
- 서명 관문(`shimLock`·`signatureGate`)은 Paper 서버 서명표가 있는 PC 에서 돌려야 한다. `LevelChunk#setBiome` 이 Paper 에서 `LevelChunk` 에 선언됐는지 `ChunkAccess` 인지, `LivingEntity#addEffect(MobEffectInstance)` 가 final 인지는 가정이다(바닐라 jar 에서는 final·`LivingEntity` 선언 — 확인함). 시험 `NmsLinkerTest` 의 final 목록에 넣어 두었다.
- Bukkit `Player#addPotionEffect` 가 플레이어에게 되는지는 이 묶음 범위 밖이다(`CraftLivingEntity` 의 물약 메서드는 `EntityStates` 를 쓰고 플레이어 핸들을 거부한다 — 필요하면 새 `Player` API 로 돌리면 된다).

## 시험
`NmsEffectProfileAttributeTest` 16개(값 규칙·플레이어/몹 효과·프로필 왕복·속성·`Util` 난수 순서·월드 데이터·`managedBlock`·`setBiome`). 호환 계층 전체 시험 1530개 통과(금 파일 `scene-compat-asm-prefilter` 변화 없음).

## 지원 비율(표본 100개, Mojang 이름 직접 호출분만 — Spigot 이름 33개는 이 환경 감사에서 건너뜀)
| 기준 | 전 | 후 |
|---|--:|--:|
| 심볼 단순 합 | 2725/3213 (84.8%) | 2747/3222 (85.3%) |
| 인기 가중 합 | 15636/18327 (85.3%) | 15759/18377 (85.8%) |
