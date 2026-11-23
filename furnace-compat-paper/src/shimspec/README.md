# 연결 명세(shim spec) 형식 — T-229·T-232 (2026-11-20)

NMS shim(`net.minecraft.*` Mojang 이름 클래스)을 손으로 다 쓰지 않고, **겉모양은 기계가, 연결은 사람이 한 줄로** 적는다.
설계 근거: `docs/roadmap/broad-support-version-scaling-2026-11-20.md` 4.3·4.6, 이번 구현 기록: `docs/feature-parity/compat-shim-generator-2026-11-20.md`.

## 파일 셋

| 파일 | 누가 쓰나 | 커밋 | 내용 |
|---|---|---|---|
| `src/shimspec/*.spec` | 사람 | 함 | 어떤 클래스·멤버를 무엇에 잇는지. 판과 무관(서술자·상속을 적지 않는다) |
| `src/shimspec/shim-<판>.lock` | `:furnace-compat-paper:shimLock` | 함 | 명세의 클래스·멤버와 그 상위 사슬의 선언(접근·상위·인터페이스·서술자·제네릭 서명, 열거형 상수 개수)을 서명표에서 푼 것. 손으로 고치지 않는다(줄바꿈은 CRLF 여도 같은 파일로 본다) |
| `src/shimspec/kind-classes-<판>.txt` | `:vanilla-extract:run -Pmode=install -Ptools=kind-classes` | 함 | 블록·아이템·엔티티 종류 → 바닐라 클래스(T-231, 바닐라를 부트스트랩해 레지스트리 객체의 클래스를 읽음. 엔티티는 T-232 — `EntityType` 상수의 형식 인자). 판마다 하나 |
| `src/shims/java/<패키지>/<클래스>.java` | 사람 | 함 | 손 몸통(`@hand`·`@fast`)이 필요한 클래스의 "조각". 머리는 생성기가 붙인다 |
| `build/generated/sources/nms-shims/java` | `generateNmsShims`(컴파일 때 자동) | 안 함 | 완성된 shim 소스 |
| `build/generated/resources/nms-shims/META-INF/furnace/shim-*.txt` | 같음 | 안 함 | 구현 없는 추상 메서드 색인(`NmsSymbols` 가 읽는다), 생성·합친 클래스 목록 |

빌드는 잠금 파일만 읽으므로 **바닐라 jar·네트워크 없이** 돈다. 잠금 파일은 shim 이 실제로 갖는 이름만 담는다(명세 멤버·그 클래스·상위 사슬, 이미 shim 에 있는 형식).
제네릭 서명이 shim 에 없는 `net.minecraft` 형식을 가리키면 그 서명은 적지 않는다(서술자는 같다). 서명 관문 기준선과 같은 원칙이다.

## 명령

```
tools/compat/sig-tables.sh                                         # 서명표 셋(Paper 서버·바닐라·라이브러리)을 캐시에 — 처음 한 번
./gradlew -PskipOracle=true :furnace-compat-paper:shimLock         # 명세를 고친 뒤 잠금 파일 다시 쓰기
./gradlew -PskipOracle=true :furnace-compat-paper:shimLockCheck    # 잠금 파일이 서명표·명세와 같은지(CI vanilla-gates)
./gradlew -PskipOracle=true :furnace-compat-paper:compileJava      # 생성은 컴파일에 딸려 자동
```

## 명세 문법

