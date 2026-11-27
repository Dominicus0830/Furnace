# 엔티티 값 채우기에서 만난 설계 한계 두 가지 (2026-11-27, 엔티티 스레드)

FancyNpcs 등이 부르는 엔티티 멤버를 채우다가 생성기 구조 때문에 손 조각으로 못 하는 두 가지를 만났다. 설계는 Opus 설계 스레드에 넘겼다.

## 1. 열거형 인자를 받는 종류·색 setter (Parrot·Fox·MushroomCow·Axolotl·Llama `setVariant`, Rabbit 포함)

- 바닐라·Paper 1.21.1 javap: 다섯 다 `entityData.set(<칸>, 열거값.id / getId() / type)` 한 번, 그 클래스와 상위에 `onSyncedDataUpdated` 반응 없음.
  Rabbit 은 다르다 — 살인 토끼면 방어구 속성·목표를 바꾼다(서버 쪽 조작 필요).
- 막힌 곳:
  - `Parrot$Variant` 등은 **전체 껍데기에만 있는 형**이라 손 메서드 인자로 쓰면 `shimLock` 이 "type not in the shim" 으로 멈춘다.
  - 열거형을 손 클래스로 만들면 "손이 이김" 규칙으로 전체 껍데기가 사라져 `byId`·`CODEC`·`getSerializedName` 등을 모두 손으로 다시 써야 한다.
  - 엔티티 클래스(Parrot·MushroomCow·Axolotl·Llama)도 손 클래스가 되면 전체 껍데기·연결 멤버가 함께 빠질 위험이 있다(Fox·Goat·Camel 은 이미 entity-data.spec 의 손 클래스라 괜찮았다).
- 필요한 설계: 손 메서드가 전체 껍데기 형을 참조하게 하거나, 전체 껍데기 위에 손 멤버를 얹는 방식.

## 2. 인스턴스 필드 `hurtTime`·`hurtDuration`·`hurtMarked`

- FancyNpcs 가 직접 읽고 쓴다. shim 에는 필드가 색인만 있고, 월드 엔티티 값과 이으려면 NmsLinker 가 GETFIELD/PUTFIELD 를 정적 접근자로 바꾸는 설계가 필요하다.

## 같은 날 넣은 것: 도우미를 거치는 깃발 setter (연결 줄, proof=review)

추론기는 클래스 안 도우미(`setFlag(int, boolean)`·`setVexFlag`)를 따라가지 않아 못 잡았다. 도우미 몸통이 관용구 `entity-data-flag-set` 과 같아 손 연결 줄로 넣었다
(`links-1.21.1/2-entity-core.links` 끝): Bee `setHasStung`, Fox `setIsCrouching`·`setSleeping`, Panda `roll`·`setOnBack`, AbstractHorse `setEating`, Vex `setIsCharging`.

**넣지 않은 것(Paper 가 바닐라와 다름):**
- Fox `setSitting`, TamableAnimal `setInSittingPose`: Paper 는 `EntityToggleSitEvent` 를 낸다.
- Bee `setRolling`: Paper 는 `rollingOverride` 를 거친다.
- Bee `setHasNectar`: 꿀 타이머를 함께 초기화한다.
- ArmorStand `setShowArms`: 반응 있는 칸이다.

## ArmorStand `setShowArms` (반응 칸, 연결 줄 proof=review)

- 바닐라·Paper 1.21.1 javap 같음: `entityData.set(DATA_CLIENT_FLAGS, setBit(b, 4, v))`.
- 반응(`ArmorStand.onSyncedDataUpdated`): `refreshDimensions()` + `blocksBuilding = !isMarker()`. 팔 비트(4)는 크기(small·marker)도 marker 도 바꾸지 않아 두 결과 모두 값이 그대로다.
- Furnace 서버 `ArmorStand.onSyncedDataUpdated` 도 이 칸에서 `refreshDims()` 를 부른다 → 같은 결과.
- 남은 구멍: 서버에 `blocksBuilding` 이 없다. 표본 플러그인은 `setMarker` 를 NMS 로 부르지 않아 지금은 영향 없음. `setMarker` 를 넣을 때 함께 메운다.
