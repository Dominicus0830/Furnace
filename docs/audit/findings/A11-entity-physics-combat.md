# A11 엔티티 물리·전투·효과 — 2차 전체 점검 조사 보고서 (2026-11-08)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `eab009d9`(PR #180 병합 시점). 코드는 고치지 않았다.
- 바닐라 근거: 1.21.1 서버 jar(`main` 브랜치 `tools/vanilla-data/server-1.21.1`)를 `javap -c -p` 로 읽었다. 난독화 이름을 함께 적는다.
  주요 대응: `btn` LivingEntity, `bsr` Entity, `brk` DamageSource, `brl` DamageSources, `cmx` Player, `aqv` ServerPlayer, `awg` DamageTypeTags, `awi` EntityTypeTags,
  `bsb` MobEffects(`m` 물숨쉬기, `C` 전도체의 힘, `B` 슬로 폴링, `y` 부양), `bsa` MobEffectUtil, `aqk` DistanceManager, `aqa` ChunkLevel.
- 재현 시험: `A11-repro/` 4개 파일, 시험 14개. 기준 커밋에서 돌리면 **13개가 실패**하고(차이 재현) 1개(`survivalPlayerCannotHurtInvulnerableMob`)는 같음 확인용이라 통과한다.
  쓰는 법은 아래 "재현 시험 쓰는 법".

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 4 | A11-01 가루눈 냉각 없음 · A11-02 플레이어 질식 없음 · A11-03 낙하 면역 종류가 낙하 피해를 입음 · A11-04 전도체의 힘이 호흡을 주지 않음 |
| 중간 | 4 | A11-05 엔티티 틱 범위 · A11-06 몹이 화염 저항을 무시 · A11-07 창조 모드가 Invulnerable 을 못 뚫음 · A11-08 몹 무기 마법 부여 미적용 |
| 낮음 | 6 | A11-09 ~ A11-14 |
| 참고 | 3 | A11-15 ~ A11-17 |

도미니코의 결정이 필요한 항목은 없다(전부 바닐라와의 관측 차이).
가장 큰 것은 A11-01~04 네 개다. 공통점은 **"플레이어와 몹이 따로 구현되어 있어 한쪽에만 있는 위험 요소·보호"** 라는 것이다 — 질식은 몹에만, 낙하 면역·화염 저항은 플레이어에만, 냉각은 어느 쪽에도 없다.

## 조사한 범위

- 피해 흐름: `LivingEntity.hurtByInner`(바닐라 `btn.a(brk,F)`), `PlayerVitals.hurt`·`actuallyHurt`(`cmx.a`·`aqv.a`), `Entity.isInvulnerableTo`(`bsr.b(brk)`), `actuallyHurt`·`getDamageAfterArmorAbsorb`·`getDamageAfterMagicAbsorb`(`btn.f/d/e`), `CombatRules`, 보호 마법 합산, 넉백(`btn.a(DDD)`), 방패 막기, 불사의 토템(`btn.g(brk)`), 난이도 배율.
- 효과: `MobEffect`(재생·독·위더·허기·포화·흡수·즉시 치유/피해)의 틱 간격과 식, `MobEffectInstance`(겹침·숨은 효과·만료), 속성 수정자 표.
- 환경 위험: 낙하 피해(`LivingEntity`·`PlayerVitals`·`AbstractHorse`·`Entity.causeFallDamage`), 익사·공기, 질식, 용암·불, 냉각, 월드 밖, 굶주림, 겉날개 충돌.
- 이동: `LivingEntity.travel`(지상·물·용암 가지), 점프 강화·슬로 폴링·부양, 엔티티 틱 범위(`ChunkLevel.tickEntities` ↔ `Region.blockEntityTicking`), 플레이어 이동 검증(`PlayerMoveValidator`·`VehicleMoves` ↔ 바닐라 `handleMovePlayer`·`handleMoveVehicle`).
- 발사체·폭발: `AbstractArrow.onHitEntity` 피해 식, 눈덩이·달걀·진주·바람 충격구 피해 종류, `Explosion` 피해·밀림 식, 피해 유형(`DamageType` 47종) 사용처.
- 몹 근접: `Mob.doHurtTarget`.
- 메모 후보 확인: `processMoves` 텔레포트 가지 탄 상태 검사(→ 고치지 않음으로 닫혔다. 2026-11-08 `context-notes/2026-11-08-movement.md`; 바닐라 `handleAcceptTeleportPacket` 도 탄 상태와 무관하게 `absMoveTo`. 다시 열지 않는다), 몹 불 보호·`is_on_fire`(후속 목록에만 있고 이번에 코드로 확인 → A11-06 과 아래 "같음"), 엔티티 틱 범위(A07-07 → A11-05).

## 조사하지 못한 범위 (이유)

