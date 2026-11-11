# A08 빛 차단 근사 제거·가루눈 fallOn (2026-11-11)

2차 점검 A08 보고서(`docs/audit/findings/A08-general-blocks-fluids-light.md`)의 A08-01·02·03 수정.

- **잔디·균사체·나일리움(A08-01)**: `canBeGrass`/`canBeNylium` 이 바닐라처럼 `LightTables.lightBlockInto(자기 상태, 위 칸, UP, lightBlock(위 칸)) < 15` 로 판정한다. 양탄자·중계기·비교기·퇴비통·이끼 양탄자 밑에서 흙이 되지 않고, 색유리 밑에서는 흙이 된다. `B.lightBlockedAbove` 는 삭제.
- **신호기(A08-02, A09-01 과 같은 원인)**: 빛줄기 구간이 `LightTables.lightBlock(state) >= 15 && !bedrock` 일 때만 끊긴다.
- **가루눈 fallOn(A08-03)**: 4칸 이상 떨어진 `LivingEntity` 는 소리만 난다(7 미만 `getFallSounds().small`, 이상 `big`). 낙하 피해 없음. `LivingEntity.fallSounds()` 를 추가했고 바닐라와 같이 몬스터는 `entity.hostile.*`, 갑옷 거치대는 `entity.armor_stand.fall` 둘 다, 그 밖은 `entity.generic.*`(바이트코드 `btn.eH`·`ckd`·`ciw` 확인). 플레이어는 `PlayerVitals.fallMultiplier` 가 이미 처리.
- 시험: `A08LightBlockTest`(모든 위 칸 상태 대조 포함)·`A08PowderSnowFallTest`·`A08LightRandomizedTest`(재현 시험을 정식 시험으로).
- 남김: A08-04(네더랙 propagatesSkylightDown)·05·06 은 낮음 등급이라 따로.
