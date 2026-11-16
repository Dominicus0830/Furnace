# 바닐라 ↔ Furnace 대응표 (V-06)

- Furnace 서버 클래스 머리에 소스 전용 주석 `@VanillaPort("net.minecraft....")` 를 붙인다(`org.domi.furnace.server.VanillaPort`, `@Retention(SOURCE)` — 클래스 파일에 안 남는다). 여러 바닐라 클래스를 합친 클래스는 `@VanillaPort({"a", "b"})`.
- 표 만들기: `python3 tools/vanilla-port/vanilla-port-map.py` → `build/vanilla-port-map.tsv` (바닐라 클래스 · Furnace 클래스 · 파일).
- 이름 검증(선택): `--verify 목록.txt` — 로컬 바닐라 jar(Mojang 이름)의 클래스 목록을 주면 없는 이름을 오류로 알린다. 바닐라 jar·목록은 커밋하지 않는다.
- 이름은 바닐라 소스를 읽고 적는다. 바닐라에 없는 도우미 클래스는 붙이지 않는다.
- 현재 표시된 묶음: `block/**`(블록·블록 엔티티·발사기). 나머지 패키지는 V-06 다른 조각이 채운다.; `item`·`recipe`·`loot`(V-06 item·recipe·loot 조각).