- **탈것 조종 물리**(말 점프 힘·보트 물 위 물리·광산 수레 레일 가속·돼지/스트라이더 당근 막대 가속): `HorseRidingTest`·`BoatTest`·`MinecartTest`·`HorseRemountTest` 가 있고 M21(`m21-pr123`)·PR #126 검토가 끝났다. 이번에는 코드 대 바이트코드 대조까지 못 갔다. 봇 접속 시험이 필요한 부분이라(클라이언트 권위 이동) 클라우드 컨테이너에서는 못 한다.
- **플레이어 근접 공격 식**(치명타·휩쓸기·밀림·강타 등): M6(`m6-pr116`)·철퇴(`MaceEnchantTest`)가 검토를 마쳤다. 다시 하지 않았다.
- **창 열기 등 클라이언트 보정이 필요한 이동 오탐**: 클라이언트가 없어 바닐라 클라이언트 쪽 값을 비교하지 못했다. 서버 쪽 검증 식만 대조했다.
- **삼지창 급류·바람 충격구 반동·낚싯대 당기기** 수치: 단위 시험(`FishingTridentTest`·`ArrowDeflectParityTest`)이 있는 범위만 확인했다.
- 틱 단위 장면 대조(바닐라 서버 실행): Mojang 라이브러리 호스트가 막혀 못 돌린다. 바이트코드 근거와 Furnace 시험으로 대신했다.

## 같음 확인 (다시 볼 필요 없는 것)

- 방어력·강인함 식: `CombatRules.getDamageAfterAbsorb`(`brg.a`) 의 `2 + toughness/4`, `clamp(armor − damage/f, armor×0.2, 20)/25`, 관통(breach) 보정과 같다. 보호 마법 `clamp(protection,0,20)/25`, 저항 효과 `(25 − 5(amp+1))/25`, 순서(방어력 → 저항 → 보호 마법 → 흡수)와 `bypasses_armor`/`bypasses_effects`/`bypasses_resistance`/`bypasses_enchantments` 분기가 바이트코드(`btn.d`, `btn.e`)와 같다. 보호 합산이 몸(BODY) 칸을 포함하는 것도 같다.
- 무적 시간 창(`invulnerableTime > 10` 이면 `amount ≤ lastHurt` 를 막고 차액만 입음, 새 피해는 20·`hurtTime` 10), 방패 막기 뒤 넉백(0.4) 유무, `markHurt` 조건, 투사체 넉백 방향, 원인 없는 피해 넉백 (0,0).
- 플레이어 난이도 배율(쉬움 `min(x/2+1, x)`·어려움 `×3/2`·평화 0)과 `scalesWithDifficulty` 조건, 게임 규칙(익사·낙하·불·냉각 피해) 면제, 창조 모드 `abilities.invulnerable`.
- 효과 틱: 재생 `50 >> amp`, 독 `25 >> amp`(체력 1 초과일 때만), 위더 `40 >> amp`, 허기 `0.005 × (amp+1)`, 포화 `eat(amp+1, 1.0)`, 즉시 치유 `4 << amp`·즉시 피해 `6 << amp`(언데드 반전 태그 `inverted_healing_and_harm`), 흡수 시작 시 `max(흡수량, 4×(amp+1))`, 즉시 효과 3종의 `duration ≥ 1`, 효과 속성 수정자 표(속도 +20%·둔화 −15%·성급함 +10%·채굴 피로 −10%·힘 +3·나약함 −4·점프 강화 안전 낙하 +1 …).
- 몹 낙하 피해 식 `ceil((거리 − safe_fall_distance) × 배율 × fall_damage_multiplier)`, 개구리 −5·염소 −10 보정, 말의 승객 전파, 라마 6칸 규칙(식 자체. 낙하 면역 태그는 A11-03).
- 폭발 피해 `((d²+d)/2 × 7 × 2r + 1)`, 밀림 `(1 − 거리) × 노출 × (1 − explosion_knockback_resistance)`, 눈높이(TNT 는 발) 방향, 드래곤 파트 처리.
- 화살 피해 `ceil(clamp(속도 × 기본 피해, 0, 2^31−1))`, 치명타 추가 `nextInt(damage/2 + 2)`, 관통 처리, 불 붙이기 5초, 화살 속도 감쇠 0.99/물 0.6.
- 지상·물·용암 `LivingEntity.travel` 가지(물 마찰 0.8·효율 보정·돌고래의 은총 0.96, 용암 0.5/0.8, 지상 마찰·부양·중력 식)와 점프 강화 `0.1 × (amp+1)`, 슬로 폴링 중력 `min(g, 0.01)`.
- 용암 `lavaHurt`(15초 점화 + 4 피해), 굶주림 규칙(난이도·체력 조건), 월드 밖 피해, 겉날개 충돌 피해 식.
- 플레이어 이동 검증: `handleMovePlayer`·`handleMoveVehicle` 의 거리²−속도² 한도, `moved wrongly` 0.0625, 세로 차이 항 무효(`d7 > -0.5 || d7 < 0.5` 가 늘 참인 바닐라 그대로), 떠 있기 검사 면제 목록(부양·겉날개·급류 회전 …).
- **몹 불 보호·`is_on_fire`**(메모 후보): 불붙은 몹의 `onFire` 피해와 `burning_time` 속성, 불 끄기(물·가루눈·비)는 바닐라와 같다. 다만 화염 **저항 효과**를 가진 몹은 A11-06 이다. 플레이어 `is_on_fire` 조건 값은 PR #162·M20 검토 뒤 `LootEntities.PlayerView` 가 `PlayerFire` 를 읽는다(추가 지적 없음).