```
# '#' 뒤는 주석. 줄 맨 앞에는 import 또는 class 만.
import org.domi.furnace.compat.bind.EntityOps          # 아래에서 단순 이름으로 쓴다(완전 이름도 된다)

class net.minecraft.world.entity.Entity  owner=org.domi.furnace.compat.bind.EntityOwner
  getX            -> EntityOps.x            read=snapshot  # 정적 위임: EntityOps.x(this)
  discard         -> EntityOps.discard      write          # 위임 전에 EntityOwner.write(this, "Entity#discard")
  setPos(DDD)     -> EntityOps.setPos       write          # 같은 이름이 여럿이면 인자 서술자를 적는다(반환형은 생략 가능)
  position        -> this.getPosition       read=any       # 같은 클래스의 다른 메서드로(바닐라 별칭)
  save            -> @hand  read=owner                     # 손 조각이 몸통을 쓴다(첫 호출이 EntityOwner.read 인지 빌드 시험이 본다)
  getBlockState   -> @fast(docs/…/hot-path-block-read.md) read=snapshot  # 핫 경로 맞춤 설계(설계 4.6) — 손 조각 + 근거 문서
  tick            -> @empty write                          # 빈 몸통(void·생성자만). write 면 소유 검사만 있는 몸통
  <init>          -> @empty                                # 생성자는 검사 종류를 적지 않는다(아직 주인이 없다)
  getType -> @each(EntityOps.type) read=any                # 상위 공통 연결(T-231): 바닐라가 추상인 메서드를 추상 그대로 두고, 물려받는 생성 구체 클래스마다 위임 몸통
  getType                                                  # 이름만: 바닐라가 추상인 메서드 = 추상 선언
  MAX_UP_STEP     = 0.6F                                   # 필드 값(자바 식, 완전 이름으로)
  CODEC           = @hand                                  # 필드를 손 조각이 선언
class net.minecraft.world.InteractionHand                  # 열거형: 상수를 "전부" 바닐라 순서대로(서명표는 이름 순이라 명세가 순서를 정한다)
  MAIN_HAND                                                # 하나라도 빠지면 빌드 실패(잠금 파일의 상수 개수와 견준다)
  OFF_HAND
class net.minecraft.world.level.block.CropBlock            # 멤버 없이 클래스만: instanceof·상속 사슬용 선언
```

- 멤버 이름은 바닐라(Mojang) 이름 그대로. 생성자는 `<init>`. 필드 서술자를 굳이 적을 때는 `NAME:I`.
- **메서드 연결 줄(`->`)은 전부 검사 종류를 하나 적는다 — 위임·별칭뿐 아니라 `@hand`·`@fast`·`@empty` 도(생성자 `<init>` 만 빼고):**
  `pure`(공유 상태 없음), `read=any`(어느 스레드든), `read=snapshot`(공개 스냅샷), `read=owner`(주인 스레드에서만 — 몸통 첫 호출 `owner.read(this, "C#m")`),
  `write`(쓰기 — 몸통 첫 호출 `owner.write(this, "C#m")`).
  - 생성 몸통(위임·별칭·`@empty`)은 생성기가 그 검사를 첫 줄에 넣는다.
  - 손 몸통(`@hand`·`@fast`)은 조각에서 사람이 첫 줄에 쓰고, `ShimSpecBuildTest` 가 컴파일된 몸통의 첫 호출이 그 검사인지 본다(빠뜨리면 시험 실패).
  - 쓰기 메서드에 `pure`·`read=any` 를 잘못 적는 것은 기계가 못 막는다 — 검토가 본다.
  `read=owner`·`write` 는 class 줄의 `owner=` 가 있어야 하고, 정적 메서드에는 못 붙인다(빌드 실패 — 대상이 스스로 검사).
- 소유 규칙 클래스(`owner=`)는 `static void read(Object self, String what)`·`static void write(Object self, String what)` 를 가진 핵심 층 클래스다.
  주인이 아니면 무엇을 할지(던지기·주인에게 넘기기)는 그 클래스가 정한다 — 생성기는 "검사가 위임보다 먼저"만 보장한다.
- 위임 대상 `C.m` 은 정적 메서드다. 인스턴스 멤버면 `this` 가 첫 인자로 간다. 생성 몸통에는 리플렉션·`invokedynamic` 이 없다(시험이 본다).
- **명세에 없는 멤버는 만들지 않는다.** 클래스 파일에 없으니 플러그인 호출은 지금처럼 `NmsLinker` 덫이 잡는다(던지는 빈 껍데기를 만들지 않는다 — 기능 감지 보존).
  단 **전체 껍데기(T-260)** 는 별도 jar(`furnace-compat-shells.jar`)로, 호환 계층에 없는 Paper 클래스 전부의 모양을 서명표에서 바로 만든다. 그 멤버는 `@NmsShellMember` 표지가 있어
  `NmsSymbols` 가 "없음"으로 보므로 정적 참조는 그대로 덫이 잡고, 리플렉션 호출은 몸통이 같은 오류를 던진다. 손·생성 shim 이 있는 클래스는 껍데기를 만들지 않는다(손이 이김).
  설계·결정: `docs/roadmap/full-shells-2026-11-23.md`.

