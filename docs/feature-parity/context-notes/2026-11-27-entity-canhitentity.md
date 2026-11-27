# 투사체 canHitEntity — 바닐라·Paper 재정의 이식 (2026-11-27, 엔티티 스레드)

근거: 바닐라·Paper 1.21.1 javap. 출발점: `2026-11-27-t263-g2-canhitentity.md`(PR 657).

- 서버 `Projectile.canHit(Object)` 하나가 바닐라 `canHitEntity` 몸통이다. 대상은 찾기가 넘기는 Targetable(생물·플레이어·광산 수레 표지) 또는 그 밖의 Entity.
  공개 입구 `canHitEntity(Rider)`(Paper 는 public), 안쪽 찾기는 `canHitTarget(Targetable)`(final, 같은 몸통).
  - 기본(Projectile): 살아 있고 맞출 수 있음 → **Paper**: 소유자·대상이 둘 다 플레이어이고 소유자가 대상을 못 보면(플러그인 숨김) 거짓 → 소유자와 같은 뿌리 탈것이면 벗어나기 전엔 거짓.
  - AbstractArrow: 이미 꿰뚫은 엔티티(piercingIgnoreEntityIds) 제외.
  - AbstractHurtingProjectile·ShulkerBullet: 대상이 noPhysics 면 제외(셜커 탄환은 전에 shouldHit 으로 있던 것을 옮김, 불덩이류는 없던 것).
  - FishingHook: 보통 규칙 **또는** 살아 있는 아이템 엔티티(소유자 규칙 없음).
  - AbstractWindCharge: 바람 돌격·엔드 수정 제외 후 AbstractHurtingProjectile 규칙.
- 고친 차이:
  - **WindCharge 의 canHitTarget 재정의(던진 뒤 5틱 소유자 제외)를 지웠다** — 바닐라·Paper WindCharge 에는 canHitEntity 재정의가 없다(noDeflectTicks 는 deflect 에만 쓴다 — `canDeflect` 로 그대로 둠).
  - **관통 화살 고리**: 전에는 첫 적중 뒤 늘 멈춰 한 틱에 한 엔티티만 꿰뚫었다. 바닐라는 튕겨 나갈 때만 멈추고 꿰뚫은 엔티티를 빼고 다음을 찾는다 → 한 틱에 길 위 엔티티를 모두.
  - **Paper hitCancelled**: 플러그인이 적중을 취소하면 `hitCancelled`. 관통 화살은 다음 묻기 때 그것을 보고 대상 엔티티를 관통 제외에 넣는다(Paper `AbstractArrow.preHitTargetOrDeflectSelf`) — 취소된 엔티티를 한 번 더 묻고 넘어간다(끝없이 다시 찾지 않음).
- Bukkit `Projectile#canHitEntity`: API `ProjectileState.canHitEntity` → 서버 `canHitEntity`. 대상은 같은 region 이어야 한다(소유 스레드 규칙).
- 남은 차이(이번 범위 밖): Paper 는 **블록** 적중을 플러그인이 취소해도 `hitTargetOrDeflectSelf` 를 부르고 `onHitBlock` 안에서 `hitCancelled` 를 본다. Furnace 는 블록 적중도 취소면 아무것도 안 한다. 엔티티 찾기가 Targetable·광산 수레(화살)만 보는 것(배·액자·투사체 등 다른 맞출 수 있는 엔티티를 못 맞힘)도 그대로다.