---

## 항목

### A11-01 [높음] 가루눈 냉각(ticksFrozen)이 어디에도 없다

- 바닐라(`btn.m_` = aiStep, 오프셋 678 "freezing" 구간): 서버에서 살아 있는 생물이 **가루눈 안(`isInPowderSnow`)이고 얼 수 있으면(`canFreeze`: `freeze_immune_entity_types` 가 아니고 가죽이 아닌… 방어구 가림 규칙) 매 틱 `ticksFrozen = min(140, +1)`, 아니면 `max(0, −2)`**. 그 뒤 얼음 이동 속도 수정자를 지우고 다시 건다(`removeFrost`·`tryAddFrost`, 얼린 비율만큼 이동 속도 감소). **40틱마다 완전히 얼었으면(`ticksFrozen ≥ 140`) `freeze` 피해**를 입힌다 — 생물 1, `freeze_hurts_extra_types`(스트라이더·블레이즈·마그마 큐브)는 ×5(`btn.a(brk,F)` 안 `is_freezing` 분기). 불이 붙으면 얼음이 0 으로 풀린다(이 부분만 `Entity.baseTick` 에 있다).
- Furnace: 가루눈 블록의 끼임(`PowderSnowBlock.entityInside`)과 `isInPowderSnow` 플래그, 불 끄기, 해골의 스트레이 변환용 타이머까지는 있다. 하지만 **`ticksFrozen` 을 올리는 곳이 없다** — `Entity.java:386` 은 불 붙을 때 0 으로 지우기만 하고(`DATA_TICKS_FROZEN`), `LivingEntity.aiStep`(`LivingEntity.java:1166`)·`PlayerVitals.tick` 에 냉각 구간이 없다. `DamageType.FREEZE` 는 사용처 0 곳. 얼음 이동 속도 수정자도 없다.
- 관측 차이: 가루눈에 오래 서 있어도 푸른 화면 가장자리와 하트 얼음(클라이언트가 동기화된 `ticksFrozen` 으로 그린다)이 없고, 느려지지 않으며, 얼어 죽지 않는다. 눈 지대 동물(북극곰·염소는 면역 태그)·가죽 장화 장비·스트라이더/블레이즈 ×5 피해가 모두 의미가 없다. 가죽 장화로 가루눈 위를 걷는 것(이미 구현)과 짝인 기능이라 한쪽만 있다.
- 재현: `A11HazardReproTest.mobInPowderSnowAccumulatesTicksFrozen`(NBT `TicksFrozen` 기대 100, 실제 0).
- 제안: ① 몹: `LivingEntity.aiStep` 끝에 바닐라 순서대로 `tickFrozen`(증가·감소, 속도 수정자 `minecraft:powder_snow`, 40틱 피해 ×5 종류), 얼 수 있는지 `canFreeze`(방어구 중 `freeze_immune_wearables` 하나라도 입으면 면역 — 가죽 장화·모자·바지·튜닉)와 `freeze_immune_entity_types`. ② 플레이어: `PlayerVitals` 쪽에 같은 상태를 두고 `DATA_TICKS_FROZEN` 갱신 패킷과 속도 수정자 속성 패킷을 보낸다. 게임 규칙 `freezeDamage` 는 이미 있다. **모델: Opus high 권장**(몹·플레이어 두 경로, 속성 패킷, 태그 3개). 크기: 중.

### A11-02 [높음] 플레이어가 블록 속에서 질식하지 않는다

- 바닐라(`btn.aw` baseTick 앞 구간): 살아 있고 서버면 `Entity.isInWall()`(눈 높이 `폭×0.8`×`1e-6` 상자가 질식 블록의 충돌 모양과 겹침, `noPhysics` 면 거짓)이면 `in_wall` 1 피해를 **플레이어에게도** 입힌다(`cmx` 는 baseTick 을 덮어쓰지 않는다). 모래·자갈 더미에 파묻히거나 설치한 블록이 머리에 걸리거나 피스톤에 끼면 10틱마다 1 피해.
- Furnace: 몹은 `LivingEntity.baseTick`(`LivingEntity.java:1070`)에 있지만 플레이어는 `LivingEntity` 가 아니고, `PlayerVitals`·`Player`·`Region` 어디에도 `isInWall`/`IN_WALL` 사용처가 없다(`PlayerSleeping` 의 `suffocating` 은 잠자리 검사). `/tp` 로 블록 속에 넣거나 떨어지는 모래에 묻혀도 피해가 없다.
- 재현: `world/A11SuffocationReproTest`(생존 플레이어 눈 높이에 돌 → 60틱 뒤 체력 20 그대로, 바닐라는 감소).
  > **정정(2026-11-11):** 눈 칸만 막으면 바닐라도 `Player.updatePlayerPose` 가 수영 자세(높이 0.6)로 바꿔 눈이 빠지므로 피해가 없다. 회귀 시험 `world/A11SuffocationTest` 는 발·눈 두 칸을 막는다.
