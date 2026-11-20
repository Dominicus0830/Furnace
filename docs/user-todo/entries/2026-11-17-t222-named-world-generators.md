# T-222 이름별 월드 생성기 설정 — 확인할 것 하나 (2026-11-17)

근거: `docs/feature-parity/compat-t222-named-world-generators-2026-11-17.md`

플러그인이 월드를 만들 때(`Bukkit.createWorld`) 생성기·바이옴 공급자를 안 주면, Paper 는 `bukkit.yml` 에 적힌 그 이름의 설정(`worlds.<이름>.generator`)을
씁니다. Furnace 도 이제 똑같이 하고(`furnace.properties` 의 `world-generator.name.<월드 이름>`), Paper 설정 가져오기가 `bukkit.yml` 의 해당 줄을 옮깁니다.
바꾸고 싶은 것이 있으면 알려 주면 됩니다. 아래는 이미 추천안으로 적용했습니다.

## H1. Furnace 플러그인(Bukkit 이 아닌 것)이 만드는 추가 월드에도 이 설정을 쓸까

- 쉬운 말: Paper 의 이 설정은 Bukkit 플러그인이 월드를 만들 때만 읽힙니다. Furnace 전용 플러그인은 월드를 만들 때 생성기를 직접 주므로 이 설정이 필요하지 않습니다.
- 추천(적용함): 쓰지 않는다. Paper 와 같은 결과이고, Furnace 플러그인이 월드마다 다른 설정을 줄 수 있어 서버 설정이 덮어쓸 일이 없습니다.
- 이렇게 안 하면(쓴다고 하면): 서버 주인이 설정으로 Furnace 플러그인의 월드를 몰래 바꿀 수 있지만, 플러그인이 의도한 생성기를 주었는데 설정이 겹쳐 쓰면 혼란이 생깁니다.
