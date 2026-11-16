# T-141 E10 머리·프로필 — 도미니코 결정 (2026-11-16)

근거: `docs/feature-parity/context-notes/2026-11-16-t141e10-skull-profile.md`

## 결정
**1. 머리 `setOwner("이름")` 이 Mojang 에 이름을 물어봐야 할까?**
- 쉬운 말: Paper 는 서버 캐시에 없는 이름이면 Mojang 에 비동기로 묻는다. 지금 Furnace 는 캐시만 읽는다(모르는 이름이면 이름만 적힌 머리).
- 안 하면: 처음 보는 이름의 머리가 스킨 없이 나올 수 있다. 서버는 안 멈춘다.
- 추천: **지금대로 캐시만**, 스킨이 필요하면 플러그인이 `PlayerProfile#update` 를 쓴다(그쪽은 Mojang 에 묻는다). 서버 스레드를 네트워크로 막지 않기 위함.

**2. 프로필 API 가 처음으로 Mojang 에 요청을 보낸다 (온라인 모드만).**
- 쉬운 말: `PlayerProfile#update`·`complete` 가 `api.minecraftservices.com`·`sessionserver` 를 부른다. 요청 수가 많으면 Mojang 이 429 로 막는다(그때는 빈 결과로 넘어간다).
- 안 하면(끄면): 서명된 스킨 속성을 못 받는다. 기본은 켬(오프라인 모드는 원래 안 부름).
- 추천: **켜 둠**. 끄는 키는 필요해지면 추가.

**3. `createProfile(온라인 플레이어)` 가 속성을 복사할까, 그 플레이어를 따라갈까?**
- 쉬운 말: Paper 는 플레이어 프로필을 같이 쓰지만 여기서는 만든 시점의 사본이다.
- 안 하면: 만든 뒤 플레이어 스킨이 바뀌어도 사본은 옛 값.
- 추천: **사본 유지**(플러그인이 대개 바로 머리에 쓴다).

**4. 아직 없는 것**: `Player#setPlayerProfile`·`SkullMeta`·`BannerMeta`·authlib `getGameProfile`. 다음 조각(E11)으로 이어서 할지 — 추천: **SkullMeta·BannerMeta 먼저**(플러그인 사용 빈도 높음).
