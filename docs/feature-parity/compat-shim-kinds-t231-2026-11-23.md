# 블록·아이템 종류 클래스 계층 (T-231, 2026-11-23)

플러그인은 블록·아이템을 **클래스로 가른다**: `block instanceof CropBlock`, `item instanceof BlockItem`, `state.getBlock() instanceof BedBlock`.
지금까지 shim 의 블록 객체는 거의 전부 `Block` 이었고(작물·줄기·색유리·불·블록 엔티티 블록만 손으로 고른 클래스), 아이템 객체는 전부 `Item` 이었다.
이번에 **모든 블록·아이템 객체를 바닐라와 같은 클래스로** 만든다. 손으로 고르지 않고 바닐라에서 표를 뽑고, 클래스 선언은 shim 생성기(T-229)가 만든다.

## 1. 한 일

| 부분 | 내용 |
|---|---|
| 표 뽑기 | `tools/vanilla-extract` 도구 `kind-classes`(`ExportKindClasses`): 바닐라를 부트스트랩해 `BuiltInRegistries.BLOCK`·`ITEM` 의 객체 클래스 이름을 레지스트리 순서로 쓴다 → `furnace-compat-paper/src/shimspec/kind-classes-1.21.1.txt`(블록 1060줄·아이템 1333줄, 블록 클래스 241종·아이템 클래스 86종). 익명·지역 클래스가 있으면 실패 |
| 명세 | 표의 클래스는 생성기가 멤버 없는 `class` 줄로 저절로 더한다. 멤버를 잇는 클래스만 `kinds.spec` 에 적었다(작물·줄기 `AGE`·`MAX_AGE`, `EntityBlock#newBlockEntity`, `BeaconBeamBlock#getColor`, `BlockItem#getBlock`·`updateCustomBlockEntityTag`) |
| 생성기 | **상위 공통 연결 `@each(C.m)`**: 바닐라가 추상인 메서드는 추상으로 두고 물려받는 생성 구체 클래스마다 위임 몸통. 블록 엔티티 블록 전부(상자·침대·표지판…)가 `newBlockEntity` 한 줄, 색유리·색유리판·신호기가 `getColor` 한 줄. 표 → `META-INF/furnace/shim-kinds.txt` |
| 만들기 | `NmsKinds` 가 표의 클래스를 생성기 전용 생성자 `C(ShimCtor)` 로 만든다. 재료(블록 종류·아이템 이름)는 `ShimCtor.carrying` 으로 실어 사슬 맨 위 손 shim(`Block`·`Item`)이 받는다. 클래스마다 생성자를 한 번 찾아 둔다(블록 표·아이템을 처음 만들 때만 — 핫 경로 아님) |
| 잠금 | 390클래스(지형지물 시범 8개 포함), 멤버 10. 사슬에 생긴 인터페이스: `SimpleWaterloggedBlock`·`BonemealableBlock`·`ProjectileItem`·`WeatheringCopper`·`Equipable`·`BucketPickup`·`Fallable`·`GameMasterBlock`·`FeatureElement` … (멤버 없는 선언 — 부르면 지금처럼 링크 덫) |

## 2. 옮기기 전후 클래스 모양 차이 (전부 바닐라 쪽으로)

| 클래스 | 전 | 후 |
|---|---|---|
| `CropBlock`·`StemBlock` | 손 shim, `extends Block`, 공개 생성자 `(BlockType, BlockTypes)` | 생성, `CropBlock extends BushBlock implements BonemealableBlock`·`StemBlock extends BushBlock implements BonemealableBlock`, 생성자 `C(ShimCtor)` 만. `AGE`·`MAX_AGE` 그대로 |
| `StainedGlassBlock` | 손 shim, `extends Block`, `getColor` 만 | 생성, `extends TransparentBlock implements BeaconBeamBlock`. `getColor` 는 `@each`(색유리판 `StainedGlassPaneBlock`·`BeaconBlock` 도 이제 있다 — 신호기는 바닐라처럼 흰색, 바닐라 바이트코드로 확인) |
| `BaseFireBlock` | 손 shim, 구체 클래스 | 생성, 바닐라처럼 **추상**. 불·영혼 불 객체는 `FireBlock`·`SoulFireBlock` |
| `FurnaceEntityBlock`(Furnace 전용) | 블록 엔티티 블록 전부의 클래스 | **지움**. 각 블록이 바닐라 클래스(`ChestBlock`, `BedBlock` …). "EntityBlock 구현 = 서버의 블록 엔티티 있음 상태"를 시험이 전 블록에 대해 본다 |
| `EntityBlock` | 손 인터페이스 | 생성(모양 같음 — `newBlockEntity` 추상) |
| `BlockItem` | 손 shim, `private` 생성자, 정적 도우미만 | 손 조각 + 명세. `getBlock()` 생김(블록 표의 같은 블록 — 바닐라 블록 아이템 표 전부와 대조) |
| `Block` | 공개 생성자 `(BlockType, BlockTypes)` | `protected Block(ShimCtor)` 만(공개 생성자 없음 — 바닐라 `Block(Properties)` 는 여전히 덫) |
| `Item` | 패키지 생성자 `(String)` | `protected Item(ShimCtor)` |
| `BlockBehaviour`·`Item` | `FeatureElement` 없음 | 바닐라처럼 `implements FeatureElement`(빈 선언 — `requiredFeatures` 는 덫) |

