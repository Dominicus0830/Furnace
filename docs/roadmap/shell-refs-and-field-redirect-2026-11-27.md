# 껍데기 형식을 쓰는 손 멤버 · 인스턴스 필드 리다이렉트 (설계, 2026-11-27)

> 영역 2(엔티티) 스레드가 11-27 보고한 생성기·연결기 한계 두 가지를 푸는 설계.
> (1) 손 메서드가 전체 껍데기(T-260)에만 있는 형식을 참조할 수 없다 — 예: `Parrot#setVariant(Parrot$Variant)`.
> (2) `hurtTime`·`hurtDuration`·`hurtMarked` 같은 인스턴스 필드를 플러그인이 직접 읽고 쓴다 — 연결기가 `GETFIELD`/`PUTFIELD` 를 다시 이어야 한다.
> 원칙: 결과는 바닐라·Paper 와 같다, 바닐라 통째 이식 금지, 커밋 기준선에 Mojang 이름 금지(지문만), "임시로 하고 나중에 고치기" 금지.

## 검토 반영 (11-27 독립 검토 "고쳐서 승인" — 보고서 `furnace/reviews/shell-refs-field-redirect-design-review-2026-11-27.md`)

재검토는 아래 절만 보면 된다. 표시 **[검토 n]** 이 붙은 문단이 바뀐 곳이다.

| 필수 | 반영한 곳 | 한 줄 요약 |
|---|---|---|
| 1 기동 자기 점검 | 2.3 | 껍데기 jar 가 실렸는데 로더가 틀(`@NmsShellRef`)을 주면 오류 로그 + 호환 계층 시작 거부 |
| 2 안쪽 틀 자리 | 2.1·2.4 | 안쪽 `ref` 는 바깥이 생성·조각일 때만, 검사 1 에 `InnerClasses`·`NestHost`·`NestMembers` 서로 가리킴 |
| 3 플러그인 소유 필드 | 4.3·6 | (이름, 서술자)가 색인에 있으면 소유가 플러그인·비 NMS 여도 끝까지 풀고, 못 풀면 덫 |
| 4 걸개 문턱·unreflect | 4.4 | 리다이렉트 걸개는 `NMS_HINTS` 문턱 없이, `unreflectGetter/Setter` 다리, `unreflectVarHandle` 거절, `find*` 는 원래 호출 먼저 |
| 5 리플렉션 쓰기 유실 | 4.4 | 거울은 값 통로가 아니다 — 원래 `get*/set*` 을 부르고 걸개가 원래 인자·접근자 값을 쓴다 |
| 6 C·D 사이 구간 | 4.2·7 | 거울 필드 선언을 PR D 로 옮김. C 는 바이트코드 바꾸기만(리플렉션은 지금처럼 없음) |
| 7 플레이어 | 4.5·4.7·7 | 서버 `Player` 는 `LivingEntity` 가 아님 — `hurtDuration` 추가, `hurtMarked` 를 Paper 와 같은 속도 패킷으로, 플레이어 접근자는 스스로 스레드 안전 |
| 8 월드 밖 읽기 | 4.6 | 월드 밖 저장소에서 방금 쓴 값을 돌려줌, 재생 순서 |
| 9 껍데기 쪽 거울 읽기 | 4.2 | `FullShells`·`ShellMerge` 가 리다이렉트 필드를 바로 읽고 쓰는 몸통을 내면 빌드 멈춤, 검사 범위에 껍데기 jar |
| 권장 | 2.4·4.3·4.5·7 | 검사 2 를 바이트코드로, `full-shells` 시험 개수 확인, 100 jar 훑기 범위 넓힘, 색인에 접근 플래그, JMH 에 소유 검사 다형성 |

## 0. 결론

