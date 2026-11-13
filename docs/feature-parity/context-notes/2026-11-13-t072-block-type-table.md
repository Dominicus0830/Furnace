# T-072 Block.getType 빠른 길(상태 번호 표) — 구조와 함정 (2026-11-13)

설계 근거: 공유 폴더 `furnace/reviews/opt-review/O13-compat.md` P-O13-02. 관측 결과는 그대로다(바닐라·Paper 와 무관한 어댑터 내부 최적화).

## 바꾼 것
- 서버 `ApiConvert.block(stateId, BlockRegistry)` 가 상태 번호별 불변 `BlockData` 를 `BlockRegistry` 의 칸에 게으르게 보관한다(`cachedApiBlock`/`cacheApiBlock`,
  번호 범위 안만). 옛 길(`canonicalKeyOf` → `split(",")` → `TreeMap`)은 `buildBlock` 으로 남겨 시험의 기준으로 쓴다. 레지스트리는 만든 뒤 바뀌지 않고
  데이터 팩 리로드는 새 레지스트리를 만들므로 칸도 같이 버려진다. 값이 불변 record 라 경합해 두 번 채워도 같은 값이고(최종 필드로 안전 게시) 잠금은 배열 만들 때만 쓴다.
  같은 번호는 같은 객체가 된다 — 지금까지는 늘 새 객체였으니 플러그인이 `==` 를 기대할 수 없다(관측 변화 없음).
- 어댑터 `Compat.material(String)` 에 종류 키 → `Material` 표(`ConcurrentHashMap`). **정식 키(`minecraft:stone`)로 찾은 것만** 담는다 — `matchMaterial` 이 받아주는 다른 철자
  ("STONE" 등)는 담지 않아 플러그인이 만든 임의 문자열로 표가 자라지 않고, 없는 종류(데이터 팩 블록)는 지금처럼 부를 때마다 `UnsupportedFeature` 다.
  서버가 같은 번호에 같은 `type` 문자열을 주므로 해시가 이미 계산돼 있다.
- 어댑터 모듈은 서버 상태 번호를 직접 못 본다(furnace-api 만 본다). 그래서 "번호 → Material 배열"을 어댑터에 두지 않고 "번호 → 공유 BlockData(서버)" + "종류 → Material(어댑터)" 두 단으로 나눴다 —
  같은 효과(문자열 파싱 0)이고 API 를 늘리지 않는다.

## 증명
- `ApiConvertBlockCacheTest`(서버): 26684 상태 전부 `block(i) equals buildBlock(i)`·문자열 같음·같은 번호 같은 객체, 범위 밖 번호(−1·total·정수 끝값)는 캐시 안 하고 옛 값, 불변 확인, 8 스레드 동시 첫 읽기 수렴.
- `BlockTypeTableTest`(어댑터): 모든 상태에서 `Compat.material(type) == Material.matchMaterial(type)`(두 번 — 표 적중 포함, 없는 종류면 둘 다 같은 예외), 다른 철자, 그리고 **세상 길** —
  모든 상태를 한 칸에 놓고 읽어 `Block.getType()`·`getBlockData().getMaterial()` 이 옛 길(`matchMaterial(Furnace Block.type())`)과 같음(26668/26684 놓음, 차이 0; 못 놓은 16개는 `Block.set` 이 거절하거나 예외를 낸 상태 — 건너뜀).

## 측정(region 스레드, 계단, 중앙값, 같은 컨테이너 전/후)
- `CompatBlock.getType` 약 480ns → 약 130ns. `getBlockData` 약 400~500ns → 약 100~120ns. Furnace `Block.type()` 약 370~450ns → 약 80ns(서버 API 도 같이 빨라짐).
- 남은 130ns 는 대부분 `WorldHandle` 찾기(`handleFor` CHM compute, T-071)와 `blockNow`(소유 검사·Optional·BlockSnapshot) 몫이다. T-071 이 들어오면 더 줄어든다.
- 메모리(검토 R1 반영): 처음엔 상태마다 종류·속성 문자열을 `substring` 으로 새로 만들어 전 상태를 읽으면 약 19.8MB(상태당 ~780B)였다. 캐시에 넣기 전에 문자열을 `intern` 으로 합쳐(`shareStrings`) **약 7.4MB(상태당 ~290B)** 로 줄였다(전 상태를 읽은 뒤 GC 해 잰 값). 실제로는 읽은 상태만 남는다.
  레지스트리는 여러 곳에서 `load` 돼 각자 칸을 가진다 — 주 서버 레지스트리 하나만 플러그인 API 가 쓴다(나머지는 폴백·월드젠, 이 캐시를 부르지 않는다고 가정).
- `Compat.material(null)` 은 옛 길처럼 `UnsupportedFeature`(검토 R2). 없는 종류는 부정 캐시를 두지 않는다 — 옛 길과 같은 동작(검토 R4).
