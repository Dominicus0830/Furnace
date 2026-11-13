## 2026-11-13 리스너 통째 탈락 막기 + 보호 이벤트 여섯 (T-040a)

근거: `furnace/reviews/opt-review/O13-compat.md` P-O13-06·07, 반론 `wave2-rebuttal-compat.md` 0장 1·2번, TASK 표 T-040a(P0).

## 1. 리스너 통째 탈락 (P-O13-07)
- 원인: `Class.getMethods()`/`getDeclaredMethods()` 는 클래스의 *모든* 메서드 서명을 한꺼번에 푼다. 없는 이벤트 클래스를 쓰는 처리기 하나가 같은 리스너 클래스의 `BlockBreak`·`PlayerInteract` 처리기까지 죽였다(기존 코드는 `NoClassDefFoundError` 를 잡고 그 리스너를 통째 건너뜀).
- 수정: `SimplePluginManager.registerEvents` 가 `NoClassDefFoundError` 를 잡았을 때만 `ListenerFallback`(ASM 으로 클래스 파일을 읽어 `@EventHandler` 메서드의 이름·서술자·priority·ignoreCancelled 를 얻고, 메서드마다 매개변수 형을 따로 올림 → `privateLookupIn(...).findVirtual`)으로 풀리는 처리기만 등록한다. 정상 경로는 그대로(성능 영향 없음). 건너뛴 처리기 이름과 없는 클래스는 한 줄 경고에 남긴다(A-2).
- `getMethods()` 와 같은 가시성: 이 클래스의 모든 메서드(비공개·정적 포함), 부모 클래스의 *공개* 메서드, 자식의 주석 없는 덮어쓰기는 부모 처리기를 가린다. 시험이 이 셋을 확인한다. 한계: 인터페이스 default 메서드의 `@EventHandler`.
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
- **끈끈이 피스톤이 빈 칸을 끌 때의 빈 `BlockPistonRetractEvent`**: Paper 는 이 경우도 낸다(취소하면 `triggerEvent` 가 false). Furnace 서버는 일반 피스톤 수축(`checkIfExtend`)과 끈끈이의 블록 끌기(`moveBlocks`)만 낸다. 영향은 머리 제거뿐이라 보호 가치는 낮다 — T-040b 후보.
- 서브 인터페이스(`Arrow`·`Snowball`…)는 없다. `instanceof Arrow` 를 쓰는 플러그인은 `getType()` 비교로 바꿔야 한다.
