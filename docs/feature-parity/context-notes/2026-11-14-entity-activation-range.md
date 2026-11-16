# 엔티티 활성 범위(D11) — 대조 기록 (2026-11-14)

- 기준 원문: Paper 1.21.1(커밋 33ff3dff) `patches/server/Entity-Activation-Range-2.0.patch`, `Add-more-entities-to-activation-range-ignore-list.patch`,
  `Fix-items-not-falling-correctly.patch`, `Remove-wall-time-unused-skip-tick-protection.patch`. Spigot 쪽 원래 코드는 이 패치의 `-` 줄과,
  하드포크 직후 Paper main(`8a2c2db2a^`·`47c06357f` 의 `paper-server/src/main/java/org/spigotmc/ActivationRange.java`, `paper-server/patches/sources/.../*.java.patch`)으로 읽었다.
  동작 비교용으로만 읽고 코드는 옮기지 않았다(GPLv3 결정 대기).
- 1.21.1 vs 1.21.3: 1.21.3(`8a2c2db2a` "Fix item EAR ticks")에서 활성 엔티티 1/4 건너뛰기를 지우고 아이템 중력 틱을 늘 활성으로 했다. Furnace 는 이 둘을 따랐다
  (user-todo `2026-11-14-entity-activation-range.md` E1).
- 아이템 중력 틱: 바닐라 `ItemEntity.tick` 은 `tickCount` 를 올린 뒤 `(tickCount + id) % 4 == 0` 을 본다. `checkIfActive` 는 올리기 전에 불리므로 `+1` 을 더해 본다
  (Spigot 1.21.3 식). Paper `8a2c2db2a` 는 `+1` 을 뺐지만 같은 시점 ServerLevel 패치 문맥상 checkIfActive 는 여전히 `++tickCount` 앞이라 한 틱 어긋나 보인다 —
  `+1` 을 빼면 받침이 사라진 먼 아이템이 20틱 동안 안 떨어진다(`EntityActivationTest.farItemStartsFallingOnItsFirstGravityTick`, PR #326 검토 R2).
- villagers 범위: `villagers` 키와 `hasAnimalsConfig` 는 Paper 가 더한 것이다. `hasAnimalsConfig` 는 `config.getInt("entity-activation-range.animals")` 로
  **루트 경로**를 읽어 늘 거짓이라 Paper 에서 "animals 를 바꾸면 villagers 도 따른다"는 죽은 규칙이다 → Furnace 도 villagers 기본은 늘 32.
  순수 Spigot 은 주민 종류가 없어 주민이 ANIMAL(animals 범위)이므로, villagers 키 없는 spigot.yml 가져오기만 villagers=animals 로 적는다(검토 R1).
- 같은 이유로 WATER·FLYING_MONSTER 도 Paper 가 더한 종류다(검토 R8): 순수 Spigot 은 물 동물이 ANIMAL(PathfinderMob), 가스트·팬텀이 MISC(Monster 도 PathfinderMob 도 아님).
  키 없는 Spigot 파일 가져오기는 water=animals, flying-monsters=misc 를 적는다(Furnace 기본과 같으면 생략). 분류 차이 하나 더: Spigot 은 `Monster || Slime` 만 MONSTER 라
  호글린·셜커(Enemy 지만 Monster 아님)가 ANIMAL·MISC 쪽인데 Paper 는 `Enemy` 로 MONSTER — Furnace 는 Paper 를 따른다(가져오기로 맞출 수 없는 차이).
- `MinecartHopper.immunize` 는 옮기지 않았다: 광산 수레는 늘 활성 목록이라 Paper 에서도 효과가 없다. `FireworkRocket.inactiveTick` 도 폭죽이 늘 활성이라 필요 없다(검토 R6).
- region 단위 깨우기 몫(검토 R7): 몫 총량이 region 수 × 상한으로 늘고, 합치기 때 흡수되는 쪽 몫이 사라지며 나뉜 새 region 은 0 에서 시작한다(상한 8 이라 8틱 안에 참).
  활성으로 만드는 플레이어를 그 region 의 플레이어로만 봐도 놓치는 엔티티는 없다 — 플레이어 시야 청크는 이웃 구역이 늘 합쳐져 그 플레이어 region 에 있고,
  활성 범위는 `simulationDistance*16-8` 로 잘린다.
- 시험 JVM 은 활성 범위 기본 꺼짐(`EntityActivationOffExtension` 자동 등록 확장, 검토 R4). 운영 기본(켜짐)은 그대로이고 활성 범위 시험만 스스로 켠다.
  RegionLevel 경로는 `RegionEntityActivationTest`(표시·끔·정지 중 건너뜀).
- `movement == getDeltaMovement()`(참조 비교)는 Furnace 에서 `move(SELF, vx(), vy(), vz())` 값 비교로 옮겼다. 끈끈한 칸(stuck) 갈래는 바닐라가 벡터를 새로 만들므로 거짓으로 둔다.
- Paper 의 `checkIfActive` 는 `isTemporarilyActive = false` 를 늘 활성 갈래(새 엔티티·묶임 등) **뒤**에서 지운다 — 잠깐 깨운 바로 다음 틱에 플레이어가 끈으로 묶으면 묶인 동안 제 속도 이동이 계속 0 이 된다. Paper 와 같게 뒀다.
- Furnace 박쥐는 `PathfinderMob`(바닐라는 `AmbientCreature`)이지만 둘 다 ANIMAL 이라 분류는 같다. Furnace `EnderDragonPart`·`Player` 는 Entity 가 아니라 늘 활성 목록에서 빠져도 같다.
- 증명: 기존 장면 9개는 활성 범위를 끈 채 돌아 금 파일이 바뀌지 않았다(TickScene.entityActivation 기본 OFF). 새 장면 `activation` 은 켠 채 금 파일을 만들었고,
  같은 장면을 끈 것과 비교해 처음 200틱(새 엔티티 면역)은 같고 그 뒤로 달라짐을 확인한다(`SceneLibraryTest.activationOffMatchesOnUntilImmunityEndsThenDiffers`).
- 장면 `activation` 덮는 범위(검토 R3): 변이 14건(목표 선택 주기·1/4 건너뛰기·아이템 +1·이동 건너뛰기·구슬 면역·비활성 나이·noActionTime·주민 두뇌·적대 몹 깨우기·
  새 엔티티 200틱·표적 선택·면역 주기 20·생물 면역 1·새끼/사랑 면역 5)을 모두 잡는다. 면역이 이어지는 동안은 면역 틱 수가 겉으로 안 보이므로(끝나자마자 다시 검사해 또 면역)
  새끼·효과는 장면 중(300~400틱)에 끝나게 했고, 걸어 다니면 "움직이는 중" 100틱 면역에 먼저 걸리므로 AI 없는 몹을 바닥에 바로 세웠다(AI 없는 몹은 바닐라에서 안 움직여
  땅 여부가 갱신되지 않는다). noActionTime 은 저장 NBT 에 없어 따로 한 줄을 둔다. 1틱 면역은 2틱 걸음이라 끝나는 틱의 홀짝에 따라서만 보여 효과 길이를 홀짝 섞어 뒀다.
  변이 실행기는 gradle 파일 감시가 되돌린 파일을 못 볼 수 있어 `--no-watch-fs` 로 돌린다.