- `@each(C.m)`(상위 공통 연결, T-231)은 바닐라가 추상인 인스턴스 메서드에만 쓴다. 그 클래스·인터페이스에는 추상 선언이 남고, 그것을 물려받는 **생성** 구체 클래스마다
  (스스로 잇지 않았으면) `C.m(this, …)` 위임 몸통이 생긴다 — 상위 사슬에 손 shim 이 있어도 만든다. 단 그 손 shim 소스에 같은 이름의 메서드 선언이 있으면
  생성 몸통이 그것을 말없이 가리므로 생성기 오류로 멈춘다(손 메서드를 지우거나 `@each` 대신 명세로 잇는다).
  예: `EntityBlock#newBlockEntity` 하나로 블록 엔티티 있는 블록 클래스 전부, `BeaconBeamBlock#getColor` 하나로 색유리·색유리판·신호기.

## 종류 표 (T-231)

`kind-classes-<판>.txt` 의 줄 `B|I <레지스트리 이름> <클래스>` 는 바닐라에서 그 블록·아이템 객체의 클래스다. 생성기는 표의 클래스를 **멤버 없는 `class` 줄**로 더하고
(명세에 이미 있으면 명세 줄이 이긴다), 표를 `META-INF/furnace/shim-kinds.txt` 로 옮긴다. 실행 때 `NmsKinds` 가 그 표로 블록 표·`Item.byKey` 의 객체를
그 클래스로 만든다(생성기 전용 생성자 `C(ShimCtor)`, 재료는 `ShimCtor.carrying` 으로 실어 사슬 맨 위 손 shim `Block`·`Item` 이 받는다).
그래서 `Blocks.WHEAT instanceof CropBlock`·`Items.TORCH instanceof StandingAndWallBlockItem` 이 바닐라와 같다. 표에 없는 이름(바닐라에 없는 종류)은 `Block`·`Item`.
판을 올리면 표를 다시 뽑고 `shimLock` 을 다시 쓴다.

엔티티 종류 클래스는 바닐라 생성자 `(EntityType, Level)` 도 받는다(명세에 쓰지 않는다 — 몸통 종류 `FRESH`, 2026-11-23). 몸통은 재료를 표지에 실어 생성기 전용 생성자로 넘기므로 `new Shulker(EntityType.SHULKER, level)` 이 `EntityType#create` 와 같은 "아직 월드에 없는 엔티티" 가 된다.

엔티티(T-232): 줄 `E <엔티티 종류> <클래스>` 는 바닐라 `EntityType` 상수 필드의 형식 인자다(`EntityType<Zombie> ZOMBIE`). 도구는 그 클래스가 구체이고
`(EntityType, Level)` 생성자가 있는지, 레벨 없이 만들어지는 종류(1.21.1 은 46종)는 공장이 만든 객체의 클래스와 같은지 본다. 플레이어는 뺀다(늘 `ServerPlayer`).
실행 때 `NmsEntityKinds` 가 `CraftEntity#getHandle()`·`EntityType#create` 의 객체를 그 클래스로 만든다(재료는 Bukkit 객체 또는 아직 월드에 없는 엔티티의
종류·레벨 — 사슬 맨 위 조각 `Entity` 가 받는다). 그래서 `Entity`·`LivingEntity`·`Mob` 이 바닐라처럼 추상이고 `instanceof Mob`·`instanceof HangingEntity` 가 같다.

## 생성기가 만드는 것

- 명세 클래스 + 그 상위 사슬(바깥 클래스·상위·인터페이스)의 **선언**: 접근·`final`·`abstract`·종류·상속·제네릭 — 잠금 파일 그대로.
  손 shim(`src/main/java`)·패킷 생성물이 이미 있는 클래스는 만들지 않는다(사슬에 있어도 손 shim 이 이긴다).
- 생성 클래스(인터페이스·열거형 밖)는 `protected C(ShimCtor tag)` 만 갖는다 — 바닐라에 없는 기본 생성자 `public C()` 가 생기지 않게, 그리고 생성 하위 클래스가 부를 수 있게.
  **손 shim 이 생성 클래스의 상위이면 그 손 shim 도 `C(ShimCtor)` 생성자를 가져야 한다**(없으면 컴파일 오류가 알려 준다).
  조각이 `C(ShimCtor 이름)` 생성자를 스스로 쓰면(재료를 받는 사슬 맨 위 — 엔티티의 `Entity`) 생성기는 그 생성자를 만들지 않는다. 종류 표의 클래스는 조각이어도 이 생성자를 받는다.
- Paper 내부 패치 인터페이스(`ca/spottedleaf/` — moonrise 청크·추적 시스템, Paper `Entity` 가 구현)는 잠금 파일·선언에서 뺀다. 플러그인이 쓰는 형식이 아니고 shim 에 없다.
- 구체 클래스가 생성 상위(클래스·인터페이스)의 추상 메서드를 명세에 안 적었으면: 던지는 몸통 + 색인 한 줄 → `NmsSymbols` 가 "없음"으로 보고 링크 때 덫을 건다.
  형식 변수를 쓰는 메서드는 형식 인자를 갈아 끼울 수 없어 생성을 거절한다(명세에 이으라는 오류).
