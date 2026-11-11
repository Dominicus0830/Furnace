# A12-04 이동·피해 통계 21종 (2026-11-11)

바닐라 `ServerPlayer.checkMovementStatistics`(aqv.b)·`checkRidingStatistics`(aqv.q)·`Player.jumpFromGround`(cmx.ff)·`Player.causeFallDamage`(cmx.a(FFbrk))·`Player.actuallyHurt`(cmx.f)·`LivingEntity` 의 저항·흡수(btn)를 `javap -c -p` 로 대조해 옮겼다(통계 필드: avz.p~D 이동, E jump, H/I dealt 흡수·저항, J taken, L absorbed, M resisted, t fall).

- 이동(`PlayerVitals.checkMovementStatistics`): 탈것을 타고 있으면 아무것도 안 하고, 아니면 **한 분기만** — 헤엄(`player.swimming`, 3차원) → 눈이 물속(3차원) → 물 안(수평) → 오르는 중(dy>0 일 때 `Math.round(dy*100.0)`) → 땅 위(수평, 달리기 → 웅크리기 → 걷기) → 겉날개(3차원, 제한 없음) → 공중(수평, 25 **초과**만). 거리는 `Math.round((float)sqrt(..)*100.0f)`, 헤엄·물속·물 안·땅은 `> 0` 일 때만 센다. 허기 소모는 같은 분기에서 같이 낸다.
- 탑승: `Player.rideTick` 전후 위치 차를 `checkRidingStatistics` 에 넘긴다(수레·배·돼지·말류·스트라이더 순서의 instanceof). 바닐라 `handleMoveVehicle` 의 `checkMovementStatistics` 호출은 탄 플레이어라 아무것도 안 하므로(위 분기 첫 줄) VehicleMoves 에는 연결하지 않는다.
- `jump`: 땅에서 위로 떠나는 이동 패킷마다 1(허기 소모 앞).
- `fall_one_cm`: `fallDamage` 맨 앞(비행 가능이면 그 앞에서 끝) — 충격 높이 규칙으로 자르기 **전** 거리가 2 이상일 때 `Math.round(거리*100.0)`.
- 피해: 플레이어가 맞으면 `damage_resisted`(저항 효과가 줄인 양), `damage_absorbed`(흡수 체력이 막은 양), `damage_taken`(체력이 깎인 양 — 전부 흡수되면 안 오름). 모두 `Math.round(값*10)`, 0 보다 크고 3.4028235E37 보다 작을 때만. 몹이 맞으면 원인 엔티티가 플레이어일 때 그에게 `damage_dealt_resisted`·`damage_dealt_absorbed`(플레이어가 맞을 때는 때린 쪽에 안 준다 — 바닐라 `Player.actuallyHurt` 가 `LivingEntity` 것을 안 부름).
- 플러그인 `EntityDamageCalculateEvent` 경로는 단계 input/output 차이로 같은 통계를 낸다.
- 구현: 통계는 `PlayerVitals` 잠금 안에서 `queueStat` 으로 모았다가 잠금 밖 `flushStats` 에서 `awardStat`(점수판 통계 기준은 `awardStat` 이 이미 갱신). 다른 region 의 플레이어에게는 `runOnOwner`.
- 가정: `Player.swimming`(Entity 수영 플래그)·`forcedCrouch` 가 바닐라 `isSwimming`·`isCrouching` 과 같다고 본다(수영 자세 갱신은 별개 작업).
- 시험: `entity/MovementDamageStatsTest`(19개) — 재현 4개(A12 보고서) + 계단·사다리·공중 25·낙하 2 미만·겉날개·헤엄 우선순위·탑승 5종·점수판 연동·몹 대상.

## 독립 검토 반영(PR #219)
- R1 침대 착지: 바닐라 `BedBlock.fallOn` 은 `Block.fallOn(거리 * 0.5F)`(배수 1) — 낙하 피해·`fall_one_cm` 모두 거리 절반 기준. 기존 침대 0.5 배수 식이 틀려 `PlayerVitalsTest.landingBlockChangesMultiplier` 기대값을 18.0 으로 고쳤다.
- R2 관전자는 `onClimbable` 이 항상 false → `climb_one_cm` 안 오름.
- **후속(이번엔 안 고침, 가정):** R3 물 판정(바닐라 `isInWater`/`isEyeInFluid` 는 틱에서 갱신한 값인데 Region 이동 경로는 이동 뒤 위치로 다시 계산 — PR 전부터), R4 웅크림(바닐라는 `Player.tick` 의 자세 갱신 값이라 웅크림을 누른/뗀 틱의 패킷 하나가 반대로 셈 — 지금은 실시간 `sneaking`), N1 되돌려진(moved wrongly) 패킷에도 바닐라는 jump 통계를 줌.
