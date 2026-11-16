## 2026-11-13 리스너 통째 탈락 막기 + 보호 이벤트 여섯 (T-040a)

근거: `furnace/reviews/opt-review/O13-compat.md` P-O13-06·07, 반론 `wave2-rebuttal-compat.md` 0장 1·2번, TASK 표 T-040a(P0).

## 1. 리스너 통째 탈락 (P-O13-07)
- 원인: `Class.getMethods()`/`getDeclaredMethods()` 는 클래스의 *모든* 메서드 서명을 한꺼번에 푼다. 없는 이벤트 클래스를 쓰는 처리기 하나가 같은 리스너 클래스의 `BlockBreak`·`PlayerInteract` 처리기까지 죽였다(기존 코드는 `NoClassDefFoundError` 를 잡고 그 리스너를 통째 건너뜀).
- 수정: `SimplePluginManager.registerEvents` 가 `NoClassDefFoundError` 를 잡았을 때만 `ListenerFallback`(ASM 으로 클래스 파일을 읽어 `@EventHandler` 메서드의 이름·서술자·priority·ignoreCancelled 를 얻고, 메서드마다 매개변수 형을 따로 올림 → `privateLookupIn(...).findVirtual`)으로 풀리는 처리기만 등록한다. 정상 경로는 그대로(성능 영향 없음). 건너뛴 처리기 이름과 없는 클래스는 한 줄 경고에 남긴다(A-2).
- `getMethods()` 와 같은 가시성: 이 클래스의 모든 메서드(비공개·정적 포함), 부모 클래스의 *공개* 메서드, 자식의 주석 없는 덮어쓰기는 부모 처리기를 가린다. 시험이 이 셋을 확인한다. 인터페이스 default 메서드의 `@EventHandler` 도 클래스 사슬 뒤에 같은 규칙으로 본다(정적·추상은 제외). 부모 클래스·인터페이스의 형은 그 클래스 자신의 로더로 푼다(검토 R4·R5).
- 클래스 파일 읽기는 `Class.getResourceAsStream` — 플러그인 클래스로더가 변환(NMS 심볼 바꾸기) 전 원본 바이트를 주지만 처리기 서술자(이벤트 형)는 변환 대상이 아니다.
- 껍데기 클래스(T-040c)는 이 길과 별개다: 플러그인 *본문*이 `instanceof`·`new`·`getHandlerList()` 로 없는 클래스를 직접 쓰는 경우는 여전히 `NoClassDefFoundError` 다.

## 2. 보호 이벤트 여섯 (P-O13-06)
Paper 원문(`PaperMC/Paper` main, `PistonBaseBlock.java.patch` 와 `paper-api` 이벤트 클래스)과 대조했다. GPLv3 결정 7 은 대기 중이라 동작 비교용으로만 읽고 코드는 옮기지 않았다.
- `BlockPistonEvent`(추상)·`BlockPistonExtendEvent`·`BlockPistonRetractEvent`: **방향 규칙이 Paper 안에서도 둘로 갈린다** — `moveBlocks` 의 이벤트는 `pushDirection`(확장=바라보는 쪽, 수축=반대), 끌 블록이 없는 수축(일반 피스톤)은 바라보는 쪽. 다리가 `blocks().isEmpty()` 로 가른다. 서버(`PistonBaseBlock`)는 두 경우 모두 `dir`=바라보는 쪽을 넘긴다.
- `BlockFormEvent extends BlockGrowEvent`, `BlockSpreadEvent extends BlockFormEvent`: 각자 `HandlerList` 가 있어 서로의 처리기에는 오지 않는다. Furnace API 쪽은 `BlockSpreadEvent extends BlockFormEvent` 라 `BlockForm` 짝이 하위 이벤트를 건너뛴다(`create` 가 null).
- `EntityExplodeEvent`: Furnace API 이벤트에 `yield()`·`result()` 를 더했다(`BlockExplodeEvent` 와 같은 모양, 서버가 `Explosion.interactionName()` 으로 채움). `setYield` 는 서버가 적용하지 않는다(BlockExplode 와 같은 한계).
- `ProjectileHitEvent`: Bukkit `getEntity()` 반환형이 `Projectile` 이라 `org.bukkit.entity.Projectile`·`org.bukkit.projectiles.ProjectileSource`·`CraftProjectile` 을 새로 뒀다(`LivingEntity extends ProjectileSource`, `launchProjectile` 은 `UnsupportedFeature`). 쏜 쪽은 핸들 원인 사슬의 SHOOT 항목 UUID → 플레이어는 `Bukkit.getPlayer`, 그 밖은 이미 감싼 적이 있는 엔티티만(Furnace API 에 UUID 로 엔티티를 찾는 길이 없음).

