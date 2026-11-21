# shim 생성기 뼈대와 연결 명세 형식 (T-229·T-232 1단계, 2026-11-20)

설계: `docs/roadmap/broad-support-version-scaling-2026-11-20.md` 4.3·4.6. 형식 설명서(사용법): `furnace-compat-paper/src/shimspec/README.md`.
이 문서는 무엇을 왜 그렇게 정했는지, 시범 이전 결과, 남은 일을 적는다.

## 1. 한 일

| 부분 | 파일 |
|---|---|
| 명세 읽개(문법·검사 종류·오류는 파일:줄) | `furnace-compat-paper/src/shimgen/java/.../shimgen/ShimSpec.java` |
| 잠금 파일(풀기·쓰기·읽기·차이) | `ShimLock.java`, 도구 `ShimLockTool.java`, 작업 `:furnace-compat-paper:shimLock`·`shimLockCheck` |
| 생성기(선언·위임·소유 검사·별칭·필드 값·열거형 상수·안쪽 클래스·조각 합치기·생성기 전용 생성자·던지는 몸통 색인) | `ShimGenerator.java`, 작업 `generateNmsShims`(컴파일에 딸림) |
| 서술자·제네릭 서명 → 자바 소스 | `JavaTypes.java` |
| 이미 있는 shim 판정(손·패킷 생성물·조각) | `ShimSources.java` |
| "구현 없는 추상 메서드 = 없음" | `NmsSymbols#unimplemented`(빌드 색인 `META-INF/furnace/shim-unimplemented.txt`) |
| 생성기 전용 생성자 표지 | `org.domi.furnace.compat.nms.ShimCtor` |
| 패킷 생성기와 겹침 방지 | `PacketShimGenerator` 가 잠금 파일의 클래스는 빈 채움 shim·처리자 표지를 만들지 않음 |
| 서명 관문 | `SigGate` 가 `C(ShimCtor)` 를 차이로 세지 않음(참고 `generator-ctor`) |
| CI | vanilla-gates 에 `shimLockCheck` 단계 |
| 시험 | `ShimGeneratorTest`(9), `ShimLockTest`(4), `ShimSpecBuildTest`(실제 빌드 대조), `NmsSymbolsUnimplementedTest`(2) |

## 2. 정한 것과 이유

1. **잠금 파일을 커밋하고 빌드는 그것만 읽는다.** 서명표는 바닐라 jar(약 50MB)·Paper 서버 jar 를 받아야 만들어지므로, 컴파일마다 그걸 요구하면
   오프라인 빌드(`--offline` 시험 규칙, 배포 저장소 빌드)가 깨진다. 잠금 파일은 명세가 쓰는 선언만 담아 작고(시범 9클래스 11줄), PR 에서 "이번에 바뀐 선언"이
   그대로 보인다. 판을 올릴 때는 새 서명표로 `shimLock` 을 다시 돌리면 차이가 곧 표류 보고(T-236 의 바탕)다. 서명표와 맞는지는 CI `shimLockCheck` 가 본다.
2. **잠금 파일에는 shim 에 있는 이름만.** 멤버 서술자가 shim 에 없는 `net.minecraft` 형식을 쓰면 풀기가 실패한다(명세에 그 클래스를 더하라는 오류).
   제네릭 서명이 그런 형식을 가리키면 서명만 지운다(서술자는 같아 링크는 같다). 서명 관문 기준선의 "Mojang 이름은 shim 에 있는 것만" 원칙(11-20 결정 (가))과 같다.
3. **우선순위는 Paper 서버 → 바닐라 → 라이브러리 서명표.** 플러그인은 Mojang 이름 Paper 로 컴파일되므로 Paper 가 더한 인터페이스·멤버가 정답이다.
4. **부분 클래스(손 조각).** 손 몸통은 `src/shims/java` 의 조각에 두고 머리(접근·상속)는 잠금 파일이 붙인다 — 판이 바뀌어 상속이 바뀌어도 조각은 그대로다.
   조각 머리는 `class 이름 {` 꼴만 받는다(손으로 상속을 적으면 생성과 어긋날 수 있어서). 패킷 덧붙임(`@@`)을 일반화한 것.
