# 엔티티 데이터 처음 값 관문 (엔티티 데이터 설계 D1b-1, 2026-11-23)

## 무엇을 견주나
- 정답표 `furnace-server/src/test/resources/entity_data/defaults-1.21.1.txt`(시험만 쓴다) 는 vanilla-extract 도구 `entity-data-defaults`(`ExportEntityDataDefaults`)가 바닐라 jar 에서 만든다. 플레이어를 뺀 129종, 칸마다 정의 값(DataItem.initialValue)과 생성 뒤 값(DataItem.value).
- 시험 `EntityDataDefaultsTest` 가 서버 엔티티를 종류마다 만들어 두 값을 칸 코덱 바이트로 견준다.

## 도구 함정
- `EntityType.create` 는 레벨 기능 깃발(서버 설정)을 읽는다 → `factory` 필드를 바로 부른다. 낚시찌는 공장이 없어 `(EntityType, Level)` 생성자.
- 가짜 ServerLevel(Unsafe) 에 난수·레지스트리·프로파일러·피해 원천, 시각 0 세계 정보(주민 뇌), 빈 점수판 서버(위더 보스바)가 있어야 129종이 다 만들어진다.
- 데이터 팩 레지스트리만 키 이름순으로 다시 등록한다(진짜 서버가 파일을 이름순으로 읽는다 → 그림 `getAny()` = alban). 내장 레지스트리까지 다시 등록하면 아이템·입자·고양이 번호가 뒤섞인다.
- 좀비 주민 직업은 생성자가 씨앗 없는 엔티티 난수로 고른다 → 16번 만들어 다른 바이트를 `??` 로 쓴다(종류·레벨 바이트는 견준다). 길이까지 다르면 값 전체 `~`.

## 관문이 잡은 서버 차이 (고침)
- 공기 칸 정의 값은 `getMaxAirSupply()`(아홀로틀 6000, 돌고래 4800). 서버는 늘 300 이었다.
- 고양이 변형 정의 값은 black(1). 서버는 tabby(0).
- 그림 변형 처음 값은 레지스트리 첫 항목 alban. 서버는 kebab 이라 kebab 그림이 기본값으로 여겨질 수 있었다.

## 월드 밖 NMS 엔티티의 칸 (D1b-2)
- 운영은 표를 읽지 않는다. 어댑터 다리 `detachedEntityData(kind, ops)` 가 서버 빈 레벨(`SandboxLevel`: 청크 없음, 엔티티 번호를 자기 안에서 셈)에서
  그 종류를 만들고 shim 이 기록한 조작을 `addNmsEntityNow` 와 같은 길로 재생해 칸을 읽는다. load·setter 의 칸 효과를 shim 에 다시 적지 않는다.
  `entityDataDefaults(kind)` 는 조작 없는 경우를 종류마다 한 번 만들어 둔다. 시험 `EntityDataSlotsTest` 가 128종(낚시찌 제외 — 바닐라도
  `EntityType.create` 가 null)을 API 값 → 서버 값으로 돌려 정답표와 견주고, 전역 엔티티 번호·월드 난수가 그대로인지 본다.
- shim `getEntityData()`(월드 밖만): 처음 부를 때 위 칸으로 바닐라 모양 `SynchedEntityData` 를 만들고, 조작이 붙을 때마다 같은 객체를 다시 채운다.
  `set` 은 `EntityOp.SetData`(force)로 차례대로 기록 — 바닐라에서 훅이 불린 쓰기만 기록되므로 재생도 훅을 부른다.
- 함정: 살아 있는 엔티티는 모두 효과 입자 칸(particles)이 있다. 입자 shim 이 없어 빈 목록만 다리에 넣었다(설계 D4 전까지). 고양이 변형·주민 정보 등
  아직 못 바꾸는 값 종류가 있는 종류는 `getEntityData` 가 UnsupportedNmsCall 이고 setter 는 그대로 된다.
- 가정: 생성 뒤 칸 더러움은 서버 엔티티의 것이다(서버 생성자가 바닐라 `set` 자리에 `setQuiet` 을 쓴 곳이 있으면 다를 수 있다 — 바닐라와 대조하지 않음).
- 월드에 넣은 뒤 남은 칸 객체에 `set` 하면 UnsupportedNmsCall(서버에 닿지 않는 쓰기). 월드 엔티티의 칸은 설계 D5.
- 생성자 난수(검토 조건): 빈 레벨의 엔티티 번호는 늘 1 이라 엔티티 난수가 매번 같고, 넣은 엔티티는 다른 번호라 좀비 주민 직업 같은 생성자 선택이
  미리 본 칸과 달라질 수 있었다. shim 이 칸을 처음 꺼낼 때 엔티티 난수 씨앗(`EntityOp.EntityRandom`, 씨앗 없는 난수에서)을 조작 맨 앞에 두고,
  서버는 미리 보기·넣기 모두 그 씨앗으로 엔티티 난수를 만든다(`Entity.withRandomSeed`). UUID 도 그 난수에서 나온다.
