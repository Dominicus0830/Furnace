# 서버 내부 접근(NMS) 쉽게 쓰기

> 2026-11-06. 대상: `furnace-api` 의 `org.domi.furnace.api.nbt`, `org.domi.furnace.api.internal`, `Entity.readNbt/editNbt/mergeNbt`, `World.blockEntityNbt/editBlockEntityNbt/mergeBlockEntityNbt`. 서버 구현은 `furnace-server/.../plugin/InternalsImpl.java`, `ApiNbt.java`.
> 근거: Paper·Spigot 불만 조사 107번("NMS 가 필요 없게 하기"), 결정 D2·D5, 1.21.1 이후 버전 지원 방침.

## 1. 왜 Paper 의 NMS 는 어렵고 Furnace 는 어떻게 다른가

Paper·Spigot 에서 플러그인이 서버 내부(`net.minecraft.server`, "NMS")를 쓰려면 난독화 매핑(Spigot·Mojang 매핑, paperweight-userdev), 버전마다 바뀌는 패키지 이름, 리플렉션, 그리고 "어느 스레드에서 불러도 되나"를 스스로 알아내야 한다. 버전이 바뀌면 `NoSuchMethodError` 로 서버 시작 때 터진다.

Furnace 는 서버 코드를 처음부터 직접 썼고 **난독화가 없다.** 내부 클래스 이름이 그대로 `org.domi.furnace.server.entity.Zombie`, `...attribute.Attribute.MOVEMENT_SPEED` 다. 그래서 Furnace 의 일은 두 가지다.

1. **NMS 를 쓰는 흔한 이유를 안정 API 로 없앤다** — 엔티티·블록 엔티티의 NBT 를 `/data` 와 같은 의미로 읽고 고치는 API(1단).
2. **그래도 내부가 필요하면 공식 다리로 안전하게 꺼내 준다** — 리플렉션 없이 타입을 지정해 꺼내고, 소유 스레드를 서버가 맞춰 주며, 지원 버전을 한 줄로 밝힌다(2단 `Internals`).

| 단 | 무엇 | 버전이 바뀌면 | 언제 쓰나 |
|---|---|---|---|
| 0 | 이벤트·스케줄러·월드·엔티티 등 일반 API | 그대로(semver, D5) | 늘 먼저 |
| 1 | NBT 데이터 API (`readNbt`/`editNbt`/`mergeNbt`, 블록 엔티티 판) | API 는 그대로. NBT **키 이름**만 마인크래프트 데이터 버전을 따른다(`/data` 와 같다) | 일반 API 에 없는 엔티티·블록 엔티티 상태(NoAI, 무적, 생성기 설정, 표지판 글, 속성 …) |
| 2 | 내부 객체 다리 `Internals` | 다리 자체는 그대로, **꺼낸 내부 클래스·메서드는 바뀔 수 있다** | 1단으로도 안 되는 것(목표·두뇌 AI, 경로 찾기, 빛, 내부 계산 …) |

패킷은 별도 패킷 API 가 맡는다(패킷 API 문서). 이 문서는 패킷을 다루지 않는다.

## 2. 1단 — NBT 데이터 API (내부 클래스 없이)

### 2.1 NBT 타입

`org.domi.furnace.api.nbt` 의 `NbtCompound`, `NbtList`, `NbtType`, `Snbt` 는 서버 버전과 무관한 NBT 트리다. 값의 자바 형은 `Byte/Short/Integer/Long/Float/Double/String/byte[]/int[]/long[]/NbtList/NbtCompound` 다(참·거짓은 바이트 1/0).

```java
NbtCompound n = NbtCompound.parse("{NoAI:1b,Tags:[\"boss\"],Attributes:[{id:\"minecraft:generic.max_health\",base:40d}]}");
n.putBoolean("Silent", true);
n.compound("Brain").compound("memories");      // 없으면 만들어 붙인다(깊은 경로 고치기)
n.list("Tags").add("elite");                   // 리스트도 같다
String snbt = n.toString();                    // 한 줄 SNBT(키 정렬) — /data get 출력과 같은 꼴
```

- 규칙은 바닐라 `CompoundTag`·`ListTag`·`TagParser`(1.21.1)와 같다. 게터는 키가 없거나 형이 다르면 기본값(0·""·빈 묶음), 숫자끼리는 변환한다. `getCompound`/`getList` 는 없을 때 **붙지 않은** 빈 값을, `compound`/`list` 는 만들어 **붙인** 값을 준다.
- 리스트 원소는 한 종류만(다르면 `IllegalArgumentException`). SNBT 문법 오류는 `SnbtParseException`(위치 포함).
- `merge` 는 `/data merge` 와 같다: 묶음은 안쪽으로 합치고 리스트·값은 통째로 바꾼다.
- 스레드 안전하지 않다(한 스레드에서 만들고 고친다). `UUID` 는 `putUuid`/`getUuid`(int[4]).

### 2.2 엔티티 NBT