## 3. 확인만 한 것 (후속)
- **`EntityChangeBlockEvent`**: Furnace API 이벤트·서버 발화 지점 모두 없다(`NOT_FIRED` 로 안내만). 서버에 후보 지점은 있다 — `FallingBlock`(착지·낙하 시작), `FarmBlock`(밟힘), `BrushableBlock` 등. 엔더맨·위더·양·좀벌레 계열은 서버에 해당 AI 가 없거나 별도. **T-040b 로 넘긴다**(API 이벤트 + 지점별 발화).
- Paper 의 `ProjectileSource#launchProjectile(Class, Vector, Consumer)`·`Projectile#hasLeftShooter/setHasLeftShooter/hasBeenShot/setHasBeenShot/canHitEntity/hitEntity` 는 껍데기만 있다(`UnsupportedFeature`, 검토 R6).
- **끈끈이 피스톤이 빈 칸을 끌 때의 빈 `BlockPistonRetractEvent`**: Paper main(1.21.4+)은 이 경우도 내지만, **1.21.1 Paper 패치는 조건이 늘 거짓인 죽은 코드**(`iblockdata2` 가 그 자리에서 MOVING_PISTON 상태)라 낸 적이 없다(검토 R3). 지금 서버가 안 내는 것이 1.21.1 Paper 와 같다 — 할 일 아님.
- **일반 피스톤 빈 수축 이벤트의 발화 자리(검토 R2, 고침)**: Spigot 자리(`checkIfExtend`)에서 Paper 1.21.1 자리(`triggerEvent` 수축 갈래, `setBlock(MOVING_PISTON)` 앞)로 옮겼다. 같은 틱에 여러 번 부르거나 1틱 펄스로 신호가 돌아와도 블록 이벤트 하나당 한 번이다.
- **폭발 `blockList()` 순서·공기(검토 R1, 고침)**: Paper(CraftBukkit)는 서버 목록 `toBlow` 를 뒤에서부터·공기를 뺀 목록으로 준다. 다리가 같게 준다(BlockExplode·EntityExplode 공통). 처리기가 고치지 않았으면 서버 목록은 바닐라 순서 그대로(Furnace 원칙), 고쳤으면 Bukkit 목록 순서로 교체한다. 참고: Paper 는 안 고쳐도 `toBlow` 를 그 목록으로 다시 채우므로(드롭 난수 배정 순서) 이 부분은 Paper 와 다르다 — 바닐라 순서를 지킨다는 판단.
- **비플레이어 쏜 쪽(검토 R7)**: `Compat.cachedEntity` 가 약한 참조 캐시라 그 엔티티를 이미 감싼 적이 있을 때만 값이 있다(실행 이력에 따라 null). 결정적으로 하려면 Furnace API 에 UUID 로 엔티티를 찾는 길이 필요하다 — T-040b 후보. 디스펜서가 쏜 것은 Paper 의 `BlockProjectileSource` 대신 null.
- 서브 인터페이스(`Arrow`·`Snowball`…)는 없다. `instanceof Arrow` 를 쓰는 플러그인은 `getType()` 비교로 바꿔야 한다.
