# 엔티티 활성 범위(D11) — 대조 기록 (2026-11-14)

- 기준 원문: Paper 1.21.1(커밋 33ff3dff) `patches/server/Entity-Activation-Range-2.0.patch`, `Add-more-entities-to-activation-range-ignore-list.patch`,
  `Fix-items-not-falling-correctly.patch`, `Remove-wall-time-unused-skip-tick-protection.patch`. Spigot 쪽 원래 코드는 이 패치의 `-` 줄과,
  하드포크 직후 Paper main(`8a2c2db2a^`·`47c06357f` 의 `paper-server/src/main/java/org/spigotmc/ActivationRange.java`, `paper-server/patches/sources/.../*.java.patch`)으로 읽었다.
  동작 비교용으로만 읽고 코드는 옮기지 않았다(GPLv3 결정 대기).
- 1.21.1 vs 1.21.3: 1.21.3(`8a2c2db2a` "Fix item EAR ticks")에서 활성 엔티티 1/4 건너뛰기를 지우고 아이템 중력 틱을 늘 활성으로 했다. Furnace 는 이 둘을 따랐다
  (user-todo `2026-11-14-entity-activation-range.md` E1).
- 아이템 중력 틱: 바닐라 `ItemEntity.tick` 은 `tickCount` 를 올린 뒤 `(tickCount + id) % 4 == 0` 을 본다. `checkIfActive` 는 올리기 전에 불리므로 `+1` 을 더해 본다.
- `movement == getDeltaMovement()`(참조 비교)는 Furnace 에서 `move(SELF, vx(), vy(), vz())` 값 비교로 옮겼다. 끈끈한 칸(stuck) 갈래는 바닐라가 벡터를 새로 만들므로 거짓으로 둔다.
- Paper 의 `checkIfActive` 는 `isTemporarilyActive = false` 를 늘 활성 갈래(새 엔티티·묶임 등) **뒤**에서 지운다 — 잠깐 깨운 바로 다음 틱에 플레이어가 끈으로 묶으면 묶인 동안 제 속도 이동이 계속 0 이 된다. Paper 와 같게 뒀다.
- Furnace 박쥐는 `PathfinderMob`(바닐라는 `AmbientCreature`)이지만 둘 다 ANIMAL 이라 분류는 같다. Furnace `EnderDragonPart`·`Player` 는 Entity 가 아니라 늘 활성 목록에서 빠져도 같다.
- 증명: 기존 장면 9개는 활성 범위를 끈 채 돌아 금 파일이 바뀌지 않았다(TickScene.entityActivation 기본 OFF). 새 장면 `activation` 은 켠 채 금 파일을 만들었고,
  같은 장면을 끈 것과 비교해 처음 200틱(새 엔티티 면역)은 같고 그 뒤로 달라짐을 확인한다(`SceneLibraryTest.activationOffMatchesOnUntilImmunityEndsThenDiffers`).