5. **생성기 전용 생성자 `protected C(ShimCtor)`.** 생성 클래스에 생성자가 없으면 javac 가 `public C()` 를 만든다 — 바닐라에 없는 생성자라 플러그인이 "있다"고
   오판한다(시범에서 `TreeConfiguration` 의 이 구멍을 실제로 메웠다). 표지 형식은 Furnace 것이라 플러그인 바이트코드가 부를 수 없다.
6. **소유 검사는 "위임보다 먼저 한 번".** `read=owner`·`write` 줄은 `owner.read/write(this, "C#m")` 를 먼저 부르고 위임한다. 주인이 아닐 때 던질지 넘길지는
   핵심 층 소유 규칙 클래스가 정한다(엔티티·칸마다 규칙이 달라서 — 지금 손 shim 의 `NmsOwner.call` 같은 넘기기도 그대로 쓸 수 있다).
   강제되는 범위(검토 뒤 바로잡음): **메서드 연결 줄은 전부(위임·별칭·`@empty`·`@hand`·`@fast`, 생성자 제외) 검사 종류를 적어야 빌드된다.** 생성 몸통(위임·별칭·
   `@empty`)의 `read=owner`·`write` 는 생성기가 검사를 첫 줄에 넣고, 손 몸통(`@hand`·`@fast`)의 `read=owner`·`write` 는 `ShimSpecBuildTest` 가 컴파일된 몸통의
   첫 호출이 그 소유 검사인지 본다. 사람이 쓰기 메서드에 `pure`·`read=any` 를 잘못 적는 것은 기계가 못 막는다(검토가 본다). 정적 메서드에는 검사를 생성하지
   않는다(this 가 없음 — 대상이 검사, 적으면 빌드 실패).
7. **던지는 몸통은 인터페이스 때문일 때만.** 구체 생성 클래스가 생성 인터페이스·상위의 추상 메서드를 명세에 안 적으면 javac 가 거부하므로 던지는 몸통을 붙이고
   색인에 적는다. `NmsSymbols` 는 그 메서드를 "없음"으로 봐 그 클래스·하위로 부르는 호출에 링크 때 덫을 건다(지금 실패 방식과 같음). 인터페이스로 부른 호출만
   그 몸통에 닿아 `UnsupportedNmsCall` 이 난다. 형식 변수를 쓰는 메서드는 형식 인자를 갈아 끼울 수 없어 거절한다(명세에 이으라는 오류).
8. **생성 몸통에 리플렉션·`invokedynamic` 없음.** 정적 호출만 쓴다(`ShimSpecBuildTest`·`ShimGeneratorTest` 가 바이트코드로 본다). 측정 계수기(T-241)는 아직 넣지 않았다.

## 3. 시범 이전 — 지형지물 패키지 8개

옮긴 것: `configurations.FeatureConfiguration`·`TreeConfiguration`·`NoneFeatureConfiguration`(전부 생성), `CoralFeature`·`CoralClawFeature`·`CoralMushroomFeature`·
`CoralTreeFeature`·`TreeFeature`(Furnace 전용 생성자 때문에 손 조각). `Feature`·`ConfiguredFeature`·`FurnaceFeature` 는 손 shim 그대로(사슬로만 잠금에 나옴).

`tools/ci/javap-same.sh` 로 옮기기 전(기준 브랜치 33b0065d 컴파일)·후 호환 계층 클래스 폴더 전체를 견줬다: **2031개 중 2개만 다름.**
(독립 검토가 기준 브랜치 끝 b94460a2 로 다시 돌려 2140개 중 같은 2개 다름 + 새 `ShimCtor` 를 확인했다.)
- `TreeConfiguration`: 바닐라에 없던 `public TreeConfiguration()` 이 빠지고 `protected TreeConfiguration(ShimCtor)` 가 생김 — 의도한 구멍 메움(바닐라는 `protected` 다인자 생성자뿐).
  서명 관문 기준선의 이 클래스 `descriptor` 줄은 그래서 지웠다.
- `NmsSymbols`: 2장 7번 규칙.
- 새로 생긴 클래스: `ShimCtor`.

## 4. 한계(정직하게)

