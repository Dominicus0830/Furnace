# CraftBukkit 클래스 계층 설계 (T-264, 2026-11-23)

> 목표: 플러그인이 Paper 에서 하듯 `(CraftPlayer) p`, `inv instanceof CraftInventoryPlayer`, `((CraftCow) e).getHandle()`, `meta.getClass().getName()` 을 해도
> **Paper 와 같은 결과**가 나오게 한다. 그 위에서 Craft 클래스의 Bukkit API 메서드 5,508개가 "이미 있는 Furnace 구현"으로 저절로 채워지게 한다.
> 선행: T-260(전체 껍데기)·T-261(빠진 멤버 합치기) 병합됨. 짝: T-263(연결 추론기) — 7장에서 경계를 정한다.
> 상태: **설계 — 독립 검토 "고쳐서 승인"(11-23, 보고서 공유 폴더 `furnace/reviews/t264-craftbukkit-hierarchy-design-review-2026-11-23.md`) 6건 반영본.** 반영한 곳은 **[검토 N]** 으로 표시. 구현은 바뀐 절 재검토 뒤.

## 1. 결론 (먼저 읽을 것)

1. **정체성 규칙(I):** 플러그인 손에 쥐어지는 Bukkit 객체는 **Paper 가 그 자리에서 주는 바로 그 Craft 클래스의 인스턴스**다. `getClass().getName()` 이 같아야 하고(익명 클래스 25개는 예외, 4.4절), 상위 클래스 사슬·직접 구현하는 API 인터페이스·`final`/`abstract`/접근 수준도 Paper 와 같다.
2. **방법은 "Furnace 구현 = Craft 클래스"(H6 방식 확대)**, 감싸개 위임이 아니다. 지금 `Compat*` 이름으로 있는 구현(인벤토리·아이템 메타·명령 발신자·스케줄러·태그 등 89개 자리)을 Paper 의 Craft 이름·자리로 옮기고, 엔티티처럼 Paper 에만 있는 중간 계층(`CraftMonster`·`CraftAbstractHorse` 등)은 기계로 만든다. 그러면 그 클래스들의 API 메서드(1,539개)는 이미 있는 Furnace 코드가 그대로 맡는다 — 새 동작 코드가 아니라 이름·자리 정리다.
3. **`getHandle()` 다리는 기계 생성:** Paper Craft 클래스 210개가 각자 반환형을 좁힌 `getHandle()` 을 갖는다(`CraftCow#getHandle()` → NMS `Cow`). 껍데기 jar 를 만들 때(ShellMerge 단계) 서명표만 보고 "뿌리 핸들 + `checkcast`" 몸통을 만든다. 지금은 이 210개가 던지는 껍데기 멤버다.
4. **판정은 관측으로:** Paper 실서버에 같은 탐침 플러그인을 올려 "어느 상황에서 어느 클래스가 나오나" 표(이름만)를 뽑아 커밋하고, Furnace 시험이 같은 탐침으로 같은 표를 내는지 본다(줄어들기만 하는 기준선). 서명 관문(T-228)의 Craft `super`·`type-missing` 줄도 같은 일의 척도다.
5. **못 하는 것·남는 것:** Paper Craft 클래스 중 386개는 그것이 구현하는 API 형식 자체가 Furnace `org.bukkit` 에 아직 없다(블록 데이터 종류 246, 책·물약 메타·스코어보드·발전과제 등 140). 이들은 API 형식이 생길 때(영역 10·11, T-240) **이 규칙으로** 들어온다 — 그때 클래스 이름·자리는 이 문서의 표와 시험이 강제한다.

## 2. 지금 상태 (측정)