- 열거형은 명세가 상수를 **전부** 적어야 한다. 잠금 파일의 `E` 줄(상수 개수, 이름 아님)과 견줘 모자라면 빌드 실패(서수·`values()` 가 바닐라와 같아야 하므로).
  순서는 사람이 바닐라대로 적는다. 조각 열거형은 생성기가 상수를 머리 바로 뒤에 쓴다(조각에는 상수를 적지 않는다).
- 바닐라가 추상인 메서드(인터페이스의 `default` 없는 메서드 포함)는 이름만 적는다. 몸통(`->`)을 적으면 이름 있는 생성기 오류.
- 던지는 몸통은 링크 때만 "없음"이다. 리플렉션(`getMethod`·`getMethods()`) 기능 감지는 있다고 본다(바닐라도 있으니 모양은 같고, 부르면 `UnsupportedNmsCall`).
- 레코드·애노테이션은 아직 만들지 않는다(T-230 에서) — 손 shim 으로 둔다.

## 손 조각

```java
package net.minecraft.world.level.levelgen.feature;

import java.util.List;                       // 조각의 import 는 그대로 남는다

/** 문서 주석·애노테이션도 그대로. */
class CoralFeature {                         // 머리는 "class|interface|enum 이름 {" 꼴로만 — 접근·extends·implements 는 잠금 파일이 붙인다
    CoralFeature(String typeId) { super(typeId); }   // Furnace 전용 멤버도 둘 수 있다
}
```

- 생성기가 머리를 바닐라 선언으로 바꾸고, 생성 멤버·생성 안쪽 클래스를 마지막 `}` 앞에 끼운다. 조각 안에 손으로 쓴 안쪽 클래스는 손 shim 으로 본다.
- 조각 파일은 IDE 소스 폴더가 아니다(완성본은 `build/generated` 에 있다 — 그 파일을 고치지 말고 조각을 고친다).
- 같은 클래스가 조각과 `src/main/java` 에 다 있으면 오류다. 손 shim 을 옮길 때는 `git mv` 뒤 머리만 줄인다.

## 손 shim 을 옮기는 법(패키지 단위)

1. 명세에 `class` 줄을 쓰고, 기계적인 멤버(위임·상수·별칭·추상 선언)는 줄로, 나머지는 `@hand` 로 적는다.
2. 손 shim 을 `src/shims/java` 로 옮겨 머리를 `class 이름 {` 로 줄이고, 줄로 옮긴 멤버는 지운다. 몸통이 필요 없으면 조각도 지운다.
3. `shimLock` → `compileJava` → 옮기기 전·후 클래스 폴더를 `tools/ci/javap-same.sh` 로 견준다. 달라진 곳은 바닐라와 맞춘 곳(상속 구멍·바닐라에 없던 멤버)뿐이어야 하고 목록으로 남긴다.
4. `:vanilla-extract:signatureGate` — 바로잡힌 기준선 줄은 지운다.

## 전체 껍데기 채우기 — 공통 형식 (T-262, 2026-11-23)

전체 껍데기(T-260)의 던지는 멤버 가운데 **값만으로 정해지는 것**은 기계가 진짜로 채운다. 영역 스레드가 채우기를 늘릴 때 따르는 틀이다.
설명·측정: `docs/feature-parity/compat-shell-fills-t262-2026-11-23.md`.

| 파일 | 누가 | 커밋 | 내용 |
|---|---|---|---|
| `<캐시>/<판>/fill-table.tsv.gz`, `fill-table-paper.tsv.gz` | `tools/compat/sig-tables.sh`(`-Pmode=fill-table`) | 안 함(Mojang 이름) | 바닐라 jar·Paper 서버 jar 에서 값만 뽑은 채움 표. **두 표에서 줄이 같은 것만** 채운다(Paper 가 바꾼 클래스는 안 채움). 줄 `P`(단순 생성자) `G`(단순 읽기) `S`(정적 리터럴) `V`(열거형 상수 인자) `T`(레코드 기본 메서드). 형식은 `FillTable` |
| `full-shells-<판>.fp`, `fill-tables-<판>.fp` | `-PfullShellsFpWrite=true` | 함 | 서명표 셋의 sha256 / 채움 표 둘의 sha256(추출기를 바꾸는 PR 만 건드린다 — Paper 표가 있는 노트북에서 다시 씀) |
| `fill-areas.txt` | 사람 | 함 | 영역 번호·이름·접두(로드맵 8장 12개). 가장 긴 접두가 이긴다 |
| `fills-baseline-<판>/<영역>.txt` | `-PfullShellsBaselineWrite=true` | 함 | 영역 합계 + 클래스(이름 해시 앞 8자리)별 남은·채운 멤버 수. **이름 없음.** 영역마다 파일이 따로라 영역 PR 끼리 충돌하지 않는다. **채운 수가 줄 때만 CI 실패**(늘거나 손 shim 으로 빠진 클래스는 통과) — 다시 쓰기는 노트북 정리 때 몰아서 |

