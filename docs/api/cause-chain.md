# 변경 원인 사슬 (CauseChain) — 누가·무엇이 이 변경을 일으켰나

대상: Paper·Spigot 불만 표 13행(`docs/feature-parity/paper-spigot-dev-complaints-2026-11-06.md`), 작업 묶음 B4.
배경: Bukkit 에는 "플레이어가 켠 TNT 가 다른 TNT 를 터뜨려 부순 블록"처럼 여러 단계를 거친 변경의 책임자를 알려 주는 수단이 없다.
로그·롤백·보호 플러그인은 이벤트 여러 개를 짜맞춰 추측한다. Furnace 는 원인 사슬을 이벤트에 직접 싣는다.

API: `org.domi.furnace.api.event.cause` — `CauseChain`, `CauseEntry`, `Caused`. 서버 쪽은 `furnace-server/.../plugin/Causes.java`.

## 1. 모양

```
EntityExplodeEvent.causeChain()
  [minecraft:tnt explode @8,-50,10]      ← direct (가장 가까운 원인)
  [minecraft:tnt explode @8,-50,8]
  [minecraft:player ignite name=Steve]   ← root (가장 근본 원인)
```

- 사슬은 **가까운 쪽 → 근본 쪽** 순서다. `direct()`·`root()`·`player()`(가장 가까운 플레이어)·`first(종류)`·`entries()`.
- 고리(`CauseEntry`)는 종류(PLAYER·ENTITY·BLOCK·PLUGIN), 레지스트리 키, UUID, 플레이어 이름, 차원·칸, `action` 을 담은 **불변 스냅샷**이다.
  서버 객체를 붙잡지 않으므로 어느 스레드에서 읽어도, 비동기 DB 기록으로 넘겨도 안전하다.
- `action` 은 그 고리가 한 일이다(`explode`·`ignite`·`break`·`place`·`redstone`·`fire`·`lava`·`dispense`·`hit`·`shoot`·`damage`·`attack`·`use`).
  맨 앞 고리의 action 은 이벤트 대상과의 관계다(폭발 이벤트면 `explode`, 피해 이벤트면 `damage`).
- 원인을 모르면 `CauseChain.NONE`(빈 사슬)이다. null 이 아니다.
- 길이는 `MAX_LENGTH`(32)로 묶인다. 넘치면 가까운 쪽 절반과 root 만 남기고 `truncated()` 가 참이 된다(TNT 대포 같은 긴 연쇄). 사슬을 한 단계 늘리는 비용은 고리 하나다(꼬리를 나눠 쓰는 연결 목록).
- 엔티티의 원인은 `Entity.origin()` 으로도 읽는다(점화된 TNT 면 켠 쪽, 투사체면 쏜 쪽 — 엔티티 자신은 들어 있지 않다).

## 2. 이벤트별 사슬

| 이벤트 | 사슬 |
|---|---|
| `EntityExplodeEvent` | [터진 엔티티 explode] ← 그 엔티티의 origin |
| `BlockExplodeEvent` | [터진 블록 explode] ← 쓴 플레이어 use(침대·리스폰 정박기) |
| `EntityDamageEvent` | [직접 맞힌 것 damage] ← 그것의 origin(비었으면 책임자). 직접 엔티티가 없으면 [책임자 damage]. 환경 피해(낙하·용암·굶주림)는 NONE. 피해 종류는 원래대로 `cause()` |
| `EntitySpawnEvent`(투사체·아이템·생물 하위 포함) | 스폰된 엔티티의 origin |
| `BlockBreakEvent` / `BlockPlaceEvent` | [플레이어 break/place] |
| `BlockIgniteEvent` | [플레이어 ignite], 번짐이면 [불 칸 fire], 용암이면 [용암 칸 lava] |

점화된 TNT 의 origin:

