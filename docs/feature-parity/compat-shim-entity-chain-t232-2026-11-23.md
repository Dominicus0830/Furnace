# 엔티티 사슬을 연결 명세로 (T-232, 2026-11-23)

플러그인은 엔티티를 **클래스로 가른다**: `entity instanceof Mob`, `handle instanceof ItemFrame`, `e instanceof HangingEntity`.
지금까지 엔티티 shim 은 손으로 쓴 `Entity`·`LivingEntity`·`ItemEntity`·`ExperienceOrb`·`ServerPlayer` 넷뿐이었고, 바닐라와 달리 `Entity`·`LivingEntity` 가 구체 클래스였다
(좀비의 `getHandle()` 이 그냥 `LivingEntity`). 이번에 **모든 엔티티 객체를 바닐라와 같은 클래스로** 만들고, 사슬(`Entity` → `LivingEntity` → `Mob` → …)의 선언은
shim 생성기(T-229)가 서명표에서 만든다. T-231(블록·아이템)과 같은 방식이다.

## 1. 한 일

| 부분 | 내용 |
|---|---|
| 표 뽑기 | `kind-classes` 도구(`ExportKindClasses`)에 `E <엔티티 종류> <클래스>` 줄을 더했다. 정답은 바닐라 `EntityType` 상수 필드의 형식 인자(`EntityType<Zombie> ZOMBIE`). 도구가 그 클래스가 구체이고 `(EntityType, Level)` 생성자가 있는지 보고, 레벨 없이 만들어지는 **46종은 공장이 만든 객체의 클래스와 같은지** 대조한다(나머지 83종은 레벨이 있어야 생성자가 돌아 형식 인자를 그대로 믿는다 — **가정**: 공장이 형식 인자와 같은 클래스를 만든다. 월드 시험의 좀비·표지·상자 배는 실물로 맞음). 플레이어는 뺀다(공장이 없음, 늘 `ServerPlayer`). 129줄 |
| 명세 | `src/shimspec/entity.spec`: `Entity`·`LivingEntity`·`Player`·`ItemEntity` 는 손 조각(`src/shims/java`, `git mv`), 바닐라 멤버 연결 55줄에 검사 종류. 표의 129클래스와 그 상위 사슬·인터페이스(새로 209클래스, 그중 인터페이스 41)는 생성 |
| 객체 만들기 | `NmsEntityKinds`: `CraftEntity#getHandle()`(종류 이름 → 표 → `C(ShimCtor)`)과 `EntityType#create(level)`(아직 월드에 없는 엔티티). 재료는 표지에 실려 사슬 맨 위 조각 `Entity(ShimCtor)` 가 받는다(Bukkit 객체 또는 종류·레벨). `CraftLivingEntity`·`CraftItem`·`CraftExperienceOrb` 의 손 `createHandle` 은 지웠다 |
| 생성기 | ① 조각이 `C(ShimCtor 이름)` 생성자를 스스로 쓰면 생성기는 만들지 않는다(재료를 받는 사슬 맨 위). ② 종류 표의 클래스는 조각이어도 그 생성자를 받는다(`ItemEntity`). ③ Paper 내부 패치 인터페이스 `ca/spottedleaf/`(moonrise 청크·추적 시스템 — Paper `Entity` 가 구현)는 잠금 파일·선언에서 뺀다. 서명 관문도 같은 규칙(`type-missing` 에서 제외) |
| 소유 규칙 | `NmsEntityOwner`(명세 `owner=`): 엔티티를 가진 스레드·틱 스레드가 아닌 스레드는 통과(몸통이 주인 region 에 맡겨 기다린다), 다른 region·전역 스레드는 `ThreadOwnershipException`. 아직 월드에 없는 엔티티·플레이어는 통과. `NmsEntities.call` 과 같은 규칙을 몸통 앞에서 먼저 본다 |
| 담는 탈것 | 손 shim `Clearable` 의 추상 메서드 `clearContent` 를 바닐라처럼 `AbstractMinecartContainer`·`ChestBoat` 가 갖는다 — 명세 위임 한 줄 → 다리 `EntityEdit.ClearContent` → 서버 `ContainerEntity`(전리품 표 풀기 뒤 칸 비우기, Paper jar javap: `unpackChestVehicleLootTable(null)` → `getItemStacks().clear()`) |

