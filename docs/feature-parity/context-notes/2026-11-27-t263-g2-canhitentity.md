# T-263 G2 — 투사체 `canHitEntity` 짝과 Bukkit 구현에 잇는 줄의 현재 한계 (2026-11-27)

## canHitEntity (엔티티 스레드 확인 요청)

- Paper `AbstractProjectile.canHitEntity(Entity)`(javap, Paper 1.21.1 userdev jar)는 `getHandle().canHitEntity(((CraftEntity) e).getHandle())` 한 줄이다.
  `getHandle()` 반환형이 `Projectile` 이고 호출이 가상이므로, 실제로는 **하위 클래스의 재정의**가 불린다.
- 바닐라 1.21.1 에서 `canHitEntity` 를 재정의하는 투사체(바닐라 jar javap 확인 — ThrowableProjectile·LlamaSpit·FireworkRocketEntity 는 재정의 없음. 전체 껍데기가 따로 껍데기를 가진 클래스 = `LinkG2Census` 가 짝을 낸 클래스):
  `AbstractArrow`, `AbstractHurtingProjectile`, `FishingHook`, `ShulkerBullet`, `windcharge.AbstractWindCharge`(그리고 기본 `Projectile`).
- Furnace:
  - 호환 계층 `org/bukkit/craftbukkit/entity/AbstractProjectile.canHitEntity` 는 아직 `UnsupportedFeature` 를 던진다(구현 없음).
  - 서버 `entity/Projectile.canHitTarget`(바닐라 `Projectile.canHitEntity` 이식 — 집을 수 있음·소유자 탈것 규칙)을 재정의하는 곳은 `WindCharge`·`AbstractWindCharge` 뿐이다.
    화살·불덩이류·낚시찌·셜커 탄환의 재정의 몫이 다른 자리(충돌 처리 안)에 들어가 있는지는 **확인 안 함(가정 아님 — 엔티티 스레드가 볼 것)**.
- 그래서 Bukkit `canHitEntity` 를 구현할 때는 하위 클래스마다 바닐라 재정의와 같은 판정이 되도록 서버 `canHitTarget` 재정의를 맞추는 것이 먼저다.
  그 전에 NMS 껍데기 6개를 이 Bukkit 메서드에 잇는 줄은 만들지 않는다.

## Bukkit 구현에 잇는 줄(G2)이 지금 얻는 것

- 후보 세기(`LinkG2Census`, `inferLinks` 가 `build/reports/link-infer/g2-census.md`·`g2-candidates.tsv`): 이름·형식 짝 211, Paper 몸통이 그 NMS 멤버를 그대로 부르는 것 122(엔티티 105).
- Furnace 손 Craft 구현이 **실제로 동작하는** 짝: 처음 셈(21, 그대로 위임 11)은 던지는 손 Craft 자리(`UnsupportedFeature`)도 "있음"으로 셌다 — 고침(몸통이 `org.domi.furnace…Unsupported*` 를 만들면 살아 있지 않음).
  고친 뒤 14, 그중 그대로 위임은 5(읽기 1 `AbstractHorse.isTamed`, 쓰기 4 `Entity.setInvulnerable`·`ArmorStand.setMarker`·`setSmall`·`ServerBossEvent.setVisible`).
- 증명 길이 없다: 엔티티 G2 줄은 `getBukkitEntity` 를 거치는데 Furnace 는 `EntityType#create` 로 만든 월드 밖 엔티티에서 이것을 "아직 월드에 없음"으로 던진다
  (정답기 표의 `Shulker.canBeCollidedWith` 와 같은 이유). 월드 밖 정답기(O3)로는 못 보고 월드 안 장면 정답기(O4, 범위 밖)가 필요하다.
  쓰기는 설계상 늘 검토 대상(Bukkit setter 의 범위 검사·이벤트).
- 결론: G2 는 후보 보고서로만 두고 연결 문법에 Craft 대상을 더하지 않는다. 다음은 S4(블록 상태 관용구 + O2).