- 제안: `Region` 의 플레이어 틱에서 소유 region 스레드가 눈 위치 상자와 질식 블록을 `LivingEntity.isInWall` 과 같은 식으로 검사해 `p.vitals().hurt(DamageType.IN_WALL, 1.0F)`(`noPhysics` = 관전자는 제외; 창조 모드 면제는 `hurt` 가 이미 처리). `LivingEntity.isInWall` 을 정적 함수로 빼서 두 곳이 같이 쓰게 한다. **모델: Sonnet medium**, 크기: 작음.

### A11-03 [높음] `fall_damage_immune` 종류가 낙하 피해를 입는다

- 바닐라: `LivingEntity.calculateFallDamage`(`btn.e(FF)I`)의 **맨 앞**이 `type.is(fall_damage_immune)`(`awi.o`) 이면 0 이다. `Entity.isInvulnerableTo`(`bsr.b`)에도 `is_fall` 피해(`awg.m`)와 같은 태그 검사가 있어 `hurt` 가 false 를 돌려준다(피해 소리·표시도 없음). 태그 16종(`registries/tags-1.21.1.txt:261`): 철 골렘·눈 골렘·셜커·알레이·벌·블레이즈·고양이·닭·가스트·팬텀·마그마 큐브·오셀롯·앵무새·박쥐·위더·브리즈 등.
- Furnace: `Entity.causeFallDamage`(`Entity.java:860`)는 태그를 보고 `false` 를 돌려주지만 **`LivingEntity.causeFallDamage`(`LivingEntity.java:1042`)가 그 결과(`flag`)를 무시하고 `calculateFallDamage`(`LivingEntity.java:1037`, 태그 검사 없음)로 직접 피해를 준다.** 박쥐·가스트·팬텀·앵무새는 각자 `causeFallDamage` 를 덮어써 우연히 안전하다. 나머지 12종은 10칸 낙하에 7 피해: 닭(체력 4)·눈 골렘(4)이 죽고, 고양이·오셀롯·벌(10)이 7 을 잃는다. 높은 곳에서 떨어지는 닭·고양이·철 골렘은 바닐라에서 늘 무사하다. `Entity.isInvulnerableTo`(`Entity.java:824`)에는 `is_fall` 분기 자체가 없다.
- 재현: `A11HazardReproTest.fallDamageImmuneTypesTakeNoFallDamage`(태그를 돌며 `causeFallDamage(10,1)` — 12종 실패 목록을 메시지에 찍는다).
- 제안: `calculateFallDamage` 맨 앞에 `if (typeInTag("minecraft:fall_damage_immune")) return 0;`, `Entity.isInvulnerableTo` 에 `source.isFall() && typeInTag(fall_damage_immune)`. **모델: Sonnet low**, 크기: 몇 줄.

### A11-04 [높음] 전도체의 힘이 호흡을 주지 않는다(플레이어·몹)

- 바닐라: `MobEffectUtil.hasWaterBreathing`(`bsa.c(btn)`) = **물숨쉬기(`bsb.m`) 또는 전도체의 힘(`bsb.C`)**. `btn.aw` 익사 구간이 이 함수를 쓴다(오프셋 278).
- Furnace: `PlayerVitals.java:1234` 와 `LivingEntity.java:1072` 가 `WATER_BREATHING` 만 본다. 전도체(`ConduitBlockEntity`)는 `CONDUIT_POWER` 만 주므로 **전도체의 힘 아래에서도 숨이 줄어 익사한다**. 수중 신전·해저 기지에서 전도체의 핵심 기능이 빠져 있다.
- 재현: `A11HazardReproTest.conduitPowerStopsDrowning`(전도체의 힘 400틱 물속 → 체력 13.3, 바닐라 20).
- 제안: 호흡 판정을 한 함수(`hasWaterBreathing`)로 모아 두 곳이 쓰게 한다. **모델: Sonnet low**, 크기: 몇 줄. A11-09 와 같은 PR 로.

### A11-05 [중간] 엔티티 틱 범위가 바닐라보다 한 칸 좁다 (A07-07 재확인)

