# 엔티티 데이터 처음 값 관문 (엔티티 데이터 설계 D1b-1, 2026-11-23)

## 무엇을 견주나
- 정답표 `furnace-server/src/main/resources/entity_data/defaults-1.21.1.txt` 는 vanilla-extract 도구 `entity-data-defaults`(`ExportEntityDataDefaults`)가 바닐라 jar 에서 만든다. 플레이어를 뺀 129종, 칸마다 정의 값(DataItem.initialValue)과 생성 뒤 값(DataItem.value).
- 시험 `EntityDataDefaultsTest` 가 서버 엔티티를 종류마다 만들어 두 값을 칸 코덱 바이트로 견준다.

## 도구 함정
- `EntityType.create` 는 레벨 기능 깃발(서버 설정)을 읽는다 → `factory` 필드를 바로 부른다. 낚시찌는 공장이 없어 `(EntityType, Level)` 생성자.
- 가짜 ServerLevel(Unsafe) 에 난수·레지스트리·프로파일러·피해 원천, 시각 0 세계 정보(주민 뇌), 빈 점수판 서버(위더 보스바)가 있어야 129종이 다 만들어진다.
- 데이터 팩 레지스트리만 키 이름순으로 다시 등록한다(진짜 서버가 파일을 이름순으로 읽는다 → 그림 `getAny()` = alban). 내장 레지스트리까지 다시 등록하면 아이템·입자·고양이 번호가 뒤섞인다.
- 좀비 주민 직업은 생성자가 씨앗 없는 엔티티 난수로 고른다 → 16번 만들어 다르면 생성 뒤 값을 `~` 로 쓰고 시험은 정의 값만 본다.

## 관문이 잡은 서버 차이 (고침)
- 공기 칸 정의 값은 `getMaxAirSupply()`(아홀로틀 6000, 돌고래 4800). 서버는 늘 300 이었다.
- 고양이 변형 정의 값은 black(1). 서버는 tabby(0).
- 그림 변형 처음 값은 레지스트리 첫 항목 alban. 서버는 kebab 이라 kebab 그림이 기본값으로 여겨질 수 있었다.