```java
entity.readNbt().thenAccept(nbt -> log(nbt.getFloat("Health")));          // /data get entity
entity.editNbt(nbt -> { nbt.putBoolean("NoAI", true); nbt.list("Tags").add("boss"); });   // /data modify
entity.mergeNbt(NbtCompound.parse("{Invulnerable:1b}"));                   // /data merge entity
```

- 엔티티를 가진 region 스레드에서 돈다(엔티티가 다른 region 으로 넘어가도 따라간다). 결과는 `CompletableFuture`.
- `editNbt` 는 읽기 → 고치기 → **바뀌었을 때만** 싣기(`Entity.load`)를 한 번에 한다. 바뀌었으면 `true`. UUID 는 바뀌지 않는다(`/data` 와 같다).
- 플레이어는 바닐라처럼 읽기만 된다(`editNbt` 는 `UnsupportedOperationException`). 엔티티가 사라졌으면 `IllegalStateException`.

### 2.3 블록 엔티티 NBT

```java
world.blockEntityNbt(pos).thenAccept(o -> o.ifPresent(nbt -> log(nbt.getString("id"))));   // /data get block
world.editBlockEntityNbt(pos, nbt -> nbt.putShort("Delay", (short) 20));                    // 생성기 지연
world.mergeBlockEntityNbt(pos, NbtCompound.parse("{CustomName:'\"Loot\"'}"));
```

- 그 칸을 가진 region 스레드에서 돈다. 로드되지 않았거나 블록 엔티티가 없으면 `Optional.empty()`/`false`(청크를 만들지 않는다).
- 쓰기는 `/data` 와 같은 경로(`loadWithComponents → setChanged → 클라이언트 갱신`).

**버전 메모**: NBT 의 키 이름·구조는 마인크래프트 데이터 버전을 따른다(1.21.1 = 3955, `Internals.dataVersion()`). 이후 버전 지원 때 API 메서드는 그대로이고, 바닐라가 키를 바꾸면 그 키만 바뀐다 — `/data` 명령을 쓰는 데이터팩과 같은 처지다.

## 3. 2단 — 내부 객체 다리 `Internals`

### 3.1 빌드 설정

서버 jar 를 그대로 `compileOnly` 로 둔다. 실행용 단일 jar(`furnace-server.jar`, `:furnace-server:fatJar`)에 API 와 서버 클래스가 다 들어 있다.

```kotlin
// build.gradle.kts (플러그인)
dependencies {
    compileOnly(files("libs/furnace-server.jar"))   // 서버를 돌리는 그 jar
}
```

서버 저장소 안에서는 `compileOnly(project(":furnace-server"))` — `furnace-plugin-test` 의 `internalsExample` 소스 묶음이 이 꼴이고 CI 가 함께 컴파일한다(`InternalsExamplePlugin.java`). 매핑·리맵·리플렉션은 필요 없다.

### 3.2 쓰는 법

```java
Internals in = Internals.get();                 // = server().internals()
in.requireMinecraft("1.21.1");                  // onEnable 첫 줄: 맞지 않는 서버에서는 이유가 분명한 오류로 켜지지 않는다

// 비동기: 아무 스레드에서 → 엔티티를 가진 region 스레드에서 돈다
in.runWithEntity(entity, Zombie.class, z -> z.setNoAi(true));
in.withEntity(entity, Mob.class, m -> m.goalSelector()).thenAccept(...);

// 동기: 이미 그 region 스레드일 때(그 엔티티 이벤트 처리기, entityScheduler 작업 안)
@Subscribe
public void onDamage(EntityDamageEvent e) {
    Mob mob = in.entityNow(e.entity(), Mob.class);
    mob.attributes().get(Attribute.MOVEMENT_SPEED).setBaseValue(0.3);
}

// 칸·region: 그 칸의 RegionLevel(블록·블록 엔티티·엔티티·빛)
in.withLevel(world, pos, RegionLevel.class, level -> level.getBlockLight(new Pos(x, y, z)));

// 서버 전체: 전역 스레드(게임 규칙·시간·날씨·점수판의 주인)에서
in.withServer(ServerCore.class, core -> core.gameRules());

// 거꾸로: 내부 객체 → API 핸들(소유 스레드에서)
Entity api = in.wrapEntity(internalZombie);
```

| 메서드 | 어느 스레드에서 부르나 | 실패 |
|---|---|---|
| `withEntity`/`runWithEntity` | 아무 스레드(Future) | 사라짐 `IllegalStateException`, 형 다름 `ClassCastException` |
| `entityNow` | 그 엔티티의 region 스레드만 | 아니면 `ThreadOwnershipException` |
| `wrapEntity` | 그 엔티티의 region 스레드(플레이어는 아무 스레드) | 아니면 `ThreadOwnershipException` |
| `withLevel` | 아무 스레드(Future) | 로드 안 됨 `IllegalStateException` |
| `levelNow` | 그 칸의 region 스레드만 | 아니면 `ThreadOwnershipException` |
| `world`/`server` | 아무 스레드(객체를 꺼내기만) | 형 다름 `ClassCastException` |
| `withServer` | 아무 스레드(전역 스레드에서 돈다) | — |