- 바닐라: 플레이어 시뮬레이션 티켓 레벨 = `max(0, 31 − sim)`(`aqk.g()`, `sim` 은 `aqs` 생성자가 `aqk.b(int)` 에 그대로 넘긴다). 청크 레벨 = 티켓 + 체비셰프 거리. `ENTITY_TICKING` 은 레벨 ≤ 31(`aqa.a(aql.d)`), `BLOCK_TICKING` 은 ≤ 32. → **엔티티는 체비셰프 거리 ≤ sim, 블록(블록 엔티티·예약 틱·블록 이벤트)은 ≤ sim + 1**. (1차 점검 `first-wave-check.md` 의 같은 결론. 코디네이터 메모의 "엔티티가 sim+1" 은 블록 쪽 값이다.)
- Furnace: 엔티티 틱 판정이 블록 엔티티 판정과 같은 함수다 — `RegionLevel.java:294` 의 `entityTicking = … blockEntityTicking.test(k)` ← `Region.blockEntityTicking`(`Region.java:1573`)의 `range = max(sim, 2) − 1`. 즉 **엔티티 ≤ sim − 1**, 바닐라보다 한 칸 좁다(블록은 ≤ sim + 1 이어야 하니 두 칸 좁다 — A07-02).
- 관측: 시뮬레이션 거리 4 에서 플레이어로부터 청크 4 칸 떨어진 몹·아이템·탈것이 멈춘다(바닐라는 돈다). 낮은 시뮬레이션 거리(서버가 흔히 쓰는 설정)일수록 비율이 크다. 가장자리 몹이 갑자기 얼어붙고 AI·낙하·불·효과 틱이 멈춘다.
- 재현: `furnace-test/A11EntityTickRangeReproTest`(sim 4, 갑옷 거치대 `tickCount` — 기대 {2,3,4: 틱, 5,6: 멈춤}, 실제 4 도 멈춤).
- 제안·충돌: 엔티티(≤ sim), 블록(≤ sim + 1), 스폰·무작위 틱(C1-01)을 **세 개의 판정 함수로 나눈다**. `Region.java` 의 이 영역은 A07-02(블록 엔티티 ≤ sim+1)·A10-06/C1-01(스폰 판정 `naturalSpawningAllowed`)이 같이 건드리므로 **A07·A10 수정 PR 이 병합된 뒤 이 항목을 그 구조에 맞춰 얹는다**(`first-wave-check.md` 표의 충돌 주의). 또한 엔티티 틱 판정이 넓어지면 추적(`trackEntities`)·시험 `RegionTickBench` 의 기대값도 같이 본다. **모델: Sonnet medium**, 크기: 작음(판정 함수 하나 + 시험).

### A11-06 [중간] 화염 저항 효과를 가진 몹이 불·용암 피해를 그대로 입는다

- 바닐라: `LivingEntity.hurt`(`btn.a(brk,F)`) 앞부분에서 `is_fire`(`awg.i`) 피해이고 `hasEffect(FIRE_RESISTANCE)` 면 `false` 를 돌려준다(피해 없음, 표시·소리 없음). 플레이어 경로(`PlayerVitals.isInvulnerableTo`)는 이미 같다.
- Furnace: `LivingEntity.hurtByInner` 에도 `Entity.isInvulnerableTo` 에도 화염 저항 검사가 없다(`FIRE_RESISTANCE` 사용처는 토템 부여·마녀 마시기·플레이어 경로뿐). 화염 저항 물약을 마신 좀비·말·주민이 용암 속에서 4 피해와 불 피해를 계속 입는다. 화염 저항 마녀의 불 방어 이득 등 몹 쪽 사용 장면이 전부 무의미하다.
- 재현: `A11HazardReproTest.fireResistanceMobIgnoresFireDamage`(`hurt` 가 true, 체력 20 → 19).
- 제안: `hurtByInner` 앞부분에 `source.fire() && hasEffect(FIRE_RESISTANCE)` → `false`. **모델: Sonnet low**, 크기: 몇 줄(A11-03 과 같은 PR).

### A11-07 [중간] 창조 모드 플레이어의 직접 공격이 `Invulnerable` 몹을 못 뚫는다

- 바닐라: `Entity.isInvulnerableTo`(`bsr.b`)의 `invulnerable` 항은 `!source.is(bypasses_invulnerability) && !source.isCreativePlayer()` 일 때만 참이다(`brk.h()` = 직접 엔티티가 `abilities.instabuild` 인 플레이어). 맵 제작자가 `Invulnerable:1b` 몹·거치대를 창조 모드로 쳐서 지우는 흔한 경로.
- Furnace: `Entity.isInvulnerableTo(Damage)`(`Entity.java:824`)는 피해 원인을 보지 않는다. 같은 호출이 `PlayerMelee` 에서 `new Damage("player_attack", …)` 로 오므로 창조 모드여도 막힌다. 생존 모드가 막히는 것은 맞다.
- 재현: `A11CreativeInvulnerableReproTest.creativePlayerHurtsInvulnerableMob`(생존은 통과해 대조).
- 제안: `Damage` 에 "직접 원인이 창조 모드 플레이어" 칸을 하나 더하거나(`withCreativeSource`), `hurtBy(…attacker…)` 경로에서 `attacker`(직접 원인)를 `isInvulnerableTo` 에 넘긴다. 투사체의 직접 엔티티는 플레이어가 아니므로 화살은 여전히 막힌다(바닐라와 같음). **모델: Sonnet medium**, 크기: 작음.

