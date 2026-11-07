# M1: 입자·소리·게임 이벤트·진동 (2026-11-07)

바닐라 1.21.1 바이트코드(`javap -c -p`, 난독화 이름 dbf=SpawnParticlesEffect, day=PlaySoundEffect, dac/dae=Enchantment/EnchantmentHelper, btn=LivingEntity, cgv=Camel, cwa=SpawnEggItem, cmb=Warden)로 대조했다.

## 채운 것
| 항목 | 바닐라 | Furnace |
|---|---|---|
| 영혼 가속 tick 효과 | `LivingEntity.baseTick` 머리의 `EnchantmentHelper.tickEffects` → soul_speed.json 의 `minecraft:tick` 두 개 | `PlayerLocationEffects.tickEffects`(Region 이 매 틱). 조건: 지면·비행 아님·known movement 수평 속도(×20, 제곱 비교) ≥ 1e-5·발밑 #soul_speed_blocks·tickCount%5==0. ① `soul` 입자 count 0(속도 입자): 위치 x,z = 발 + (엔티티 난수 nextDouble−0.5)×너비(x 먼저), y = 발+0.1, 속도 = known movement×−0.2 / 0.1 / ×−0.2, 속도 1.0. ② 레벨 난수 nextFloat<0.35(영혼 가속 장화면 조건이 거짓이어도 매 틱 굴린다)이면 `particle.soul_escape` 크기 0.6·높이 엔티티 난수 nextFloat×0.4+0.6, 분류 player |
| 착지 블록 입자 | `LivingEntity.checkFallDamage`: 지면·낙하>0 → 낙하>SAFE_FALL_DISTANCE 이고 발밑이 공기가 아니면 `block` 입자 (int)(150×min(0.2F+ceil(낙하−안전)/15F, 2.5)) 개, 퍼짐 0, 속도 0.15000000596046448. 발밑 칸이 서 있는 칸과 x·z 가 다르면 칸 가장자리로 당김 | `LandingParticles`. 몹은 `LivingEntity.checkFallDamage`, 플레이어는 `Region.processMoves` 의 착지 판정(ServerPlayer.doCheckFallDamage → super) |
| 낙타 돌진 | `Camel.handleStartJump`: 소리 `camel.dash` → `entity_action` → dashing, `canJump()` 는 늘 true | `Camel.handleStartJump/canJump/handleStopJump`. 돌진 힘(executeRidersJump)과 쿨다운은 조종하는 클라이언트가 계산(서버 dashCooldown 은 바닐라도 0) |
| 스폰 알 물에 쓰기 | `SpawnEggItem.use`: fluid SOURCE_ONLY 시선, LiquidBlock 만(아니면 PASS), 못 쓰는 자리 FAIL, 몹이 안 만들어지면 PASS, 하나 소모·사용 통계, 마지막에 `entity_place`(출처 플레이어, 자리 = 몹 위치) | `ItemUse.spawnEggUse` |
| 워든 걸음 소리 | `Warden.playStepSound`: `warden.step` 크기 10, 높이 1 | `Entity.walkingStepSound/playStepSound` 훅 + `Warden`·`AbstractSkeleton`(크기 0.15) 덮어쓰기 |

## 이미 되어 있어 닫은 줄 (user-todo 가 낡았음)
- 진동 입자(`VibrationSystem.Ticker` 가 `VibrationParticleOption` 을 보낸다 — 파일 머리 주석만 낡았음), 워든 음파 입자(`WardenAi.SonicBoom` 이 바닐라와 같은 개수·위치), 알레이 진동 수신(`Allay.AllayVibrationUser`, 바닐라와 같은 조건).

## 남은 차이 (가정)
- 엔티티 기본 걸음 소리(블록 SoundType × 0.15)와 amethyst chime, 몹별 걸음 소리 전반은 없다(`Entity.walkingStepSound` TODO). 플레이어 걸음 소리도 같은 묶음이다. 워든·해골 계열만 채웠다.
- 착지 때 `onChangedBlock`(위치 변경 효과 재실행)은 아직 없다. 영혼 가속 tick 효과는 플레이어만(영혼 가속 장화를 신은 몹은 없다고 봄). 마지막으로 `Entity.isOnRails` 조건이 걸음 판정에 빠져 있다.
- 철퇴 착지 추가 입자(`spawnExtraParticlesOnFall`)는 M6.

## 검증
- 단위: `ParticleSoundEventsTest` 12건, `SpawnEggWaterTest` 3건.