규칙(어기면 `FillParityTest` 가 잡는다):
- 모르면 채우지 않는다. 정적 리터럴은 정적 초기화의 **첫 분기·try 앞 직선 구간**에서만 받는다. 추출기는 정해진 몸통 모양(분기·호출·계산이 없는 것)에만 반응한다. 열거형은 상수 인자가 **전부** 리터럴이고 쓰는 생성자가 전부 채워질 때만 채운다(아니면 생성자·읽기도 껍데기).
- 상위 생성자가 채워져야(또는 `Object`·`Record`·`Enum`) 하위 생성자를 채운다. 읽기는 그 클래스의 채워진 생성자가 대입하는 필드만.
- 채운 멤버에는 `@NmsShellMember` 표지가 없다 → `NmsSymbols` 가 "있음"으로 본다. 그래서 값이 바닐라와 같아야 한다.
- 새 종류(예: 배열 리터럴, 상수 참조)를 더할 때는 (1) `FillExtractor` 에 모양 하나 + `FillExtractorTest`, (2) `FillTable` 줄 종류, (3) `Fills` 규칙·`FullShells` 몸통, (4) `FillParityTest.checkClass` 에 비교 하나, 이 네 곳을 한 PR 로.
- 영역 스레드의 손·추론 채우기(명세 줄 `->`)는 이 틀과 별개 길이다(손 shim 이 이긴다). 영역을 옮길 때는 `fills-baseline` 숫자가 줄어드는지로 진척을 본다.
- **정적 필드 하나가 터지면 클래스 전체가 죽는다**(PR 579 검토). `BuiltInRegistries`·`Registries`·`SoundEvents` 처럼 많은 영역이 기대는 상수 클래스에 레지스트리·값을 필드 초기화로 더할 때, 그 초기화가 던지면(서버 목록과 순서가 다름, 이름 누락) 그 클래스의 다른 필드(블록·아이템 레지스트리)까지 `NoClassDefFoundError` 로 모두 못 쓴다. 바닐라와 같은지는 **시험으로** 견주고, 실행 때는 서버 목록을 믿고 만든다. 실행 때 꼭 견줘야 하면 그 레지스트리를 처음 꺼낼 때(필드 초기화 밖) 본다(`NmsVillagers.check` 처럼).

```
tools/compat/sig-tables.sh                                          # 서명표 셋 + 채움 표
./gradlew -PskipOracle=true :furnace-compat-paper:generateFullShells # 지문·기준선 검사 후 껍데기 생성
./gradlew -PskipOracle=true :furnace-compat-paper:fullShellsTest    # 전부 올리기 + 바닐라 대조(FillParityTest)
```

## 연결 파일 — 동작 없는 껍데기 멤버를 있는 기능에 잇기 (T-263 S1, 2026-11-24)

설계: `docs/roadmap/link-inferrer-t263-2026-11-23.md` 3.5. 파일은 `links-<판>/<영역 번호>-<이름>.links`(영역은 `fill-areas.txt`). 줄이 있는 멤버는 `generateFullShells`(껍데기 클래스)와 합치기(T-261, 손 shim 에 덧붙이는 멤버)가 던지는 몸통 대신 **연결 몸통**을 쓴다: [소유 검사] → [인자 변환] → 대상 → [반환 변환] → 반환. 리플렉션·invokedynamic 없음, `@NmsShellMember` 표지 없음(연결기가 "있음"으로 본다).

```
#furnace-links 1 1.21.1 area=9
# "# " 로 시작하는 줄과 줄 끝 " # …" 는 주석. 지문(#ab12…)은 # 뒤에 빈칸이 없다.
class net.minecraft.world.entity.monster.Zombie
  isBaby()Z -> @idiom(entity-data-get, #16271d1c1b) read=owner via=G1 proof=O3
  #0123456789()I -> org.domi.furnace.compat.nms.NmsEntityOps.size pure     # private 멤버는 지문
  getLevel()I -> this.level pure                                           # 별칭: 같은 사슬의 살아 있는 멤버
```