### A11-08 [중간] 몹이 든 무기의 마법 부여(날카로움·밀치기·화염)가 반영되지 않는다

- 바닐라 `Mob.doHurtTarget`(1.21.1): `EnchantmentHelper.modifyDamage(level, getWeaponItem(), target, source, f)` 로 날카로움·강타·해충 강타 등을 더하고, `getKnockback` 이 `modifyKnockback`(밀치기 마법) 을 더하며, 맞춘 뒤 `doPostAttackEffects`(화염 속성 점화 …)를 부른다. 몹은 스폰 때 마법 부여 무기를 받는다(`MobSpawnEnchantTest` 로 구현됨).
- Furnace `Mob.doHurtTarget`(`Mob.java:763`): 주석에 `TODO(인챈트 효과·무기 후처리)` 가 그대로 있다. 공격력 속성(+ 무기 속성 수정자)만 쓴다. 가시는 `EnchantEffects.thorns` 로 구현돼 있다.
- 재현: `A11HazardReproTest.mobWeaponSharpnessAddsDamage`(일반 철 칼 8.0, 날카로움 V 8.0 — 기대 11.0).
- 제안: 플레이어 근접(`PlayerMelee`)이 이미 가진 날카로움·강타·해충 강타·화염·밀치기 계산을 `EnchantEffects` 로 일반화해 몹도 같이 쓴다. 활·쇠뇌 몹의 화살 마법(힘·화염·밀치기)은 이미 `AbstractArrow.firedFromWeapon` 으로 있다. **모델: Sonnet high**, 크기: 중.

### A11-09 [낮음] 물숨쉬기·창조 모드 플레이어는 물속에서 공기가 차오르지 않는다

> **정정(2026-11-11, A11 높음 수정 PR):** 바이트코드를 다시 보니 반대다. `btn.aw` 오프셋 308 `ifeq 477` → 515 `goto 541` 로, 눈이 물속이면 숨을 쉴 수 있어도 채움 가지(518)를 건너뛴다. 플레이어(`PlayerVitals`)가 바닐라와 같았고 **몹(`LivingEntity.baseTick`)이 물속에서 공기를 채우던 것이 차이**였다 — 몹 쪽을 고쳤다(`A11HazardTest.waterBreathingDoesNotRefillAirUnderwater`).

- 바닐라 `btn.aw`: 눈이 물속이어도 `hasWaterBreathing`(A11-04) 또는 플레이어의 `abilities.invulnerable` 이면 공기를 줄이지 않는 대신 `else if (air < max) air = increaseAirSupply(air)` 로 틱당 +4 를 채운다.
- Furnace 몹(`LivingEntity.java:1079`)은 같지만 `PlayerVitals.java:1233~1241` 은 `if (물속) { if (물숨쉬기·무적이 아니면) 줄이기 }` 구조라 **물속에 있는 한 공기가 늘지 않는다**. 물숨쉬기 물약을 마셔도 이미 줄어든 공기 방울이 물을 나올 때까지 그대로이고, 창조 모드가 물속에서 방울을 못 채운다.
- 재현: `A11HazardReproTest.waterBreathingPlayerRefillsAirUnderwater`(200 → 20틱 뒤 기대 280, 실제 200).
- 제안: `else if` 로 채움 가지 추가(A11-04 와 같은 PR). **모델: Sonnet low**.

### A11-10 [낮음] 슬로 폴링·부양 효과가 몹의 낙하 거리를 지우지 않는다

- 바닐라 `btn.m_`(aiStep) 오프셋 591~611: 효과 `SLOW_FALLING`(`bsb.B`) 또는 `LEVITATION`(`bsb.y`)이 있으면 **매 틱 `resetFallDistance()`**(생물 전체). Furnace 는 `PlayerVitals.tick` 에만 있다(`PlayerVitals.java:1245`). 슬로 폴링을 받은 몹(`/effect give`·화살·물약)이 천천히 내려와도 착지에서 전체 거리의 낙하 피해를 입는다(재현 시험은 31칸 낙하 후 체력 10 → 0).
- 재현: `A11HazardReproTest.slowFallingMobTakesNoFallDamage`.
- 제안: `LivingEntity.aiStep` 에 두 줄. **모델: Sonnet low**.

### A11-11 [낮음] `baseTick` 안에서 효과 틱이 무적 시간 감소보다 먼저 돈다