## 2. 시운전 판정 — 명세가 Java 보다 짧고 안전한가 (설계 11장 3번, 이 PR 에서 끝낸다)

- **선언(상속·인터페이스·추상·final)은 명세가 압도적으로 낫다.** 엔티티 클래스 129개와 상위 사슬 80개를 손으로 쓰면 수천 줄이고 판마다 손으로 맞춰야 한다. 명세는 0줄(표가 저절로 더함)이고,
  잠금 파일이 바닐라 모양을 묶어 판이 바뀌면 `shimLockCheck` 가 알린다. 이번에 손 shim 이 바닐라와 달랐던 곳이 기계로 드러났다: `Entity#discard()`·`setPosRaw(DDD)`·
  `getX/Y/Z`·`isRemoved` 가 Paper 에서 **final**(손 shim 은 아니었다), `Sniffer`·`Armadillo` 는 바닐라에서 `Animal` 하위인데 손 shim 은 final 빈 껍데기였다.
- **위임 한 줄짜리 연결(`clearContent`)도 명세가 짧다**: 한 줄 + 핵심 층 함수. 생성 몸통이 소유 검사를 첫 줄에 넣는다.
- **스레드 길을 고르는 몸통(기존 엔티티 멤버 대부분)은 Java 조각이 맞다.** 명세에는 `@hand` 와 검사 종류만 적는다 — 그 값어치는 ① 잠금 파일이 서술자·접근·final 을 바닐라에 묶고
  ② `read=owner`·`write` 는 몸통 첫 호출이 소유 검사인지 빌드 시험(`ShimSpecBuildTest`)이 보는 것이다.
- **명세에 적지 않은 손 멤버**: 몸통이 Bukkit API 를 거치는 것(체력·속성·손 아이템, 월드 엔티티 `moveTo`, `Player` 의 게임 모드·프로필 읽기). 그 API 가 주인에게 넘기는 규칙을
  갖고 있어 소유 검사를 앞에 두면 동작(다른 region 에서 부르면 넘기기 → 던지기)이 바뀐다. 이 판정은 명세 머리 주석에도 적었다.
- 결론: **형식은 그대로 쓴다**(Java 주석 방식으로 바꾸지 않는다). 다음 패키지(`ServerLevel`·`ItemStack`·`MinecraftServer`)도 같은 나눔 — 선언·위임은 명세, 길 고르기는 조각 + `@hand`.

## 3. 바뀐 동작(바닐라·Paper 와 같아진 곳)

- `getHandle()` 이 종류의 바닐라 클래스: 좀비 `Zombie`(→ `Monster` → `PathfinderMob` → `Mob` → `LivingEntity`, `Enemy`), 표지 `Marker`, 액자 `ItemFrame`(→ `HangingEntity`),
  발광 액자 `GlowItemFrame`(→ `ItemFrame`), 상자 배 `ChestBoat`, 낚시찌 `FishingHook` …. Axiom 이 가르는 `Marker`·`HangingEntity`·`ItemFrame` 이 바닐라와 같은 길을 탄다.
- 서버: 전리품 표 풀기(상자 블록·담는 탈것)가 데이터 팩이 없을 때도 표 이름을 지운다. 바닐라는 표를 못 찾아도(`LootTable.EMPTY`) 이름을 지우고 채우기만 하지 않는다. 검토에서 더한 `clearContent` 의 전리품 표 단언이 잡았다.
- `EntityType#create(level)` 도 그 클래스. 낚시찌는 바닐라처럼 null(`createNothing`).
- `Entity`·`LivingEntity`·`Mob` 등은 바닐라처럼 추상. 플러그인이 상속해 몹을 만드는 것(Citizens 류)은 여전히 지원하지 않는다(결정 D3 — T-237 설계 뒤). 생성 클래스에는 생성기 전용
  생성자만 있어 바닐라 생성자를 부르는 하위 클래스는 지금처럼 링크 덫에 걸린다.
- `ServerPlayer#isRemoved` 재정의는 Paper 에서 `Entity#isRemoved` 가 final 이라 지우고, 월드에 없는 플레이어 판정을 `Entity` 조각으로 옮겼다(결과 같음).
- 생성된 엔티티 클래스에는 바닐라의 종류별 메서드(예: `Mob#getTarget`, `ItemFrame#getItem`)가 없다 — 부르면 지금처럼 링크 덫(`/furnace compat gaps` 에 잡힘). 표본이 부르는 순서대로 잇는다.

