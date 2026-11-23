# 어댑터 분리 2단계(T-250): 공개 API 에서 호환 전용 항목 하나를 뺐다 (2026-11-23)

급하지 않다. 추천대로 적용돼 있다. 다르게 원하면 알려 주면 된다. 근거는 `docs/ops/adapter-boundary.md` 의 "훅 한 곳으로 모으기" 절.

| # | 무엇이 바뀌었나 | 예시 | 안 고치면 어떻게 되나 | 추천 |
|---|---|---|---|---|
| ① | 공개 API `Furnace.compatOption(키)` 를 지우고 같은 기능을 불안정 다리 `Internals.compatOption(키)` 로 옮겼다. 이 메서드는 처음부터 "호환 어댑터 전용, 예고 없이 바뀐다"고 적혀 있었고, 저장소 안에서 부르는 곳은 Bukkit·Paper 어댑터 세 곳뿐이었다(모두 고쳤다). | 어떤 Furnace 플러그인이 `Furnace.server().compatOption("plugins.compat.scheduler.async-threads")` 를 불렀다면 이제 컴파일 오류가 난다. 대신 `Furnace.server().internals().compatOption(…)`. | 공개 API(MIT 공개 표면)에 어댑터만 쓰는 통로가 남아, 플러그인 개발자가 기대도 되는 기능으로 오해한다. | 옮김(적용됨). 공개 API 를 쓰는 바깥 플러그인이 아직 없어 폐기 기간(`@DeprecatedApi` 두 마이너)을 두지 않았다 — 이미 배포된 API 로 보고 기간을 두길 원하면 되돌려 `@DeprecatedApi` 로 남길 수 있다 |
| ② | `World.entityUuidById` 는 그대로 둔다. 설명에서 "호환 계층용" 문구만 뺐다. | 패킷의 엔티티 번호로 UUID 를 찾는 일반 기능이다. | — | 그대로(적용됨) |
| ③ | `PluginFormat`·`CompatGap` 은 그대로 둔다(형식 어댑터가 꽂는 SPI 자체라 옮길 곳이 없다). 설명에 "플러그인이 쓸 일 없음"을 적었다. | — | — | 그대로(적용됨) |

서버 안쪽 정리(월드젠 통로 분기 모으기, 패킷 가로채기 한 곳으로, 섹션 개수 대입 패키지 전용)는 동작이 같아 결정할 것이 없다.
