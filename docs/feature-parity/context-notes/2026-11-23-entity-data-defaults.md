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
