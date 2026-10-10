# 바닐라 1.21.1 데이터 추출 도구

바닐라 서버 jar(Mojang 공식, EULA 대상 — **jar와 생성 결과물은 커밋하지 않는다**)에서 Furnace가 쓰는 데이터를 뽑는 스크립트들.
커밋하는 것은 결과 리소스(`registries/tags-1.21.1.txt`, `worldgen/1.21.1/block_tags.txt`, `blocks/blockattrs_1.21.1.txt` 등)와 이 도구들뿐이다.

## 준비
1. 서버 번들러 jar에서 `META-INF/versions/1.21.1/server-1.21.1.jar`(내부 서버 jar)와 `META-INF/libraries/**.jar`를 꺼낸다.
2. 바닐라 데이터 생성기 실행:
   `java -cp "<내부 서버 jar>:<libraries/*.jar>" net.minecraft.data.Main --all --output generated`
   (번들러 jar라면 `java -DbundlerMainClass=net.minecraft.data.Main -jar server.jar --all --output generated`)
   → `generated/data`(태그·레시피·전리품·월드젠 JSON), `generated/reports`(registries/blocks/items/packets/commands).
3. 실행 가능한 서버(월드 정답지 생성용)가 필요하면, 내부 서버 jar의 클래스 + `generated/data` + `version.json`을 한 jar로 묶고
   `Main-Class: net.minecraft.server.Main`, `Class-Path`에 라이브러리를 적는다(서명 파일 META-INF/*.SF는 제외해야 클래스 로드가 된다).

## 스크립트
| 도구 | 입력 | 출력 |
|---|---|---|
| `extract_tags.py <generated> <registries/1.21.1.txt> <출력>` | 태그 JSON + registries.json | `registries/tags-1.21.1.txt` (Update Tags 패킷용, 숫자 id) |
| `extract_builtin_registries.py <reports/registries.json> <출력>` | registries.json | `registries/builtin-1.21.1.txt` (태그 있는 내장 레지스트리의 protocol_id 순서 — 데이터 팩 태그를 숫자 id 로 바꿀 때) |
| `extract_block_tags.py <generated> <출력>` | 블록 태그 JSON | `worldgen/1.21.1/block_tags.txt` (월드젠용, 블록 이름) |
| `ExportBlockAttrs.java` | 서버 클래스(난독화 이름 사용 — **1.21.1 전용**) | `blocks/blockattrs_1.21.1.txt` (state별 air/occlude/replaceable/liquid/blocksMotion/solid/solidRender/collisionEmpty/발광/면지지 마스크) |

`ExportBlockAttrs`는 난독화된 클래스 이름(`akt`, `dfy`, `dtc`, `dcl` …)을 직접 참조한다. 다른 버전에는 그대로 쓸 수 없고, Vineflower로 디컴파일해 이름을 다시 찾아야 한다.
실행: `ab.a(); akt.a();`(SharedConstants 버전 설정 + Bootstrap) 후 블록 레지스트리 `lt.e`를 순회한다.

## 디컴파일
Vineflower(Maven Central `org.vineflower:vineflower`)로 내부 서버 jar를 디컴파일하면 난독화된 이름이지만 로직은 정확히 읽힌다.
월드젠 포팅의 1차 자료로 쓴다(기능 이름 → 클래스 매핑은 `Feature` 레지스트리 클래스의 정적 초기화에서 `ldc "<이름>"`과 `new <클래스>` 순서로 찾는다).