- 레코드·애노테이션 클래스는 생성하지 않는다(T-230). 열거형 상수는 명세가 순서를 정한다(서명표는 이름 순). 잠금 파일에 상수 "개수"(이름 아님, `E` 줄)를
  담아 명세가 하나라도 빠뜨리면 빌드가 실패한다 — 서수·`values()` 가 바닐라와 달라지지 않게. 순서가 바닐라와 같은지는 사람이 본다(서명표에 순서가 없다).
- 바닐라가 추상인 메서드(인터페이스의 `default` 없는 메서드 포함)에는 몸통을 적을 수 없다(이름 있는 생성기 오류 — 클래스 모양이 바뀌므로). 이름만 적는다.
- 던지는 몸통은 링크 때만 "없음"이다. `Class.getMethod`·`getMethods()` 같은 리플렉션 기능 감지는 그 메서드를 있다고 본다(바닐라에도 있으니 모양은 같고,
  부르면 `UnsupportedNmsCall`).
- 실제 빌드의 끝에서 끝(명세 → 빌드 → `NmsSymbols` 가 "없음" 보고) 시험은 합성 클래스(`NmsSymbolsUnimplementedTest`·`ShimGeneratorTest`)뿐이다. 시범 패키지에
  던지는 몸통 사례가 없어 `theRuntimeIndexIsTheBuildOutput` 은 지금 빈 색인을 읽는다 — 첫 사례가 생기는 이전 묶음에서 실제로 돈다.
- 잠금 파일 비교는 줄바꿈을 `\n` 으로 맞춘 뒤 한다(Windows `core.autocrlf` 체크아웃의 CRLF 로 가짜 실패하지 않게).
- `throws` 절은 서명표에 없어 쓰지 않는다 — 대상이 확인 예외를 던지면 컴파일 오류(그때는 `@hand`).
- 손 shim 이 생성 클래스의 상위면 그 손 shim 에 `C(ShimCtor)` 생성자가 있어야 한다(컴파일 오류가 알려 준다).
- 생성 상위 사슬 선언(멤버 없는 클래스)이 늘면 `Class.forName("net.minecraft…")` 감지에 걸리는 클래스도 는다 — 설계 4.4 가 허용한 "채운 클래스의 상위 사슬" 범위다.
- 조각 머리·안쪽 클래스 판정은 정규식이다(패킷 생성기와 같은 방식). 주석 안의 `class 이름 {` 같은 글은 피한다.

## 5. 남은 일

- **T-232 이어서**: 엔티티 사슬(`Entity`·`LivingEntity`…)을 명세로 옮기고 핵심 층 소유 규칙 클래스를 둔다. 지금 `Entity` 는 바닐라와 달리 추상이 아니다(손 shim 이
  `new Entity(bukkit)` 로 만든다) — 옮길 때 이 차이를 어떻게 메울지(숨은 구체 하위 클래스 등)를 먼저 정한다.
- 손 shim 패키지별 이전(작은 패키지부터, javap 전후 비교).
- T-230 데이터 상수·레코드, T-231 블록·아이템·엔티티 종류 계층(`CropBlock` 등은 지금도 명세 `class` 줄 하나로 선언만 만들 수 있다 — `Block` 손 shim 에 `C(ShimCtor)` 를 더하면 된다).
- T-236 표류 보고는 `ShimLock.diff` 를 바탕으로 이름 바뀜 후보 제안만 더하면 된다. T-241 측정 계수기는 위임 몸통에 넣는다.

## 6. NMS 채우기 스레드와 나눔

"기계적으로 만들 수 있는 것"(기존 shim 위임·상수·빈 클래스 계층·별칭·추상 선언)은 이제 손 shim 을 늘리지 말고 명세 줄로 쓴다.
설계가 필요한 것(여러 다리를 엮는 연결, 상속 대상)은 지금처럼 손으로 쓰되, 새 클래스라면 처음부터 손 조각 + 명세(`@hand`)로 둔다.
이미 손 shim 인 클래스에 멤버를 더할 때는 그 클래스를 먼저 조각으로 옮겨야 명세 줄을 붙일 수 있다(손 shim 과 명세를 한 클래스에 섞지 않는다).