## 4. 시험

- `NmsEntityKindsTest`(새, 5): 129종 모두 `create` 가 표의 클래스(낚시찌만 null), 사슬 추상, 월드의 좀비·표지 클래스와 `instanceof Mob`·`Enemy`, 액자·발광 액자 `HangingEntity`,
  상자 배 `clearContent` 가 칸을 비움, 다른 region 스레드의 쓰기 → `ThreadOwnershipException`.
- `NmsLinkerTest`: 바닐라 final 목록에 위 6개, final 부모 시험의 예를 `Sniffer`(이제 바닐라처럼 상속 가능)에서 `StringUtil` 로.
- `NmsGapRankingTest`: "shim 에 없는 감지 대상" 예를 `Pig`(이제 있음)에서 `FloatGoal` 로.
- 서명 관문: 새 차이 0, 기준선 84줄 삭제(바로잡힌 엔티티 차이 + moonrise 제외로 사라진 줄).

## 5. 표본 지원 비율 (전·후, 같은 표본 100개)

표본은 `fetch-sample-plugins.py … 100 1.21.1`(2026-11-23 받음 — 11-20 표본과 판이 다른 jar 가 섞여 있어 숫자를 그때와 바로 견주지 않는다). 전은 기준 브랜치 0b6b6475, 후는 이 PR, 같은 jar 폴더로
`nmsShimAudit -PauditJars=… -PrankingOut=… -Pmanifest=…`.

| | 전 | 후 |
|---|--:|--:|
| 심볼 단순 합 | 2685 / 3181 (84.4%) | 2707 / 3213 (84.3%) |
| 인기 가중 합 | 15412 / 18151 (84.9%) | 15533 / 18327 (84.8%) |
| Axiom 6.0.1 | 495 / 498 | 501 / 505 |
| vexbot(26.3 대상) | 742 / 1216 | 758 / 1241 |

- 지원 심볼은 22개 늘었다. 비율이 0.1%p 내린 것은 **전에는 클래스가 없어 한 줄로 세던 곳이 이제 클래스가 생겨 그 안의 멤버가 따로 세어지기** 때문이다(분모 +32).
- Axiom: 없던 클래스 3개(`Marker`·`HangingEntity`·`ItemFrame`)가 생겼고, 그 안의 멤버 4개(`ItemFrame#getDirection`·`getRotation`·`setRotation`, `HangingEntity#rotate`)가 새로 보인다 — 다음 PR 에서 잇는다.

## 6. 금 파일 갱신

`identity/1.21.1/scene-compat-asm-prefilter.txt` 5줄(IF-0.12.2 두 옵션 줄, commandapi-bukkit-plugin-10.1.2 두 옵션 줄, TOTAL). 바뀐 클래스 수는 같고 해시만 바뀌었다.
두 jar 가 부르는 클래스 중 이번에 shim 에 생긴 것(IF: `Container`·`Merchant`, commandapi: `ScoreHolder`)과 모양이 바뀐 것(`Entity` 추상·인터페이스, `Player`)이 있어,
변환기가 그 참조를 덫으로 바꾸지 않고 그대로 둔다 — 바닐라에 있는 클래스라 정확성 수정이다.

## 7. 남은 것·주의

- **문자열 감지**: 표본 순위의 "문자열로 가리키는 NMS 클래스" 표에 엔티티 클래스(`Mob`·`Pig`·`Display` 등, 각 2개 플러그인)가 있다. 이제 shim 에 있어 `Class.forName` 이 참이 된다.
  확인한 FancyNpcs·FancyHolograms 는 그 이름을 엔티티 데이터 꾸러미의 키 글로 쓸 뿐 `Class.forName` 을 부르지 않는다. T-231(블록)과 같은 성격이라 그대로 둔다.
- `ServerPlayer` 는 아직 손 shim(`src/main/java`) — 서버 레벨 패키지 이전(`ServerLevel` 과 함께) 때 조각으로 옮긴다.
- 종류별 메서드 채우기, 몹 상속(T-237).