- 바닐라 `btn.aw` 순서: …익사·질식 → `hurtTime--` → `invulnerableTime--` → 사망 틱 → 마지막으로 때린 쪽 기록 갱신 → **`tickEffects`(`btn.eo`, 오프셋 765)**. Furnace `LivingEntity.baseTick`(`LivingEntity.java:1065`)은 `super.baseTick()` 바로 뒤에 `tickEffects()` 를 부르고 무적 시간 감소는 그 뒤다. 그래서 효과가 입히는 피해(독·위더)가 **무적 시간을 한 틱 늦게 본다**: 맞은 뒤 10번째 틱에 들어가야 할 위더 피해가 11번째 틱에 들어간다. 효과 피해 한 번이 한 틱씩 밀리므로 관측 차이는 작지만 같은 틱에 여러 소스가 겹칠 때 체력 값 순서가 달라진다. 플레이어(`PlayerVitals.tick`)는 순서가 맞다.
- 재현: `A11HazardReproTest.effectDamageSeesInvulnerabilityAfterDecrement`(위더 V, 기대 체력 5.0, 실제 6.0).
- 제안: `tickEffects()` 호출을 `baseTick` 끝(마지막으로 때린 쪽 기록 뒤)으로 옮긴다. 효과 해제로 `setHealth` 등이 호출되는 흐름과 `isAlive()` 가드를 바이트코드와 함께 다시 본다. **모델: Sonnet medium**, 크기: 작음(순서만, 하지만 회귀 시험이 많이 걸린다).

### A11-12 [낮음] 플레이어 낙하 피해가 `safe_fall_distance`·`fall_damage_multiplier` 속성을 읽지 않는다

- 바닐라 `calculateFallDamage` 는 속성 `generic.safe_fall_distance`(기본 3)·`generic.fall_damage_multiplier`(기본 1)를 읽는다. 몹(`LivingEntity.java:1037`)은 이미 읽는다. 플레이어 `PlayerVitals.fallDamage`(`PlayerVitals.java:1163`)는 상수 `SAFE_FALL_DISTANCE = 3.0f` 와 점프 강화 효과만 쓴다. 점프 강화는 바닐라에서 같은 속성에 수정자를 거는 방식이라 결과가 맞지만, **`/attribute`·아이템 수정자·데이터 팩·플러그인이 속성을 바꾸면 플레이어에게만 안 먹는다.**
- 재현: `A11HazardReproTest.playerFallDamageReadsSafeFallDistanceAttribute`(안전 거리 10 으로 올리고 8칸 낙하 → 기대 피해 0, 실제 5).
- 제안: `fallDamage` 가 `player.attributes()` 의 두 속성을 쓰게 한다(점프 강화 항은 속성 수정자로 옮기거나 중복되지 않게). **모델: Sonnet medium**.

### A11-13 [낮음] 몹의 무적 시간 우회 판정이 `bypasses_invulnerability` 로 되어 있다

- 바닐라 `LivingEntity.hurt` 의 `invulnerableTime > 10` 우회는 태그 **`bypasses_cooldown`**(`awg.e`)이다. 바닐라 1.21.1 데이터에는 이 태그 파일이 없어 늘 거짓이다(데이터 팩이 채울 수 있게 만든 훅).
- Furnace `LivingEntity.java:247` 은 `source.bypassesInvulnerability()`(`out_of_world`·`generic_kill`)를 쓴다. 그래서 월드 밖으로 떨어진 몹이 무적 시간 없이 **매 틱 4 피해**를 입는다(바닐라는 `amount ≤ lastHurt` 이므로 약 10틱마다 한 번). 플레이어는 이미 바닐라와 같다. `/kill` 은 `Float.MAX_VALUE` 라 어느 쪽이든 죽는다.
- 재현: `A11HazardReproTest.outOfWorldDamageObeysInvulnerabilityFrames`(같은 틱 `out_of_world` 4 두 번 → 기대 4, 실제 8).
- 제안: 우회 판정 태그를 `bypasses_cooldown`(비어 있음)으로 바꾸고, 태그 조회가 데이터 팩 태그를 반영하는지 확인. **모델: Haiku low**(한 줄) 또는 A11-03 PR 에 편승.

### A11-14 [낮음] 플레이어 이동 검증이 `step_height` 속성을 읽지 않는다

- 바닐라 서버의 `move(PLAYER, …)`(`handleMovePlayer`)는 플레이어의 `generic.step_height` 속성(기본 0.6)을 쓴다. `PlayerMoveValidator.move`(`PlayerMoveValidator.java:238`)는 `float step = 0.6F; // 수정자 없음` 상수. `/attribute @s minecraft:generic.step_height base set 1` 이나 아이템 수정자로 높은 단차를 오르는 플레이어는 클라이언트가 올라간 자리를 서버가 "moved wrongly"(0.0625 초과)로 되돌린다. 지도 제작자가 쓰는 `step_height` 보너스에서 오탐이 난다. 속성이 기본값인 일반 플레이는 영향 없음.
- 재현 시험은 없다(Region 시험 틀에서 속성 + 단차 지형이 필요 — 후속). 코드 대 바이트코드 비교로 확인.
- 제안: `p.attributes().value(Attribute.STEP_HEIGHT)` 를 넘긴다. **모델: Sonnet low**.

### A11-15 [참고] 플레이어 이동 검증은 `stuckSpeedMultiplier` 를 적용하지 않는다