- 형이 틀리면 **실제 내부 클래스 이름**이 메시지에 나온다(`엔티티 minecraft:cow 의 내부 객체는 ...Cow 이다 — ...Zombie 가 아니다`).
- 꺼낸 객체를 다른 스레드로 들고 나가지 않는다. 다른 region 의 것은 그 region 으로 다시 보낸다(`withEntity`·`withLevel`). region 스레드에서 Future 를 `get()`/`join()` 으로 기다리면 서버가 거부한다(교착 방지, `RegionFuture`).
- 서버는 `Internals` 를 처음 부른 플러그인 이름을 로그에 한 번 남긴다 — 운영자가 버전을 올릴 때 다시 확인할 플러그인을 안다.

### 3.3 자주 찾는 내부 클래스

| 하고 싶은 일 | 내부 클래스·메서드 |
|---|---|
| 몹 목표(Goal) 추가·제거 | `server.entity.Mob#goalSelector()`, `#targetSelector()`, `server.entity.ai.Goal` |
| 두뇌(Brain) 몹 기억·활동 | 종류별 접근자(`Allay#allayBrain()`, `Axolotl#axolotlBrain()` …) → `server.entity.ai.brain.Brain` |
| 속성(이동 속도·최대 체력 …) | `LivingEntity#attributes()` → `AttributeMap#get(Attribute.X)` → `AttributeInstance#setBaseValue` |
| AI 끄기·조용히·무적 | 1단 NBT(`NoAI`·`Silent`·`Invulnerable`)가 더 낫다 |
| 블록 상태·이웃 갱신·빛 | `server.world.RegionLevel`(= `ChunkLevel`) `getBlockState`·`setBlock`·`getBlockLight`·`getRawBrightness` |
| 블록 엔티티 내부 | `RegionLevel#getBlockEntity(Pos)` → `server.block.entity.*` |
| 게임 규칙·점수판·명령 | `ServerCore#gameRules()`·`scoreboard()` 등(전역 스레드에서 `withServer`) |
| 플레이어 연결·패킷 | 패킷 API 를 쓴다(이 다리로 패킷을 직접 만들지 않는다) |

정확한 이름은 서버 소스가 기준이다(난독화 없음). 코드 위치를 찾을 때는 `graphify query "<질문>"` 도 쓸 수 있다.

### 3.4 청크 내부 다리 `ChunkInternals` (호환 계층 전용)

`Internals#chunks()` 는 Bukkit·Paper 호환 계층의 NMS 청크 shim(`LevelChunk`·`LevelChunkSection`·`PalettedContainer`·`Heightmap`)이 쓰는 번호 단위 다리다
(블록 상태 번호·바이옴 번호 읽기 12개, 바닐라 `Level.setBlock`·`LevelChunk.setBlockState` 그대로인 단일 칸 쓰기 2개). **호환 계층 전용이고 버전 중립 보장이 없다** —
일반 플러그인은 `World`·블록 API 나 위의 `withLevel` 을 쓴다. "Now" 메서드는 그 청크를 가진 region 스레드에서만 되고, 로드되지 않은 청크는 표시값(-1·null·`Integer.MIN_VALUE`)을
돌려줄 뿐 불러오지 않는다. 플러그인이 직접 꺼내면 `Internals` 처럼 로그에 남는다. 상세: `docs/feature-parity/compat-nms-n1-n2a-2026-11-14.md`.

## 4. 버전 정책 (D5 와 1.21.1 이후 버전 지원)

- **안정**: `Internals` 인터페이스, NBT 타입, 1단 메서드. semver 를 따르고 폐기는 대체를 먼저 낸 뒤 두 마이너 이상 유지한다.
- **불안정**: `Internals` 로 꺼낸 내부 클래스·메서드. 마인크래프트 버전이나 Furnace 버전이 바뀌면 이름·동작이 달라질 수 있다. `requireMinecraft(...)` 로 지원 버전을 밝히고, 내부 접근은 플러그인 안 한 클래스에 모아 두면 새 버전 대응이 한 파일로 끝난다.
- 내부 접근이 자주 필요한 쓰임새는 안정 API(0·1단)로 올린다 — 목표·두뇌 AI, 속성, 스폰 정책 등은 불만 조사 표(17~25번)에 이미 올라 있다.

## 5. 시험

- `furnace-api` `NbtTest`: SNBT 종류 추론·쓰기 꼴·왕복, `CompoundTag` 게터·`merge`, 리스트 종류 규칙, 깊은 복사·UUID.
- `furnace-server` `InternalsApiTest`: 엔티티 NBT(실림·UUID 유지·바뀜 여부·사라짐), 플레이어 읽기 전용, 블록 엔티티 NBT(상자 칸 실림·없는 칸·청크 안 만듦), `Internals` 버전 확인·소유 스레드 규칙(다른 region 거부 포함)·형 오류 메시지·`wrapEntity`·`withLevel`·`levelNow`·`withServer`.
- `furnace-plugin-test` `internalsExample`: 서버 jar 를 compileOnly 로 둔 예제가 컴파일되는지(CI `:furnace-plugin-test:classes`).