서명 관문 기준선: 13줄 지움(위 생성자 서술자 5, 상위 사슬 3, `FeatureElement` 없음 5), 새 차이 0.

## 3. 결과

- 시험 `NmsKindClassesTest`(6): 블록 1060개·아이템 1333개의 객체 클래스가 표와 같다(레지스트리 순서까지), 바닐라에 없는 아이템 이름은 `Item`,
  `EntityBlock` 여부 = 블록 엔티티 있음 상태, 침대·문·두 칸 식물·관리자 블록·액체 클래스, 신호 광선 색 33개, `BlockItem.getBlock` = 바닐라 블록 아이템 표.
  생성기 시험 2개 더(`@each` — 손 상위 아래·추상 중간 클래스, 종류 표 읽기·오류 줄 번호).
- Axiom(6.0.1) 점검: **493 중 476 → 481 있음**. 클래스 계층 몫 8개 중 블록 5개(`BedBlock`·`DoorBlock`·`DoublePlantBlock`·`GameMasterBlock`·`LiquidBlock`)가 채워졌다.
  남은 12개 = 5e-3c 9개(다른 스레드) + 엔티티 클래스 3개(`Marker`·`HangingEntity`·`ItemFrame` — 아래 4장).
- 5d 에서 남긴 것 풀림: Axiom 은 침대·문·두 칸 식물이면 `setPlacedBy` 를 건너뛰는데, 이제 그 블록 객체가 그 클래스라 바닐라·Paper 와 같은 길을 탄다.

## 4. 남은 것

- 검토 후속(비차단): 종류 표가 지금 판(`mc.version`)과 맞는지 생성기가 보지 않는다(파일이 하나인지만 본다 — 판을 올리며 표를 잊으면 옛 표가 쓰인다).
  `@each` 몸통이 이미 그 몸통을 받은 생성 상위 아래(`TrappedChestBlock` < `ChestBlock`)에서도 다시 생긴다(동작 같음, 무해).

- **엔티티 종류**: 엔티티 shim 은 서버 엔티티의 보기 객체이고 `Entity` 가 손 shim(바닐라와 달리 구체 클래스)이다. 엔티티 사슬(T-232)을 명세로 옮긴 뒤 같은 표 방식
  (`E <엔티티 종류> <클래스>` — 바닐라에서 뽑으려면 엔티티를 만들어 봐야 해 도구가 따로 필요)으로 더한다. Axiom 의 `Marker`·`HangingEntity`·`ItemFrame` 이 여기 걸린다.
- 생성된 블록·아이템 클래스에는 바닐라의 종류별 메서드(예: `CropBlock#getAge`, `SwordItem` 의 도구 성질)가 없다 — 부르면 지금처럼 링크 덫(`/furnace compat gaps` 에 잡힘).
  표본이 부르는 순서대로 명세 줄로 잇는다(동작은 서버에 있으므로 대부분 위임 한 줄).
- 판 올릴 때: `kind-classes` 를 다시 뽑고 `shimLock` → 생성. 클래스 이름이 바뀌면 잠금 검사(`shimLockCheck`)가 알린다.

## 5. 걸린 시간

스레드 시작 2026-11-23 08:28Z → 전체 시험 시작 약 08:57Z(작업 기록의 명령 시각 — 커밋은 하나라 git log 로는 검증되지 않는다)(약 30분: 표 도구 5분, 생성기 확장·객체 만들기 10분, 시험·관문 15분).

## 6. 금 파일 갱신

`identity/1.21.1/scene-compat-asm-prefilter.txt` 3줄(IF-0.12.2 jar 두 옵션 줄과 TOTAL). IF 의 베틀 코드(`LoomInventoryImpl$1`·`$2`) 둘 다 `instanceof BannerItem` 을 쓰는데,
전에는 shim 에 `BannerItem` 이 없어 변환기가 둘 다 바꿨다(바뀐 클래스 761). 이제 `$2` 는 shim 에 없는 것을 부르지 않아 바뀌지 않고(760),
`$1` 은 shim 에 없는 `ResultContainer` 의 생성자·`setItem` 을 불러 여전히 바뀐다(두 클래스의 javap 참조 목록으로 확인 — `$2` 는 `ResultContainer` 를 필드 형식으로만 쓴다).
결과가 달라진 이유는 "의도한 정확성 수정"(바닐라 1.21.1 의 깃발 아이템은 `BannerItem` — 종류 표)이다. 전체 시험에서 다른 금 파일은 그대로였다.