측정: 이 컨테이너, 기준 브랜치 `2bbc4ba5`(#572 병합 뒤), 서명표 지문이 `furnace-compat-paper/src/shimspec/full-shells-1.21.1.fp` 와 같음(세 표 sha256 일치 확인).
도구: `python3 -I tools/compat/craft-identity-scan.py`(이 PR 에 넣음 — 서명표·컴파일된 호환 클래스를 읽어 숫자와 Craft·Bukkit 이름만 낸다).

| 묶음 | 수 | 뜻 |
|---|---:|---|
| Paper `org/bukkit/craftbukkit/**` 클래스 | 955 | 7,139 중 CraftBukkit 몫 |
| └ API 구현 클래스(impl) | 711 | Bukkit/Paper API 형식을 (상위 사슬로) 구현 — 플러그인이 쥐는 객체의 실제 클래스 |
| └ 정적 도구(static) | 52 | `CraftMagicNumbers`·`CraftChatMessage`·`CraftLocation` 류 변환 도구 |
| └ 내부(internal) | 192 | 명령 등록·스케줄러 내부·월드젠 다리 등 |

API 구현 클래스 711개의 Furnace 쪽 상태:

| 상태 | 클래스 | 그 클래스가 직접 선언한 API 메서드 | 예 | 이 설계에서 |
|---|---:|---:|---|---|
| 같은 이름·같은 상위 | 111 | 1,964 | `CraftEntity`·`CraftPlayer`·`CraftItemStack`·블록 상태 대부분 | 그대로(직접 인터페이스 7곳 보충, 3.3) |
| 같은 이름·**다른 상위** | 102 | 812 | 엔티티 95(`CraftBlaze extends CraftCreature`, Paper 는 `CraftMonster`), `CraftBlock extends CompatBlock`, `CraftWorld`(Paper 는 `CraftRegionAccessor`), `CraftPersistentDataContainer` | 상위를 Paper 대로(3.2) |
| 껍데기인데 **Furnace 가 다른 이름으로 구현** | 89 | 727 | 인벤토리 27(`CompatInventory`↔`CraftInventory`·`CraftInventoryCustom`…), 엔티티 중간 계층 30, 스케줄러 8, 생성기 6, 명령 발신자 4, 태그 5 | 이름·자리 옮기기(3.1·3.2) |
| 껍데기, Furnace 구현 없음 — API 형식은 Furnace 에 있음 | 12 | 61 | `CraftEnchantment`(Furnace `Enchantment` 가 `final`), `CraftGameEvent`(같음), `CraftKeyedBossbar`, `CraftCommandMap`, 도움말 주제 4 | 6장 의존 |
| 껍데기, Furnace 구현 없음 — API 형식부터 없음 | 386 | 1,941 | 블록 데이터 종류 246(`impl/CraftStairs` 등), 메타 20여(`CraftMetaPotion`·`CraftMetaBook`), 스코어보드 6, 발전과제 3 | 규칙만 정함(5장) |
| 범용 표지만 | 11 | 3 | `CraftParticle` 과 그 익명 하위 | 4.4 |

- **[검토 권고]** 첫 판의 표는 합이 713 으로 711 과 어긋났다. 원인: 직접 단 API 가 없는 클래스의 "대표 API" 를 집합 순서로 골라 실행마다 갈래가 바뀌었다(파이썬 문자열 해시가 실행마다 다름). 스캔 도구를 "가장 구체적인 API 전부, 정렬"로 고쳐 세 번 같은 값을 확인했고 위 표가 그 값이다(합 711).
- 로드맵 7.1 의 "6,713" 은 다른 셈법(껍데기 멤버 중 API 와 이름이 겹치는 것 전부)이었다. 위 표는 **Paper Craft 클래스가 스스로 선언한 추상 아닌 메서드 중 API 형식의 메서드와 이름·서술자가 같은 것** 5,508개다.
- 서명 관문 기준선(`shim-signature-baseline.txt`)의 Craft 줄: `super` 102, `type-missing` 227, `descriptor` 221, `class-access` 19 — 위 "다른 상위" 102 와 같은 일이다.
- 반환형을 좁힌 `getHandle()` 210개(엔티티 162). 그 반환형(NMS 클래스)이 이미 호환 jar 에 손·생성 shim 으로 있는 것 180개 — 나머지 30개는 껍데기 jar 에만 있다.
- 최상위 Craft 클래스 784개 중 `final` 250, `abstract` 98, **패키지 전용 18**(`CraftMetaItem`·`CraftMetaSkull`·`CraftMetaLeatherArmor` 등 메타 8, 스코어보드 4, 스케줄러 3 …).

## 3. 설계

### 3.1 정체성 규칙 I 와 "구현 = Craft 클래스"

- 규칙: Furnace 가 플러그인에 주는 Bukkit 객체(이벤트 인자·`getX()` 반환·`Bukkit.createX` 결과·레지스트리 값)는 Paper 가 같은 상황에서 주는 Craft 클래스와 **같은 클래스**다. 같다는 것의 판정은 4장의 관측표.
- **감싸개 위임을 택하지 않는 이유**: `CraftInventory` 가 `CompatInventory` 를 들고 위임하면 (1) 같은 대상에 객체가 둘이라 `==`·Map 키·`equals` 가 깨지고, (2) 이벤트가 넘기는 객체와 `getInventory()` 가 주는 객체가 달라지며, (3) 객체·호출이 하나씩 늘고, (4) 위임 표를 따로 유지해야 한다. H6(11-07)이 이미 `CompatPlayer` 를 `CraftPlayer` 로 옮긴 것과 같은 판단이다(자바 단일 상속 때문에 `CraftPlayer extends CompatPlayer` 로는 `CraftEntity` 사슬에 설 수 없다).
- 옮기는 법: `git mv` + 패키지·이름 변경 + 호출처 수정. 동작 코드는 바꾸지 않는다(검토는 "이름만 바뀜"을 diff 로 확인). `Compat*` 이름을 남겨 두는 별칭 클래스는 만들지 않는다(두 클래스가 같은 구실을 하면 규칙 I 가 흐려진다).
- 한 Furnace 클래스가 Paper 의 여러 클래스 구실을 하면 **나눈다**. 예: `CompatItemMeta` 는 `MetaKind` 로 종류를 가르지만 Paper 는 종류마다 클래스(`CraftMetaItem`·`CraftMetaSkull`·`CraftMetaBanner`·`CraftMetaArmor`·`CraftMetaColorableArmor`·`CraftMetaLeatherArmor`). `MetaKind#create` 가 Paper 클래스를 만들게 하면 된다(이미 종류별 하위가 있어 이름만 바꾸면 거의 맞는다). 인벤토리는 Paper 가 `InventoryType`·만든 길(`createInventory`/블록/엔티티/메뉴)마다 다른 클래스(`CraftInventoryCustom`·`CraftInventoryCrafting`·`CraftInventoryFurnace`·`CraftResultInventory`…)를 쓰므로, `CompatInventories`·`EventInventories`·`BlockInventories` 의 갈래를 관측표의 클래스에 하나씩 맞춘다.
- **패키지 전용 클래스(18개)** **[검토 3]**: Paper 처럼 패키지 전용으로 둔다(`class-access` 가 같아야 한다). `org/bukkit/craftbukkit/**` 안에는 **Paper 에 없는 클래스를 하나도 더하지 않는다**(공개든 패키지 전용이든 — 규칙 I 와 서명 관문 `type-extra` 를 흐리지 않게). 밖(`org.domi.furnace.compat.paper`)에서 만들어야 할 때는 이 순서로만:
  1. Paper 에도 있는 공개 진입점(예: 메타는 `CraftItemFactory#getItemMeta(Material)`·`asMetaFor`, 스코어보드는 `CraftScoreboardManager#getNewScoreboard`). 진입점이 Paper 에서 공개면 Furnace 에서도 공개로 진짜 몸통을 갖는다.
  2. 그런 진입점이 없으면 호환 계층 내부 클래스 **한 곳** `org.domi.furnace.compat.paper.CraftAccess` 가 `MethodHandles.privateLookupIn(대상, lookup())` 으로 생성자 핸들을 얻어 `static final` 로 들고 쓴다(같은 로더·같은 이름 없는 모듈이라 허용된다, JIT 뒤 비용은 직접 호출과 같다). 다른 곳에서 `setAccessible`·`privateLookupIn` 을 쓰지 않는다 — 시험 `CraftAccessStaticTest` 가 상수 풀로 확인한다.
- 생성자: Furnace 쪽 생성자(Furnace 핸들을 받는 것)는 지금처럼 둔다. Paper 의 생성자(`CraftCow(CraftServer, net.minecraft…Cow)` 류)는 T-261 이 껍데기 멤버로 이미 합친다. 그중 플러그인이 실제로 부르는 것(덫 계측 T-233 에 나오는 것)은 3.4 의 "NMS 쪽에서 Furnace 핸들 꺼내기" 다리로 진짜 생성자로 바꾼다 — Paper 에서도 `new CraftCow(server, cow)` 는 캐시와 다른 새 객체이므로, 새 객체를 만드는 것이 Paper 와 같은 결과다.
- **[검토 4] 따로 만든 객체도 같은 최신 값.** Paper 에서는 두 Craft 객체가 같은 살아 있는 NMS 엔티티를 보므로 늘 같은 값을 읽는다. Furnace 는 새 스냅샷을 캐시된 `CraftEntity` 에만 갈아 끼우므로(`furnaceHandle(fresh)`), Paper 생성자로 만든 객체가 제 핸들을 들면 값이 낡는다. 그래서 Paper 생성자 다리로 만든 객체는 **핸들을 따로 들지 않고 캐시된 정식 객체(같은 UUID, `Compat` 캐시)를 가리킨다**: `CraftEntity` 에 `handleSource` 하나(정식 객체는 자기 자신, 다리로 만든 객체는 정식 객체)를 두고 핸들 읽기는 늘 `handleSource.handle` 을 본다. NMS shim(`getHandle()`)도 정식 객체의 것을 준다(Paper 에서 두 객체의 `getHandle()` 은 같은 NMS 객체 — `==`). `equals`/`hashCode` 는 지금처럼 엔티티 기준이라 두 객체는 같다(Paper `CraftEntity#equals` 도 엔티티 id 비교 — 가정, 탐침으로 확인). 시험 `CraftCtorFreshnessTest`: 다리 생성자로 만든 객체와 캐시 객체가 엔티티 이동·체력 변경 뒤 같은 `getLocation`·`getHealth` 를 주고, `getHandle()` 이 같은 객체이고, `equals` 가 참.

### 3.2 계층 정렬 — Paper 와 같은 상위 사슬

- 표: Paper 서명표에서 `org/bukkit/craftbukkit/**` 클래스의 (접근, 상위, 직접 인터페이스 중 NMS 아닌 것)만 뽑은 **잠금 파일** `furnace-compat-paper/src/shimspec/craft-hierarchy-1.21.1.lock` 을 커밋한다. 안에 든 이름은 CraftBukkit·Bukkit·Paper API·Paper 내부(`io/papermc/paper/entity/PaperSchoolableFish` 같은 상위) 이름뿐이고 Mojang 이름은 없다(NMS 인터페이스는 껍데기 jar 가 따로 단다). GPL 원문은 옮기지 않는다 — 서명(이름·상속)만이다.
  - 만들기: `./gradlew -PskipOracle=true :furnace-compat-paper:craftLock`(shimgen `CraftHierarchyLock`, 서명표 → 잠금 — **[P0 구현]** vanilla-extract 새 모드 대신 `shimLock` 과 같은 자리에 두었다). 검사: CI `vanilla-gates` 의 `:furnace-compat-paper:craftLockCheck`. 빌드·시험은 잠금만 읽는다(서명표 없이 돈다). 잠금에는 Craft 계층이 기대는 Paper 내부 형식 15개(`CraftRangedEntity`·`PaperShearable`·`PaperSchoolableFish`·`PaperLootable*` 등, 구실 `paper-internal`)도 같은 줄로 넣었다 — 생성기가 Paper 와 같은 사슬을 세우려면 필요하다. 1.21.1: 970줄(impl 711·static 52·internal 192·paper-internal 15), 지문 칸 14. **[PR578 검토 보강]** 탐침 관측표(`src/test/resources/craft-identity-paper-1.21.1.tsv`)에 나온 CraftBukkit 밖 Paper 내부 구현 8개(`TerminalConsoleCommandSender`·Folia 스케줄러 3·`WritableCraftRegistry`·`PaperDatapackManager`·`PaperMobGoals`·`PaperPotionBrewer`, 구실 `paper-impl`)도 잠금에 넣는다 — P2 가 명령 발신자·스케줄러를 옮길 기준. `craftLock` 은 관측표를 다섯째 인자로 읽는다. 1.21.1: 978줄.
  - **[검토 1] Mojang 이름이 새지 않게.** 서명표로 세면 Craft 클래스 중 상위가 Mojang 이름(`net/minecraft`·`com/mojang`)인 것 4개(생성기 2, 인벤토리 1, `CraftServer` 안쪽 1), 직접 인터페이스에 Mojang 이름이 든 것 9개. 잠금에는 그 칸을 이름 대신 **지문**(`nms:` + 이름 sha256 앞 16자리)으로 적는다 — 생성기는 지문 칸의 클래스를 만들지 않고(NMS 상위가 있어야 하므로 껍데기 jar 몫으로 남는다) 손 클래스면 `CraftHierarchyTest` 가 그 칸만 "지문 일치"로 본다(컴파일된 상위 이름의 sha256 비교).
  - 검사 두 겹: (가) CI `light`(표 없이): 잠금·관측표(4.2)·기준선 파일에 `net/minecraft`·`net.minecraft`·`com/mojang`·`com.mojang` 글자가 있으면 실패(`CraftLockNoMojangTest`). (나) CI `vanilla-gates`(표 있음): 위 파일의 모든 이름 토큰을 바닐라 서명표의 Mojang 클래스 이름(전체 이름과 끝 이름 둘 다)과 맞대 겹치면 실패 — 패키지를 빼고 끝 이름만 적힌 경우(`Cow`)도 잡는다. 단, 같은 끝 이름이 Bukkit API 에도 있으면(`org.bukkit.entity.Cow`) Bukkit 쪽 전체 이름으로 적힌 것은 통과(목록은 시험이 Paper API 표에서 만든다). **[PR578 검토 보강]** P0a 는 (가)만 구현했다(`CraftHierarchyTest#noMojangNamesInCraftFiles`). 잠금 이름은 모두 패키지 붙은 전체 이름이라 (나)의 실익이 작아, (나)는 관측표를 쓰는 `CraftIdentityProbeTest`(P0b) 때 함께 본다.
  - **[PR578 검토 보강] Paper 에 없는 클래스 검사**: `CraftHierarchyTest` 는 컴파일된 `org/bukkit/craftbukkit/**` 중 잠금에 없는 클래스를 `extra <클래스>` 줄로 센다(익명 `$숫자` 는 접는다). 11-23 기준 4줄(`CraftServer$OnlineView`·`$OnlineView$Wrapped`·`$WorldsView`, `CraftItemStack$Mirror`)이 기준선에 있고 줄어들기만 한다.
- 생성: `tools/compat/gen-bukkit-entity-classes.py`(T-141 D)를 `gen-craft-classes.py` 로 넓혀 **잠금의 계층대로** 손 클래스가 없는 Craft 클래스를 만든다. 지금 생성기는 "가장 가까운 기존 칸" 을 상속시키는데, 바꾼 뒤에는 Paper 상위를 그대로 상속한다. 중간 계층(엔티티: `CraftMonster`·`CraftAnimals`…, Paper 내부 `PaperSchoolableFish`)도 만든다. 생성 클래스의 생성자 = 가장 가까운 손 상위의 Furnace 생성자와 같은 인자(지금 BASES 표가 하는 일을 손 클래스의 표지 `@CraftCtor` 로 옮김).
  - 손 클래스(머리말 없는 파일)의 `extends` 는 생성기가 바꾸지 않는다 — 시험 `CraftHierarchyTest`(서명표 없이, 잠금 vs 컴파일된 클래스)가 다르면 실패하고 사람이 고친다. 이 시험이 기준선 없이 0 이 되는 것이 P1·P2 의 끝.
- **[검토 2] 상위를 바꿔도 동작이 조용히 사라지지 않게.** 상위를 옮기면 옛 상위가 주던 몸통이 사슬에서 빠질 수 있고, 그 자리를 ShellMerge 가 "던지는 껍데기 멤버"로 채우면 컴파일·시험은 초록인데 기능만 없어진다. 그래서:
  - 시험 `CraftLiveMethodsTest`(`fullShellsTest` 와 같은 로더, CI `vanilla-gates`): Craft 클래스마다 그 클래스의 API 인터페이스 메서드(이름+서술자)를 JVM 규칙대로 해석해 **진짜 몸통**(추상 아님·`@NmsShellMember` 없음)으로 가는 것의 집합을 만들고, 커밋된 `craft-live-api-methods-1.21.1.txt` 와 비교한다. **[P0 구현]** 상속 메서드까지 줄로 적으면 십만 줄을 넘어(엔티티 클래스마다 `Entity` 메서드 전부) 클래스마다 **수 + 정렬한 집합의 sha256 16자리** 한 줄로 적는다. 수가 줄거나, 같은 수인데 지문이 바뀌면(다른 메서드가 빠지고 새 것이 들어옴) 실패, 늘면 표를 고치라며 실패 — 늘기만 한다. 처음 값(1.21.1, 11-23): 705클래스, 진짜 몸통 합 14,088.
  - 처음 목록은 P0 에서 지금 코드로 만든다. P1·P2 의 끝 기준에 "이 시험 통과(줄어든 줄 0)"를 넣는다(8장).
  - 같은 이유로 생성기는 손 클래스에서 옮기는 메서드가 없어도 되게 하고, 손 클래스의 공용 몸통이 옛 상위(`CompatBlock`·`CompatBlockState`)에 있으면 **몸통을 새 자리로 옮긴 뒤** 옛 상위를 지운다(지우기 전에 위 시험이 통과해야 한다).
- 엔티티 102→0: 손 클래스의 `extends` 만 바꾸고(예: `CraftBlaze extends CraftMonster`), 중간 계층은 생성. 중간 계층 Bukkit 인터페이스(`Monster`·`AbstractHorse`…)는 이미 생성된 표지 인터페이스로 Furnace 에 있다.
- 블록: `CraftBlock` 은 `CompatBlock` 의 몸을 받아 `Object` 를 상위로(`CompatBlock` 은 지운다). `CraftBlockState` 는 Paper 처럼 **구체** 클래스이고 블록 엔티티 없는 블록의 상태는 `CraftBlockState` 그 자체를 준다(지금은 `CompatBlockState` 하위를 준다 — `getClass()` 가 다르다). `CraftBlockEntityState extends CraftBlockState`, `CapturedBlockState extends CraftBlockState`.
- 월드: `CraftWorld extends CraftRegionAccessor`(Paper 의 공통 상위, `RegionAccessor` 구현). `BukkitChunkGenerators$Region`(LimitedRegion)도 Paper 처럼 `CraftLimitedRegion extends CraftRegionAccessor` 로 — 둘이 같은 블록 읽기·쓰기 몸을 나눠 갖는 지금 구조와 맞는다. **[검토 권고]** 쓰기 몸통을 공통 상위로 모을 때 지금 각 몸통에 있는 소유 스레드 검사(월드는 region 소유 스레드, 생성 중 영역은 생성 작업 스레드)를 하위의 훅(`checkWrite(x, z)` 같은 추상 메서드 하나)으로 남겨 검사가 빠지지 않게 한다 — `Owned` 가드 시험이 그대로 통과해야 한다.

### 3.3 직접 인터페이스 보충

같은 이름·같은 상위인데 Paper 가 직접 다는 API 인터페이스가 빠진 곳 7개(`CraftBlock`→`Block` 은 상위 정리로 해결, `CraftThrownPotion`→`SplashPotion`·`LingeringPotion`, `CraftMapView`→`MapView` 등). 이 중 API 형식이 Furnace 에 없는 것은 5장으로.

### 3.4 핸들 다리 — 기계 생성 (ShellMerge 단계)

- 대상: Paper Craft 클래스의 인스턴스 메서드 중 **"뿌리 접근자 + 형 좁힘"** 꼴인 것. 1차 목록(서명표에서 이름·서술자로 판정):
  - `getHandle()` — 반환형이 상위의 `getHandle()` 반환형의 하위인 것(210개).
  - 블록 엔티티 상태의 `getTileEntity()`/`getSnapshot()`, 인벤토리의 `getInventory()`, `CraftBlockData#getState()` 처럼 같은 클래스 사슬 안에서 반환형만 좁히는 접근자(구현 때 서명표에서 자동으로 찾고, 목록을 보고서에 낸다).
- 몸통: `aload_0; invokevirtual <사슬에서 가장 가까운 손 클래스의 같은 이름 접근자>; checkcast <Paper 반환형>; areturn`. 표지 `@NmsShellMember` 를 달지 **않는다**(진짜 동작) — 대신 `@CraftBridge` 표지(CLASS 보존)로 보고서·시험이 센다.
- 왜 껍데기 jar 단계인가: 좁힌 반환형 30개는 껍데기 jar 에만 있어 자바 소스로는 못 쓴다. ShellMerge 는 이미 손 클래스 바이트에 멤버를 덧붙이는 단계라 같은 곳에서 "던지는 몸통" 대신 "다리 몸통"을 내면 된다. 서명표 없는 빌드(껍데기 없음)는 지금처럼 다리도 없다 — 배포 빌드는 `-PrequireFullShells`.
- 안전 조건(시험 `CraftBridgeTest`, `fullShellsTest` 와 같은 로더): 엔티티 종류마다 `NmsEntityKinds` 가 만드는 NMS shim 클래스가 그 종류 Craft 클래스의 `getHandle()` 반환형에 대입 가능해야 한다(T-232 종류 표 E 129개). 어긋나면 `ClassCastException` 대신 생성 단계에서 실패시키고 그 자리는 다리를 만들지 않는다(껍데기로 둔다).
- **되돌아가기(NMS → Bukkit)**: 다리 짝으로 NMS shim 이 자기 Bukkit 객체를 돌려주는 길(`Entity#getBukkitEntity`·`Level#getWorld`·`ItemStack` 거울)이 이미 있다. Paper 정적 공장(`CraftEntity.getEntity(CraftServer, Entity)` 류)과 3.1 의 Paper 생성자는 "NMS shim → Furnace 핸들" 한 메서드(`NmsHandles.furnaceHandle(Object)`, 호환 계층 내부)를 거쳐 만든다.

### 3.5 정적 변환 도구 (52 + 일부 내부)

`CraftMagicNumbers`·`CraftNamespacedKey`·`CraftLocation`·`CraftVector`·`CraftBlockType`·`CraftRegistry.bukkitToMinecraft/minecraftToBukkit` 류(이름 꼴로 세면 61클래스 176메서드). 바닐라에 짝이 없어 T-235 차분 정답기로는 못 맞춘다. 규칙:
- **가족 규칙 두 개**: (W) NMS→Bukkit = 3.4 되돌아가기, (U) Bukkit→NMS = `((CraftX) b).getHandle()` 또는 레지스트리 값이면 같은 키의 NMS 상수(T-230 상수 생성). **[11-23 T-263 맞춤으로 바뀜]** 이 둘에 맞는 메서드도 3.4 다리처럼 **ShellMerge 단계에서 서명 규칙으로 빌드 때 몸통을 낸다 — 커밋하는 명세 줄은 두지 않는다.** 서명 규칙만으로 못 정해 꼭 커밋해야 하는 줄이 생기면 T-263 연결 파일 `src/shimspec/links-<판>/10-craftbukkit.links` 에 그 파일과 같은 줄 문법(`via=T264-W|U`, `proof=roundtrip`)으로 적는다 — 형식은 한 곳.
- 나머지(값 변환: 좌표·색·문자)는 영역 10 스레드가 손으로, 시험은 **왕복**(`toNMS(fromNMS(x)) == x`)과 레지스트리 이름 같음으로 본다. Paper 를 정답기로 쓰는 차분 시험은 이 컨테이너에서 Paper 실행이 막혀 있어(4.3) 장비 몫으로 둔다.

## 4. 판정 — Paper 관측표와 탐침

### 4.1 탐침 플러그인 **[검토 6 반영]**

`tools/compat/craft-probe/`(Paper API 로만 컴파일되는 보통 플러그인, 초안은 공유 폴더 `furnace/designs/t264-craft-probe/` — paper-api 1.21.1 로 컴파일 확인). `열쇠 \t getClass().getName()` 을 쓴다(값·Mojang 이름 없음).
- **열쇠는 API 형식으로만 만든다**: 서버·월드의 getter 는 구현 클래스가 아니라 `org.bukkit.Server`·`World` **인터페이스가 선언한** 인자 없는 `get*` 를 돈다 — 두 서버의 열쇠 집합이 같아진다(구현 클래스 getter 를 돌면 Paper 전용 public 메서드 때문에 열쇠가 갈린다).
- **[재검토]** 결과 맵은 스레드 안전(`ConcurrentSkipListMap` — 비동기 작업·생성 스레드도 쓴다). 탐침 월드는 3x3 청크를 강제 생성해 콜백이 반드시 불리고, 표는 비동기 작업과 생성 콜백이 끝난 뒤(최대 약 10초, 넘으면 그 줄을 `!timeout`) 쓴다. 받은 객체가 Mojang 클래스면 이름 대신 지문(`nms:` + sha256 16자리).
- **항목마다 따로 잡는다**: 모든 호출이 `run(열쇠, …)`/`section(…)` 안이라 Furnace 에 메서드 하나가 없어도 그 줄만 `!예외클래스` 로 남고 표 전체는 나온다.
- **1부(자동, 첫 틱)**: 서버·월드 API getter 한 겹, 청크·청크 스냅샷·블록·블록 상태·월드 PDC·콘솔 / 블록 종류마다 `createBlockData`·`createBlockState`·그 인벤토리 / 아이템 종류마다 `getItemMeta`·메타 PDC / 엔티티 종류마다 `createEntity`·PDC·장비·속성·인벤토리 / `InventoryType` 마다 `createInventory`·그 칸의 스택, 크기·제목 변형, 상인 / `Registry` 정적 필드마다 레지스트리·첫 값 / 조리법·발전과제·도움말 주제(서로 다른 클래스 집합) / 스코어보드·목표·점수·팀·기준 / 보스바(일반·키) / 지도 / 태그 4종 / **스케줄러**(`runTask`·지연·반복·비동기·작업자·`callSyncMethod`·대기 작업) / **생성기**(`createChunkData`, 탐침 생성기를 단 월드를 만들어 콜백이 받는 `WorldInfo`·`ChunkData`·`LimitedRegion`·`BiomeParameterPoint`).
- **2부(봇)**: 이벤트로 넘어오는 것 — 접속한 플레이어·그 인벤토리·엔더 상자·열린 화면, `InventoryOpenEvent`·`InventoryClickEvent`·`PrepareItemCraftEvent`·`PrepareAnvilEvent` 의 인벤토리·화면·눌린 칸·조리법, 명령 발신자. mineflayer 봇(`tools/bot-test`)이 상자·작업대·모루·화로·마법부여대를 열고 칸을 누른 뒤 콘솔 `craftprobe dump` 로 다시 쓴다.
- 반복자 묶음(`recipe.`·`advancement.`·`help.topic.`·`scheduler.pending.`)은 열쇠에 클래스 이름이 들어가므로 비교는 **집합 비교**(Paper 에 있고 Furnace 에 없는 클래스, 그 반대)다.
- **상태를 바꾸는 것**: `createMap`(지도 번호 파일)·탐침 생성기 월드(월드 폴더)·스코어보드 팀(새 스코어보드라 저장 안 됨). **버리는 서버에서만 돌린다.**
- 아직 못 잡는 것: 명령 블록 발신자(`CraftBlockCommandSender`)·대리 발신자, 엔더 드래곤 조각, 정해진 이벤트 밖의 화면(상인·베컨 등은 봇 시나리오를 늘리면 잡힌다). 이 줄들은 관측표에 없으므로 P2 에서 그 클래스는 서명(잠금)으로만 맞추고 "관측 없음"으로 적는다.

### 4.2 표와 시험

- Paper 쪽 결과 `furnace-compat-paper/src/test/resources/craft-identity-paper-1.21.1.tsv` 를 커밋한다(Bukkit 열거형 이름·CraftBukkit 클래스 이름뿐 — 기존 `bukkit-entity-hierarchy-1.21.1.txt` 와 같은 성격).
- Furnace 쪽 `CraftIdentityProbeTest`: 시험 서버(기존 `NmsPluginLoadTest` 처럼 시험 안에서 탐침 jar 를 컴파일·적재)에서 같은 탐침을 돌려 표를 비교. 다른 줄은 기준선 `craft-identity-baseline.txt`(줄마다 이유)에 있어야 통과하고, **기준선은 줄어들기만** 한다(같아진 줄이 기준선에 남아 있으면 "지우라"며 실패 — `AdapterBoundaryStaticTest` 와 같은 방식).
- 처음 기준선은 지금 다른 줄 전부. P1~P4 가 줄인다.

### 4.3 Paper 실행 장소

이 클라우드 컨테이너에서 Paper 서버 jar 를 받아 실행하는 것은 자동 권한 검사가 막았다(11-23 시도). 서명표는 여기서 되지만 Paper **실행**은 장비(서버 러너 `/work/Furnace` 또는 노트북)에서 한다. 탐침 결과 파일만 저장소로 가져온다. 표가 없는 동안 확인 못 한 선택은 "가정"으로 적는다(예: `createInventory(null, CHEST)` → `CraftInventoryCustom` 은 가정).

### 4.3a 장비에서 Paper 탐침 돌리는 법 **[11-23 추가]**

탐침 초안: 공유 폴더 `furnace/designs/t264-craft-probe/`(`plugin.yml`, `src/org/domi/furnace/probe/CraftProbe.java` — Paper API 만 씀). 승인 뒤 P0 에서 `tools/compat/craft-probe/` 로 옮긴다.

- 입력: Paper 1.21.1 서버 jar(빌드 133, `fill.papermc.io` v3 의 STABLE 최신, sha256 `39bd8c00…287b9`), 같은 판 `paper-api` jar(서명표 캐시 `~/.cache/furnace-vanilla-extract/1.21.1/paper-api-*.jar` 또는 Paper 첫 실행이 푼 `libraries/`), JDK 21.
- 순서(빈 작업 폴더에서):
  1. `java -jar paper-1.21.1-133.jar --nogui` 를 한 번 돌려 `eula.txt` 를 만들고 멈춘다 → `eula=true`, `server.properties` 에 `online-mode=false`, `level-type=minecraft:flat`(생성 시간 줄이기 — 클래스 이름은 월드 종류와 무관), `spawn-protection=0`.
  2. 탐침 컴파일: `javac -proc:none --release 21 -cp "versions/1.21.1/paper-1.21.1.jar:libraries/**(모든 jar)" -d out src/org/domi/furnace/probe/CraftProbe.java` → `jar cf plugins/CraftProbe.jar -C out . plugin.yml`.
  3. 1부만: `java -Dcraftprobe.shutdown=true -Xmx2G -jar paper-1.21.1-133.jar --nogui` — 탐침이 첫 틱 뒤 표를 쓰고 서버를 끈다.
  4. 2부(봇): `-Dcraftprobe.shutdown` 없이 켜고 mineflayer 봇 시나리오(상자·작업대·모루·화로·마법부여대 열기와 칸 누르기)를 돌린 뒤 콘솔 `craftprobe dump`, 그다음 `stop`. 봇 시나리오는 P0 에서 `tools/bot-test/` 에 더한다.
- 산출물: `plugins/CraftProbe/craft-identity.tsv`(정렬된 `열쇠 \t 클래스 이름`, 실패는 `!예외 클래스`). 이 파일만 공유 폴더 `furnace/t264/craft-identity-paper-1.21.1.tsv` 로 올리고 작업 폴더는 지운다(장비 용량 규칙). 서버 jar·라이브러리·월드는 올리지 않는다.
- 같은 jar 를 Furnace 시험 서버에도 올려 `craft-identity-furnace.tsv` 를 내면 지금 차이가 바로 보인다(P0 기준선의 처음 값).

### 4.4 예외

- 익명 클래스(`CraftEntity$1`·`CraftParticle$CraftParticleRegistry$3` 등 25개): 번호는 컴파일러가 정하는 것이라 이름을 맞추지 않는다. 상위·인터페이스만 맞춘다. 관측표에서 `$숫자` 로 끝나는 줄은 비교에서 "익명"으로 접는다.
- `CraftParticle`: Paper 1.21.1 `Particle` 은 열거형이고 `CraftParticle` 은 변환 도구 구실 — 정적 도구로 다룬다.

## 5. API 형식이 없는 386개 — 들어오는 규칙

- 블록 데이터 종류 246(`block/impl/**` 173, `block/data/type/**` 54, `block/data/**` 19): Paper 는 블록마다 `impl/CraftStairs extends CraftBlockData implements Stairs, …` 를 준다. Furnace 는 `CompatBlockData` 하나라 `instanceof Stairs` 가 거짓이고, `org.bukkit.block.data.type.*` 인터페이스가 Furnace 에 없다. **규칙**: 영역 11(API 빈칸)이 `type` 인터페이스를 들여올 때, 블록→Craft 클래스는 관측표 `blockdata.<Material>` 줄대로, 클래스는 잠금(3.2)대로 생성하고, 속성 읽기·쓰기는 이미 있는 블록 상태 속성 접근 위에 생성기가 만든다(바닐라 블록 속성 표가 Furnace 에 있다 — 바닐라 몸통이 아니라 속성 이름·값 목록에서). 이 작업은 이 문서 밖(영역 1·10·11)이고, T-264 는 이름·계층·판정만 정한다.
- 그 밖 140(메타 20여·스코어보드·발전과제·밴 목록·데이터팩·구조물 등): API 형식을 들여오는 스레드가 같은 커밋에서 Craft 클래스를 잠금대로 만들고 관측표 줄을 기준선에서 지운다.

## 6. 의존·충돌

| 무엇 | 왜 | 누구 |
|---|---|---|
| Furnace `Enchantment`·`GameEvent` 가 `final class`(Paper 는 `abstract class`) | `CraftEnchantment extends Enchantment` 를 못 만든다 | T-240(API 서명 관문) — 편차 목록에 이미 있음. 고칠 때 이 설계의 2단계로 |
| 블록 데이터 `type` 인터페이스 없음 | 5장 | 영역 11 |
| 엔티티 사슬 스레드(T-232 남은 것) | `CraftEntity` 파일·`NmsEntityKinds` 를 함께 만진다 | P1 시작 전에 그 스레드 열린 PR 을 확인하고, 겹치면 순서를 코디네이터와 정한다 |
| 컨테이너 채우기(PR 560 계열) | `CraftInventory#getInventory()` 다리의 반환형이 NMS `Container` shim | 다리는 있는 shim 만 잇고 없는 것은 껍데기로 둔다 |
| 어댑터 경계 | 새 코드는 전부 `furnace-compat-paper` 안. 서버 `api.internal` 허용 목록을 늘리지 않는다 | `AdapterBoundaryStaticTest` 그대로 |

## 7. T-263(연결 추론기)과의 경계 — **[11-23 T-263 동의, 맞춤 반영]**

Craft 클래스 멤버를 T-264 가 다음 네 칸으로 나눠 **기계가 읽는 표**로 낸다. T-263 은 (a)~(c) 를 후보에서 빼고 (d) 만 다룬다.

| 칸 | 내용 | 채우는 쪽 |
|---|---|---|
| (a) API 상속 | API 인터페이스 메서드와 이름·서술자가 같음 → 정체성(3.1·3.2)이 맞으면 상속으로 저절로 동작 | T-264(정렬만) |
| (b) 핸들 다리 | 3.4 꼴 | T-264 생성기, ShellMerge 단계 빌드 때 몸통(커밋 줄 없음) |
| (c) 변환 가족 W/U | 3.5 | T-264 생성기, ShellMerge 단계 빌드 때 몸통(커밋 줄 없음 — 꼭 필요하면 `links-<판>/10-craftbukkit.links`, `via=T264-W|U`, `proof=roundtrip`) |
| (d) 나머지 | Paper 내부 동작(`CraftPlayer#getProfile` 등) | T-263 이 후보만 — 바닐라 짝이 없어 T-235 로 증명할 수 없으므로 대부분 **"증명 없음" 후보로 영역 10 에 넘긴다**. 영역 10 스레드가 손 몸통 + 시험(왕복·관측표·Paper 탐침 확장)으로 채운다 |

- 표: 껍데기 빌드 산출물 `META-INF/furnace/full-shells/craft-roles.tsv`(이름 들어감 — 커밋 안 함, 껍데기 jar 안), 열은 `클래스 \t 멤버 \t 서술자 \t a|b|c|d`. T-263 이 이 형식 그대로 읽는다(11-23 동의).
- 그래서 영역 10 의 사람 몫은 (d) 칸이다. 크기는 P3 뒤 `craft-roles.tsv` 의 (d) 수로 다시 잰다(지금 추정 안 함).

## 8. 단계·크기·검토

| 단계 | 내용 | 끝 기준 | 크기 | 검토 |
|---|---|---|---|---|
| P0 | 잠금 추출 모드·`craftLockCheck`·`CraftHierarchyTest`(기준선 지금 값)·`CraftLockNoMojangTest`·`CraftLiveMethodsTest`(처음 목록) + 탐침 플러그인 + `CraftIdentityProbeTest` 틀(Paper 표는 장비에서 뽑아 넣음) | CI 초록, 기준선 = 지금 상태 | M | 도구 변경 → 독립 검토 |
| P1 | 엔티티 계층 정렬(손 95 `extends` 바꿈 + 중간 계층 30 생성, `gen-craft-classes.py`) | 엔티티 `super` 0, 관측표 엔티티 줄 같음, **`CraftLiveMethodsTest` 줄어든 줄 0** | M | 기계적 + 손 `extends` 한 줄씩 → 독립 검토(작음) |
| P2 | 이름 옮기기 89자리(인벤토리·메타·스케줄러·명령 발신자·태그·생성기·PDC·도움말) + 블록·월드 상위 정리(3.2) + 금 파일 bless 1건 | `furnace-other-name` 0, `other-super` 0, **`CraftLiveMethodsTest` 줄어든 줄 0** | L | 이름만 바뀐 diff + 시험 무변경 확인 + 금 파일 → 독립 검토 |
| P3 | 핸들 다리(ShellMerge) + `CraftBridgeTest` + 역할 표(7장) | 다리 210 중 대입 가능한 것 전부, 던지는 `getHandle` 0 | M | 생성기 변경 → 독립 검토 |
| P4 | 변환 가족 W/U(ShellMerge 빌드 때 몸통 — **[11-23 바뀜]** 커밋 줄 없음) + Paper 생성자 다리(덫 계측에 나온 것) | 해당 덫 0 | M | 생성기 변경 → 독립 검토 |

- 모두 `furnace-compat-paper`(+ `tools/`) 안이고 서버 핵심은 안 건드린다. 성능: 이름·상속만 바뀌므로 핫 경로 비용 0, 다리는 `checkcast` 하나.
- **[검토 5] 금 파일 하나가 바뀐다.** `furnace-compat-paper/src/test/resources/identity/1.21.1/scene-compat-block-reads.txt` 의 `state-classes` 줄에 `CompatBlockState` 가 있다(블록 엔티티 없는 블록의 상태 클래스). P2 에서 이것이 `CraftBlockState` 로 바뀐다 — Paper 와 같아지는 **정확성 수정**이다. 절차는 `docs/testing/identitykit.md` "금 파일 갱신": `-Dfurnace.identity.bless=scene-compat-block-reads` 로 그 이름 하나만 갱신, PR 본문에 "금 파일 갱신" 절(바뀐 파일·줄 수·이유·근거 = 관측표의 `blockstate.<Material>` 줄)을 쓴다. 그 PR 은 금 파일 변경이라 독립 검토를 유지한다. 다른 장면 금 파일에 Compat 클래스 이름이 더 있는지는 P2 시작 때 `grep -rn Compat src/test/resources/identity` 로 다시 본다(11-23 기준 이 한 줄뿐).
- **[P1 구현]** 생성기는 `tools/compat/gen-bukkit-entity-classes.py` 를 잠금으로 돌게 고쳤다(새 `gen-craft-classes.py` 는 만들지 않음): 종류 표의 Craft 클래스와 손 클래스에 닿을 때까지의 상위 사슬(중간 계층 26 + Paper 내부 `PaperSchoolableFish` + Craft 인터페이스 3 `CraftEnemy`·`CraftMerchant`·`PaperSharedSpawnerLogic`)을 잠금의 상위·접근대로 만든다. 손으로 고친 것은 `CraftProjectile` 하나 — Paper 처럼 `AbstractProjectile`(추상) 아래로 두고 몸통(쏜 쪽 읽기 등)을 `AbstractProjectile` 로 올렸다(화살·화염구·라마 침·셜커 탄환도 같은 몸통). 결과: 엔티티 `super` 줄 0(기준선 103줄 지움).
  - 인터페이스: Furnace 에 있는 형만 붙이고, 그 형(과 상위)에 몸통 없는 메서드가 있으면 붙이지 않는다(엔티티 API 는 손 사슬이 채움 — 컴파일이 확인). 그래서 새로 생긴 중간 계층에서 처음 보이는 인터페이스 차이 5줄(API 형이 Furnace 에 없음: `RangedEntity`·`Bucketable`·`LootableEntityInventory`·`Merchant`, 몸통 없음: 주민 `InventoryHolder`)은 기준선에 같은 커밋으로 들어간다 — 옛 상위 줄이 지워진 자리라 기준선은 순으로 98줄 준다. 이 5줄은 5장 규칙대로 API 형을 들여오는 스레드가 지운다.
  - 패키지 전용: Paper 의 `CraftMinecartMobSpawner` 는 패키지 전용 `final` 이라 그대로 만들고, `CompatEntityKinds` 는 `CraftAccess`(3.1 [검토 3]) 로만 만든다. `CraftAccessStaticTest` 가 craftbukkit 밖에서 패키지 전용 Craft 클래스 이름을 드는 곳이 `CraftAccess` 뿐인지 본다.
- **[P2a 구현]** 명령 발신자·도움말 지도·인벤토리를 Paper 이름으로 옮겼다: `CompatConsole`→`CraftConsoleCommandSender`(Paper 처럼 실제 콘솔은 그 하위 `com.destroystokyo.paper.console.TerminalConsoleCommandSender`), `CompatRemoteSender`→`CraftRemoteConsoleCommandSender`, 새 추상 `ServerCommandSender`(플러그인 발신자 `CompatSender` 도 그 아래 — 명령 블록 발신자 `CraftBlockCommandSender` 는 P2b), `CompatHelpMap`→`help/SimpleHelpMap`(필드도 Paper 와 같은 `helpTopics` — commandapi 가 반사로 읽는다), `CompatInventory`·`CompatMenuInventory`·`CompatPlayerInventory`·`CompatDoubleChestInventory`→`CraftInventory`·`CraftInventoryCustom`·`CraftInventoryPlayer`·`CraftInventoryDoubleChest`, `BlockInventories` 안의 양조기·화로 인벤토리→`CraftInventoryBrewer`·`CraftInventoryFurnace`. 옛 중첩 클래스(거울·에포크·메뉴 칸·겹친 상자)는 Paper 에 없는 craftbukkit 중첩 클래스가 되지 않도록 `org.domi.furnace.compat.paper.InventoryParts` 로 뺐다(`extra` 줄 0 유지). 금 파일 `scene-compat-asm-prefilter` 3줄 갱신(정확성 수정 — `SimpleHelpMap` 이 Paper 처럼 생겨 commandapi 의 참조에 함정이 더는 안 들어간다). 화면(`CompatInventoryView`)·이벤트 인벤토리는 P2b.
- **[P2b-1 구현]** 창과 이벤트 인벤토리를 옮겼다: `CompatInventoryView`→`CraftInventoryView`(칸 규칙은 새 추상 상위 `CraftAbstractInventoryView` 로 — Paper 와 같은 자리), 모루·마법 부여대 창→`view/CraftAnvilView`·`view/CraftEnchantmentView`. 이벤트 사본의 위쪽 칸은 `SnapshotInventory` 하위 클래스 대신 Paper 클래스 그대로 만든다 — 제작 `CraftInventoryCrafting`, 모루 `CraftInventoryAnvil`(새 상위 `CraftResultInventory`), 마법 부여 `CraftInventoryEnchanting`, 양조·화로는 P2a 의 `CraftInventoryBrewer`·`CraftInventoryFurnace`, 그 밖은 `CraftInventory`. `SnapshotInventory` 는 사본 칸(Furnace Inventory)만 만드는 도구가 되었다. **[PR583 검토 보강] 변환기 규칙:** 상위 CraftBukkit 클래스는 호환 계층에 있는데 플러그인 생성자가 부르는 그 상위 생성자(Paper 서술자)가 없으면(T-261 껍데기 멤버도 "없음") `NmsLinker` 가 클래스가 없을 때처럼 껍데기로 만든다 — 상위는 이제 Paper 와 같은 Craft 클래스, 생성자는 같은 덫(`NmsTrap.husk`)·계측, 문구 `nms.no-craftbukkit-constructor`. P2 로 Craft 클래스가 생길 때마다 그 하위 플러그인 클래스가 `NoSuchMethodError`·껍데기 생성자 던짐으로 실패 방식이 바뀌지 않게 하는 일반 규칙이다(시험 `NmsLinkerTest#craftParentPresentButCalledConstructorMissingIsAHusk`). 금 파일 `scene-compat-asm-prefilter` 3줄 갱신: InventoryFramework 의 모루·마법 부여대 창 하위 클래스(`AnvilInventoryImpl$1`·`EnchantingTableInventoryImpl$2`)는 상위 사슬이 Paper 와 같아졌으나(상위 `CraftInventoryAnvil`·`CraftInventoryEnchanting`) Paper 생성자가 아직 없어 껍데기 그대로다 — 바뀐 클래스 수는 759 그대로, 해시만 바뀐다(껍데기의 상위와 문구). 레시피(`EventInventories.CompatRecipe` → Paper `CraftShapedRecipe` 등)는 모양을 Furnace 가 알리지 않아 남긴다(레시피 영역).
- **[P2b-2 구현]** 스케줄러와 태그를 옮겼다: `CompatScheduler`→`scheduler/CraftScheduler`(서버마다 하나, 상태는 스케줄러 core), 작업 손잡이는 Paper 처럼 주 스레드 `CraftTask`·비동기 `CraftAsyncTask`(패키지 전용)·`callSyncMethod` 의 `CraftFuture`(패키지 전용, core future 에 맡김), `CraftTask#run` 은 Paper 처럼 몸통을 곧바로 돌린다(`LegacyTask#runBody`). 일꾼은 이름 없는 `BukkitWorker`. 태그는 `CompatTags$Impl`→`tag/CraftTag`(추상) 아래 `CraftBlockTag`·`CraftItemTag`·`CraftFluidTag`·`CraftEntityTag`, 게임 이벤트는 Paper 자리 `io.papermc.paper.CraftGameEventTag`. 시험 `CraftSchedulerTagIdentityTest` 가 관측표(`scheduler.*`·`tag.*`)와 실제 클래스를 견준다. 대기 목록(`getPendingTasks`)도 Paper 처럼 `callSyncMethod` 작업은 `CraftFuture`(`LegacyTask#syncCall`). 손잡이는 Paper 처럼 작업 하나에 객체 하나(`LegacyTask#view`) — 예약 때 받은 객체와 대기 목록의 객체가 같다.
  - [PR584 검토 보강] `callSyncMethod` 의 future 는 작업을 만들 때(대기열·작업 표에 넣기 전) 붙인다 — 넣은 뒤 붙이면 그 틈에 `getPendingTasks` 가 보통 `CraftTask` 손잡이로 굳혀 플러그인의 `Future` 형 변환이 깨진다. 동시 조회 시험을 더했다.
- **[P2b-3 구현]** 아이템 메타를 옮겼다: `CompatItemMeta`→`inventory/CraftMetaItem`(패키지 전용), `CraftMetaArmor`·`CraftMetaColorableArmor`·`CraftMetaBanner`(공개, final 아님), `CraftMetaLeatherArmor`·`CraftMetaSkull`(패키지 전용) — Paper 와 같은 접근. 종류별 생성은 `CraftMetaItem.create`(MetaKind 는 종류 판별만), 가죽 색 도우미는 Paper 처럼 `CraftMetaLeatherArmor` 의 정적 도우미, 기본 가죽 색은 `CraftItemFactory.DEFAULT_LEATHER_COLOR`. 꾸러미 밖(Bukkit `ItemStack`)은 공개 `ItemMetaBridge` → `CraftAccess` 핸들(`CraftMetaItem.of`·`putInto`)로만 들어온다. 되읽기는 `ItemMetaSerialization.read`(공통 키) 뒤 `SerializableMeta` 가 메타를 만든다. enum switch 는 switch 표 클래스(`CraftMetaItem$1`)가 Paper 의 같은 이름과 겹쳐 if 로 바꿨다. Paper 의 `CraftMetaItem` 이 구현하는 `BlockDataMeta` 는 API 형식이 아직 없어 계층 기준선 `interfaces` 1줄·서명 기준선 6줄(영역 11 + 4, `docs/roadmap/craft-missing-api-types-t264-2026-11-24.md`).
- **[P2c-1 구현]** 블록과 블록 상태의 상위 사슬을 Paper 와 같게 했다(3.2): 옛 `CompatBlock` 몸통을 `CraftBlock`(상위 Object)에, 옛 `CompatBlockState` 몸통을 `CraftBlockState`(상위 Object)에 합쳤다. `CraftBlockEntityState`·`CapturedBlockState` 는 `CraftBlockState` 를 잇는다. 블록 엔티티 없는 블록의 상태는 Paper 처럼 `CraftBlockState` 그대로이고(전에는 `CompatBlockState`), `getRelative`·이벤트의 "놓일 블록"도 이제 `CraftBlock` 이다(전에는 `CompatBlock`). Paper 생성자 `(World, BlockPos, BlockState)` 로 상속한 플러그인 상태(Axiom)는 안쪽 스냅숏에 맡기지 않고 그 자체가 스냅숏이다. 스냅숏 만들기 입구는 `BlockStateSnapshots`, 블록 엔티티 종류별 형은 생성기 `gen-bukkit-block-state-classes.py` 의 `CompatBlockStates`(공개), 놓일 블록은 `CraftAccess.blockShowing`. `requirePlaced` 는 Paper 처럼 final 이 아니다. 금 파일 `scene-compat-block-reads` 1줄(+합계) 갱신 — [검토 5] 의 정확성 수정(`CompatBlockState` → `CraftBlockState`). 시험 `BukkitBlockStateKindsTest#stateClassesMatchPaperObservation` 이 관측표 `blockstate.*` 와 재료별 상태 클래스를 견준다. 서명 기준선 96줄 줄고 1줄(Furnace 스냅숏 생성자) 더함.
- **[P2c-2 구현]** 월드와 생성 중 영역의 상위 사슬을 Paper 와 같게 했다: 새 추상 `CraftRegionAccessor`(public abstract, `RegionAccessor` 구현, 몸 없음) 아래에 `CraftWorld`(final 을 풀었다 — Paper 는 public)와 `generator/CraftLimitedRegion`(옛 `BukkitChunkGenerators$Region`, 생성자는 패키지 전용이라 `CraftAccess.newLimitedRegion` 으로 만든다)을 둔다. 쓰기 몸통은 공통 상위로 모으지 않았다 — 두 하위가 지금처럼 자기 손잡이로 구현하므로 각 몸통의 소유 스레드 검사(월드는 region 스레드, 영역은 생성 작업 스레드)가 그대로 남는다(검토 권고 충족). `RegionAccessor` 의 추상 멤버를 처음 받게 된 `CraftWorld` 에 `getBlockData`·`getType`·`setBlockData`(같은 칸 `CraftBlock` 과 같은 길 — Paper `CraftRegionAccessor` 도 `CraftBlock.setTypeAndData` 를 물리 적용으로 부른다)와 `getHighestBlockYAt(x, z, HeightMap)`(Paper 처럼 높이 지도의 첫 빈 칸 - 1. `*_WG` 지도는 바닐라가 그 자리에서 만들어 답하므로 술어가 같은 살아 있는 지도로 답한다 — `context-notes/2026-11-23-t264-p2c2.md`. 로드 안 된 청크는 `UnsupportedFeature` — Paper 차이는 `user-todo/entries/2026-11-23-t264-unloaded-chunk-reads.md`)을 넣었다. `World` 와 `RegionAccessor` 둘 다 기본 몸을 주는 네 멤버(`getEntitiesByClass`·`getEntitiesByClasses`·`getBiome(Location)`·`setBiome(Location, Biome)`)는 `World` 쪽 몸을 쓴다. 아직 Bukkit `World` 가 `RegionAccessor` 를 넓히지 않아(T-218) 플러그인은 `RegionAccessor` 형으로 볼 때만 이 멤버에 닿는다 — 그때 `RegionAccessor` 의 생성 중 영역용 기본 몸(엔티티 목록 등 `UnsupportedFeature`)이 월드에서 보일 수 있으니 T-218 에서 `World extends RegionAccessor` 와 함께 정리한다. 기준선: 계층 -2(CraftWorld 상위·접근), 서명 -2, 살아 있는 메서드 CraftRegionAccessor 6→38·CraftWorld 171→203·CraftLimitedRegion 14→63.
- **[P2c-3 구현]** 생성기 다리의 네 클래스를 Paper 이름으로 옮겼다(상위는 모두 Object 로 같았고 이름만 달랐다): `BukkitChunkGenerators$LiveChunkData` → `generator/CraftChunkData`(public final, `breakLink` 공개 — Paper 와 같다), `$OldChunkData` → `generator/OldCraftChunkData`(@Deprecated public final, `Server#createChunkData` 가 돌려준다), `BukkitBiomeProviders$LateWorldInfo` → `generator/CraftWorldInfo`(public), `$Point`(record) → `generator/CraftBiomeParameterPoint`(public, 생성자 private — Paper 와 같다). Paper 생성자는 NMS 손잡이를 받아 같게 만들 수 없으므로 Furnace 생성자·멤버(`OldCraftChunkData#applyTo`, `CraftWorldInfo#bind`·`prepare`)는 패키지 전용으로 두고 `CraftAccess` 로 부른다 — Craft 클래스에 Furnace 전용 공개 멤버를 새로 늘리지 않는다(P3 빚 없음). 호환 계층 밖 꾸러미(`ServerLevel`·`CraftServer`·`Server`)는 `BukkitBiomeProviders.worldInfo`·`bind`, `BukkitChunkGenerators.createOldChunkData` 를 거친다. `CraftWorldInfo` 의 환경 enum switch 는 합성 `CraftWorldInfo$1` 이 Paper 의 익명 BiomeProvider 이름과 부딪혀 if 사슬로 썼다. 기준선 변화 없음(없던 클래스라 계층·서명 기준선에 줄이 없었다), 살아 있는 메서드 0→18·10·7·10. 시험: 관측표 `generator.callback.chunkData`·`generator.chunkData`·`generator.callback.biomeParameterPoint` 와 같은 클래스(생성기 시험 두 곳에 단언), 기본 공급자에 넘기는 WorldInfo 가 `CraftWorldInfo` 인 것은 관측표에 없는 가정. 남김: `CustomChunkGenerator$CustomBiomeGrid` 는 Paper 에서 내부 클래스의 private 중첩이라 플러그인이 이름으로 볼 일이 드물어 옮기지 않았다(`BukkitChunkGenerators$Grid`).
- **[P2c-4 구현]** 영속 데이터 묶음(PDC)을 Paper 와 같은 클래스로: 플러그인이 받는 묶음은 아이템 메타·엔티티·블록 엔티티(스냅숏·살아 있는 것)·중첩 묶음·문맥이 만든 새 묶음 모두 `CraftPersistentDataContainer`(public, 상위 `io.papermc.paper.persistence.PaperPersistentDataContainerView` — 새 paper-internal 추상 칸, 몸 없음)이고 `getAdapterContext()` 는 `CraftPersistentDataAdapterContext`(public final) 다(관측표 `itemmeta.*.pdc`·`entity.*.pdc` 1,460줄 모두 이 클래스). Furnace 는 저장 방식이 둘(메모리 맵 `ItemPdc`, 주인 스레드에서 지금 값을 읽고 쓰는 `LivePdc`)이라 `CraftPersistentDataContainer` 는 그 몸에 그대로 맡기는 얇은 껍데기다 — 몸을 하위 클래스로 두면 `getClass()` 가 Paper 와 달라진다. 껍데기 생성자·`furnaceBody()` 는 패키지 전용(`PdcViews.craft`·`body` → `CraftAccess`). 감싸는 자리: `CraftMetaItem#getPersistentDataContainer`(메타마다 같은 객체 — Paper 와 같다), `CraftEntity`(부를 때마다 새 껍데기, 몸은 전과 같음), `CraftBlockEntityState`, `TileStatePdc.of`, 꺼내는 중첩 값(`TAG_CONTAINER`·배열·목록), 문맥의 `newPersistentDataContainer`. 받는 자리(`set` 의 중첩 값·`copyTo` 대상·`equals`)는 껍데기를 벗겨 전과 같은 빠른 길을 탄다. 사용자 데이터 종류의 `toPrimitive`·`fromPrimitive` 는 이제 그 묶음의 `CraftPersistentDataAdapterContext` 를 받는다(전에는 Furnace 몸이 문맥으로 새어 나갔다 — `PdcBody#useContext`). `EntityPdc` 는 `LivePdc` 를 바로 잇는다. 기준선: 계층 -3(접근·인터페이스·상위), 서명 -3. 남김: `DirtyCraftPersistentDataContainer`(Paper 청크 PDC — Furnace 에 청크 PDC 가 아직 없다), `CraftItemStack$3`·`CraftOfflinePlayer$1`(Paper 의 익명 보기 — 상위는 이제 같고 이름은 익명 번호라 맞추지 않는다), `DeprecatedItemTagType`·`DeprecatedContainerTagType`(옛 태그 API).
- **[P2d-1 구현]** 상태 효과 종류: `PotionEffectType$Vanilla` → `potion/CraftPotionEffectType`(public, 상위 `PotionEffectType` — Paper 와 같다). Paper 생성자는 NMS `MobEffect` 를 받아 Furnace 생성자는 패키지 전용이고 `PotionEffectTypes.vanilla` → `CraftAccess` 로 만든다(상수 초기화 때 — `CraftAccess` 는 클래스를 올리기만 하고 초기화하지 않아 순환이 없다). Paper 의 내부 인터페이스 `Handleable`·`PaperFeatureDependent` 가 없어 계층 `interfaces` 1줄·서명 `type-missing` 3줄을 더했다(`docs/roadmap/craft-missing-api-types-t264-2026-11-24.md`). 금 파일 `scene-compat-asm-prefilter` 갱신: CommandAPI 의 `NMS_1_21_R1`·`NMS_1_20_R*` 등이 `CraftPotionEffectType.minecraftToBukkit` 을 부르는데 이제 그 클래스가 shim 에 있어 변환 결과 바이트가 달라진다(정확성 — 없는 클래스가 아니라 Paper 와 같은 클래스로 링크된다).
- **[P2d-2 구현]** 레지스트리: `Registry` 상수 가운데 Paper 가 `CraftRegistry`·`WritableCraftRegistry` 로 내는 것(관측 표 `registry.*`)을 같은 클래스로 맞췄다. Furnace 는 본체가 여럿(고정 목록·데이터 팩 목록)이라 PDC 처럼 얇은 래퍼(`CraftRegistry` 가 본체로 넘김)를 두고 `CompatRegistries.craft`·`fixedWritable` → `CraftAccess` 로 만든다. `WritableCraftRegistry` 는 다른 패키지라 `CraftRegistry` 생성자가 protected — 서명 `descriptor` 1줄(이유 P3 정리). 살아 있는 메서드 표 `CraftRegistry` 2→6. 시험 `CraftSchedulerTagIdentityTest#registryClassesMatchPaper`(관측 13개 이상 대조). 계층 기준선 무변경.
- 끝난 뒤 남는 것: 5장의 386개(API 형식 들여오기와 함께), (d) 칸(T-263·영역 10).

## 9. 위험

1. **이름 바꾸기가 다른 스레드와 부딪힘** — `Compat*` 파일은 호환 계층 곳곳에서 쓴다. P2 는 묶음을 작게(인벤토리 / 메타 / 그 밖) 나눠 각각 빨리 병합한다.
2. **`getClass()` 로 가르는 Furnace 내부 코드** — 지금 `instanceof CompatBlockState` 같은 검사가 있으면 이름을 옮길 때 같이 바뀐다(컴파일러가 잡는다).
3. **기능 감지가 Paper 와 같아짐** — `instanceof CraftInventoryCustom` 으로 NMS 길을 고르는 플러그인이 이제 그 길로 들어와 `getInventory()` 다리 뒤의 덜 채운 NMS 멤버에서 덫에 걸릴 수 있다. T-260 과 같은 대가이고, 덫 계측이 숫자로 보여 준다.
4. **패키지 전용 클래스의 접근** — 3.1 대로 진입점 한 곳. 리플렉션으로 `setAccessible` 하는 플러그인도 Paper 와 같은 결과(같은 접근 수준)다.
5. **Paper 관측표가 늦게 들어옴** — 그동안 P1·P2 는 잠금(서명)과 기존 등록표(`bukkit-entity-hierarchy-1.21.1.txt`)로 진행하고, 상황별 선택(인벤토리 갈래)은 가정으로 적었다가 표가 들어오면 고친다.
