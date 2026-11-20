# 호환 구멍 내보내기 (`/furnace compat gaps`, T-233)

플러그인이 마인크래프트 내부 이름(NMS)으로 Furnace 에 아직 없는 것을 부르면 그 자리에서 `NoSuchMethodError` 계열 오류가 나고 서버 기록에 한 줄 남는다(플러그인 전체가 죽지는 않는다). 어떤 구멍이 실제로 자주 걸리는지 알면 채울 순서를 정할 수 있다 — 이 명령이 그 표를 파일로 내보낸다.

## 쓰는 법
- 콘솔에서 `furnace compat gaps` (권한 3). 서버 작업 폴더에 `compat-gaps-<UTC 날짜-시각>.tsv` 를 쓰고 상위 10줄을 보여 준다. 기록이 없으면 파일을 만들지 않는다.
- **서버는 이 파일을 어디에도 보내지 않는다**(결정 D2, 2026-11-20). 도움을 주고 싶은 운영자가 파일을 직접 보내 준다. 파일에는 플러그인 이름·NMS 심볼 이름·횟수만 있고 플레이어·월드·경로 정보는 없다.
- 표는 서버를 켠 뒤부터 쌓이고(재시작하면 비워진다), 서로 다른 (플러그인, 심볼)이 5만 개를 넘으면 새 항목은 버린다(이미 있는 항목의 횟수는 계속 센다).

## 파일 형식 (탭 구분, UTF-8)
```
#furnace-compat-gaps<TAB>1<TAB><마인크래프트 판><TAB><UTC 시각>
plugin  kind  target  referenced  calls
```
- `kind`: `class`·`method`·`field`·`extends`(NMS 클래스를 상속해 정의만 한 플러그인 클래스 — 상속 거부) · 모르면 `unknown`.
- `target`: 점 이름. 메서드는 `클래스#이름(인자 형, …)`(예: `net.minecraft.server.level.ServerLevel#getChunkAt(BlockPos)`), 필드는 `클래스#이름 : 형`, 상속 거부는 상속하려던 NMS 부모 클래스(플러그인 자기 클래스 이름은 담지 않는다). 로드 때 보고와 실행 때 덫이 같은 함수(`NmsLinker.methodTarget`/`fieldTarget`)로 이름을 만들어 한 심볼은 한 줄이다.
- `referenced`: 플러그인 jar 를 열 때 바이트코드가 이것을 가리킨다고 확인했는가(`yes`/`no`).
- `calls`: 실행 중 실제로 걸린 횟수. 0 이면 가리키기만 하고 아직 안 밟았다. 횟수 많은 순으로 정렬된다.

## 만든 방식
- 모으는 곳은 `furnace-compat-paper` 의 `NmsGaps`. 로드 때 `NmsCompatReport.log()` 가 `gaps`(말투와 무관한 고정 이름)를 넣고, 실행 때 `NmsTrap` 이 덫 안에서만 `LongAdder` 를 올린다 — **정상 호출 경로의 비용은 0**이다.
- 서버는 `PluginFormat#compatGaps()`(기본 빈 목록)로 묻는다. 어댑터를 아직 올리지 않았으면 이 명령 때문에 올리지 않는다.
- 우선순위 점수에 쓰는 법은 설계 `docs/roadmap/broad-support-version-scaling-2026-11-20.md` 4.4.
