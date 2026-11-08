# helper 다음 장벽 (helper-barriers) — 2026-11-08

helper 를 쓰는 플러그인이 helper 의 아이템 빌더·아이템 직렬화·보스바·메뉴(Gui)·홀로그램을 부를 수 있게 했다. 상세와 Paper 와 다른 점은
`docs/feature-parity/compat-h4-helper-barriers-2026-11-08.md`, `docs/feature-parity/compat-item-serialization-2026-11-08.md`.

## 결정할 것

없다. 다만 아래 두 가지는 지금 Paper 와 다르게 두었으니, 실제 플러그인에서 문제가 되면 알려 주면 된다.

- 메뉴 클릭 처리기 안에서 플러그인이 칸을 바꾸면 클릭이 끝난 뒤에 적용된다(Paper 는 곧바로). 멀티스레드 서버에서 칸 복사를 막으려고 고른 방식이다.
- 다른 서버(Paper)가 쓴 아이템 직렬화 문자열 가운데 포션·머리·책 같은 종류별 메타는 읽을 때 잃는다. Furnace 가 쓴 문자열을 Furnace 가 읽는 것은 모두 된다.

## 후속 후보(작업 스레드 없음)

- 플레이어 사망 때 떨어뜨릴 아이템 목록(`PlayerDeathEvent#getDrops`)을 Furnace 사망 이벤트에 싣기. 지금은 `getDrops().clear()` 를 부르는 플러그인(무덤·keep-inventory 류)이 죽을 때마다 오류 로그를 남긴다. 바닐라 떨어뜨리기 순서(난수)를 그대로 지켜야 해서 따로 하는 편이 낫다.

- 서버 `MobEffect.isInstantaneous()` 가 포화(saturation)를 즉시 효과로 치지 않는 바닐라 차이 고치기(독립 검토가 1.21.1 jar 로 확인 — `/effect give … saturation` 기본 길이가 바닐라 1틱이 아니라 600틱이 된다). 서버 효과 코드라 이 PR 밖.

## 직접 확인하면 좋은 것 (실제 클라이언트)

- helper 메뉴를 쓰는 플러그인(또는 `createInventory` 메뉴 플러그인)으로 메뉴를 열고, 막아 둔 칸을 눌러도 아이템이 안 빠지는지, 허용한 칸은 집히는지, 두 사람이 같은 메뉴를 열었을 때 한쪽에서 옮긴 아이템이 다른 쪽에도 보이고 복사되지 않는지.
- 보스바 색·모양(SEGMENTED_10 등)·하늘 어둡게 효과가 클라이언트에 바르게 보이는지.