- 기록: 빈 레벨에서 만들어도 정적 공유 난수(몹 감지기 시작 틱, `Sensor` 의 스레드 안전 난수)는 한 번 소비된다. 바닐라도 엔티티를 만들 때마다
  같은 공유 난수를 쓰므로 결과 분포는 같고 월드 난수·엔티티 번호와는 무관하다.

## 칸 상수 DATA_* (D2)
- `tools/compat/entity-data-spec.py` 가 Paper 서버 서명표(클라우드에서도 `tools/compat/sig-tables.sh` 로 만든다 — Maven 429 면 다시 실행)에서
  public/protected `EntityDataAccessor` 정적 필드 49개를 골라 명세 줄을 쓴다. 이미 다른 명세에 있는 클래스(Entity·LivingEntity·Player·ArmorStand·ItemFrame)는
  `entity.spec` 의 표지 블록에, 나머지는 `entity-data.spec` 에. 값은 명세에 숫자를 적지 않고 실행 때 `NmsEntityData.accessor(클래스, 이름)` 가
  #565 칸 번호표의 지문으로 찾는다.
- Paper 1.21.1 칸 상수 210개: public 31·protected 18(생성), private 159·package 2(명세에 안 적음 — 필요하면 목록을 뽑아 따로 결정).
  FancyNpcs 의 Goat 뿔 두 개·Shulker 색·Player 모드는 모두 public.
- 시험 `NmsEntityDataConstantsTest`: 상수마다 그 클래스를 사슬에 가진 종류 전부의 배치에서 번호·값 종류가 맞는지.

## 재생 비용 줄이기 (11-23, PR 573 검토의 후속)
- 예전: 칸을 꺼낸 월드 밖 엔티티에 조작이 붙을 때마다 `detachedEntityData(kind, 전체 ops)` 로 빈 레벨에서 처음부터 다시 만들어 재생했다 — 조작 n 개면 n² 재생.
- 지금: `ChunkInternals#openDetachedEntityData` 가 빈 레벨의 엔티티를 쥔 `DetachedEntityData` 를 돌려주고, shim `Entity.Detached` 가 그것을 들고 있다가 붙은 조작 하나만 `apply` 한다. 결과는 처음부터 재생한 것과 같다(`EntityDataSlotsTest.incrementalReplayMatchesFullReplay` 가 세 종류·조작 8개로 매 단계 견준다).
- 실패한 조작 뒤로는 그 예외를 계속 던진다: 엔티티가 조작 도중 상태일 수 있고, 처음부터 재생해도 그 조작에서 늘 실패하므로 관측 결과가 같다.
- 쥔 엔티티는 어느 region 에도 속하지 않아 `synchronized` 로 아무 스레드에서 부른다(월드 상태를 건드리지 않는다). 월드에 넣을 때는 지금처럼 기록한 ops 를 주인 스레드에서 새 엔티티로 재생한다.

## D4-1 주민 종류·직업·데이터 (11-23)
- shim `VillagerType`·`VillagerProfession`(record)·`VillagerData` 와 `BuiltInRegistries.VILLAGER_TYPE`·`VILLAGER_PROFESSION`(기본값 plains·none), `PoiTypeTags`.
- 바닐라처럼 두 클래스의 초기화가 상수를 차례로 등록한다. 처음에는 레지스트리 shim 이 서버 목록을 돌며 값을 만들게 했는데, 값을 만들면 그 클래스 초기화가 돌고 그 초기화가 다시 레지스트리를 불러 **레지스트리가 둘 생겼다**(상수와 레지스트리 값이 다른 객체). 함정: 레지스트리를 값 클래스의 `<clinit>` 바깥에서 값 생성으로 채우지 말 것.
- 번호 순서는 서버 `registries/builtin-1.21.1.txt` 에 villager_type·villager_profession·frog_variant 를 더해(바닐라 reports/registries.json, `extract_builtin_registries.py`) 처음 꺼낼 때 견준다.
- 직업 성분(직업 블록 두 람다·요청 아이템 차례·보조 지점·일 소리)과 바이옴 대응은 바닐라 바이트코드로 확인했고, `NmsVillagersTest` 가 서버 `Villager` 정의(heldJobSite·acquirableJobSite·requestedItems·secondaryPoi·typeForBiome)와 견준다.
- 다리: 등록되지 않은 직업 record 는 바닐라 DefaultedMappedRegistry 처럼 기본값(none)으로 나간다(바닐라 STREAM_CODEC 도 레지스트리 번호).

## D4-2 고양이·개구리 무늬 (11-23)
- shim `CatVariant`·`FrogVariant` 를 바닐라 record(그림 위치)로, `BuiltInRegistries.CAT_VARIANT`·`FROG_VARIANT`(바닐라 registerSimple, 기본값 없음). 그림 위치 꼴은 바닐라 bootstrap 바이트코드로 확인(고양이 `<이름>.png`, 개구리 `<이름>_frog.png`).
- 칸 값은 레지스트리 손잡이. 다리에서 등록되지 않은 값(Direct 손잡이에 새 record)은 바닐라 holderRegistry 코덱처럼 실패한다. Direct 라도 값이 등록된 것이면 그 이름.
- 남은 값 종류: 늑대·그림 무늬(데이터 팩 레지스트리 — RegistryAccess 쪽), 입자, optional_global_pos, vector3·quaternion.