- **이름**: 평문은 Paper 서버 서명표에서 공개·보호인 클래스·멤버·정적 필드뿐. private·패키지 멤버와 값은 `#<SHA-256("a.b.C.이름") 앞 10자리>`(`entity_data/ids-<판>.txt` 와 같은 꼴, `Links.fingerprint`). 어기면 멈춘다(공개 이름을 지문으로 써도 멈춘다).
- **대상**: `a.b.C.m` = `@LinkTarget` 을 단 공개 정적 메서드(인스턴스 멤버면 첫 인자가 받는 쪽 — 그 형식이 받는 쪽 클래스를 정한다), `this.m`(private·패키지 대상이면 `this.#지문`, 지문은 선언 클래스로) = 같은 사슬의 살아 있는 멤버(손 shim·JDK·T-262 채움; 껍데기·다른 연결 줄은 안 됨, 같은 인자·같은 정적 여부, 정적이면 같은 클래스), `@idiom(이름, 값…)` = `@LinkIdiom("이름")` 정적 메서드에 [받는 쪽] + 값 + 인자. 대상·변환·관용구·소유 규칙 클래스는 `org.domi.furnace.compat`·`org.domi.furnace.api` 아래만(어댑터 경계).
- **값**: 정수, `1.5F`, `2.0D`, `10L`, `true`, `"글"`, 공개 정적 필드 `a.b.C.F`, private·패키지 정적 필드 `#지문`. 필드는 값이 있어야 한다(상수·열거형 상수·T-262 채움·손 shim). 지문 필드는 줄의 클래스에서 읽을 수 있어야 하고(private 은 그 클래스 자신의 것만, 패키지 필드는 같은 패키지), 사슬에서 더 가까운 클래스가 같은 이름의 정적 필드를 가지면 멈춘다(이름은 같고 선언 클래스가 다른 지문).
- **형식 변환**: 인자·반환이 대입되지 않으면 `@LinkConvert` 정적 `(A)B` 를 찾는다(형식 쌍마다 하나). 없으면 그 겹침은 안 맞는다.
- **제네릭 관용구**: `@idiom` 대상에 한해 `Object` 자리의 원시 멤버 인자는 박싱(`Integer.valueOf` 등)하고, 관용구 반환이 `Object` 면 멤버 반환형으로 `CHECKCAST`(원시면 박스형 + `xxxValue()`)한다. 칸마다 형식이 다른 엔티티 데이터 칸 같은 관용구를 하나로 쓰기 위함이다. 관용구는 검토 받은 손 코드이고 틀 맞추기가 바닐라 몸통 모양을 정확히 보므로 형식은 바닐라가 보증한다. `@LinkTarget` 다리에는 적용하지 않는다(전역 `Object>Z` 변환을 두지 않는 이유 — 다른 줄의 형식 어긋남을 숨긴다).
- **검사 종류**: `pure | read=any | read=snapshot | read=owner | write | api` — 줄과 대상 표지가 같아야 한다. `read=owner`·`write` 는 표지의 `owner=` 클래스의 `read/write(Object, String)` 를 첫 호출로(`"단순이름#멤버"`), 정적 멤버엔 쓸 수 없다. 별칭 줄은 소유 검사를 못 단다(이어 받는 멤버가 스스로 검사).
- **멈추는 것**: 어긴 줄, 그리고 쓰이지 않은 줄(낡은 줄) — 멤버가 이미 T-262 로 채워졌거나, 손 shim(상위 사슬 포함)에 있거나, 클래스가 안 만들어졌거나, NMS 밖 껍데기라 클래스 초기화가 던질 때. 지우라는 뜻이다(손 shim 바이트를 덮어쓰지 않는다). 생성자·추상 멤버는 잇지 않는다.
- 기준선: 이은 멤버는 `fills-baseline-<판>` 의 "채운 수"에 들어간다(줄이 사라지면 CI 가 잡는다).
- 줄 속성 `via=`·`proof=`·`body=`·`target=` 는 지금 읽어 두기만 한다. 후보를 만들고 정답기로 증명해 줄을 쓰는 도구(`inferLinks`·`adoptLinks`)와 `body=`/`target=` 지문 검사는 다음 조각(S1 뒤쪽·S2)이다.
