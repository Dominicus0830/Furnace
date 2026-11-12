# A15: ItemStack·OfflinePlayer 의 PDC 보기와 목록의 목록 (2026-11-12)

## 한 것
- `ItemStack#getPersistentDataContainer()` / `OfflinePlayer#getPersistentDataContainer()` — Paper 1.21.1 의 `PersistentDataViewHolder` 가 돌려주는 읽기 전용 `PersistentDataContainerView`. `PersistentDataHolder` 는 이제 이것을 확장하고 반환형만 쓰기 가능한 컨테이너로 좁힌다(Paper 와 같다).
- 보기는 부를 때마다 원본에서 다시 읽는다(`PdcViews.live`). 그래서 보기를 들고 있다가 `setItemMeta` 로 고친 뒤에도 최신 값이 보인다.
- `OfflinePlayer` 보기는 접속 중이면 살아 있는 값, 아니면 플레이어 파일을 **동기로** 읽는다(블로킹 I/O — Paper 도 같다). 파일이 없으면 빈 보기. API 쪽은 `OfflinePlayer#persistentDataNow()`(기본 null)로 열었다.
- PDC 목록의 목록(`LIST.listTypeFrom(LIST.strings())` 등): 저장·읽기·바이트 직렬화·`copyTo` 모두 된다. 안쪽 목록의 원소 종류가 서로 다르면 읽을 때 IOException(NBT 목록은 원소 종류가 하나). 빈 안쪽 목록은 종류에 영향을 주지 않는다. `ItemPdc` 는 겹 수를 8겹까지 찾아본다(Paper 는 한도 없음).
- `PersistentDataType.list()` 의 "목록의 목록 거부"를 없애고 `equals` 가 원소 종류를 안쪽까지 비교하게 했다.

## 바로잡음
`2026-11-12-a15-structural-pdc.md` 의 "안 한 것" 중 목록의 목록·ItemStack/OfflinePlayer PDC 는 이 묶음에서 했다.

## 남은 차이
- 오프라인 보기의 저장된 데이터 읽기는 시험하지 못했다(시험 서버에 플레이어 파일을 쓰는 길이 없다) — 접속 중·알 수 없는 id 경로만 시험했다.
- `getAdapterContext()` 는 새 빈 컨테이너 컨텍스트를 준다.
