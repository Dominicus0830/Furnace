# A15: ItemStack·OfflinePlayer 의 PDC 보기와 목록의 목록 (2026-11-12)

## 한 것
- `ItemStack#getPersistentDataContainer()` / `OfflinePlayer#getPersistentDataContainer()` — Paper 1.21.1 의 `PersistentDataViewHolder` 가 돌려주는 읽기 전용 `PersistentDataContainerView`. `PersistentDataHolder` 는 이제 이것을 확장하고 반환형만 쓰기 가능한 컨테이너로 좁힌다(Paper 와 같다).
- 보기는 부를 때마다 원본에서 다시 읽는다(`PdcViews.live`). 그래서 보기를 들고 있다가 `setItemMeta` 로 고친 뒤에도 최신 값이 보인다.
- `OfflinePlayer` 보기는 접속 중이면 살아 있는 값, 아니면 플레이어 파일을 **동기로** 읽는다(블로킹 I/O — Paper 도 같다). 파일이 없으면 빈 보기. API 쪽은 `OfflinePlayer#persistentDataNow()`(기본 null)로 열었다.
- PDC 목록의 목록(`LIST.listTypeFrom(LIST.strings())` 등): 저장·읽기·바이트 직렬화·`copyTo` 모두 된다. 안쪽 목록의 원소 종류가 서로 다르면 읽을 때 IOException(NBT 목록은 원소 종류가 하나). 빈 안쪽 목록은 종류에 영향을 주지 않는다. `ItemPdc` 는 겹 수를 8겹까지 찾아본다(Paper 는 한도 없음).
- `PersistentDataType.list()` 의 "목록의 목록 거부"를 없애고 `equals` 가 원소 종류를 안쪽까지 비교하게 했다.

## 바로잡음
`2026-11-12-a15-structural-pdc.md` 의 "안 한 것" 중 목록의 목록·ItemStack/OfflinePlayer PDC 는 이 묶음에서 했다.

## 독립 검토(#262) 반영
- `PersistentDataView#raw(key)`: 저장된 값을 종류를 묻지 않고 한 번에 읽는다(서버 `NbtPersistentData` 가 NBT 를 그대로 변환). 호환 계층 `ItemPdc` 는 종류를 하나씩 물어보던 탐색(8겹 한도·키 하나에 원본 `has` 99번)을 없애고 값의 모양에서 종류를 정한다 — 겹 수 한도 없음.
- `has(key, type)` 은 Paper `matchesListTag` 처럼 **바깥 목록의 원소 종류만** 본다(안쪽 목록 종류가 달라도 참, `get` 이 IllegalArgumentException). `get` 은 안쪽까지 검사.
- 안쪽 목록의 원소 종류가 서로 다른 목록의 목록(`[["a"],[1]]`)은 바닐라·Paper 에서 올바른 NBT 라 `readFromBytes`·`serializeToBytes`·`copyTo`·`has(key)` 가 된다. 서버(NBT)로 쓰는 길도 `PersistentDataContainer#setRaw`(`raw` 의 짝, 모양 그대로)로 되므로 섞인 값도 `setItemMeta`·엔티티 PDC 에 적용된다.
- 오프라인 보기에서 플레이어 파일을 읽지 못하면(손상, 또는 서버가 지원하지 않는 DataVersion — 서버는 그런 파일을 로드도 거부한다) 경고 로그 한 줄 뒤 빈 보기. Paper 는 옛 판을 DataFixer 로 올리고 새 판을 그대로 읽어 PDC 가 보인다(차이) — 순위표류 플러그인이 파일 하나 때문에 끊기지 않는다.
- 읽을 때 모르던 `==` 별칭이 나중에 등록되면 읽기 입구(`get`·`getKeys`·`getValues`·`contains`…)에서 트리를 먼저 되살린 뒤 읽는다(클래스 등록이 바뀐 뒤에만 다시 시도). 되살리기 전에 `getConfigurationSection` 으로 잡아 둔 구역은 떨어져 나간다(Paper 는 이 경우 읽기 자체가 실패).
- 보기의 `getAdapterContext()` 는 보기마다 하나이고 컨테이너가 아니다.

## 남은 차이
- 접속 중인 플레이어의 `CraftOfflinePlayer`(들고 있던 인스턴스)의 보기는 Furnace 는 살아 있는 값, Paper 는 언제나 디스크 값(마지막 저장 시점) — 의도한 차이(`Bukkit.getOfflinePlayer` 는 접속 중이면 `Player` 를 준다).
- `has(key, TAG_CONTAINER_ARRAY)` 는 Paper 에서 아무 목록에나 참이고 `get` 은 빈 컨테이너 배열을 준다. Furnace 는 컨테이너 목록에만 참(검토 R8, #257 범위의 기존 차이).
- 오프라인 보기의 "저장된 파일 읽기" 경로는 시험 서버에 플레이어 파일을 쓰는 길이 없어 접속 중·알 수 없는 id 경로만 시험했다.
