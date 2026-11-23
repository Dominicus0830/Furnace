# 엔티티 번호표·대응표 선행 작업 (V-04 · V-06 엔티티) — 2026-11-23

버전업(1.21.2) 때 엔티티 쪽을 기계가 찾고 고치게 하는 준비. 동작은 바뀌지 않는다(번호 값이 같은 상수로 바뀌었을 뿐).

## 한 일
1. **대응 표시(V-06 엔티티):** `entity/**` 429개 파일 머리에 `@VanillaPort`(소스 전용)를 붙였다. 기준은 "파일 이름이 바닐라(Mojang 이름) 클래스 이름과 같고, `net.minecraft.world.entity` 아래에 하나뿐이거나 전체에서 하나뿐"이다. `tools/vanilla-port/vanilla-port-map.py --verify` 로 이름이 모두 실제 바닐라 클래스인지 확인했다(엔티티 쪽 오류 0). 길 찾기 도우미로 이름만 같은 `Mth`·`Unit`·`GlobalPos`·`UniformInt`·`Portal`·`EntityTypeTags` 는 뺐다. 이름이 달라 손으로 이은 것: `Enderman`→`EnderMan`, `Mooshroom`→`MushroomCow`, `ThrowableProjectile`→`ThrowableProjectile`·`ThrowableItemProjectile`. 나머지(약 90개)는 바닐라에 대응이 없는 Furnace 전용 도우미다.
2. **번호표 도구 `entity-data`(vanilla-extract):** 바닐라 서버 파일에서 엔티티 메타데이터 표 셋을 만든다. `entity_data/layouts-<판>.txt`(종류별 칸 배치), `owners-<판>.txt`(주인 부류), `ids-<판>.txt`(`S` 직렬화기 번호, `A` 클래스·상수·칸 번호). 엔티티를 만들지 않고 클래스의 정적 `EntityDataAccessor` 상수를 읽는다. 1.21.1 결과가 저장소에 있던 layouts·owners 와 내용이 완전히 같았다(130종류, 머리말만 바뀜).
3. **`EntityDataIds`(빌드 때 생성):** `ids-<판>.txt` → 상수 클래스(`<바닐라 클래스>_<상수>`, `SERIALIZER_<상수>`). `gradle :furnace-server:generateEntityDataIds`. 엔티티 클래스의 `DATA_*` 번호 198개와 `SetEntityData.SERIALIZER_*` 31개가 숫자 대신 이 상수를 쓴다. 번호를 직접 쓴 곳은 `ArmorStand` 포즈 칸 6개와 `WitherBoss` 머리 대상 3개까지 같은 방식으로 바꿨다.
4. **시험 `EntityDataTablesTest`:** 번호표의 직렬화기 순서가 서버의 `EntityData767.SERIALIZERS` 와 같고, 생성 상수가 표와 같고, 배치 표가 알려진 값 종류만 쓰는지 본다.

## 버전업 때 쓰는 법
`./gradlew -PskipOracle=true :vanilla-extract:run -Pmc=<새 판> -Ptools=entity-data -Pmode=install` → 표가 덮어써지고 `EntityDataIds` 가 새 번호로 다시 만들어진다. 바닐라가 칸을 지우거나 이름을 바꾸면 해당 상수를 쓰는 줄이 컴파일 오류로 나타나 고칠 곳을 알려 준다(번호가 밀리는 경우는 조용히 따라간다).

## 알아 둘 것
- Furnace 가 바닐라와 다르게 합쳐 둔 곳은 같은 칸을 다른 부류에서 선언한다: `Boat`·`AbstractMinecart` 의 `DATA_ID_HURT*`(바닐라는 `VehicleEntity`), `Villager`·`WanderingTrader` 의 `DATA_BABY`(`AgeableMob`)·`DATA_UNHAPPY`(`AbstractVillager`). 상수 이름에 바닐라 선언 클래스가 그대로 드러난다.
- `EntityDataIndex767`(API 이름 있는 칸 → 번호)은 아직 손으로 쓴 표다. `EntityDataKeysTest` 가 서버 엔티티(이제 생성 상수)와 대조한다. 이 표를 `ids` 에서 만드는 일은 API 칸 이름과 바닐라 상수 이름 대응이 필요해 따로 둔다.
- 표 도구 출력엔 Mojang 이름 원문이 있다. 커밋한 `ids-*.txt` 는 클래스·상수 이름과 번호뿐이며 바닐라 소스·본문은 없다.