| 점화 | origin |
|---|---|
| 부싯돌·화염구(플레이어) | [플레이어 ignite] |
| 불안정한 TNT 부수기 | [플레이어 break] |
| 레드스톤 신호 | [그 TNT 칸 redstone] — 신호를 낸 칸(레버를 당긴 사람)은 추적하지 않는다 |
| 불이 옮겨 붙음 | [불 칸 fire] |
| 발사기(TNT 내보내기·부싯돌로 켜기) | [발사기 칸 dispense] |
| 불붙은 투사체 | [투사체 hit] ← [쏜 쪽 shoot] |
| 폭발에 휘말림 | 그 폭발의 사슬([앞 TNT explode] ← …) |

## 3. 비용 — 구독이 없으면 0

- 이벤트 객체와 사슬은 지금까지처럼 **구독자가 있을 때만** 만든다.
- 엔티티에 원인을 적어 두는 일(TNT 점화 때 고리 하나)은 원인을 담는 이벤트(`EntityExplodeEvent`·`BlockExplodeEvent`·`EntityDamageEvent`·`EntitySpawnEvent`, 그 상위 타입 포함)를
  받는 처리기가 **하나라도 있을 때만** 한다(`GameEvents.tracksCause()` — 등록이 바뀔 때만 다시 세고, 발화 경로는 필드 읽기 하나).
  `BlockBreakEvent` 처럼 사슬이 처리기 쪽에서 바로 나오는 이벤트만 구독하면 기록은 꺼진 채다.
- 그래서 구독하기 **전에** 켜진 TNT 는 바닐라가 아는 만큼만 나온다: 적힌 원인이 없으면 바닐라 주인(TNT 의 owner, 투사체의 owner)으로 [주인 ignite]/[주인 shoot] 을 만든다.
- 원인은 저장하지 않는다. 청크를 다시 읽은 TNT 는 바닐라 주인(NBT `owner`)만 안다.

## 4. 바닐라와 같게 고친 것

- 폭발에 휘말린 TNT 의 주인: 바닐라 `TntBlock.wasExploded` 는 `explosion.getIndirectSourceEntity()`(앞 TNT 의 주인, 터진 몹, 투사체를 쏜 생물)를 주인으로 준다.
  Furnace 는 주인 없이 만들고 있었다(1.21.1 바이트코드 `doi.a(dcw, jd, dco)` → `dco.f()` 로 확인). 이제 같다 — 연쇄 TNT 의 NBT `owner` 와 피해 책임자가 바닐라와 같아진다.

## 5. 플러그인이 원인을 달 때

`CauseEntry.plugin("MyCannon", "mycannon:fire")` 로 고리를 만들어 `CauseChain.of(entry, parent)` 로 이을 수 있다(자체 이벤트·로그용).
서버 엔티티에 직접 다는 API(`setOrigin`)는 아직 없다 — 필요하면 B 묶음 후속으로.

## 6. 아직 없는 것

- 레드스톤 신호의 출처(누가 레버를 당겼나), 불이 처음 어디서 왔나(번짐 연쇄), 떨어지는 블록·엔더맨처럼 엔티티가 블록을 바꾸는 경로(`EntityChangeBlockEvent` 는 T-040b 에서 생겼지만 원인 사슬은 싣지 않는다 — 바꾸는 엔티티 하나만).
- 블록 폭발의 피해 이벤트(침대 폭발로 다친 엔티티)는 사슬이 비어 있다 — 바닐라 피해 원인에 엔티티가 없기 때문.

## 7. 시험

- 단위: `furnace-api` `CauseChainTest`(순서·빈 사슬·길이 제한), `furnace-server` `plugin/CauseChainTest`(플레이어→TNT→TNT, 레드스톤·발사기, 투사체·폭발 피해, 침대, 구독 없을 때 기록 안 함, 바닐라 주인).
- 봇: `tools/bot-test/round14/cause.js` + 시험 플러그인 `round14/probe/CauseProbe` — 2026-11-07 11/11 통과(부싯돌 연쇄 TNT·소/플레이어 폭발 피해·레드스톤·발사기·눈덩이·블록 부수기). 화면에 보이는 것이 없어 실제 클라이언트 확인은 필요 없다.
