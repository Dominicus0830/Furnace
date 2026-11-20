# 플러그인 표본 순위 돌리는 법 (T-234)

인기 플러그인 표본 jar 100개를 정적 분석해 "부르는 심볼 중 지원 비율"과 "채울 순서(점수 = 부르는 플러그인마다 log10(내려받은 수+10) 합)"를 만든다. 판마다·달마다 다시 돌려 비율을 기록한다(설계 5.4, 목표 95%).

```
python3 -I tools/compat/fetch-sample-plugins.py <받을 폴더> 100 1.21.1      Modrinth 인기순 jar 와 sample-manifest.tsv (jar 는 저장소에 넣지 않는다)
./gradlew -PskipOracle=true :furnace-compat-paper:nmsShimAudit -PrankingOut=<결과.md> -Pmanifest=<받을 폴더>/sample-manifest.tsv
```

- 서버·플레이어 정보는 보내지 않는다(Modrinth API 에 일반 클라이언트 이름으로 목록·jar 만 요청).
- Spigot 이름(`WorldServer`·`IBlockData`)으로 쓴 jar 는 이름 대응표가 있어야 분석된다. 클라우드 환경에서는 대응표를 못 받아 건너뛴다 — 대응표가 있는 PC 에서 같은 명령을 돌리면 그 jar 도 포함된다(`NmsShimAudit` 가 Spigot→Mojang 이름 대응표를 쓰는 판).
- 결과 예: `docs/feature-parity/compat-gaps-ranking-2026-11-20.md`. 여러 판을 한 jar 에 담는 플러그인은 Spigot 이름 코드 길이 "클래스 없음"으로 잡히는데 Mojang 이름 서버에서는 쓰이지 않아 진짜 구멍이 아니다.
- `Class.forName`·리플렉션 문자열로 NMS 를 찾는 플러그인 감지 목록도 같은 결과 파일에 나온다.