- 코드 주석이 이미 밝힌 가정("거미줄 등… 플레이어에게 아직 없다 — 그 안에서는 더 관대하다"). 바닐라 서버는 거미줄·달콤한 열매·가루눈 안에서 `move` 의 이동량에 `stuckSpeedMultiplier` 를 곱해 그 안에서 더 빨리 걷는 클라이언트를 되돌린다. Furnace 는 되돌리지 않는다. 보안(속도 핵) 쪽이라 **A02 관할**로 넘긴다. 정상 플레이 오탐은 없다.

### A11-16 [참고] 피해 종류가 바닐라와 다른 발사체

- 라마 침 `LlamaSpit.java:58` 은 `mob_projectile`(바닐라 `brl.b(bsr, btn)` = `spit`), 화염구(`LargeFireball.java:29`·`SmallFireball.java:18`)는 항상 `fireball`(바닐라 `brl.a(cnj, bsr)` 는 원인 엔티티가 없으면 `unattributed_fireball`). 사망 메시지 번역 키(`mob`/`mob`, `fireball`/`onFire`)와 `damage_type` 술어·`/damage`·플러그인 피해 종류에서만 보인다. 사용처 0 곳이던 `DamageType` 들: `freeze`(A11-01)·`spit`·`unattributed_fireball`·`player_explosion`.

### A11-17 [참고] 몹이 불사의 토템을 쓸 때 `ITEM_INTERACT_FINISH` 게임 이벤트가 나간다

- 바닐라 `btn.g(brk)` 의 `dxz.C`(ITEM_INTERACT_FINISH)는 `instanceof ServerPlayer` 분기 **안**이다(오프셋 86~129). Furnace `LivingEntity.java:799` 는 몹도 낸다. 몹이 토템을 쓰는 경우가 거의 없어(명령으로 장비를 준 경우뿐) 관측 영향은 진동 센서뿐.

---

## 충돌·순서 주의 (수정 스레드용)

| 항목 | 겹치는 곳 | 순서 |
|---|---|---|
| A11-05 | `Region.java` 틱 판정 함수: A07-02(블록 엔티티 ≤ sim+1), A10-06/C1-01(`naturalSpawningAllowed`·무작위 틱 ≤ sim+1) | A07·A10 수정이 먼저. 판정 함수를 엔티티·블록·스폰 셋으로 나눈 구조 위에 얹는다 |
| A11-03·06·13 | `LivingEntity.hurtByInner`·`Entity.isInvulnerableTo` | 한 PR 로 묶는다(같은 함수) |
| A11-04·09 | 호흡 판정 `PlayerVitals.tick`·`LivingEntity.baseTick` | 한 PR |
| A11-11 | `LivingEntity.baseTick` 순서 | A11-02(몹 질식 위치)와 같은 함수라 같은 PR 이거나 순차 |

## 수정 묶음 제안 (코디네이터용)

| 묶음 | 항목 | 모델·노력치 | 크기 |
|---|---|---|---|
| 1 | A11-03·06·13 (피해 입구 정리) + A11-04·09 (호흡) + A11-10 (낙하 거리) | Sonnet medium | 작음 |
| 2 | A11-02 (플레이어 질식) | Sonnet medium | 작음 |
| 3 | A11-07 (창조 모드 vs Invulnerable) + A11-12·14 (속성) | Sonnet medium | 작음~중 |
| 4 | A11-01 (냉각, 몹+플레이어, 패킷) | Opus high | 중 |
| 5 | A11-08 (몹 무기 마법) | Sonnet high | 중 |
| 6 | A11-05 (엔티티 틱 범위) — A07·A10 수정 뒤 | Sonnet medium | 작음 |
| 7 | A11-11 (효과 틱 순서) | Sonnet medium | 작음 |

## 재현 시험 쓰는 법

`A11-repro/*.java.txt` 의 확장자 `.txt` 를 떼고 아래 폴더에 넣는다.

| 파일 | 위치 | 모듈 |
|---|---|---|
| `A11HazardReproTest` (시험 10개) | `furnace-server/src/test/java/org/domi/furnace/server/entity/` | `:furnace-server:test --tests '*A11HazardReproTest'` |
| `A11CreativeInvulnerableReproTest` (2개, 1개는 통과 대조) | 같은 폴더 | 같음 |
| `A11SuffocationReproTest` (1개) | `furnace-server/src/test/java/org/domi/furnace/server/world/` | 같음 |
| `A11EntityTickRangeReproTest` (1개) | `furnace-test/src/test/java/org/domi/furnace/test/` | `:furnace-test:test --tests '*A11EntityTickRangeReproTest'` |

수정이 들어가면 이 시험들이 통과로 바뀌어야 하고, 통과한 것은 회귀 시험으로 `.txt` 를 떼고 정식으로 옮긴다. 조사 도중 gradle 은 `~/.gradle` 미러 init 스크립트(`-I`)로 Maven Central 429 를 피했다(`furnace-build-env` 메모).