| | 무엇을 | 어떻게 | 새로 생기는 것 |
|---|---|---|---|
| (1) | 손 코드가 껍데기 형식을 이름으로 쓴다 | **껍데기 참조 틀(shell ref)**: 명세 `ref` 줄이 그 형식의 "모양만 있는 틀"을 생성해 컴파일을 통과시키고, 실행 때는 껍데기 jar 의 진짜 껍데기가 이긴다 | 명세 `ref` 줄, 잠금 `Q`·`QM` 줄, 틀 표지 `@NmsShellRef`, 생성기 검사 3개 |
| (1') | 껍데기 클래스 위에 손 멤버를 덧씌운다 | **새 장치 없음** — 기존 연결 줄(T-263)의 대상(`@LinkTarget` 정적 메서드)이 (1) 덕분에 껍데기 형식을 받을 수 있게 된다. 덧씌우기 = 연결 줄 + 손 대상 | 없음 |
| (2) | 플러그인의 필드 접근을 Furnace 값으로 | **필드 리다이렉트**: 명세 `=>` 줄 → 생성기가 접근자 정적 메서드(소유 검사 먼저)와 색인을 만들고, `NmsLinker` 가 플러그인의 `GETFIELD`/`PUTFIELD` 를 그 접근자 `INVOKESTATIC` 로 바꾼다(스택 모양 같음 → 프레임 다시 계산 없음) | 명세 `=>` 줄, 색인 `field-redirects.tsv`, 연결기 분기 하나, 리플렉션 길(거울 필드와 함께 PR D) |

- 도미니코 결정이 필요한 항목은 없다(동작은 바닐라·Paper 와 같고, 차이는 기법뿐). 코디네이터 확인 거리: 7장의 PR 순서와 담당.
- "Parrot 을 손 클래스로 바꾸면 기존 껍데기·연결 멤버가 사라진다"는 걱정은 **반만 맞다**(2.2절). 엔티티 종류 클래스는 이미 생성 shim 이고 합치기(T-261)·연결 줄이 생성 shim 에도 붙는다. 진짜 막힘은 매개변수 형식(`Parrot$Variant`)이다.

## 1. 지금 구조 — 왜 막히나

빌드 순서(요약):

1. 명세(`src/shimspec/*.spec`) + 잠금(`shim-<판>.lock`, 커밋) → `ShimGenerator` 가 생성 shim 소스를 만든다(손 조각 `src/shims/java` 포함).
2. `compileJava` — 손 shim(`src/main/java`)과 생성 shim 을 javac 가 컴파일한다. **서명표 없이 돈다**(CI light 포함).
3. `generateFullShells`(서명표 캐시 필요, CI vanilla-gates·러너·노트북) — 2의 클래스 폴더를 보고 **없는** Paper 클래스를 바이트코드로 만들고(T-260 껍데기, T-262 채움, T-263 연결 몸통), 있는 클래스에는 빠진 멤버를 덧붙인다(T-261 `ShellMerge`). 결과 jar 는 호환 jar **앞**에 붙는다.

손 코드는 2에서 컴파일되는데 껍데기 형식은 3에서야 생긴다. 그래서

- 명세·잠금: `ShimLock.resolve` 의 `inShim` 이 "명세 사슬이거나 이미 shim 에 있는 형식"만 받는다 → `Parrot$Variant` 는 `type not in shim` 오류.
- 손 조각·`@LinkTarget` 대상: javac 가 `Parrot.Variant` 를 못 찾는다.
- 우회 1(그 enum 을 명세 클래스로): "손이 이김" — 껍데기가 안 생기고 합치기가 빠진 멤버를 던지는 몸통으로만 붙인다. 합치기에는 T-262 채움(P 생성자·G 읽기·V 상수 인자)과 인스턴스 필드가 없어서 `getId()` 같은 지금 살아 있는 채움 멤버가 **던지는 몸통으로 되돌아간다**. `byId`·`CODEC`·`getSerializedName` 을 손으로 다시 써야 하고 바닐라 통째 이식에 가까워진다.
- 우회 2(`@LinkConvert (Parrot$Variant)int`): 변환 메서드도 호환 계층 Java 라 같은 이유로 못 쓴다. 관용구 `entity-data-set` 은 값을 `Object` 로 받으므로 변환 없이 enum 자체가 칸에 들어가 버린다(틀림).

필드: `ShellMerge` 는 Paper 인스턴스 필드를 덧붙이지 않고 색인(`instance-fields.tsv` → `NmsShellFields`)만 둔다. 플러그인의 `GETFIELD LivingEntity.hurtTime` 은 `NmsLinker` 가 `UnsupportedNmsLink.Field` 덫으로 바꾼다. 서버 쪽 값(`furnace-server` `LivingEntity.hurtTime`·`hurtDuration`, `Entity.hurtMarked` — `EntityTracker` 가 속도 패킷에 씀)은 이미 있다.

## 2. (1) 껍데기 참조 틀

### 2.1 명세 문법

```
ref net.minecraft.world.entity.animal.Parrot$Variant      # 전체 껍데기에만 있는 형식을 손 코드가 이름으로 쓴다
  getId                                                   # 손 코드가 쓰는 멤버만(메서드·필드·열거형 상수). 같은 이름이 여럿이면 서술자
  RED_BLUE
ref net.minecraft.world.entity.animal.Rabbit$Variant
  id
  EVIL
  BROWN
  values
```

- `ref` 줄 클래스는 **만들지 않는다**(모양 틀만). 멤버에 `->`·`=` 를 적으면 오류(몸통은 껍데기·채움·연결이 정한다).
- 같은 클래스가 `class` 줄과 `ref` 줄에 함께 있으면 오류. `ref` 클래스가 이미 손·생성 shim 이면 오류(필요 없는 줄 — 지우라는 뜻).
- 상위 사슬: `ref` 클래스의 상위·인터페이스 중 shim 에 없는 NMS 형식은 멤버 없는 `ref` 로 자동으로 끌려온다(`class` 줄 사슬과 같은 규칙).
- **[검토 2] 안쪽 클래스**: javac 가 `Parrot.Variant` 로 찾으려면 틀이 바깥 클래스 소스 **안에** 있어야 한다. 그래서 안쪽 `ref` 는 바깥이 생성 shim(종류 표 클래스 포함)이거나 조각일 때만 받는다. 바깥이 손 shim(`src/main/java`)·패킷 생성물이면 지금 `ShimGenerator` 의 안쪽 클래스 오류와 같은 문구로 멈추고 "바깥을 조각으로 옮긴다"고 안내한다. 바깥이 shim 에 없으면(껍데기뿐) 바깥도 멤버 없는 `ref` 로 끌려와 같은 틀 소스에 들어간다.

### 2.2 잠금

`ShimLock.resolve` 가 `ref` 클래스를 서명표에서 풀어 **새 줄 종류**로 적는다(명세가 shim 에 실제로 쓰는 이름만 — 지금 잠금 원칙 그대로):

- `Q <이름> <접근> <상위> <인터페이스> <서명>` — 클래스 선언(`C` 줄과 같은 칸).
- `QM <소유> <이름> <서술자> <접근> <서명>` — 손 코드가 쓰는 멤버. 열거형 상수는 `F` 와 같은 꼴.

`inShim` 은 `ref` 클래스도 받는다 → `Parrot` 의 손 멤버 서술자에 `Parrot$Variant` 가 있어도 `type not in shim` 이 아니다. `shimLockCheck` 가 낡음을 잡는다.

엔티티 종류 클래스(`Parrot`·`Axolotl`·`Llama`·`MushroomCow`·`Rabbit` …)는 종류 표(`kind-classes-<판>.txt`)가 멤버 없는 `class` 줄로 더한 **생성 shim** 이다. 합치기(T-261)와 연결 줄(T-263)은 생성·손 shim 에도 붙는다(예: `2-entity-core.auto.links` 의 `Axolotl`·`Llama` 줄은 합친 멤버에 붙은 것). 그래서 이 클래스에 손 멤버를 두어도 다른 멤버는 그대로다 — 손이 선언한 멤버의 연결 줄만 "낡은 줄"로 멈추니 지우면 된다.

### 2.3 생성기가 만드는 것 — 틀

`ShimGenerator` 는 `Q`/`QM` 줄에서 **틀 클래스 소스**를 생성 shim 과 같은 폴더에 낸다.

- 클래스에 `@NmsShellMember`(클래스 표지 — 지금 껍데기와 같은 표지) + 새 표지 `@NmsShellRef`. 멤버는 전부 던지는 몸통(`NmsShell.unsupported`) + `@NmsShellMember`.
- 정적 필드는 **상수값(ConstantValue)을 싣지 않는다** — javac 가 손 코드에 값을 박아 넣지 못하게. 실행 때 `GETSTATIC` 으로 진짜 껍데기 값을 읽는다.
- 열거형 틀은 명세에 적은 상수만 `static final` 필드(값 없음)로 낸다. `values()`·`valueOf` 는 적었을 때만, 던지는 몸통.
- 틀 목록을 `META-INF/furnace/shell-refs.txt` 로 낸다.

틀은 호환 jar 에 **들어간다**(대체물). 이유: 서명표 없는 빌드·CI light 시험에서 `Parrot.class.getDeclaredMethods()` 같은 반사가 `NoClassDefFoundError` 로 깨지지 않게. 실행 순서는 이미 정해져 있다 — 껍데기 jar 가 앞이라 **진짜 껍데기가 이긴다**(합친 손 shim 이 같은 이름으로 호환 jar 원본을 가리는 T-261 과 같은 방식). `NmsSymbols` 는 로더가 실제로 주는 바이트를 읽으므로 따로 고칠 것이 없다: 껍데기 jar 가 있으면 껍데기를, 없으면 틀(클래스 표지 → 모든 멤버 "없음")을 본다.

**[검토 1] 기동 자기 점검**: 순서가 맞는 것은 지금 `compatIndex` 정렬과 `SharedLibraryLoader`(부모 우선, 부모가 호환 클래스를 못 봄) 덕분이다. 누가 정렬을 바꾸거나 호환 클래스가 부모 로더에 보이는 실행 방식이 생기면 틀이 조용히 이기고 손 멤버가 전부 던진다. 그래서 호환 계층 시작 때 껍데기 jar 가 실렸으면 `shell-refs.txt` 의 클래스마다 로더가 주는 바이트(`NmsSymbols.RUNTIME` 과 같은 길)에 `@NmsShellRef` 가 없는지 본다(클래스 수십 개 머리 읽기 — 기동 비용 무시할 만함). 있으면 `CompatLang` 키로 오류 로그를 남기고 호환 계층 시작을 거부한다(손 멤버가 던지는 서버를 조용히 띄우지 않는다). 껍데기 jar 가 없는 축소 빌드는 점검하지 않는다(틀이 대체물로 쓰이는 것이 정상).

### 2.4 전체 껍데기 생성기가 하는 검사

`FullShells`/`ShellMerge` 는 `shell-refs.txt` 의 클래스를 "이미 있음"으로 보지 않는다(껍데기를 만들고, 합치지 않는다). 그리고 멈추는 검사 셋:

1. **틀 = 껍데기 모양**: 틀의 모든 멤버가 만든 껍데기에 같은 이름·서술자·정적 여부·접근으로 있다(잠금과 서명표가 같은 곳에서 오므로 늘 맞아야 하고, 어긋나면 낡은 잠금).
   **[검토 2]** 안쪽 틀이면 중첩 속성도 견준다: 실행 때 쓰이는 바깥 클래스(생성 shim, 또는 합쳐서 껍데기 jar 에 들어간 사본)의 `InnerClasses`·`NestMembers` 가 껍데기 안쪽 클래스를 가리키고, 껍데기 안쪽 클래스의 `NestHost`·`InnerClasses`(바깥·이름·접근)가 그 바깥을 가리켜야 한다. 어긋나면 `getDeclaredClasses`·`getEnclosingClass`·nestmate private 접근이 Paper 와 달라지므로 멈춘다.
2. **쓰는 멤버는 살아 있어야 한다**: 손 코드가 부르는 `ref` 클래스 멤버는 껍데기에서 T-262 채움·T-263 연결·값 몸통·열거형 상수처럼 **던지지 않는** 멤버여야 한다. 던지는 껍데기 멤버를 손 코드가 부르면 손 멤버는 "있음"인데 실제로는 던지게 되어 기능 감지가 거짓말을 한다 — 그래서 빌드를 멈추고 "그 멤버를 먼저 채우거나 잇는다"고 알린다.
   **[권장 반영]** 쓰는 멤버 목록은 `QM` 줄이 아니라 **컴파일된 호환 클래스 바이트코드**에서 모은다: `ref` 클래스를 소유로 가진 `INVOKE*`·`GET/PUT*`·메서드 핸들·invokedynamic 부트스트랩 인자. 그래서 `QM` 에 적었지만 안 쓰는 멤버로 빌드가 멈추지 않고, 핸들·람다 참조도 빠지지 않는다(`QM` 은 javac 용 모양 목록으로만 남는다).
3. **만들 수 없는 껍데기 금지**: `ref` 클래스가 못 만드는 5개(1.21.1)이거나 클래스 초기화 덫(NMS 밖 범위의 값 없는 필드)이면 멈춘다.

### 2.5 덧씌우기 = 연결 줄 + 손 대상 (새 장치 없음)

껍데기 클래스 자체에 손 몸통이 필요한 멤버(예: `Parrot$Variant.byId(int)` — 바닐라는 `ByIdMap` 이라 채움이 안 된다)는 지금 있는 연결 줄로 잇는다. 대상 `@LinkTarget` 정적 메서드가 틀 덕분에 `Parrot.Variant` 를 이름으로 받을 수 있게 된 것뿐이다.

```
# links-1.21.1/2-entity-core.links (손 줄)
class net.minecraft.world.entity.animal.Parrot$Variant
  byId(I)Lnet/minecraft/world/entity/animal/Parrot$Variant; -> org.domi.furnace.compat.nms.NmsEntityVariants.parrotById pure proof=review
class net.minecraft.world.entity.animal.Parrot
  setVariant(Lnet/minecraft/world/entity/animal/Parrot$Variant;)V -> org.domi.furnace.compat.nms.NmsEntityVariants.parrotSet write proof=review
  getVariant()Lnet/minecraft/world/entity/animal/Parrot$Variant; -> org.domi.furnace.compat.nms.NmsEntityVariants.parrotGet read=owner proof=review
```

- 생성자·추상 멤버·인스턴스 필드는 연결 줄로 못 잇는다(지금 규칙 그대로). 생성자를 덧씌울 필요는 이번 범위에 없다.
- 이로써 "껍데기 위 손 멤버(overlay)"를 위해 바이트코드 옮겨 심기(Mixin 같은) 단계를 새로 만들 필요가 없다. 손 코드는 늘 2단계(javac)에서 컴파일되고 CI light 에서도 컴파일 오류가 보인다.
- 칸(`DATA_VARIANT_ID` 등 private)은 대상 클래스가 `NmsEntityData.accessor(Parrot.class, "DATA_VARIANT_ID")` 로 한 번 받아 둔다 — 지금 `entity-data.spec` 의 Fox 줄과 같은 방법(손 코드의 이름은 shim 이 그 칸을 실제로 갖는 것). 연결 파일에는 지금처럼 지문만 쓴다.

### 2.6 시험

- `ShimSpecBuildTest`: `ref` 틀이 클래스·멤버 표지를 갖고 몸통이 던진다, 상수값이 없다, `class`·`ref` 겹침·`ref` 에 몸통 적기 오류.
- `ShimLock` 시험: 사슬 자동 `ref`, `inShim` 이 `ref` 를 받음.
- `FullShells` 시험(작은 서명표 조각): 검사 1~3 이 각각 멈춘다, `ref` 클래스가 껍데기로 만들어지고 합치기 대상에서 빠진다.
- `fullShellsTest`(CI vanilla-gates): 껍데기 jar 를 앞에 두고 `Parrot.Variant.BLUE` 가 진짜 껍데기 상수(서수·`getId()` 가 바닐라와 같음)이고 손 대상이 그것을 받는다. 로드된 `ref` 클래스에 `@NmsShellRef` 가 없다.
- 기동 자기 점검 시험(검토 1): 틀이 앞인 로더를 일부러 만들어 호환 계층 시작이 거부되는지.
- **[권장 반영]** `ref` 형식 값은 서명표 없이 만들 수 없어 PR B 의 동작 시험은 `full-shells` 태그로만 돈다. `fullShellsTest` 가 그 태그 시험이 실제로 돈 개수를 확인한다(0 이면 실패 — 조용히 건너뛴 초록 금지).

## 3. (1) 적용 — 영역 2 의 변종 setter·getter

| 클래스 | 바닐라·Paper 몸통(javap, 영역 2 확인) | Furnace |
|---|---|---|
| Parrot·Axolotl·Fox | `entityData.set(칸, variant.getId())` (Fox 는 `getId()` 꼴, 구현 PR 에서 재확인) | 연결 줄 → 대상이 `NmsEntityData.set(self, 칸, v.getId())`. 칸은 반응 없음(영역 2 확인) |
| Llama | `entityData.set(칸, variant.id)` (필드 읽기) | 같은 값을 주는 살아 있는 읽기(`getId()` 채움)를 부른다 — 값은 같다(T-262 G 채움이 `id` 필드를 그대로 돌려줌) |
| MushroomCow | `entityData.set(DATA_TYPE, variant.type)` (문자열) | 같은 값을 주는 `getSerializedName()`. 단 `MushroomType` 상수 인자에 블록 상태가 있어(가정 — 구현 PR 에서 javap) T-262 채움이 안 됐을 수 있다. 그러면 2.4 검사 2 가 빌드를 멈추므로 `getSerializedName` 을 먼저 연결 줄로 살린다 |
| Rabbit | 살인 토끼(`EVIL`)면 방어 8·목표 4개 추가·이름 없으면 "killer_bunny" 이름, 그다음 `entityData.set(칸, variant.id)` | 대상이 서버 `Rabbit.setVariant(int)`(이미 같은 목표·이름 처리)를 주인 스레드에서 부른다. 구현 PR 에서 바닐라·Paper javap 로 서버 쪽을 한 번 더 대조(서버의 `else` 갈래 공격 수정자 제거가 바닐라 판정과 같은지 포함) |
| getter 들 | `Variant.byId(entityData.get(칸))` | `byId` 를 연결 줄로 먼저 살리고(2.4 검사 2), getter 는 그 `byId` 를 부르는 대상 |

열거형마다 쓰는 채움 멤버(`getId()`·`id()`·`getSerializedName()`)가 정말 채워졌는지는 2.4 검사 2 가 빌드에서 확인한다. 안 채워진 것은 그 멤버부터 연결 줄로 살린다(값을 추측해 쓰지 않는다).

월드 밖(아직 월드에 없는) 엔티티는 지금 엔티티 데이터 쓰기처럼 기록·재생, 월드 엔티티는 주인 스레드(`NmsEntityOwner` 규칙 그대로).

### 3.1 (선택, 나중) 기계 채우기 확장

위 setter 대부분은 "칸 쓰기 + 인자의 살아 있는 멤버 하나" 꼴이다. 연결 줄에 인자 변환 `arg1=.getId()`(인자 형식의 살아 있는 인스턴스 멤버 하나)를 더하고 `inferLinks`/`adoptLinks` 가 그 꼴을 모양 증명하면 손 대상 없이 기계가 줄을 채울 수 있다. 생성기는 바이트코드를 쓰므로 껍데기 형식을 자유롭게 부른다. **이 설계에서는 하지 않는다** — 먼저 손 대상으로 5종을 채운 뒤 서명표로 같은 꼴의 수를 재고(구현 PR 보고서), 30개 이상이면 별도 PR 로 제안한다.

## 4. (2) 인스턴스 필드 리다이렉트

### 4.1 명세 문법

```
class net.minecraft.world.entity.LivingEntity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  hurtTime     => NmsEntityFields.hurtTime     read=owner write
  hurtDuration => NmsEntityFields.hurtDuration read=owner write
class net.minecraft.world.entity.Entity  owner=org.domi.furnace.compat.nms.NmsEntityOwner
  hurtMarked   => NmsEntityFields.hurtMarked   read=owner write
```

- 대상 `C.f` 는 정적 메서드 한 쌍: 읽기 `f(Owner)T`, 쓰기 `f(Owner, T)V`(같은 이름 겹치기). `write` 를 안 적으면 읽기 전용(`PUTFIELD` 는 지금처럼 덫).
- 읽기 검사는 `pure | read=any | read=snapshot | read=owner` 중 하나, 쓰기 검사는 `write` — 메서드 줄과 같은 낱말. `read=owner`·`write` 는 `owner=` 필요.
- 인스턴스 필드만(정적 필드는 지금처럼 값 채우기·덫). 바닐라 `final` 필드에 `write` 를 적으면 오류.

### 4.2 생성기가 만드는 것

1. **접근자**: 생성 클래스 `org.domi.furnace.compat.nms.gen.FieldRedirects` 에 정적 메서드 `get$<n>(Decl)T`·`set$<n>(Decl,T)V`. 몸통은 생성 위임 몸통과 같은 꼴: 소유 검사(`owner.read/write(self, "LivingEntity#hurtTime")`) → 대상 → 반환. 리플렉션·invokedynamic 없음.
2. **색인** `META-INF/furnace/field-redirects.tsv`: `선언클래스 이름 서술자 Paper접근플래그 → 접근자(읽기, 쓰기)`. **[권장 반영]** 접근 플래그를 실어 연결기가 바꾸기 전에 JVM 과 같은 접근 판정(protected·private 를 못 닿는 자리에서 건드리면 바꾸지 않고 `IllegalAccessError` 덫)을 한다.
3. **[검토 6] 거울 필드(진짜 필드 선언)는 이 단계(PR C)에서 만들지 않는다.** 리플렉션 길 전부(4.4)와 함께 PR D 에서 선언한다. 그래서 C 가 병합된 뒤에도 `getDeclaredField("hurtTime")` 는 지금처럼 `NoSuchFieldException`(정직한 없음)이고, "늘 0" 구간이 생기지 않는다.
4. 빌드 시험(`ShimSpecBuildTest`): 접근자 첫 호출이 검사.
5. **[검토 9]** 거울이 생기는 D 부터: 호환 계층 자신의 바이트코드와 **껍데기 jar 전체**(T-262 채움·T-263 연결 몸통·합친 멤버)가 리다이렉트 필드를 `GETFIELD`/`PUTFIELD` 하지 않는다. `FullShells`·`ShellMerge` 는 그런 몸통(예: 필드를 그대로 돌려주는 G 채움)을 내려 하면 빌드를 멈추고 "그 멤버를 접근자로 잇는다"고 알린다. 같은 검사를 `fullShellsTest` 가 껍데기 jar 바이트로 한 번 더 한다.

### 4.3 연결기 — 바이트코드 바꾸기

`NmsLinker` 의 `FieldInsnNode` 분기, `missingField` 판정 **앞**:

```
GETFIELD  O.f : D   →  INVOKESTATIC FieldRedirects.get$n (LDecl;)D
PUTFIELD  O.f : D   →  INVOKESTATIC FieldRedirects.set$n (LDecl;D)V     (읽기 전용이면 지금 덫)
```

- 스택: `GETFIELD` 는 객체 하나를 꺼내 값 하나, `PUTFIELD` 는 객체·값을 꺼내 없음 — 정적 호출과 같다. 원래 `O` 는 `Decl` 의 하위라 검증기가 받는다. 그래서 **스택 맵 프레임을 다시 계산하지 않는다**(지금 덫 바꾸기와 같은 성질).
- `O` 풀기(JVMS 5.4.3.2 필드 해석과 같게 — `NmsSymbols.hasField` 와 같은 순서): `O` 자신 → 상위 인터페이스 → 상위 클래스로 올라가 **선언 클래스**를 찾고 색인을 본다.
- **[검토 3] 판정 범위**: 지금 `judged()` 는 소유가 플러그인 자기 클래스(`own`)이거나 NMS 이름이 아니면 보지 않는다. 리다이렉트는 그 문턱을 쓰지 않는다 — **(이름, 서술자)가 색인에 있으면 소유가 무엇이든** 끝까지 푼다. 플러그인 상속 클래스(`@NmsInheritable` 을 상속한 가짜 플레이어의 `this.hurtMarked = true` → `PUTFIELD 플러그인클래스.hurtMarked`)는 `NmsClassTransformer.forJar` 가 넘기는 jar 안 상위 표로, 그다음 shim 사슬로 올라간다. 결과는 셋:
  - 선언이 리다이렉트 대상 → 접근자로 바꾼다.
  - 플러그인(또는 JDK·라이브러리) 클래스가 스스로 선언한 필드임이 **확인됨** → 그대로(JVM 과 같게).
  - **풀 수 없음**(상위가 jar 밖·다른 플러그인 jar) → `UnsupportedNmsLink.Field` 덫. 거울이 있는 D 이후에도 조용히 거울로 가지 않는다.
- 메서드 핸들 상수(`LDC H_GETFIELD/H_PUTFIELD`)와 **invokedynamic 부트스트랩 인자의 필드 핸들**도 같은 규칙으로 푼다. 소유가 선언 클래스와 같으면 접근자 `H_INVOKESTATIC` 으로 바꾸고(형식이 같다), 하위 클래스 소유면 형식이 달라 덫(javac 는 필드 핸들 상수를 내지 않으므로 드묾 — 계측으로 센다).
- 바꾼 자리는 (플러그인, 필드)별로 세어 보고서에 "이어진 필드"로 남긴다(로드 때만 — 실행 비용 없음).

### 4.4 리플렉션·핸들 (PR D — 거울 필드 선언과 한 PR)

거울 필드: 리다이렉트 대상 필드를 같은 이름·서술자·접근으로 **진짜 선언**한다(`getDeclaredField`·`getDeclaredFields` 가 Paper 와 같은 `Field` 를 주게). **[검토 5] 거울은 값 통로가 아니다** — `Field` 의 신원일 뿐이고 값은 늘 기본값이다. 값은 언제나 접근자로 오간다.

- **`Field.get*`**: 원래 `get*` 을 **플러그인 메서드에서 그대로** 부르고(호출자 민감 접근 검사가 Paper 와 같음), 그 결과를 걸개 `NmsReflect.afterFieldGet*(Field, Object 대상, 원래 값)` 에 넘겨 리다이렉트 대상이면 **접근자 값**으로 바꿔 돌려준다(아니면 원래 값 그대로). 거울에 값을 써 넣지 않으므로 동시 읽기·쓰기 경쟁이 없다.
- **`Field.set*`**: 원래 `set*` 을 그대로 부르고(접근 검사·`final` 거절이 Paper 와 같음), 걸개 `NmsReflect.afterFieldSet*(Field, Object 대상, 원래 인자 값)` 가 **넘긴 값 그대로** 접근자 쓰기로 보낸다(거울을 다시 읽지 않는다 — 다른 스레드의 읽기가 끼어도 값이 사라지지 않는다).
  스택 섞기: 지금 `FIELD_SETS` 는 범주 1 값만이다. `int`·`boolean`·참조는 기존 꼴을 늘려 `[f, t, v]` 를 한 벌 더 만든다. `long`·`double`(범주 2) 필드가 리다이렉트에 들어오면 값을 새 지역 변수에 잠시 두는 꼴(`maxLocals` 만 늘림, 그 지점 뒤 프레임은 그 지역을 쓰지 않음)로 낸다 — 지금 세 필드는 범주 1 이라 D 에는 범주 1만 넣고, 범주 2 필드를 처음 더하는 PR 이 그 꼴과 시험을 함께 넣는다(색인 생성이 범주 2 리다이렉트를 만나면 그 꼴이 없을 때 빌드 멈춤).
- **[검토 4] 문턱 없음**: 지금 `Field` 걸개는 클래스 상수에 "minecraft"/"craftbukkit" 글자가 있을 때만 붙는다(`NMS_HINTS`). 리다이렉트용 `Field.get*/set*` 걸개는 이 문턱 없이 모든 플러그인 클래스에 붙인다(범용 ReflectionUtils 류가 클래스 객체만 받아 일하는 꼴). 걸개는 `field.getDeclaringClass()` 의 `ClassValue` 조회 한 번이라 대상이 아니면 거의 비용이 없다.
- **`MethodHandles.Lookup`**:
  - `findGetter`/`findSetter`(다리 있음): 원래 `l.findGetter` 를 **먼저 불러**(접근 오류가 Paper 와 같게, 결과는 버림) 대상이면 접근자 핸들을 `asType` 으로 요청 형식(`(refc)T`)에 맞춰 준다.
  - `unreflectGetter`/`unreflectSetter`(다리 새로): 같은 방식 — 원래 호출 먼저, 대상이면 접근자 핸들.
  - `findVarHandle`·`unreflectVarHandle`(다리 새로): 대상이면 `UnsupportedNmsCall`(조용히 낡은 거울 금지, `NmsGaps` 에 셈).
- `Unsafe.objectFieldOffset` 은 가정으로 남긴다. **[권장 반영]** PR D 에서 인기 플러그인 100 jar 를 훑어 `Unsafe`·`findVarHandle`·`unreflectVarHandle`·`unreflectGetter`·`unreflectSetter` 가 이 필드들에 쓰이는지 숫자로 확정해 보고서에 싣는다.

### 4.5 성능과 동시성

- **핫 경로**: 읽기는 `INVOKESTATIC` → 소유 검사(스레드 비교) → 서버 값 읽기. 할당 없음 → JIT 가 인라인한다. 쓰기는 월드 엔티티면 주인 스레드에서 바로, 월드 밖이면 기록(4.6).
  목표: 읽기 접근자가 바로 필드 읽기의 3배 안(수 ns). `src/bench` 에 JMH 한 개(바로 읽기 / 접근자 / 리플렉션 걸개)를 더해 PR C 에 숫자를 싣는다. **[권장 반영]** 같은 JMH 에서 `NmsEntityOwner.check` 의 `furnaceHandleOrNull()`·`isOwnedByCurrentThread()` 가 단형(구현 하나)으로 인라인되는지 본다.
- **동시성(엔티티)**: 값의 주인은 서버 엔티티(그 region 스레드). 검사 규칙은 `NmsEntityOwner` 그대로 — 주인 스레드·작업 스레드(주인에게 맡김)는 통과, 다른 region·전역 틱 스레드는 `ThreadOwnershipException`(교착 방지). Paper 의 다른 스레드 평범한 필드 읽기(경쟁 읽기)보다 엄격하지만 값이 달라지지 않는다.
- **[검토 7] 동시성(플레이어)**: `NmsEntityOwner` 는 플레이어를 어느 스레드에서든 통과시킨다("몸통이 스스로 가린다"). 그래서 플레이어용 접근자는 **스스로 스레드 안전**해야 한다 — 서버 `Player` 의 기존 잠금(`knockbackLock` 류)을 쓰거나 값을 `volatile` 로 두고, 여러 값을 함께 바꾸는 쓰기는 접속 처리기에 넘긴다(4.7).
- 엔티티(플레이어 아님)의 `hurtMarked = true` 는 서버 `Entity.hurtMarked` 를 세우므로 바닐라처럼 다음 추적 때 속도 패킷이 한 번 나간다(`EntityTracker` 301줄).
- 거울 필드 비용(D 부터): 리다이렉트 대상 필드만 선언(지금 3개: int 2 + boolean 1, 엔티티 shim 객체당 약 12바이트). T-261 이 인스턴스 필드를 통째로 안 넣은 이유(수천 개)와 충돌하지 않는다.

### 4.6 월드 밖 엔티티 [검토 8]

플러그인이 NMS 엔티티를 만들고(아직 월드에 없음) → 필드를 쓰고 → 읽고 → 월드에 넣는 순서가 흔하다(FancyNpcs 등).

- **저장소**: 월드 밖 엔티티의 지금 기록(엔티티 데이터 쓰기와 같은 `DetachedEntityData` 세션·조작 목록)에 필드 조작 `EntityOp.SetField(이름, 값)` 을 더한다. 읽기는 같은 세션이 들고 있는 **필드 현재 값 표**(필드마다 값 하나, 쓰기 때 갱신)에서 돌려준다 — 쓴 적 없으면 바닐라 생성 직후 값(`hurtTime`·`hurtDuration` 0, `hurtMarked` false).
- **재생**: 월드에 넣을 때 조작을 기록 순서대로 서버 엔티티에 재생한다(엔티티 데이터 쓰기와 섞인 순서 그대로). 재생 뒤 읽기는 서버 값.
- 월드 밖 엔티티는 주인이 없어 소유 검사를 통과한다(지금 규칙). 같은 엔티티를 여러 스레드가 동시에 만지는 것은 지금 엔티티 데이터 쓰기와 같은 잠금으로 막는다.

### 4.7 플레이어 [검토 7]

서버 `Player` 는 서버 `Entity`/`LivingEntity` 를 상속하지 않는다(`public final class Player implements Targetable, Rider, …`). 접근자는 손잡이가 `Player` 면 따로 간다.

- **`hurtTime`**: 서버 `PlayerVitals.hurtTime` 이 이미 있다(맞을 때 10, 틱마다 1 줄임, `HurtTime` 저장). 읽기·쓰기 접근자를 더한다.
- **`hurtDuration`**: 플레이어에 없다. 바닐라는 플레이어가 맞을 때도 `hurtDuration = 10; hurtTime = hurtDuration` 이고 `animateHurt` 도 같다. `PlayerVitals` 에 `hurtDuration` 을 더해 `hurtTime = 10` 을 세우는 같은 자리에서 함께 세운다(구현 PR 에서 바닐라 `LivingEntity.hurt`·`handleDamageEvent`·`animateHurt` 를 javap 로 대조해 세우는 자리를 모두 맞춘다). 저장은 바닐라처럼 하지 않는다(가정 — javap 로 확인).
- **`hurtMarked`**: 지금 서버 `Player.hurtMarked` 는 private 이고 `sendPendingKnockback` 이 **밀침 누적값이 있을 때만** 속도 패킷을 보낸다. Paper 의 흔한 꼴 `player.setDeltaMovement(v); player.hurtMarked = true;` 는 다음 `ServerEntity.sendChanges` 에서 **서버가 아는 그 플레이어 속도**로 `set_entity_motion` 을 보낸다(가정 — Paper `ServerEntity` javap 로 확인). Furnace 에서 같게 하려면:
  - 서버 `Player` 에 "서버가 아는 속도" 값(바닐라 `ServerPlayer.getDeltaMovement` 에 해당 — 밀침·폭발·`setDeltaMovement` 가 정함)을 한 곳으로 모은다. 지금 밀침 누적(`knockbackPending`)과 비행 중 굴리는 값·이동 패킷 변위 근사(1389·1400줄)가 따로 있으므로, 바닐라 규칙대로 어느 것이 그 값인지 구현 PR 에서 javap 로 정하고 정리한다(근사를 새로 만들지 않는다).
  - `hurtMarked = true` 쓰기 → 표시만 세운다. 다음 보내기 때 표시가 섰으면 **그 속도로** 한 번 보내고 표시를 내린다(누적값 유무와 상관없이 — 바닐라 `if (hurtMarked)` 와 같게). 지금 `sendPendingKnockback` 의 "쌓인 것이 있을 때만"은 이 규칙으로 바뀐다(기존 밀침 시험이 그대로 통과해야 함).
  - **`Entity.setDeltaMovement` 연결이 아직 없다.** 엔티티·플레이어 둘 다의 `setDeltaMovement`/`getDeltaMovement` 연결을 PR E 범위에 넣는다(그래야 위 꼴이 끝까지 같다).
- 스레드: 위 4.5 대로 접근자가 스스로 안전(잠금 또는 접속 처리기 넘김).

### 4.8 시험

- `NmsLinker` 단위 시험(PR C): 작은 플러그인 클래스(ASM 으로 만듦)의 `GETFIELD/PUTFIELD Zombie.hurtTime`, 하위 소유, 플러그인 상속 클래스의 `this.hurtMarked`(jar 상위 표로 풀림), 같은 이름을 스스로 선언한 플러그인 클래스(안 바뀜), **상위가 jar 밖이라 못 푸는 소유(덫)**, 읽기 전용 필드 쓰기(덫), 접근 플래그 판정, 핸들 상수·부트스트랩 인자 핸들.
- 서버 값 시험(PR E): 월드 엔티티에 쓰기 → 서버 값, 다른 region 스레드 읽기 → 예외, `hurtMarked` → 다음 추적에 속도 패킷 한 번. **월드 밖**: 만들기 → 쓰기 → 읽기 = 쓴 값 → 월드에 넣기 → 서버 값 = 쓴 값(엔티티 데이터 쓰기와 섞인 순서 포함). **플레이어**: `hurtTime`·`hurtDuration` 이 맞을 때 10 이고 틱마다 준다, `setDeltaMovement(v); hurtMarked = true` → 다음 보내기에 속도 v 패킷 한 번, 누적 밀침만 있을 때 기존 동작 유지, 다른 스레드에서 쓰기 안전.
- 리플렉션 시험(PR D): `getDeclaredField("hurtTime").setInt(e, 5)` → 서버 값 5, `getInt` → 서버 값, 동시 읽기 중 쓰기가 사라지지 않음(검토 5), NMS 글자가 없는 도우미 클래스에서 부른 `Field.getInt`(검토 4), `unreflectGetter/Setter` → 접근자, `findVarHandle`·`unreflectVarHandle` → `UnsupportedNmsCall`, 접근 권한 없는 `Field.get` → Paper 와 같은 `IllegalAccessException`.

## 5. 지키는 원칙 점검

| 원칙 | 어떻게 |
|---|---|
| 결과 = 바닐라·Paper | 틀은 컴파일용 모양뿐, 실행 동작은 껍데기·채움·연결·서버. 틀이 쓰는 멤버는 살아 있어야 빌드(2.4-2). 필드는 서버 값 그대로 |
| 바닐라 통째 이식 금지 | enum 을 손으로 옮기지 않는다(껍데기 채움 유지). 손 대상은 몇 줄 |
| 커밋 기준선 Mojang 이름 금지 | 잠금 `Q`/`QM` 은 shim 이 실제로 갖는 공개 이름만(지금 잠금 원칙). 연결 줄 private 은 지문. 기준선 파일은 손대지 않음 |
| 임시 구현 금지 | C 만 병합돼도 완결(리플렉션은 지금처럼 정직한 없음), 거울 필드는 리플렉션 길 전부와 한 PR(D). 못 하는 길(VarHandle)은 정직하게 던지고 셈 |
| 스레드 소유권 | 접근자·대상 첫 호출이 소유 검사(빌드 시험이 봄) |

## 6. 위험과 가정

- (가정) 틀이 호환 jar 와 껍데기 jar 에 같은 이름으로 둘 있어도 로더 순서(껍데기 앞)로 껍데기가 이긴다 — T-261 합친 클래스가 이미 같은 방식으로 돈다. `fullShellsTest` 에 "로드된 `Parrot$Variant` 에 `@NmsShellRef` 가 없다" 단언을 둔다.
- 서명표 없는 빌드에서 손 멤버가 틀(던짐)에 닿으면 `UnsupportedNmsCall` — 그 빌드는 이미 껍데기가 없는 축소 빌드다(서버 빌드가 경고). 출시 빌드는 늘 껍데기를 싣는다.
- 필드 리다이렉트를 빠뜨린 접근 길(위 4.4 의 `Unsafe`)은 거울 값을 본다 — 가정으로 적고 표본 훑기로 확인.
- **[검토 3]** 플러그인 상속 클래스의 상위가 jar 밖(다른 플러그인 jar)에 있으면 올라가기가 끊긴다 → 4.3 의 "풀 수 없음" 규칙이 **덫을 놓는다**(지금 `judged()` 는 플러그인 소유를 안 보므로 그 규칙을 새로 넣는 것이다 — 넣지 않으면 D 의 거울로 조용히 간다). 계측으로 센다.
- (가정) 플레이어 `hurtMarked` 의 Paper 동작(4.7)은 Paper `ServerEntity`·`ServerPlayer` javap 와 봇 시험으로 PR E 에서 확인한다.

## 7. 구현 순서 (PR 별 독립 검토)

| PR | 내용 | 검토 | 담당 제안 |
|---|---|---|---|
| A | 껍데기 참조 틀: 명세 `ref`(안쪽 클래스 자리 규칙), 잠금 `Q/QM`, 틀 생성, `shell-refs.txt`, `FullShells`·`ShellMerge` 제외와 검사 1~3(중첩 속성·바이트코드로 모은 쓰는 멤버), 기동 자기 점검, `full-shells` 시험 개수 확인 | 생성기 변경 → 독립 검토 | 이 스레드 |
| B | 영역 2: Parrot·Axolotl·Fox·Llama·MushroomCow·Rabbit `setVariant`/`getVariant`/`byId` 연결 줄 + 대상, 반응 확인 | 손 구현 → 독립 검토(엔티티 검토 스레드) | 영역 2 스레드 |
| C | 필드 리다이렉트 1단계: 명세 `=>`, 접근자·색인(접근 플래그) 생성, `NmsLinker` 바꾸기(색인에 있으면 소유 무관하게 풀기·못 풀면 덫, 핸들 상수·부트스트랩 인자), 빌드 시험, JMH. **거울 필드 없음** — 리플렉션은 지금처럼 없음 | 연결기 변경 → 독립 검토 | 이 스레드 |
| D | 리플렉션 길 전부 + 거울 필드 선언(한 PR): `Field.get*/set*` 걸개(문턱 없이, 원래 값·인자로), `find*`·`unreflect*` 다리, VarHandle 거절, 껍데기 jar 의 거울 읽기 검사(검토 9), 100 jar 훑기 | 독립 검토 | 이 스레드 |
| E | 영역 2: `hurtTime`·`hurtDuration`·`hurtMarked` 줄 + `NmsEntityFields`(월드·월드 밖·플레이어), 플레이어 `hurtDuration`·`hurtMarked` 보내기 규칙·서버 속도 값 정리, `setDeltaMovement`/`getDeltaMovement` 연결, 시험 | 독립 검토(엔티티) | 영역 2 스레드(플레이어 서버 쪽은 Sonnet medium 이상 — 동시성) |

A·C 는 파일 대부분이 달라 병렬로 갈 수 있지만 `ShimGenerator`·`ShimSpec` 을 둘 다 건드리므로 A 를 먼저 병합하고 C 를 그 위로 올린다. B 는 A 뒤, D 는 C 뒤, E 는 C 뒤(리플렉션 없이도 직접 접근은 동작 — D 와 병렬 가능). C 만 병합된 상태도 완결된 상태다(직접 접근은 진짜 값, 리플렉션은 지금처럼 정직한 없음).
