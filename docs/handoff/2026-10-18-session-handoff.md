# Furnace 세션 인수인계 (2026-10-18)

이 문서는 새 Claude Code 세션이 **맥락 손실 없이** 작업을 이어가도록 쓴 인수인계서다. 새 세션은 새 클라우드 컨테이너에서 시작하므로 `/tmp` 아래 모든 것(디컴파일 소스, 바닐라 리포트, 스크래치패드, 로그)이 사라진다. 살아남는 것은 git 에 커밋된 것뿐이다. 그래서 사라지는 정보(미구현 인벤토리, 오라클 실행 스크립트, 난독화 클래스 매핑, 패킷 id 등)는 이 문서에 전부 옮겨 두었다.

- 작성 시점: 2026-10-18, 브랜치 `claude/work-continuation-u597cw`, HEAD `3738fd79` (4-1b)
- 직전 세션 사용자 방향: **다음은 메뉴(4-2)**
- 사용자에게는 **항상 한국어**로 말한다. 커밋 메시지도 한국어, 코드 주석도 한국어(간결, 기존 스타일).

---

## 1. 한눈에 보기

### 1.1 Furnace 가 무엇인가

Furnace 는 **처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버**(Java 21 / Gradle)다. 사용자의 말(원문 그대로):

> "명심해. 해당 프로젝트는 기존의 버킷이랑 똑같은 결과물을 보여주되, 최적화 이랑 성능을 최대한 뽑는 버킷이야"
> "원본 마크혹은 페이퍼랑 완전히 같아야해"

해석: 관측 가능한 동작이 바닐라/Paper 와 **동일**해야 한다(난수 호출 횟수·순서까지). 그 위에서 성능을 최대한 뽑는다. 스레딩은 **Folia 방식 동적 regionizer**다. 즉 "이 정도면 비슷하다"로 넘어가지 말고, 바닐라 코드를 그대로 이식하되(난수 소비 순서 포함) 스레드 모델만 region 단위로 바꾼다.

### 1.2 모듈

| 모듈 | 내용 |
|---|---|
| `furnace-api` | 플러그인 API(`org.domi.furnace.api.*`): 텍스트 타입, 설정, 이벤트, 스케줄러 등. Paper/Bukkit 어댑터를 나중에 붙일 수 있게 설계 |
| `furnace-server` | 서버 본체(`org.domi.furnace.server.*`). 대부분의 작업이 여기 |
| `furnace-plugin-test` | 샘플 플러그인 |
| `worldgen-oracle` | paperweight-userdev 기반 월드젠 대조 도구. Paper 저장소에 접속해야 해서 **`ORG_GRADLE_PROJECT_skipOracle=true` 로 제외**해서 쓴다(항상) |

### 1.3 지금 어디까지 왔나

- 월드젠(오버월드·네더·엔드·구조물 전종), 서버 뼈대(설정·월드 형식·온라인 모드·서명 채팅·명령 프레임워크·게임룰·월드 보더·플레이어 생존), 플러그인 API v0, 데이터 팩/레시피/전리품/발전 과제/아이템 컴포넌트, 블록 241종 동작·파괴·무작위 틱·유체·피스톤·비교기, 아이템 사용, **서버 권위 인벤토리 + 엔티티 틀 + 아이템 엔티티(4-1)** 까지 완료.
- HEAD: `3738fd79 4-1b 엔티티 틀 + 아이템 엔티티: 물리·추적·줍기·저장`
- **다음 작업: 4-2 컨테이너 메뉴 프레임워크**(서버 권위 `AbstractContainerMenu`). 지금 메뉴는 25종 중 상자 9x3 하나뿐이고 그것도 클라이언트 결과를 총량 검사만 하고 받아들이는 방식이다.
- 이후 순서: 메뉴 → 명령(83개 중 43개 없음) → 전투/효과/속성 → 폭발 → 빛 엔진 → 청크 티켓 → 몹 → 차원.

---

## 2. 반드시 지킬 규칙

### 2.1 브랜치·푸시
- 작업 브랜치는 **`claude/work-continuation-u597cw` 하나만**. PR 은 사용자가 요청할 때만.
- 푸시: `git push -u origin claude/work-continuation-u597cw` — **푸시 출력을 사용자에게 그대로 보여 준다.** (`-q` 로 출력을 숨겼다가 사용자가 불만을 표한 적이 있다.)
- 커밋/푸시는 사용자가 시켰거나 작업 완료 흐름에서만. 이 인수인계 문서 자체도 부모 에이전트가 커밋한다.

### 2.2 테스트 우선 규칙 (커밋 전 필수)
- **모든 커밋 전에** 다음이 통과해야 한다:
  ```bash
  ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test
  ```
- `ORG_GRADLE_PROJECT_skipOracle=true` 를 빼면 `:worldgen-oracle` 프로젝트 설정 단계에서 Paper 저장소 접속이 필요해 실패한다. **항상 붙인다.**
- 전체 스위트는 약 **4분**. 백그라운드로 돌리고 로그 파일에 대해 until-loop 로 기다린다(포그라운드 sleep 은 막혀 있음). 예:
  ```bash
  (ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test > /tmp/test.log 2>&1; echo "EXIT=$?" >> /tmp/test.log) &
  # 완료 대기: until grep -q '^EXIT=' /tmp/test.log; do sleep 5; done; tail -30 /tmp/test.log
  ```
- 일부 테스트만: `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test --tests '*ItemEntityTest*'`
- Stop-hook 이 "There are uncommitted changes… commit and push" 라고 해도, **테스트를 먼저 끝낸다.** 테스트 안 돈 코드를 커밋하지 않는다.
- gradle 은 동시에 두 개 돌리지 않는다(블록 오라클도 gradle 을 부르므로 테스트와 동시 실행 금지).

### 2.3 커밋 규칙
- 커밋 메시지는 **한국어**. 제목 스타일 예: `4-1b 엔티티 틀 + 아이템 엔티티: 물리·추적·줍기·저장`
- 트레일러(이 세션에서 쓴 정확한 문구):
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_01C51tC8178cHA97RMDgfg7e
  ```
  **새 세션은 자기 system reminder 가 주는 Claude-Session URL(그리고 모델 표기 줄)을 쓴다.** 위 값은 직전 세션 것이다. 커밋/코드에 **모델 ID 를 넣지 않는다.**
- 코드 주석은 한국어, 간결하게, 기존 파일 스타일(파일 머리 주석 1~3줄 + 필요한 곳에 "바닐라 X.y 의 …" 식 짧은 설명)을 따른다.

### 2.4 작업 방식
- **포팅은 직접 한다.** 포팅 서브에이전트를 다시 띄우지 않는다. 서브에이전트(Sonnet)는 인벤토리/조사 용도로만.
- 바닐라 코드는 **그대로** 이식한다: 필드 이름·메서드 순서·`random.nextXxx()` 호출 순서·HashMap 순회 순서까지. (예: 퇴비통·피스톤 갱신 순서가 바닐라 HashMap 순회 순서를 같은 해시로 재현.)
- 네트워크 프록시가 **Mojang/papermc 호스트를 막는다.** 우회 시도 금지. 바닐라 데이터는 저장소의 jar 에서 뽑는다.
- **Mojang 바닐라 데이터는 커밋하지 않는다**(첫 실행 때 바닐라 jar 에서 추출). 파생된 압축 리소스(`furnace-server/src/main/resources/blocks/*.txt`, `registries/*.txt` 등)는 커밋한다.
  - 주의: 과거에 사용자가 `tools/vanilla-data/minecraft_server.1.21.1.jar` 와 `tools/vanilla-data/server-1.21.1/**`(내부 서버 jar 를 푼 클래스 파일 12,951개)를 **이미 git 에 올려 두었다**(커밋 `ed46c6b5 tools`). 새로 추가하지 말고 지우지도 말 것. 디컴파일 소스(`.java`)나 생성 리포트(`generated/`)는 **절대 커밋 금지.**
- 사용자에게 보고할 때는 한국어로, 무엇을 바닐라와 맞췄고 무엇이 남았는지 구체적으로.

---

## 3. 환경·도구

### 3.1 기본 명령

```bash
cd /home/user/Furnace
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:compileJava      # 컴파일만
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test              # 전체 테스트(≈4분)
ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:installDist    # 실행 배포(오라클이 사용)
# 서버 실행 파일: furnace-server/build/install/furnace-server/bin/furnace-server
```

- `--offline` 은 gradle 캐시(`~/.gradle`)에 의존한다. **새 컨테이너에 캐시가 없으면** 처음 한 번은 `--offline` 없이 의존성(Maven Central: gson, netty, slf4j, logback, junit)을 받아야 할 수 있다. 막히면 `read_documentation`(environment.network)로 허용 호스트를 확인하고, 우회하지 않는다.
- Java 21, 서버 기본 JVM 옵션 `-Xmx6G`(`furnace-server/build.gradle.kts`).
- `-Dfurnace.*` 시스템 프로퍼티는 테스트 JVM 에 그대로 전달된다(오라클 봇/덤프가 이 방식으로 인자를 받는다).
- 바닐라 jar 위치: 서버는 `-Dfurnace.vanillaJar=<경로>` 또는 캐시에서 바닐라 데이터를 첫 실행 때 추출한다. 저장소에 `tools/vanilla-data/minecraft_server.1.21.1.jar` 가 있다(위 2.4 주의 참고).

### 3.2 새 컨테이너에서 사라지는 것과 복원 방법

| 사라지는 것 | 직전 세션 위치 | 복원 방법 |
|---|---|---|
| 오라클 실행 스크립트 `run-dbg.sh` | 스크래치패드 | 저장소에 **`tools/block-oracle/run.sh` 가 이미 있다**(거의 동일). 차이는 3.3 참조. 디버깅 변형을 쓰려면 아래 전문을 `tools/block-oracle/run-dbg.sh` 로 저장(커밋은 선택) |
| 디컴파일된 바닐라 소스 `<scratchpad>/decomp/src/<cls>.java` (4,058개) | 스크래치패드 | 아래 3.5 절차로 재생성 |
| 바닐라 리포트 `/tmp/claude-0/jar/work/gen/reports/*.json` (registries/packets/commands/blocks/items) | /tmp | 바닐라 데이터 생성기(3.5)로 재생성 |
| 블록 이름→클래스 맵 `/tmp/claude-0/jar/work/block_class.json` | /tmp | `ExportBlockProps` 등으로 재생성하거나 디컴파일 `dga`(Blocks) 정적 초기화를 읽는다 |
| Vineflower 디컴파일러 `/tmp/claude-0/jar/tools/vineflower.jar` | /tmp | Maven Central `org.vineflower:vineflower` (프록시가 허용하면). 못 받으면 3.5 의 대안 |
| 이 문서에 요약한 미구현 인벤토리 | 스크래치패드 `unimplemented-inventory.md` | 6장에 전부 옮김 |
| 오라클 작업 폴더(`oracle-*`)·각종 로그 | 스크래치패드 | 필요하면 다시 실행 |

### 3.3 블록 오라클 (순정 서버와의 차분 비교)

**목적:** 같은 봇 시나리오를 순정 1.21.1 서버와 Furnace 에 각각 돌리고, 저장된 월드의 블록 상태를 칸 단위로 비교한다. 블록 동작 이식이 바닐라와 같은지 확인하는 기본 도구. (엔티티는 비교하지 않는다 — 블록만. 엔티티 오라클은 TODO.)

```bash
bash tools/block-oracle/run.sh <시나리오 파일> <영역 x1,y1,z1,x2,y2,z2> <작업 폴더>
# 직전 세션은 디버깅 변형 run-dbg.sh 를 같은 인자로 썼다:
bash run-dbg.sh <시나리오 파일> <영역> <작업 폴더>
```

- 출력: 일치하면 `일치`, 아니면 `차이 N칸 — <작업 폴더>/diff.txt`.
- 흐름: `installDist` → 순정 서버(jar) 기동 → 봇(`ScenarioBot` 테스트)이 시나리오 수행 → 순정 종료 → Furnace 기동 → 같은 봇 → 종료 → 두 월드를 `WorldDump` 테스트로 `x y z 상태` 텍스트로 덤프 → diff.
- 두 서버 모두 평지(`level-type=minecraft:flat`), 구조물 없음, 시드 1, 크리에이티브, 오프라인, 압축 끔. 봇 이름 `Oracle` 은 OP 로 등록돼 `/tp`·`/save-all` 사용.
- **gradle 을 직접 부르므로 다른 gradle 실행과 동시에 돌리지 않는다. 백그라운드로 실행**하고 로그 파일을 기다린다(순정 서버 기동·월드 저장까지 수 분).
- 영역(직전 세션이 쓴 값):

  | 시나리오 | 영역 |
  |---|---|
  | `scenarios/items.txt` | `-2,-63,-2,40,-55,30` |
  | `scenarios/pistons.txt` | `-10,-61,-10,40,-50,40` |
  | `scenarios/fluids.txt` | `-40,-62,-40,40,-55,40` |
  | `scenarios/break-chain.txt`, `scenarios/survival-dig.txt` | `-16,-63,-16,16,-50,16` |
  | `scenarios/basic-place.txt`, `scenarios/types-mix.txt` | 스크립트 머리 주석 참고 |

- **최종 결과(4-1b 이후): items, break-chain, survival-dig, pistons 모두 `일치`.**
- 무작위 틱은 바이트 단위로 같지 않다(순정 레벨 난수는 시드 없음) → 영향 받을 수 있는 시나리오는 `cmd gamerule randomTickSpeed 0` 로 끈다.
- 시나리오 문법(`furnace-server/src/test/java/org/domi/furnace/server/oracle/ScenarioBot.java` 머리말):
  `tp x y z [yaw pitch]`, `look yaw pitch`, `item <핫바 0..8> <아이템> [개수]`, `hold <0..8>`, `sneak on|off`, `use x y z <면> [cx cy cz] [off] [inside]`, `useitem [off]`, `break x y z <면>`, `dig x y z <면> <틱>`, `cmd <명령>`, `wait <틱>`.
- 평지 잔디 윗면은 y=-61, 서 있는 높이 y=-60. 크리에이티브 손 닿는 거리(약 5칸) 밖은 `tp` 로 옮긴 뒤 설치.
- 메뉴(4-2)를 하면 컨테이너 조작은 블록 오라클로 못 본다. 대신 **패킷 단위 대조**(순정 서버에 봇을 붙여 `container_set_slot`/`container_set_content` 시퀀스 캡처 → Furnace 와 비교) 또는 순정 클래스 로직을 직접 호출하는 골든 테스트를 쓰는 것이 이 프로젝트의 기존 방식이다(예: `DataComponentsVanillaTest`, `VanillaRecipesTest`).

#### 오라클 스크립트 전문 (`run-dbg.sh`, 직전 세션에서 쓴 그대로)

저장소의 `tools/block-oracle/run.sh` 와의 차이는 두 가지뿐이다: (1) `REPO` 가 `/home/user/Furnace` 로 고정(저장소판은 스크립트 위치에서 계산), (2) `bot()` 이 gradle 출력을 `$W/bot.log` 에 모으고 실패 시 `ScenarioBot` 테스트 XML 을 `$W/bot-<포트>.xml` 로 복사한다(저장소판은 `/dev/null`). 디버깅할 때 유용하니 필요하면 `tools/block-oracle/run-dbg.sh` 로 저장해 쓰되, 기능 개선(2)만 `run.sh` 에 반영해도 좋다.

```bash
#!/bin/bash
# 블록 동작 차분 비교: 같은 시나리오를 순정 1.21.1 서버와 Furnace 에 돌리고 저장된 블록을 비교한다.
# 사용: run.sh <시나리오 파일> <영역 x1,y1,z1,x2,y2,z2> <작업 폴더>
#   VANILLA_JAR  순정 서버 jar(기본 tools/vanilla-data/minecraft_server.1.21.1.jar — 커밋하지 않는다)
# 결과: <작업 폴더>/vanilla.txt, furnace.txt, diff.txt (diff 가 비면 일치)
set -u
REPO=/home/user/Furnace
SCN=$(realpath "$1"); AREA=$2; W=$(realpath -m "$3")
VANILLA_JAR=${VANILLA_JAR:-$REPO/tools/vanilla-data/minecraft_server.1.21.1.jar}
FURNACE_BIN=$REPO/furnace-server/build/install/furnace-server/bin/furnace-server
NAME=${ORACLE_NAME:-Oracle}
UUID=$(python3 -c "import hashlib,uuid,sys;h=bytearray(hashlib.md5(b'OfflinePlayer:$NAME').digest());h[6]=h[6]&0x0f|0x30;h[8]=h[8]&0x3f|0x80;print(uuid.UUID(bytes=bytes(h)))")

prepare() {   # <폴더> <포트>
    rm -rf "$1"; mkdir -p "$1"
    cat > "$1/server.properties" <<P
online-mode=false
level-type=minecraft\:flat
generate-structures=false
level-seed=1
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-protection=0
network-compression-threshold=-1
server-port=$2
max-players=4
enable-command-block=false
view-distance=4
simulation-distance=4
P
    echo "eula=true" > "$1/eula.txt"
    echo "[{\"uuid\":\"$UUID\",\"name\":\"$NAME\",\"level\":4,\"bypassesPlayerLimit\":false}]" > "$1/ops.json"
}

run_server() {   # <폴더> <명령…>  — FIFO 로 콘솔을 잡고 Done 까지 기다린다
    local dir=$1; shift
    rm -f "$dir/stdin.fifo"; mkfifo "$dir/stdin.fifo"
    ( cd "$dir" && exec "$@" < stdin.fifo > out.log 2>&1 ) &
    eval "PID_$(basename "$dir")=$!"
    exec {FD}>"$dir/stdin.fifo"
    for i in $(seq 1 300); do grep -q 'Done (' "$dir/out.log" 2>/dev/null && break; sleep 1; done
    eval "FD_$(basename "$dir")=$FD"
}

stop_server() {   # <폴더>
    local fdvar="FD_$(basename "$1")"; local fd=${!fdvar}
    local pidvar="PID_$(basename "$1")"; local pid=${!pidvar}
    echo "stop" >&"$fd"; exec {fd}>&-
    for i in $(seq 1 60); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
    kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
}

bot() {   # <포트>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*ScenarioBot*' \
        -Dfurnace.scn.port=$1 -Dfurnace.scn.script="$SCN" -Dfurnace.scn.name=$NAME --rerun-tasks >>"$W/bot.log" 2>&1) || { echo "봇 실패(포트 $1)"; cp furnace-server/build/test-results/test/TEST-org.domi.furnace.server.oracle.ScenarioBot.xml "$W/bot-$1.xml"; }
}

dump() {   # <월드> <출력>
    (cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:test --tests '*WorldDump*' \
        -Dfurnace.dump.world="$1" -Dfurnace.dump.area="$AREA" -Dfurnace.dump.out="$2" --rerun-tasks >/dev/null 2>&1) || echo "덤프 실패: $1"
}

mkdir -p "$W"
(cd "$REPO" && ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline -q :furnace-server:installDist) || exit 1
prepare "$W/vanilla" 25601
prepare "$W/furnace" 25602
run_server "$W/vanilla" java -Xmx2G -jar "$VANILLA_JAR" nogui
bot 25601
stop_server "$W/vanilla"
run_server "$W/furnace" env JAVA_OPTS="-Xmx3G -Dfurnace.vanillaJar=$VANILLA_JAR" "$FURNACE_BIN"
bot 25602
stop_server "$W/furnace"
dump "$W/vanilla/world" "$W/vanilla.txt"
dump "$W/furnace/world" "$W/furnace.txt"
diff "$W/vanilla.txt" "$W/furnace.txt" > "$W/diff.txt"
if [ -s "$W/diff.txt" ]; then echo "차이 $(grep -c '^<' "$W/diff.txt")칸 — $W/diff.txt"; else echo "일치"; fi
```

### 3.4 바닐라 참고 자료 (사라짐 — 재생성 필요)

직전 세션에서 이렇게 썼다.
- 난독화된 1.21.1 서버 소스: `<scratchpad>/decomp/src/<난독화클래스>.java` (Vineflower 로 내부 서버 jar 를 디컴파일). 이름이 난독화돼 있어 아래 매핑표가 필수.
- 리포트: `/tmp/claude-0/jar/work/gen/reports/` 의 `registries.json`, `packets.json`, `commands.json`, `blocks.json`, `items.json`.
- 블록 이름 → 클래스 맵: `/tmp/claude-0/jar/work/block_class.json` (예: `"stone": "dfy"`).

### 3.5 바닐라 데이터·디컴파일 재생성

1. 저장소의 `tools/vanilla-data/minecraft_server.1.21.1.jar`(번들러 jar, 추적됨)가 있는지 확인. 없으면 `tools/vanilla-data/server-1.21.1/`(이미 풀어 둔 내부 서버 클래스) 를 쓴다. Mojang 호스트는 막혀 있으므로 새로 내려받으려 하지 않는다.
2. 리포트 생성(`tools/vanilla-data/README.md` 의 "준비"):
   ```bash
   java -DbundlerMainClass=net.minecraft.data.Main -jar tools/vanilla-data/minecraft_server.1.21.1.jar --reports --output <임시폴더>/generated
   # 번들러가 안 되면: java -cp "<내부 서버 jar>:<libraries/*.jar>" net.minecraft.data.Main --all --output generated
   ```
   → `generated/reports/{registries,packets,commands,blocks,items}.json`, `generated/data/**`.
3. 디컴파일: Vineflower(`java -jar vineflower.jar <내부 서버 jar 또는 server-1.21.1 폴더> <출력 폴더>`). Vineflower 를 못 받으면(프록시) Maven 캐시·`~/.gradle` 에 paperweight 쪽 사본이 있는지 찾아본다. 그래도 없으면 CFR 등 오프라인 가능한 도구를 쓰고, 그것도 없으면 **Mojang 매핑 이름으로 기억한 바닐라 로직을 매우 신중하게** 이식하고 TODO 로 "미검증" 표시한다(추측으로 단정 금지).
4. 클래스 매핑이 필요하면 아래 표를 먼저 본다. `tools/vanilla-data/Export*.java` 는 난독화 이름을 직접 참조하므로 **1.21.1 전용**이다(실행 예: `ab.a(); akt.a();` 로 SharedConstants+Bootstrap 후 레지스트리 순회).
5. 디컴파일 소스·`generated/` 는 임시 폴더에만 두고 커밋하지 않는다.

### 3.6 난독화 클래스 매핑 (1.21.1 서버, 직전 세션에서 확인)

| 난독화 | Mojang 이름 | 난독화 | Mojang 이름 |
|---|---|---|---|
| `bsr` | Entity | `cjh` | ItemEntity |
| `aqt` | ServerEntity | `aqb` | ChunkMap (내부 `b` = TrackedEntity) |
| `arq` | PlayerChunkSender | `exc` | Vec3 |
| `ewx` | AABB | `cmx` | Player |
| `btn` | LivingEntity | `ajy` | EntityDataSerializers |
| `dga` | Blocks | `cut` | Items |
| `avp` | SoundEvents | `dfl` | BaseFireBlock |
| `div` | FarmBlock | `dnd` | SlimeBlock |
| `dgj` | CactusBlock | `dpm` | WebBlock |
| `djs` | HoneyBlock | `dge` | BubbleColumnBlock |
| `dlt` | PowderSnowBlock | `dfr` | BedBlock |
| `dkg` | LavaCauldronBlock | `dkh` | LayeredCauldronBlock |
| `dlw` | PressurePlateBlock | `dpp` | WeightedPressurePlateBlock |
| `dlx` | PumpkinBlock | `dfy` | (기본 `Block` 계열: stone·dirt 등이 이 클래스) |
| `akt` | Bootstrap (Export 도구가 사용) | `ab` | SharedConstants |

(`dfy` 는 `block_class.json` 에서 stone/granite/dirt 등의 클래스로 확인된 값이며 일반 `Block` 으로 추정. 확정 아님.)

메뉴(4-2) 작업에서 찾아야 할 클래스는 재생성한 디컴파일 소스에서 문자열(`"container.chest"`, `"minecraft:generic_9x3"` 등)이나 `MenuType` 정적 초기화로 역추적한다.

### 3.7 엔티티 데이터 직렬화기 id (1.21.1 `EntityDataSerializers`, `ajy`)

| id | 종류 | id | 종류 |
|---|---|---|---|
| 0 | byte | 12 | direction |
| 1 | int | 13 | optional uuid |
| 2 | long | 14 | block state |
| 3 | float | 15 | optional block state |
| 4 | string | 16 | compound |
| 5 | component | 17 | particle |
| 6 | optional component | 18 | particles |
| 7 | item stack | 19 | villager data |
| 8 | boolean | 20 | optional unsigned int |
| 9 | rotations | 21 | pose |
| 10 | block pos | | |
| 11 | optional block pos | | |

`net/protocol/play/SetEntityData.java` 에 현재 구현된 직렬화기 상수: BYTE 0, INT 1, FLOAT 3, ITEM 7, BOOLEAN 8, POSE 21. 필요하면 늘린다.

### 3.8 클라이언트바운드 play 패킷 id (엔티티 작업에 쓴 것)

| id | 패킷 | id | 패킷 |
|---|---|---|---|
| 0x00 | bundle_delimiter | 0x5A | set_entity_motion |
| 0x01 | add_entity | 0x6F | take_item_entity |
| 0x58 | set_entity_data | 0x70 | teleport_entity |
| 0x2E | move_entity_pos | 0x2F | move_entity_pos_rot |
| 0x30 | move_entity_rot | 0x48 | rotate_head |
| 0x42 | remove_entities | 0x1F | entity_event |
| 0x15 | container_set_slot | | |

**4-2(메뉴)에 필요한 id (이번에 `packets.json` 에서 확인):**
- 클라이언트바운드: `container_close` 0x12, `container_set_content` 0x13, `container_set_data` 0x14, `container_set_slot` 0x15, `open_screen` 0x33, `place_ghost_recipe` 0x37, `merchant_offers` 0x2D, `set_equipment` 0x5B, `block_entity_data` 0x07, `animate` 0x03, `hurt_animation` 0x24, `award_stats` 0x04, `level_particles` 0x29, `explode` 0x20, `add_experience_orb` 0x02, `update_attributes` 0x75, `update_mob_effect` 0x76, `remove_mob_effect` 0x43, `set_passengers` 0x5F, `sound` 0x68, `sound_entity` 0x67, `stop_sound` 0x6A.
- 서버바운드: `container_button_click` 0x0D, `container_click` 0x0E, `container_close` 0x0F, `container_slot_state_changed` 0x10, `interact` 0x16, `pick_item` 0x20, `place_recipe` 0x22, `player_action` 0x24, `rename_item` 0x2A, `select_trade` 0x2D, `set_carried_item` 0x2F, `set_creative_mode_slot` 0x32, `swing` 0x36, `use_item_on` 0x38, `use_item` 0x39.
- 엔티티 종류 id 는 `furnace-server/src/main/resources/registries/entity_types-1.21.1.txt` 의 줄 순서(0부터). `minecraft:item` = 58, `minecraft:player` = 128 (총 130종).

### 3.9 메뉴 종류 레지스트리 id (`minecraft:menu`, 1.21.1 — `registries.json` 에서 확인)

| id | 이름 | id | 이름 |
|---|---|---|---|
| 0 | generic_9x1 | 13 | enchantment |
| 1 | generic_9x2 | 14 | furnace |
| 2 | generic_9x3 | 15 | grindstone |
| 3 | generic_9x4 | 16 | hopper |
| 4 | generic_9x5 | 17 | lectern |
| 5 | generic_9x6 | 18 | loom |
| 6 | generic_3x3 | 19 | merchant |
| 7 | crafter_3x3 | 20 | shulker_box |
| 8 | anvil | 21 | smithing |
| 9 | beacon | 22 | smoker |
| 10 | blast_furnace | 23 | cartography_table |
| 11 | brewing_stand | 24 | stonecutter |
| 12 | crafting | | |

(현재 코드는 `OpenScreen(containerId, 2, "Chest")` 로 generic_9x3 = 2 만 쓴다.)

---

## 4. 아키텍처 요약

경로는 모두 `furnace-server/src/main/java/org/domi/furnace/server/` 기준(이하 `S/`).

### 4.1 패키지

| 패키지 | 내용 |
|---|---|
| `S/FurnaceMain`, `ServerCore` | 진입점·전역 서비스(명령, 플러그인, 이벤트, 게임룰, 틱) |
| `S/net/` | Netty 서버. `ConnectionHandler`(핸드셰이크→로그인→설정→플레이 패킷 처리, 약 1,900줄), `net/protocol/play/*`(패킷 클래스), `net/io`(Types, NBT), `net/auth`(온라인 모드) |
| `S/world/` | `World`(공유 자원 + regionizer), `Region`, `RegionLevel`, `Regionizer`, `ChunkColumn`/`ChunkSection`(청크 저장), `anvil/`(디스크), `gen/`(월드젠: 노이즈·바이옴·카버·feature·구조물·jigsaw), `WorldBorder`, `GameRules`, `WorldClock` |
| `S/block/` | 블록 엔진. `ChunkLevel`(블록 레벨 + 엔티티 레벨의 본체), `BlockLevel`(인터페이스), `BlockBehavior`(블록 동작 기본 클래스 — 바닐라 `BlockBehaviour`), `types/`(바닐라 블록 클래스 이식, 파일 284개), `entity/`(블록 엔티티), `ItemUse`, `BlockBreaking`, `Shape`(VoxelShape), `Clip`, `NeighborUpdater`, `FluidTicker` |
| `S/entity/` | `Entity`, `ItemEntity`, `Player`(+`PlayerVitals`, `FoodData`, `PlayerData`, `Abilities`), `PlayerInventory`, `EntityStore`, `EntityTracker`, `SynchedData`, `EntityType`, `Entities`, `AABB`, `ItemStack`, `EntityView` |
| `S/item/` | 아이템 컴포넌트 57종 코덱(저장 NBT/JSON·네트워크), 패치 모델 |
| `S/data/`, `S/recipe/`, `S/loot/`, `S/advancement/` | 데이터 팩 로더, 레시피(1,290개), 전리품 표 평가, 발전 과제(1,399개) |
| `S/command/` | Brigadier 명령(`BuiltinCommands`, `builtin/`, `arguments/`) |
| `S/plugin/`, `S/sched/` | 플러그인 로더/이벤트/스케줄러 |
| `S/config/` | `server.properties`, `furnace.properties` |

### 4.2 스레딩 모델 — Folia 식 동적 regionizer

설계 문서: `docs/superpowers/specs/2026-10-17-dynamic-regionizer.md`

- 청크를 **구역(section) = 2^shift × 2^shift 청크**(기본 shift 4 → 16×16 청크)로 묶고, 구역들의 집합이 **region** 이다. 불변식: 서로 다른 region 의 구역은 체비셰프 거리로 `merge-radius`(기본 1) 구역보다 멀다 → 블록 갱신·플레이어 시야가 region 을 넘지 않는다.
- 청크 내용(`ChunkColumn`)은 월드 공용 맵(`World.chunks`, `ConcurrentHashMap<long, ChunkColumn>`)에 있지만 **그 청크의 구역을 가진 region 스레드만** 만진다.
- 작업 스레드 풀(`furnace-region-N`, 설정 `region-threads`)이 가장 이른 다음 틱 시각의 region 부터 한 번에 한 스레드가 돈다. `Region.runTick`: 작업 큐(inbox) → 지연 작업 → 게임 틱(`Region.tick`).
- **작업 보내기:** 모든 월드 작업은 청크 키를 달고 소유 region 에서 돈다. `World.runAtChunk(ChunkPos, Consumer<Region>)`, `World.runAt(blockX, blockZ, task)`, `runAtRequested(...)`(클라이언트가 보낸 좌표용), `submitAtChunk(...)`(결과 `CompletableFuture`). 큐에 넣은 뒤 그 region 이 합쳐지거나 나뉘면 `Region.mine()` 이 아니라고 보고 지금 주인에게 다시 보낸다.
- **합치기(merge)/나누기(split):** 틱 사이에만(`Regionizer` 잠금 아래). 가까워진 region 은 합치고(`absorbData`, `RegionLevel.absorbFrom`), 멀어지면 연결 성분으로 나눈다(`splitData`, `RegionLevel.splitInto`). **자료는 청크(위치) 단위로 옮긴다** — 예약 틱, 변경 방송 대기, 블록 이벤트, ack 대기, 블록 엔티티 틱 목록, **엔티티**(`entities.splitTo(target.entities, inTarget)` + `e.setLevel(target)`).
- 플러그인 `scheduler.region(RegionPos)` 의 RegionPos 는 청크 좌표.
- 결과 동일성: 한 region 안에서는 한 스레드가 바닐라 순서로 돈다. 기본 구성에서 월드 전체가 한 region 이면 바닐라와 같고, region 별 무작위 틱 난수는 스레드가 나뉘면 바이트 단위로 같을 수 없고 통계적으로만 같다.
- 소유 확인: `world.assertOwns(region)`. `Regionizer.chunkKey(cx,cz)` / `chunkX(key)` / `chunkZ(key)` 로 키 변환.

### 4.3 레벨·틱 순서

`Region.tick()` → `RegionLevel.tickBlocks(tickingChunks, n, blockEntityTicking, inRegion)`:

1. 예약 틱(`runScheduledTicks`, 블록·유체)
2. 무작위 청크 틱(`shuffle` 후 `tickChunk` — 천둥·얼음/눈·구역별 무작위 틱)
3. `broadcastChanges()` — 블록 변경 방송
4. `trackEntities` — 바닐라 `ChunkMap.tick`(엔티티 추적)
5. `runBlockEvents()`
6. `tickEntities` — 엔티티 틱
7. `tickBlockEntities` — 블록 엔티티 틱

이후 `Region.tick` 이 플레이어 처리(`processMoves` → `level.playerMoved`, `tickVitals`, `touchEntities`(줍기), `tickBreaking`, `broadcastInventory`, 청크 전송, 가시성), `handOffEntities`, `flushAcks`, 청크 퇴출/자동 저장을 한다.

- 엔티티 틱 청크 = 이 region 이 가진 로드된 청크 중 플레이어가 시뮬레이션 거리 −1(체비셰프) 안인 것(`Region.blockEntityTicking`).
- 무작위 틱 청크 = 비관전 플레이어 시뮬레이션 거리(체비셰프 ≤ min(sim,11)−1, 최대 8) + 청크 중심 128 칸 안(`collectTickingChunks`).

### 4.4 엔티티 시스템 (4-1)

- **저장소:** `EntityStore`(region 의 `RegionLevel`/`ChunkLevel` 소유). 틱 목록은 `LinkedHashMap`(넣은 순서, 틱 중 추가는 다음 틱부터 — `tickSnapshot()`), 구역 조회용 `TreeMap<Long(SectionPos 키), ArrayList<Entity>>` — 바닐라 `SectionPos` 정렬 순서로 AABB 조회(`getEntities`, `getEntitiesOfClass`)해서 줍기·합치기 순서가 바닐라와 같다.
- **`Entity`**(약 915줄): 위치·속도·회전·몸통 AABB, `move`(블록 충돌 — 바닐라 `BlockCollisions` 칸 순회, VoxelShape 격자 충돌, y 먼저 · |x|<|z| 면 z 먼저), 디딘 블록(`checkSupportingBlock`), 낙하(`fallOn`)·`updateEntityAfterFallOn`·`stepOn`·`entityInside`·속도 계수·`makeStuckInBlock`·`moveTowardsClosestSpace`, `baseTick`(물/용암 높이·흐름 밀기(`FlowingFluid.getFlow` 이식)·첨벙 소리·눈 높이 유체·불·용암 피해·세계 밑 제거), `RemovalReason`, `save/load`(NBT). 엔티티 데이터 번호 0..7(공유 플래그·공기·이름·이름 보임·조용함·중력 없음·자세·얼음 틱), 아이템은 8.
- **`SynchedData`:** 바닐라 `SynchedEntityData`. dirty 값만 `packDirty`, 처음 보일 때는 `nonDefaultValues`. 아이템 스택은 바닐라처럼 객체 동일성 비교.
- **`ItemEntity`**(270줄): 중력 0.04·공기 0.98·디딘 블록 미끄러움, 땅에서는 **4틱마다만 이동**(나머지 틱엔 중력만 누적), 물·용암에서 뜨기, 합치기(움직였으면 2틱·아니면 40틱, 같은 아이템·컴포넌트, 64 상한), 수명 6000, 체력 5, 줍기(`playerTouch`: 지연·주인·부분 줍기)·`take_item_entity`, NBT(Health·Age·PickupDelay·Thrower·Owner·Item).
- **`EntityType`:** 레지스트리 번호·크기·눈높이·추적 거리·갱신 주기·델타 추적·불 면역. 지금은 `ITEM` 만 정의(`of(...)` 로 추가). **`Entities.load(level, tag)`** 가 저장 NBT → 엔티티(지금 `minecraft:item` 만, 모르는 종류는 null → 호출 쪽이 원본 NBT 보존).
- **추적(`EntityTracker`):** 바닐라 `ChunkMap.TrackedEntity + ServerEntity`. 보임 판정은 **엔티티 구역 이동 · 플레이어 이동 패킷(`Region.processMoves` → `level.playerMoved`) · 추가** 때만 다시 한다(거리 = min(종류 범위 × 방송 비율, 시야 × 16), 청크 전달 완료 확인 `chunkTrackedBy`). 스폰은 **묶음(bundle: add_entity + 데이터 + 속도)**. 변화는 갱신 주기·`hasImpulse`·데이터 변화 때 1/4096 델타(넘침·400번째·땅 닿음 변화면 teleport)·속도(1e-7)·데이터·머리. `hurtMarked` 즉시 속도. 각도는 `Mth.floor`(바닐라) — `Types.writeAngle` 도 이에 맞춰 고침.
- **region 이동:** 엔티티가 다른 region 소유 청크로 들어가면 `Region.handOffEntities` → 소유자가 있으면 `world.runAtChunk(..., r -> r.level().adoptEntity(e))`; **소유자 없는(로드 안 된) 청크**면 `ChunkLevel.evictToDisk(e)` 가 `UNLOADED_TO_CHUNK` 로 제거 후 entities 파일에 덧붙임(`World.appendEntitiesAsync`).
- **저장:** `entities/` 지역 파일(바닐라 `EntityStorage` 형식 `{DataVersion, Position, Entities}`). 청크 로드 때 IO 풀에서 읽어 넣고(`Region.loadEntities`), 내릴 때 저장 후 제거, 자동 저장 때 엔티티가 있거나 있던 청크. 모르는 종류는 원본 NBT 보존.
- **줍기:** `Region.touchEntities(p)` 가 플레이어마다 매 틱, 몸통 상자를 (1, 0.5, 1) 넓힌 상자 안 엔티티에 `playerTouch`.
- **드롭 생성:** `ChunkLevel.popResource(pos, stack)`, `popResourceFromFace(pos, dir, stack)`(doTileDrops 확인 포함), `dropContents(pos, ItemStack[])`(= `Containers.dropContents`: **빈 칸도 위치 난수 3번** 소비, 10~30 개씩, 삼각 분포 속도). 플레이어 버리기는 연결 스레드에서 인벤토리를 먼저 바꾼 뒤 `world.runAt(...)` → `Region.playerDrop(p, stack, dropAround, includeThrower)`.
- **엔티티 id:** `World.entityIds()` 의 `EntityIdAllocator` 를 플레이어와 공유(`FurnaceMain` 이 같은 할당기를 연결). `RegionLevel.nextEntityId()` 가 이를 쓴다(`ChunkLevel` 기본은 폴백 증가 카운터 — 테스트용).
- **블록 훅(Entity 쪽 호출):** `fallOn`/`stepOn`/`entityInside` 등. 구현된 블록: 불·영혼 불, 선인장, 거미줄·가루눈, 슬라임, 침대, 꿀, 건초, 경작지, 거품 기둥, 가마솥(용암/물), 압력판(나무/돌/무게판), 인계선.

### 4.5 플레이어 인벤토리 (4-1a)

`S/entity/PlayerInventory.java`(167줄) — **서버 권위**:
- 창 칸 0..45: `0` 제작 결과, `1..4` 제작 칸, `5..8` 머리·가슴·다리·발, `9..35` 주 인벤, `36..44` 핫바, `45` 왼손. + 커서(`carried`) + 든 칸(`selected`).
- `slots[]`(서버 진실) 와 `remote[]`(클라이언트가 아는 값)를 따로 들고 `broadcastChanges()` 가 바닐라 `AbstractContainerMenu.broadcastChanges` 처럼 **달라진 칸만** `container_set_slot` 으로 보낸다(`stateId` 관리 `incrementStateId`). `fullState()` = `container_set_content`.
- `add(stack, infinite, maxStack, damaged)`: 바닐라 `Inventory.add` 칸 순서(든 칸 → 왼손 → 0..35, 다음 빈 칸) + **"마지막 시도가 다 넣지 못하면 false"** 특이점(부분 줍기에서 스택이 조용히 줄어든다).
- 메서드는 `synchronized` — region 스레드(줍기·쓰기)와 연결 스레드(클릭·저장)가 함께 쓴다.
- `Inventory` 번호 → 창 칸: `windowSlot(inventoryIndex)` (0..8 → 36..44, 9..35 → 그대로, 36..39 → 장비, 40 → 45).

### 4.6 컨테이너(상자) — 현재의 임시 구현

- `ConnectionHandler.openChest(x,y,z)`(약 1875줄): 컨테이너 id 1..100 순환(`nextContainerId`), `Player.openChest(containerId, x,y,z)` 로 열린 상자 기억, 소유 region 에서 `ChestBlockEntity`(전리품 풀기 포함) 내용을 읽어 `OpenScreen(containerId, 2, "Chest")` + `ContainerSetContent(63칸: 상자 27 + 주 27 + 핫바 9, stateId 1)` 전송. **더블 상자 병합 없음.**
- `case 0x0E`(container_click): 클라이언트가 보낸 `changedSlots` + `carried` 를 받아, **플레이어 인벤 창(containerId 0)** 은 `applyInventoryClick`(아이템 종류별 총량 보존 검사 후 수용, 아니면 `fullState()` 로 되돌림), **상자 창**은 `clickType==0`(PICKUP)만 처리해 칸 번호로 상자/인벤에 직접 반영. `stateId` 는 읽고 버린다(검증 안 함). 그 외 clickType(QUICK_MOVE·SWAP·CLONE·THROW·QUICK_CRAFT·PICKUP_ALL)은 무시(버리기 THROW·창 밖 클릭은 `inventoryDropClick` 으로 일부 처리).
- 제작 결과 칸(0) 변경 보고는 거부, 제작 격자/레시피 매칭 없음.
- **4-2 에서 이 전체를 서버 권위 메뉴로 교체한다**(7장).

### 4.7 블록 엔진

- `block/types/` 에 바닐라 블록 정의 종류(definition type) 241종 이식(누락 검사 테스트 `BlockTypesTest`). 블록 속성/상태/모양은 `resources/blocks/*.txt`(바닐라에서 파생한 압축 표)에서 로드.
- `BlockBehavior`(222줄)은 바닐라 `BlockBehaviour` 대응: `setPlacedBy`, `playerWillDestroy`, `playerDestroy`, `getDrops`, `getDestroyProgress`, `useItemOn`, `useWithoutItem`, `attack`, `canPlaceLiquid`, `getCollisionShape(state, level, pos, BlockActor)` 등. **`BlockActor`(플레이어 같은 행위자 인터페이스)가 `Entity` 가 아니다** → 엔티티별 충돌 문맥(가루눈·발판)이 막혀 있음.
- 블록 엔티티: `block/entity/BlockEntity` 일반 틀(종류 id = block_entity_type 순서, load/saveAdditional, getUpdateTag, setChanged) + `BlockEntities` 팩토리(comparator, daylight_detector, piston). 상자는 별도 경로(`world/ChestBlockEntity`, `RegionLevel.createBlockEntity` 에서 분기). 나머지 40종은 NBT 보존만.
- 피스톤(`PistonBaseBlock`, `PistonStructureResolver`, `PistonMovingBlockEntity`, `MovingPistonBlock`), 유체(`FlowingFluid`·물·용암, `FluidTicker`), 레드스톤 가루(1.21.1 기본 알고리즘, 갱신 순서 = 바닐라 HashSet 순회 재현), 무작위 틱(`ChunkLevel.tickChunk`).
- 아이템 사용: `block/ItemUse`(양동이·유리병·부싯돌·삽·도끼·밀랍·가위·뼛가루·엔더의 눈 …), 서버 시선 판정 `Item.getPlayerPOVHitResult` → `Level.clip`(`Clip`), `Region.handleUseItem`.
- 블록 파괴: `BlockBreaking`(ServerPlayerGameMode 이식).

### 4.8 영속성·네트워크

- 청크: Anvil 형식(`world/anvil/*`, `AnvilChunkCodec`)으로 `region/` 에 저장, 모르는 NBT·블록 엔티티 보존. 예약 틱은 `block_ticks`/`fluid_ticks`. 엔티티는 `entities/` 별도 지역 파일(바닐라 1.17+ 방식). 플레이어는 `playerdata/<uuid>.dat`(체력·허기·경험치·게임 모드·능력·스폰·인벤토리(`components` compound)·recipeBook).
- 접속 패킷 순서·내용은 바닐라 1.21.1 캡처와 일치하도록 맞춰 둠(레지스트리 동기화 순서 포함).
- `ConnectionHandler.handlePlay` 의 `switch(packetId)` 가 서버바운드 play 패킷 58개 중 25개를 처리(6장 참고). 연결 스레드는 Netty 이벤트 루프이고, 월드 변경은 `world.runAt...` 로 region 스레드에 넘긴다.
- **알려진 선재 불일치:** 청크 시야가 바닐라의 원형(`ChunkTrackingView`) 대신 **정사각형(체비셰프)** 이다.

### 4.9 테스트 (`furnace-server/src/test/java/org/domi/furnace/server/`)

최근 작업과 관련된 주요 클래스:

| 클래스 | 위치 | 내용 |
|---|---|---|
| `ItemEntityTest` | `block/` | 아이템 엔티티 물리·합치기·줍기·드롭·NBT·블록 훅 (@Test 22개) |
| `EntityRegionTest` | `world/` | region 틱에서 엔티티가 돌고 줍는지, 부순 블록이 엔티티를 떨구는지 |
| `ItemUseTest` | `block/` | 양동이·괭이 등 아이템 사용 |
| `PistonTest` | `block/` | 피스톤·비교기 |
| `FluidTest` | `block/` | 유체 흐름 |
| `BlockArgumentsTest` | `block/` | 블록 상태/술어 인자 파서(`/setblock`, `/fill`) |
| `RandomTickTest` | `block/` | 무작위 틱·날씨 |
| `ConnectionInputTest` | `net/` | ConnectionHandler 입력 검증(비정상 이동·임의 좌표 요청·스폰 좌표) |
| 기타 | | `BlockBreakingTest`, `BlockPlacementTest`, `BlockTypesTest`, `GameplayFlowTest`, `ChestInteractionTest`, `PlayerVitalsTest`, `*VanillaTest`(바닐라 골든), `oracle/ScenarioBot`·`WorldDump`(수동 실행용 — 인자가 없으면 skip) |

테스트 헬퍼: `TestServers.java`, `net/TestClient.java`(가짜 클라이언트), `PacketCapture`.

---

## 5. 진행 상황

### 5.1 로드맵 단계 표 (`docs/feature-parity/roadmap-2026-10-09.md`)

| 단계 | 내용 | 상태 |
|---|---|---|
| 1 | 월드젠(오버월드·네더·엔드·구조물 전종, jigsaw, sculk 근사 등) | ✅ (후속: 미완성 청크 디스크 저장, 장식 저장소 메모리, 구조물 시작점 직렬화, DF 바이트코드 컴파일러는 사용자 결정으로 보류) |
| 2-1 | 설정·파일 (`server.properties` 바이트 일치 등) | ✅ |
| 2-2 | `level.dat` + 월드 형식 | ✅ (구조물 시작점 직렬화 제외) |
| 2-3 | 온라인 모드(암호화·세션 인증·프로필·Velocity 포워딩) | ✅ |
| 2-4 | 명령 프레임워크(Brigadier) + 기본 명령 | ✅ (일부 — 43개 미구현) |
| 2-5 | 서명된 채팅 | ✅ |
| 2-6 | 시간·날씨·난이도·게임룰·월드 보더 | ✅ |
| 2-7 | 게임 모드·체력·허기·피해·사망·리스폰·경험치 | ✅ (서버 뼈대 범위) |
| 2-8 (a~d) | 플러그인 API v0: 텍스트 타입 / 설정 / 마크업 / 로더·이벤트·명령·권한·스케줄러 | ✅ |
| 3-1 | 데이터 팩 로더 | ✅ |
| 3-2a | 레시피 로드·`update_recipes` 바이트 일치·레시피 북 | ✅ (재료 매칭/제작 없음) |
| 3-2b | 전리품 표 평가 엔진(바닐라 정답지와 전부 일치) | ✅ (보물 지도·`/loot`·`random_sequences.dat` 남음) |
| 3-2c | 발전 과제 | ✅ (트리거 일부만 연결) |
| 3-3 | 아이템 컴포넌트 57종 | ✅ |
| 3-4a | 블록 동작 틀 + 블록 종류 241종 | ✅ |
| 3-4b | 블록 파괴 | ✅ |
| 3-4c | 무작위 틱·날씨·예약 틱 저장 | ✅ |
| 3-4d | 유체 + `/setblock`·`/fill` | ✅ |
| 3-4e | 블록 엔티티 틀 + 피스톤·비교기 | ✅ |
| 3-5 | 아이템 사용(use_item, 서버 시선 판정, 양동이 등) | ✅ |
| **4-1a** | 서버 권위 플레이어 인벤토리 | ✅ |
| **4-1b** | 엔티티 틀 + 아이템 엔티티 | ✅ |
| **4-2** | **컨테이너 메뉴 프레임워크** | ⏭ 다음 |
| 4-3 이후 | 명령 보강 → 전투/효과/속성 → 폭발 → 빛 → 티켓 → 몹 → 차원 (10장 순서) | 미착수 |
| 4단계(Paper) | Paper 설정 키, `/tps` 등, RCON, 안티 엑스레이 … | 미착수 |
| 5단계 | `furnace-compat-paper` 어댑터 | 모듈 없음 |

(로드맵 문서는 4-1 이 한 항목이고 이 문서는 4-1a/4-1b 로 나눠 부른다. 로드맵 문서의 "지금 상태" 일부 문장, 예: "채팅·명령·암호화·게임플레이 없음"은 옛 문장이라 현재와 다르다.)

### 5.2 이번 세션 커밋 (오래된 것 → 최근)

| 해시 | 내용 |
|---|---|
| `6037c20f` | 3-4c 무작위 틱·날씨·예약 틱 저장: 바닐라 `ServerLevel.tickChunk` 순서 |
| `8b7a800d` | 3-4d 유체: `FlowingFluid`·물·용암 흐름 + `/setblock`·`/fill` |
| `18b0e140` | 3-4e 블록 엔티티 틀 + 피스톤·비교기 |
| `0b3746da` | 3-5 아이템 사용: `use_item` 패킷·서버 시선 판정·`Item.use`/`useOn` |
| `58e42eb5` | 4-1a 서버 권위 플레이어 인벤토리: `PlayerInventory`·`broadcastChanges` |
| `3738fd79` | 4-1b 엔티티 틀 + 아이템 엔티티: 물리·추적·줍기·저장 (HEAD) |

그 앞(참고): `38dcb91a` 3-4b 블록 파괴, `b6c06e00` 오라클 types-mix, `7855254c`…`90fe9e25` 블록 종류 이식 묶음 1~10(241종), `b7b4b521`·`1755ec67`·`616846d6` 동적 region, `0291d477` 3-3 컴포넌트 등.

### 5.3 3-4c ~ 4-1b 상세 요약

**3-4c 무작위 틱·날씨·예약 틱 저장 (`6037c20f`)**
- 바닐라 `ServerLevel.tickChunk` 순서: 천둥 `nextInt(100000)` → 얼음/눈(`randomTickSpeed` 번 `nextInt(48)`) → 구역별 무작위 틱(`randValue` LCG, `isRandomlyTicking` 구역만, 용암 유체 `randomTick`).
- 서버 틱 순서: 예약 틱 → 청크 틱(레벨 난수로 섞음) → 변경 방송 → 블록 이벤트. 청크 선택: 비관전 플레이어 128칸(청크 중심 유클리드) + 시뮬레이션 거리. `InhabitedTime` 증가. 구역 무작위 틱 칸 수를 `setBlock` 때 갱신(`tickingBlockCount`). 바이옴 기후(`BiomeWeather`)로 얼음·눈·가마솥 강수.
- 예약 틱을 청크 NBT `block_ticks`/`fluid_ticks`(`{i,x,y,z,t,p}`)로 저장·로드.

**3-4d 유체 (`8b7a800d`)**
- `FlowingFluid`·`WaterFluid`·`LavaFluid`: 새 양 계산(`getNewLiquid`, 원천 변환 게임룰), 아래/옆 퍼지기, 구멍 찾기(`getSlopeDistance`, 적용 순서는 EnumMap 서수), 벽 통과(`Shapes.mergedFaceOccludes`), `canHoldFluid`, 용암 퍼짐 지연(3/4 확률 4배), 용암이 물 위로 떨어지면 돌, 물이 부순 블록 드롭, 용암 무작위 틱 불 번지기(블록·유체 두 번 도는 바닐라 특이점 포함).
- `LiquidBlockContainer`(잠기는 블록 등). `/setblock`·`/fill`(바닐라 SetBlockCommand/FillCommand, 블록 상태·술어 인자 파서). `/fill` 은 영역이 한 region 에 로드돼 있어야 한다(Folia 방식).
- 오라클 `fluids` 일치.

**3-4e 블록 엔티티 틀 + 피스톤·비교기 (`18b0e140`)**
- 일반 `BlockEntity`(종류 id = block_entity_type 순서, load/saveAdditional, getUpdateTag, setChanged, 틱 목록 — 틱 중 생긴 것은 다음 틱부터, 엔티티 틱 청크에서만). `LevelChunk.setBlockState` 의 블록 엔티티 규칙. 청크 저장·로드·청크 패킷·region 합치기/나누기.
- 피스톤: `PistonBaseBlock`(신호·준연결·블록 이벤트 0/1/2·isHandlingTick), `PistonStructureResolver`(12개·슬라임/꿀·재배열), `PistonMovingBlockEntity`(진행 0.5/틱·finalTick·NBT), `MovingPistonBlock`. 비워진 칸 갱신 순서는 바닐라 `HashMap<BlockPos>` 순회 순서를 같은 해시로 재현. 블록 이벤트 큐 중복 제거(`ObjectLinkedOpenHashSet`).
- 비교기 블록 엔티티, 햇빛 감지기 20틱 틱, 상자 찬 정도 비교기 출력(이중 상자 포함).
- 오라클 `pistons` 일치.

**3-5 아이템 사용 (`0b3746da`)**
- `use_item`(0x39) → `ServerPlayerGameMode.useItem`(관전자·재사용 대기·시선 갱신), 서버 시선 판정(`Item.getPlayerPOVHitResult` → `Level.clip`: DDA 칸 순회·VoxelShape/AABB clip·상호작용 모양 면·유체 모드).
- `Item.use`: 양동이(뜨기·붓기·잠기는 블록·울트라웜 증발·서바이벌 빈 양동이), 유리병, 연잎·개구리알, 갑옷·겉날개 장착. `Item.useOn`: 부싯돌·화염구, 뼛가루, 괭이·삽·도끼(껍질·녹·밀랍), 밀랍, 가위, 물병→진흙, 디버그 막대, 나침반, 엔더의 눈(BlockPattern). 도구 부서짐 이벤트(주 손 47·왼손 48).
- 오라클 `items` 일치.

**4-1a 서버 권위 인벤토리 (`58e42eb5`)** — 4.5 참조. `container_set_slot` 은 바닐라처럼 달라진 칸만. 테스트 통과.

**4-1b 엔티티 틀 + 아이템 엔티티 (`3738fd79`)** — 4.4 참조. 요약:
- `Entity` 바탕(충돌 이동·유체·낙하·불·데이터), `ItemEntity`, `EntityTracker`, `EntityStore`, `entities/` 저장, region 합치기·나누기·넘김.
- 드롭 생성: `popResource`·`popResourceFromFace`·상자 부수면 내용물 쏟기(`dropContents`)·퇴비통 뼛가루·호박 깎기 씨앗(난수 4번 — 전에 2번만 뽑던 차이 수정)·플레이어 버리기(Q·Ctrl+Q·크리에이티브 창 밖(버리기 한도 200)·창 밖 클릭·THROW 클릭·창 닫을 때 커서)·죽을 때 `dropAll`(소멸 저주 제외, 아무 쪽으로)·양동이 등 넘친 결과물.
- 바닐라 특이점 보존: `Inventory.add` 부분 줍기는 false 반환(스택은 조용히 줄어듦), 땅의 아이템은 4틱마다만 이동(중력은 누적), 경작지 `fallOn` 은 어떤 엔티티든 레벨 난수 소비, 거품 기둥은 수면 틱마다 레벨 난수 double 8개 소비, `Containers.dropItemStack` 은 빈 칸도 난수 3개 소비.
- 오라클 `items`·`break-chain`·`survival-dig`·`pistons` 일치(items·survival-dig 는 `randomTickSpeed 0` — 무작위 틱은 바이트 단위로 같지 않아 물 옆 경작지·잔디 번짐이 실행마다 달랐다).

---

## 6. 미구현 목록

(직전 세션의 `unimplemented-inventory.md` 전체 요약 + 4-1 이후 남은 것. **인벤토리는 4-1 이전 시점 기준이고, 4-1 로 아이템 엔티티·엔티티 틀·서버 권위 인벤토리가 해소되었다.** 해소된 항목은 취소선 대신 "(4-1 해소)"로 표시.)

규모: 작음(수 시간~1일) / 중간(며칠) / 큼(1주 이상).

### 6.0 요약 (가장 큰 구멍부터)

1. **엔티티 체계:** 4-1 이전엔 `Player` 하나뿐이었다. 지금은 틀 + 아이템 엔티티만. 바닐라 엔티티 종류 130개 중 구현 2개(player, item). 경험치 구슬·떨어지는 블록·점화된 TNT·몹·투사체·탈것·AI·스폰은 전부 없음. TODO(엔티티) 단독 태그 84줄(결합 포함 시 더 많음) 중 일부만 4-1 로 해소.
2. **컨테이너 메뉴 체계 없음.** 25종 중 상자 9x3 하나(더블 상자 병합 없음), 클릭은 PICKUP 만, 제작·화로·모루·마법 부여·양조·거래 전부 없음. TODO(메뉴) 24줄. → **다음 작업(4-2).**
3. **블록 엔티티 44종 중 동작 있는 것 4종**(상자, 피스톤, 비교기, 햇빛 감지기). 나머지 40종은 NBT 보존만.
4. **빛 엔진 없음.** 하늘빛 15/블록빛 0 고정, 청크 패킷은 항상 풀 skylight, 저장은 `isLightOn=0`.
5. **차원은 오버월드 하나만 서버가 호스팅.** 네더·엔드 월드젠은 `NoiseWorldSource.Dimension` 에 있으나 서버(`FurnaceMain` 의 `new World(...)` 1개, `ServerCore.dimension()` 이 항상 `"minecraft:overworld"`)는 안 씀. 포털·차원 이동 없음.
6. **명령 83개 중 40개만**(별칭 포함). 43개 없음.
7. **서버바운드 play 패킷 58개 중 25개만 처리, 33개 미처리.**
8. 진행 시스템: 통계(`awardStat` 빈 메서드)·점수판·팀·보스바·타이틀 명령·포션 효과·속성·마법 부여·폭발·수면/침대·파티클·맵 없음. 발전 과제는 일부 트리거만.
9. 운영: Paper 설정 파일·`/tps`·`/version`·`/plugins`·RCON/Query·리소스 팩·transfer·쿠키·몹 스폰 한도 없음.

**잘 되어 있는 것:** 월드젠(전 차원·구조물), 온라인 모드·암호화·서명 채팅, 데이터 팩, 레시피 목록/레시피 북 전송, 전리품 표 평가, 발전 과제 핵심, 아이템 컴포넌트 57종, 블록 클래스 241종·유체·무작위/예약 틱·피스톤, 월드 보더·게임룰·시간·날씨, 허기/체력/사망/리스폰/경험치, 플러그인 API v0, **(4-1)** 서버 권위 인벤토리·엔티티 틀·아이템 엔티티.

### 6.1 로드맵 문서의 "남은 것" 전수

| 단계 | 남은 것 | 규모 | 의존 |
|---|---|---|---|
| 2-7 | 드롭(아이템 엔티티 4-1 해소, 경험치 구슬 남음), 방어구·효과 경감, 용암·불·선인장·마그마 등 블록 피해(4-1 일부 해소), 질식(충돌 모양), 소리, 침대·리스폰 정박기, 스폰 반경 무작위(spawnRadius), 인벤토리 클릭 서버 재현(제작·버리기) | 큼 | 엔티티, 효과/속성, 메뉴 |
| 3-2b | 보물 지도(구조물 검색), `random_sequences.dat` 저장, `/loot` 명령, 상자 외 컨테이너(통·호퍼·디스펜서) 전리품 | 중간 | 구조물 시작점 직렬화, 블록 엔티티 컨테이너 |
| 3-2c | 위치 조건의 구조물 판정, 전리품·함수 보상, 아이템 컴포넌트 조건, 몹·차원·잠자기 등 사건이 생기는 대로 트리거 연결(현재 연결: tick·impossible·recipe_unlocked·inventory_changed·placed_block·item_used_on_block) | 중간 | 엔티티·차원·수면, `/function` |
| 3-3 | 아이템 인자 `/give @s x[컴포넌트]`·`/item`·`/data`, 네트워크 container 뒤쪽 빈 칸 보존, 프로필 속성 중복 합치기, 태그 존재 검사(HolderSet "#태그"), debug_stick_state 속성 검사 | 작음~중간 | 명령 확장 |
| 3-4a | 블록 엔티티 동작(상자·화로·호퍼·비교기 출력·피스톤 이동 등), 엔티티 관련(밟기·투사체·떨어지는 블록), 드롭·경험치, 지형 생성 연결(묘목·잔디·이끼 뼛가루), 빛 엔진 값, 메뉴 화면 | 큼 | 아래 각 절 |
| 3-4b | 경험치 구슬 생성(난수 소비는 이미 바닐라와 같게 맞춰 둠). 아이템 엔티티 생성은 4-1 해소 | 작음 | 엔티티 |
| 3-4d | 엔티티 유체 밀기(4-1 해소), 블록 엔티티 NBT 인자(`/setblock` 등) | 작음 | - |
| 3-4e | **피스톤이 엔티티 밀기**, 나머지 블록 엔티티(화로·호퍼·발사기 등 — NBT 보존만), 생성 블록 엔티티의 청크 패킷 `updateTag` | 큼 | 엔티티 |
| 3-5 | 먹기·마시기 등 오래 쓰는 아이템(using item 상태), 엔티티를 만드는 아이템(던지기·배·수레·생성 알·액자 등), 드래곤의 숨결. 인벤토리 가득 시 떨구기는 4-1 해소 | 큼 | 엔티티, 효과 |
| 청크 패킷 | 생성 블록 엔티티(스포너·표지판 등)가 청크 패킷에 아직 실리지 않음 | 작음 | 블록 엔티티 `getUpdateTag` |
| 청크 생성 후속 | 미완성(장식 중) 청크 디스크 저장, 장식 저장소 압축 전 블록 배열(~700MB) 줄이기, 지형 노이즈 최적화, 밀도 함수 바이트코드 컴파일러(사용자 결정으로 보류) | 중간 | - |
| 남은 후속 | 구조물 시작점·참조를 청크 NBT 에 직렬화(`/locate`·구조물 스폰·순정 월드 이동용), **조명 엔진**, **스폰 청크 상시 로드**, (엔티티를 청크 `entities` 위치에 저장은 4-1 로 `entities/` 파일 방식 구현됨) | 중간~큼 | 티켓 |
| 청크 관리 검토 | 바닐라 티켓 체계(로드 레벨·simulation-distance·엔티티/블록 틱 구역·스폰 청크·forceload·포털/텔레포트 티켓) 없음. 언로드 검사가 매 틱 region 의 모든 청크를 훑음 → 이벤트 기반 후보 | 큼 | 엔티티 틱 |
| 2-8c | 그라디언트·무지개 계산식 원본(Adventure) 대조 못 함 | 작음 | 네트워크 |
| 3단계 계획 | 아이템 엔티티·컨테이너(진행 중) → 빛 엔진 → 엔티티(몹·동물·투사체·전투) → 차원·포털 → 진행 시스템. 월드젠 시점 몹 스폰·스포너·구조물 상자 내용물은 전리품 표 이후 | 큼 | - |
| 4단계 | Paper 설정 파일(`paper-*.yml`, `spigot.yml`, `bukkit.yml`), `/tps /mspt /version /plugins /timings`, 안티 엑스레이, 스폰 한도·엔티티 활성 범위, RCON/Query/메트릭, 크래시 리포트·워치독, CI·배포 | 큼 | 엔티티 일부 |
| 5단계 | `furnace-compat-paper` 어댑터 — 모듈 자체가 아직 없음(`furnace-api`, `furnace-plugin-test`, `furnace-server` 만 존재) | 큼 | 4단계 |

### 6.2 TODO 주석 분석 (`S/**`, 4-1 이전 기준 223줄 / 태그 224개)

대부분 `block/**` 에 몰려 있고, 유형은 "블록 클래스는 바닐라대로 이식했으나 엔티티/블록 엔티티/메뉴가 없어 해당 분기를 건너뜀". (4-1 이후 `TODO(엔티티)` 일부가 구현되었으니 `grep -rn "TODO(" furnace-server/src/main/java | wc -l` 로 다시 센다.)

| 태그 그룹 | 개수(대략) | 기다리는 것 | 대표 위치 |
|---|---|---|---|
| `TODO(엔티티)` 단독 | 84 | 엔티티 체계 — `entityInside`/`stepOn`/`fallOn`/`onProjectileHit`, 엔티티 생성(좀벌레·올챙이·아기 스니퍼·거북·골렘·위더·TNT·경험치·떨어지는 블록), `pushEntitiesUp` | `BasePressurePlateBlock`, `FallingBlock.java:26`, `CarvedPumpkinBlock.java:20`, `CactusBlock`, `WebBlock`, `BaseFireBlock`, `block/BlockLevel.java`, `ChunkLevel`, `PistonMovingBlockEntity.java:82` |
| `TODO(블록 엔티티)`/`(블록엔티티)` 단독 | 53 | 해당 블록 엔티티 클래스(컨테이너·재생·명령·진동) | `ChestBlock.java:80,149`(여는 사람 수), `AbstractFurnaceBlock:36`, `JukeboxBlock`, `DecoratedPotBlock`, `ChiseledBookShelfBlock`, `CommandBlock`, `SculkCatalystBlock`, `SculkVeinBlock`, `CampfireBlock`, `command/builtin/BlockCommands.java:70,140,170`, `BlockPredicateArgument:67` |
| `TODO(메뉴)` 단독 | 8 | 컨테이너 메뉴 프레임워크 | `CraftingTableBlock:23`, `AnvilBlock:28`, `LoomBlock:18`, `GrindstoneBlock:19`, `StonecutterBlock:21`, `CartographyTableBlock:17`, `SmithingTableBlock:14` |
| `TODO(메뉴·블록엔티티)` 변형 | ≈16 | 메뉴 + 블록 엔티티 | `HopperBlock:33`, `FurnaceBlock/SmokerBlock/BlastFurnaceBlock:14`, `BrewingStandBlock:16`, `BeaconBlock:16`, `BarrelBlock:23`, `EnderChestBlock:28`, `ShulkerBoxBlock:25`, `DispenserBlock:23`, `CrafterBlock:73`, `LecternBlock:111`, `EnchantingTableBlock:33`, `SignBlock:55`, `JigsawBlock:30`, `StructureBlock:16` |
| `TODO(폭발)` | 10 | 폭발 시스템(`Level.explode`, `onExplosionHit`, BAD_RESPAWN_POINT) | `TntBlock:42`, `BedBlock:39`, `RespawnAnchorBlock:40`, `AbstractCandleBlock:35`, `LeverBlock:25`, `ButtonBlock:34`, `DoorBlock:42`, `TrapDoorBlock:33`, `FenceGateBlock:70`, `BellBlock:97` |
| `TODO(빛)` | 8 | 빛 엔진(sky/block 값, `getLightBlock` 표), 하늘 노출 | `BlockLevel.java:129,148,151,157`, `B.java:150`, `FrostedIceBlock:27`, `SpreadingSnowyDirtBlock:21`, `NetherrackBlock:17` |
| `TODO(드롭)` | 5 | `Containers.dropContentsOnDestroy`(블록 엔티티 내용물 쏟기 — 상자만 4-1 로 해소) | `BrewingStandBlock:22`, `CrafterBlock:67`, `DispenserBlock:55`, `BarrelBlock:29`, `HopperBlock:49` |
| `TODO(지형 생성)` | 7 | 월드젠 feature 를 게임 중 호출하는 통로: 묘목→나무, 균류, 거대 버섯, 잔디/이끼/네일륨 뼛가루 | `SaplingBlock:33`, `AzaleaBlock:27`, `FungusBlock:33`, `MushroomBlock:50`, `NyliumBlock:31`, `GrassBlock:21`, `MossBlock:22` |
| `TODO(아이템)` | 7 | 내구도 소모·통계, 음반 삽입, 발사기 동작 | `TntBlock:60`, `PumpkinBlock:35`, `BeehiveBlock:37`, `DropperBlock:13`, `JukeboxBlock:40` |
| `TODO(통계)`, `TODO(인벤토리)` | 2+2 | 통계 시스템(`BlockActor.awardStat` 빈 기본 메서드: `block/BlockActor.java:112`), 블록 쪽 인벤토리 통로 | `BellBlock:44`, `BlockActor:115,118` |
| `TODO(수면)` | 2 | 침대 수면·리스폰 정박기 | `BedBlock:47`, `RespawnAnchorBlock:43` |
| `TODO(게임 이벤트)` | 2 | 진동 시스템 | `SculkSensorBlock:2,46` |
| `TODO(엔티티·먹기)` | 1 | 오래 쓰는 아이템 | `block/ItemUse.java:105` |
| `TODO(엔티티·차원 이동)`, `(엔티티·날씨)` | 2 | 엔드/네더 포털, 벼락 | `EndPortalBlock:8`, `LightningRodBlock:67` |
| 기타 1줄씩 | 7 | 효과·속성(`BlockActor:86` 성급함·채굴 피로), 컴포넌트(`CauldronInteraction:102` 염색 지우기), 인챈트 효과(`BlockBreaking:70`), 발전 과제(`BlockBreaking:255`), 마법 부여(`DecoratedPotBlock:57` 실크 터치), 소리(`NoteBlock:153`), 파티클(`ItemUse:528`), 호퍼(`ComposterBlock:131`) | - |

해석: 엔티티 ≈89줄, 블록 엔티티 ≈79줄, 메뉴 24줄이 TODO 의 대부분이며 서로 겹친다. 이 세 덩어리가 풀리면 약 80% 해소.

### 6.3 서버바운드 play 패킷 (프로토콜 767, 58개)

`ConnectionHandler.handlePlay` 가 처리하는 id(25개): `0x00 0x03 0x04 0x05 0x06 0x07 0x09 0x0A 0x0B 0x0E 0x0F 0x18 0x1A 0x1B 0x1C 0x23 0x24 0x25 0x28 0x29 0x2C 0x2F 0x32 0x38 0x39`. 나머지는 `default -> { /* 무시 */ }`.

**미처리 33개:**

| id | 패킷 | 영향 | 규모·의존 |
|---|---|---|---|
| 0x08 | chunk_batch_received | 클라 청크 수신 속도 피드백 — 배치 크기 조절 불가 | 작음 |
| 0x0C | configuration_acknowledged | play→configuration 재진입(`/transfer`) 불가 | 작음 |
| 0x16 | interact | 엔티티 공격/상호작용(PvP·몹·탈것·거래) | 큼 — 엔티티 |
| 0x36 | swing | 팔 휘두름 전파(`animate` 클라이언트바운드도 없음) | 작음 |
| 0x26 | player_input | 탈것 조종·점프 | 중간 |
| 0x1D | move_player_status_only | onGround 만 바뀌는 이동 — 낙하/착지 정확도 | 작음 |
| 0x1E / 0x1F | move_vehicle / paddle_boat | 탈것 | 중간 |
| 0x37 | teleport_to_entity | 관전자 텔레포트 | 작음 |
| 0x22 | place_recipe | 레시피 북 자동 배치 | 중간 — 메뉴·제작 |
| 0x0D | container_button_click | 마법 부여 선택·석재 절단기·베틀·렉터른 | 중간 — 메뉴 |
| 0x10 | container_slot_state_changed | 제작기 칸 잠금 | 작음 |
| 0x2A | rename_item | 모루 이름 변경 | 중간 |
| 0x2D | select_trade | 주민 거래 | 큼 |
| 0x2E | set_beacon | 신호기 효과 | 중간 |
| 0x14 | edit_book | 책과 깃펜 | 중간 |
| 0x35 | sign_update | 표지판 글 | 중간 |
| 0x30 / 0x31 | set_command_block / set_command_minecart | 명령 블록 설정 | 중간 |
| 0x33 / 0x34 / 0x17 | set_jigsaw_block / set_structure_block / jigsaw_generate | 직소/구조물 블록 | 중간 |
| 0x20 | pick_item | 가운데 클릭 블록 복사 | 작음 |
| 0x02 / 0x19 | change_difficulty / lock_difficulty | 난이도 UI | 작음 |
| 0x01 / 0x15 | block_entity_tag_query / entity_tag_query | F3+I NBT 조회 | 작음 |
| 0x12 | custom_payload | 플러그인 메시지(play 채널) | 작음~중간 |
| 0x13 | debug_sample_subscription | F3 틱 그래프 | 작음 |
| 0x11 | cookie_response | 쿠키 | 작음 |
| 0x21 / 0x27 | ping_request / pong | 핑 | 작음 |
| 0x2B | resource_pack | 리소스 팩 응답 | 작음~중간 |

부분 처리:
- `0x24 player_action`: 0~2(파괴)만. 3·4(아이템 버리기)는 4-1 에서 구현. 5(사용 끝: 먹기·활), 6(손 바꾸기)은 무시.
- `0x25 player_command`: 0·1(웅크리기)·3·4(달리기)만. 침대 나가기(2), 말 점프(5·6), 겉날개(8) 무시.
- `0x09 client_command`: 0(리스폰)만. 1(통계 요청) 무시(`award_stats` 클라이언트바운드 패킷 없음).
- `0x0E container_click`: PICKUP 만, 상자 메뉴와 플레이어 인벤토리만. 제작 결과 칸(0) 거부.
- `0x38/0x39`: 블록 위 사용·아이템 사용만(엔티티/오래 쓰기 제외).

미구현 클라이언트바운드(새 시스템이 요구): `container_set_data`, `set_equipment`, `update_attributes`, `update_mob_effect`/`remove_mob_effect`, `animate`/`hurt_animation`, `add_experience_orb`, `set_passengers`, `level_particles`, `explode`, `boss_event`, `set_objective`/`set_score`/`set_player_team`/`set_display_objective`, `award_stats`, `map_item_data`, `light_update`, `merchant_offers`, `open_sign_editor`, `open_book`, `stop_sound`, `transfer`, `resource_pack_push/pop`, `move_vehicle`. (4-1 로 `take_item_entity`·`set_entity_motion`·`container_set_slot` 는 구현됨. 일부는 다른 이름 클래스로 구현됐을 수 있어 정확한 개수는 grep 추정.)

### 6.4 명령 (바닐라 루트 노드 83개 기준)

**구현 40개(별칭 포함):** advancement, ban, ban-ip, banlist, datapack, defaultgamemode, deop, difficulty, experience(xp), fill, gamemode, gamerule, help, kick, kill, list, me, msg(tell, w), op, pardon, pardon-ip, recipe, reload, save-all, save-off, save-on, say, seed, setblock, setidletimeout, setworldspawn, spawnpoint, stop, time, weather, whitelist, worldborder. (플러그인이 `plugins/` 에서 명령을 추가로 붙일 수는 있음 — `plugin/CommandRegistryImpl`.)

**미구현 43개:**

| 묶음 | 명령 | 규모 | 의존 |
|---|---|---|---|
| 즉시 가치 | **give**, **tp/teleport**, **clear**, **locate**, **tellraw**, **title**, **playsound**/**stopsound**, **particle**, transfer, publish, return, random, jfr, perf, debug, tick, spreadplayers, fillbiome, forceload, clone, place | 작음~중간 | give: 아이템 컴포넌트 인자 파서(3-3 남은 것), tp: 엔티티 선택자, locate: 구조물 직렬화, clone: 블록 엔티티, forceload: 티켓, particle/playsound: 패킷 |
| 엔티티 필요 | **summon**, **effect**, attribute, damage, enchant, ride, spectate, tag, data, item, loot | 중간~큼 | 엔티티, 효과/속성, 블록 엔티티 NBT |
| 점수판 계열 | **scoreboard**, team, teammsg(tm), bossbar, trigger | 큼 | 점수판/팀/보스바 + 패킷 |
| 함수/흐름 | **execute**(+조건 전부), function, schedule | 큼 | 데이터 팩 함수 로더, 선택자, 점수판 |

`EntityArgument`/`EntitySelectorParser` 는 있으나 엔티티가 플레이어(+아이템)뿐이라 `@e` 는 사실상 플레이어에만(평가기 소스를 끝까지 읽어 확인하지는 못함). Paper/운영 명령(`/tps`, `/mspt`, `/version`, `/plugins`, `/timings`, `/paper`)은 없음. `/summon`·`/kill`(엔티티 대상) 등은 4-1 이후에도 미구현.

### 6.5 블록 엔티티 (44종, `block/entity/BlockEntity` TYPES)

**동작 있음 4종:** `minecraft:chest`(`world/ChestBlockEntity` — 27칸·전리품 표/시드·첫 열기 때 채움; 더블 상자 병합·여는 사람 수·뚜껑 애니메이션 없음. 함정 상자는 NBT/아이템 읽기만 같음), `minecraft:piston`(이동 중 블록 — 엔티티 밀기 제외), `minecraft:comparator`, `minecraft:daylight_detector`. 등록: `block/entity/BlockEntities.java` FACTORIES = comparator, daylight_detector, piston (상자는 별도 경로).

**동작 없음(NBT 보존만) 40종:**

| 종류 | 필요 | 규모 | 의존 |
|---|---|---|---|
| furnace, smoker, blast_furnace | 조리 틱·레시피 매칭·경험치 누적·메뉴 | 큼 | 메뉴, 레시피 매칭 |
| ender_chest, shulker_box, barrel | 컨테이너 + 메뉴 + 열림 애니메이션/소리, 엔더 인벤토리(플레이어별) | 중간 | 메뉴 |
| hopper, dispenser, dropper, crafter | 아이템 이동 틱/쿨다운, 발사 동작(아이템별 수십 종), 제작 | 큼 | 아이템 엔티티(해소), 메뉴, 레시피 |
| brewing_stand, enchanting_table, beacon, lectern | 양조 틱, 책 흔들림, 신호기 피라미드·효과, 독서대 | 중간~큼 | 메뉴, 효과 |
| sign, hanging_sign | 글 면·밀랍·염료, 편집 화면 | 중간 | `sign_update` |
| jukebox | 음반 삽입·재생·비교기 출력 | 작음~중간 | 아이템 엔티티 |
| campfire | 요리 4칸·틱 | 작음 | 레시피 매칭 |
| chiseled_bookshelf, decorated_pot, bed(색), banner(무늬), skull(프로필), end_portal, end_gateway, conduit | 데이터 보관 + 일부 틱 | 작음~중간 | 일부 엔티티 |
| beehive | 벌 보관·내보내기 | 큼 | 몹 |
| mob_spawner, trial_spawner, vault | 몹 스폰 틱, 시련 상태, 열쇠 | 큼 | 몹 |
| sculk_sensor, calibrated_sculk_sensor, sculk_catalyst, sculk_shrieker | 게임 이벤트 진동, SculkSpreader | 큼 | 게임 이벤트 |
| brushable_block | 솔질 진행·전리품 | 중간 | 아이템 엔티티 |
| command_block | 명령 실행·연쇄 | 중간 | `set_command_block` |
| structure_block, jigsaw | 구조물 저장/불러오기, 직소 생성 | 중간~큼 | 템플릿 IO |
| 공통 | 생성 블록 엔티티의 청크 패킷 `getUpdateTag`, `Containers.dropContentsOnDestroy`(상자 외), `/setblock` NBT 인자 | 중간 | - |

### 6.6 엔티티

**현재 존재:** `Player`(+`PlayerVitals`/`FoodData`/`PlayerData`/`Abilities`/`EntityView`/`damage/CombatTracker,DamageType`), `EntityIdAllocator`, **(4-1)** `Entity`/`ItemEntity`/`EntityStore`/`EntityTracker`/`SynchedData`/`EntityType`/`Entities`/`AABB`.

**없는 것:**
- 엔티티 종류 대부분(130종 중 2), 엔티티별 AI·충돌 문맥.
- 경험치 구슬, 떨어지는 블록, 점화된 TNT, 번개, 투사체(화살·삼지창·진주·눈덩이·화염구·풍선구…), 액자/그림/갑옷 거치대, 탈것(배·수레·말), 영역 효과 구름.
- 몹 전부: 수동/적대/중립, AI 목표·길찾기, 속성(`UpdateAttributes` 없음), 포션 효과(`UpdateMobEffect` 없음), 장비 표시(`SetEquipment` 없음), 애니메이션·피해 반응, 주민/거래, 길들이기, 번식, 목줄.
- 스폰: `NaturalSpawner`, 월드젠 시점 몹 스폰(청크 파이프라인은 구조물 몹 NBT 를 보관만), 스포너, 습격, 순찰.
- 전투: `interact` 미처리 → PvP/몹 공격 불가, 방어구 경감·넉백·화염·쿨다운 없음.
- 엔티티 관련 블록 훅 중 4-1 로 안 닿은 것(투사체 맞음·좀벌레 등)은 TODO.

### 6.7 기타 주요 바닐라 시스템 상태

| 시스템 | 상태 | 근거 / 빠진 것 |
|---|---|---|
| 컨테이너 메뉴 | **부분 (1/25)** | 단일 9x3 상자만(`ConnectionHandler.openChest`). 더블 상자 없음. 클릭 6종 미처리. 서버 권위 메뉴(`container_set_slot/data`) 없음. 큼 |
| 플레이어 인벤토리 | **부분** | 서버 권위 저장은 4-1a 로 완료. 클릭은 아직 클라이언트 보고 수용(총량 보존 검사) — `ConnectionHandler.applyInventoryClick`. 제작 칸(0~4)·shift-클릭 서버 재현 없음 |
| 제작(2x2/3x3) | **없음(매칭)** | 레시피 1,290개 로드·네트워크 전송·레시피 북은 있음(`recipe/RecipeManager`, `RecipeBook`, `Recipe`, `Ingredient`). 재료 매칭·결과 계산·`place_recipe`·화로/모루/대장장이/석재 절단기 적용 코드 없음 |
| 화로/양조/마법 부여/모루/주민 거래 | **없음** | 메뉴 + 블록 엔티티 부재. 마법 부여는 `loot/Enchantments` 만 있고 테이블 로직 없음 |
| 빛 엔진 | **없음** | `net/protocol/play/ChunkSerializer`: 풀 skylight, block light 비어 있음. `AnvilChunkCodec` isLightOn=0. `BlockLevel.java:129-157` 상수. 큼(수천 줄급, 성능 민감, 스레드 모델에서 region 경계 전파가 설계 쟁점) |
| 폭발 | **없음** | `explode(` 실구현 0건(TntBlock 스텁), `explode` 패킷 없음. 중간, 엔티티·피해 필요 |
| 날씨 | **부분** | 순환·무작위 틱·얼음/눈 있음. 벼락 엔티티·번개 막대·강수 입자/하늘 노출 계산 없음 |
| 네더·엔드/포털 | **월드젠만** | 서버는 오버월드 1개. 포털 생성/이동, 엔드 드래곤, 차원별 틱 없음. 큼 |
| 점수판·팀 | **없음** | `/scoreboard`·`/team`·관련 패킷 없음. 중간~큼 |
| 보스바 | **없음** | `/bossbar`·`boss_event` 없음. 중간 |
| 발전 과제 | **부분(핵심)** | 1,399개 로드·진행·알림·명령 있음. 트리거 일부만 |
| 통계 | **없음** | `BlockActor.awardStat`/`Player.awardStat` 빈 메서드(TODO(통계)), `award_stats` 패킷·`client_command 1` 응답 없음 |
| 월드 보더 | **구현됨** | |
| 수면/침대 | **없음** | 밤 건너뛰기, 리스폰 앵커 정박, 주민 침대 |
| 습격·마을(POI)·주민 | **없음** | 마을 구조물 생성(월드젠)만. POI 저장·직업 블록·주민 AI·습격 코드 확인 못 함 |
| 구조물 생성 | **구현됨** | 단, 구조물 시작점·참조를 청크 NBT 에 직렬화하지 않아 `/locate`·구조물 몹 스폰·보물 지도 불가 |
| 청크 티켓/로드 레벨 | **없음** | 시야 구독 기반 로드 + 100틱 후 언로드. forceload·스폰 청크·simulation-distance 기반 구역·엔티티 틱 구역(현재는 시야/플레이어 거리 근사) 없음. 큼 |
| 아이템 사용(오래 쓰기) | **부분** | 먹기/마시기/활/방패/던지기/망원경/책/지도 없음 |
| 효과·속성 | **없음** | 포션 39종·속성 31종, 방어구 경감, 성급함/채굴 피로 |
| 소리·파티클 | **부분** | `SoundPacket`, `LevelEventPacket` 있음. `level_particles`, `stop_sound`, `sound_entity` 없음. NoteBlock 소리 시드 TODO |
| 맵 | **없음** | `map_item_data`, 지도 아이템, 지도 제작대 |
| 게임 이벤트(진동) | **없음** | 스컬크 센서 계열 전체 |
| 데이터 팩 함수/`/execute` | **없음** | 팩 로더는 있으나 function/명령 실행 컨텍스트 없음 |
| Paper/운영 기능 | **없음/확인 못 함** | Paper 설정 파일, RCON/Query, 리소스 팩/쿠키/transfer, 안티 엑스레이, 몹 스폰 한도 |
| 플러그인 | **API v0 구현** | 로더·이벤트·명령·권한·스케줄러. Paper 어댑터 모듈은 없음 |
| 서버 단위 | **구현됨** | 설정·level.dat·온라인 모드·서명 채팅·게임룰 51종·월드 형식 |

### 6.8 4-1 이후 남은 구멍 (다음 할 일 후보)

- **경험치 구슬(XP orb)**, **떨어지는 블록**(`FallingBlock` — `FallingBlock.java:26` TODO), **TNT**(폭발과 함께).
- **엔티티별 충돌 문맥:** 가루눈 걷기/낙하>2.5, 발판 등. 지금 `BlockBehavior.getCollisionShape(state, level, pos, BlockActor)` 는 `BlockActor` 를 받는데 `Entity` 는 `BlockActor` 가 아니다 → 시그니처/인터페이스 정리 필요.
- **월드 보더 충돌**(엔티티 이동 시).
- **피스톤이 엔티티 밀기**(`PistonMovingBlockEntity.moveCollidedEntities`, `PistonMovingBlockEntity.java:82` TODO).
- **컨테이너 내용물 쏟기:** 통·화로·호퍼·디스펜서/드로퍼·양조대·제작기·장식 항아리·모닥불 등. 현재 **상자만** `ChunkLevel.dropContents` 로 구현. (메뉴 작업과 함께 블록 엔티티 컨테이너를 만들 때 같이.)
- **통계 시스템:** `awardStat` 이 no-op(`TODO(통계)`). `picked_up`/`dropped`/`used` 등.
- **`thrown_item_picked_up` 발전 과제 트리거.**
- **호퍼가 아이템 엔티티 빨아들이기.**
- **엔티티 오라클 도구**(현재 오라클은 블록만 비교).
- **`/summon`, `/kill`(엔티티), `/data`, `/tp` 등 명령.**
- **컨테이너 클릭 서버 권위화** — 아래 7장의 4-2 그 자체.

---

## 7. 다음 할 일

### 7.1 4-2 메뉴 프레임워크 — 계획과 시작점

**목표:** 바닐라 `AbstractContainerMenu` 를 이식해, 컨테이너 클릭을 서버가 직접 재현(PICKUP·QUICK_MOVE·SWAP·CLONE·THROW·QUICK_CRAFT·PICKUP_ALL 7종 전부)하고, 슬롯 동기화(`broadcastChanges`)·`stateId` 검증·`container_set_data` 를 바닐라와 같게 한다. 지금의 "클라이언트 보고 수용 + 총량 검사"는 폐기.

**현재 코드의 출발점**
- `net/ConnectionHandler.java`: `case 0x0E`(container_click, 약 1201줄), `case 0x0F`(container_close), `applyInventoryClick`(약 1304줄), `inventoryDropClick`, `openChest`(약 1875줄), `nextContainerId`(1..100 순환, 0 = 플레이어 인벤), `onPlayerDied`(창 닫기), `returnCarried`.
- `entity/Player.java`: `openChest/closeChest/openContainerId/openChestPos`(약 394~405줄) — 열린 상자 좌표만 기억. → 열린 `AbstractContainerMenu` 참조로 교체.
- `entity/PlayerInventory.java`: 창 칸 0..45 + `remote[]` + `broadcastChanges` + `stateId`. 이 클래스가 사실상 `InventoryMenu` 의 일부 역할을 하고 있다. **4-2 에서 `InventoryMenu`(= containerId 0) 를 메뉴 클래스로 만들고 `PlayerInventory` 는 `Inventory`(실제 아이템 컨테이너) 역할로 정리**하는 게 바닐라 구조와 맞다. 단, 동기화 상태(`remote`/`stateId`)는 메뉴가 갖는다(바닐라에서도 `AbstractContainerMenu.remoteSlots/stateId`).
- `net/protocol/play/`: `OpenScreen`, `ContainerSetContent`, `ContainerSetSlot`, `ContainerClose` 존재. **추가 필요:** `ContainerSetData`(0x14), 서버바운드 `container_button_click`(0x0D)·`container_slot_state_changed`(0x10)·`place_recipe`(0x22)·`rename_item`(0x2A) 처리. `OpenScreen` 은 지금 `(containerId, menuTypeId, title 문자열)` — 제목을 `Text`(컴포넌트 NBT)로 보내는지 확인하고 바닐라 `container.chest` 번역 키 등 맞출 것.
- `world/ChestBlockEntity.java`(27칸), `RegionLevel.removeBlockEntity` → `region.closeChestScreens(x,y,z)`(상자가 부서질 때 화면 닫기) — 일반화 필요.
- `block/ChunkLevel.dropContents` — 컨테이너 내용물 쏟기(상자 한정, 일반화 필요).
- `recipe/RecipeManager`, `Recipe`, `Ingredient` — 매칭 로직 추가 필요(제작 격자). 레시피 종류별 `matches`/`assemble` 이식(shaped/shapeless/special …) — 바닐라 `RecipeManager.getRecipeFor` 순서(레시피 타입별 멀티맵, 3-2a 에서 만든 순서)를 쓸 것.
- 스레드 모델: 메뉴는 플레이어(연결 스레드, Netty 이벤트 루프)가 소유하지만 컨테이너(블록 엔티티)는 **소유 region 스레드**의 자료다. 바닐라는 단일 스레드라 `Container` 를 직접 만진다. 방안: (a) 클릭을 `world.runAt(컨테이너 좌표)` 로 소유 region 에 던져 region 스레드에서 메뉴 클릭을 처리하고 `broadcastChanges` 로 응답을 보낸다(`PlayerInventory` 가 이미 `synchronized` 이므로 인벤 쪽은 안전), (b) 컨테이너를 감싸 락. **(a) 를 권장** — `ChestInteractionTest`/`EntityRegionTest` 가 쓰는 패턴과 같고 region 순서(틱 안에서 처리)와 바닐라 순서가 맞는다. 플레이어 인벤 전용 메뉴(containerId 0)는 현재처럼 플레이어 소속 스레드/region 에서.

**제안 구현 순서 (작은 세로 조각 + 매 단계 테스트)**
1. **메뉴 틀:** `menu` 패키지(예: `S/menu/`)에 `AbstractContainerMenu`(slots, `remoteSlots`, `remoteCarried`, `stateId`, `carried`, `containerId`, `broadcastChanges`, `broadcastFullState`, `sendAllDataToRemote`, `clicked(slotIndex, button, ClickType, player)`, `doClick`, `quickMoveStack`, `moveItemStackTo`, `canTakeItemForPickAll`, `getQuickCraftPlaceCount/…` 등), `Slot`, `ClickType`, `Container` 인터페이스(+ `SimpleContainer`), `DataSlot`(container_set_data). 바닐라 `doClick` 의 모든 분기 순서·반환(`ItemStack`)·`stateId` 처리를 그대로. **`ChestMenu` 9xN + `InventoryMenu` 두 개로 먼저 완성하고 오라클/골든 테스트.**
2. **서버 권위 container_click 처리:** 패킷의 `stateId` 검증(`!= this.stateId` 면 `suppressRemoteUpdates`/전체 재전송 — 바닐라 `ServerGamePacketListenerImpl.handleContainerClick`), 슬롯 번호 범위, `changedSlots` 의 해시 비교(`HashedStack` 는 1.21.5+이니 **1.21.1 은 `changedSlots` 가 전체 ItemStack** 임에 유의), 처리 후 `broadcastChanges`. 클라이언트 보고값은 이제 **검증용**이지 진실이 아니다.
3. **열기/닫기:** `ServerPlayer.openMenu` 흐름(containerId 1..100 순환 `nextContainerCounter`, `OpenScreen`, `initMenu` → `container_set_content` stateId 부여), `doCloseContainer`(커서 돌려주기/떨구기 `removed`), 사거리 밖/블록 소멸 시 닫기(`stillValid`), 사망/접속 종료 처리.
4. **더블 상자 병합**(`ChestBlock.getMenuProvider`/`CompoundContainer`), **통·셜커·엔더 상자·호퍼·디스펜서/드로퍼** 블록 엔티티(NBT 로드/저장 + 컨테이너 + 열린 사람 수/뚜껑 애니메이션 `ContainerOpenersCounter`), `Containers.dropContentsOnDestroy`(내용물 쏟기 일반화) → 이 단계에서 6.8 의 "컨테이너 쏟기" 해소.
5. **제작:** `CraftingMenu`/`InventoryMenu` 의 2x2/3x3 격자, 결과 슬롯(`ResultSlot`, `onTake` 에서 남은 아이템·재료 소모), `CraftingContainer`, 레시피 매칭(`RecipeManager`), `slotsChanged`→`slotChangedCraftingGrid`, `place_recipe`(0x22), 레시피 북 연동(레시피 해금 `recipe_unlocked` 트리거는 이미 있음).
6. **나머지 메뉴(우선순위):** 화로 3종(`FurnaceMenu`+`AbstractFurnaceBlockEntity` 조리 틱·경험치) → 모루(`rename_item`)·숫돌·석재 절단기(`container_button_click`)·베틀·지도 제작대·대장장이 → 양조대·마법 부여대(`container_set_data`, 시드)·신호기(`set_beacon`)·렉터른·주민(`merchant_offers`·`select_trade`, 몹 이후).
7. **`creative` 슬롯 클릭**(`set_creative_mode_slot` 0x32)은 바닐라처럼 메뉴 슬롯에 반영(`inventoryMenu.getSlot(...).setByPlayer`).

**테스트 전략**
- 바닐라 `AbstractContainerMenu`/`ChestMenu` 로 직접 구동한 골든(디컴파일 소스 재생성 시) 또는 알려진 시퀀스(클릭 → 기대 슬롯 상태) 단위 테스트를 클릭 종류별로.
- 패킷 단위: 클릭 → `container_set_slot`/`container_set_content` 시퀀스와 `stateId` 가 바닐라와 같은지(순정 서버에 `TestClient` 로 붙여 캡처하는 방식은 `PacketCapture`/`TestClient` 참고).
- 기존 `ChestInteractionTest`, `GameplayFlowTest`, `ItemEntityTest`(버리기 경로) 가 계속 통과해야 한다.
- 스레드: 클릭 도중 상자가 부서지는 경쟁, region 합치기/나누기 중 열린 메뉴.

**바닐라 퀴크 (놓치기 쉬운 것)**
- QUICK_CRAFT(드래그) 3단계 상태(`quickcraftStatus`, `quickcraftType`, `quickcraftSlots`), 버튼 번호 인코딩(`getQuickcraftHeader/Type/Mask`).
- PICKUP_ALL(더블 클릭) 은 `canTakeItemForPickAll` 과 슬롯 순서(역순 반복 조건).
- 결과 슬롯 shift-클릭 반복(`while (!remaining.isEmpty() && ItemStack.isSameItem…)`), `onQuickCraft`.
- `Inventory.add` 의 부분 삽입 특이점(4.5), `moveItemStackTo` 의 reverse 인자.
- 창 밖 클릭(-999)·THROW 의 버리기 한도와 `ThrownItemPickedUp` 관련 발전 과제 트리거(thrower 설정).
- 바닐라는 `changedSlots` 로 서버 상태와 클라 예측을 대조해 달라진 칸만 `broadcastChanges` 로 되돌린다 — 따라서 서버 결과가 클라 예측과 같으면 추가 패킷이 나가지 않는다(패킷 일치 대조의 핵심).

### 7.2 그 다음 순서 (인벤토리 기반 제안)

1. **4-2 메뉴** (위)
2. **명령 보강** (병행 가능): `/give`·`/tp`·`/clear`·`/tellraw`·`/title`·`/playsound`·`/particle`·`/locate`(구조물 직렬화 후) 먼저 → 엔티티 이후 `/summon`·`/data`·`/item`·`/effect`·`/execute`.
3. **효과·속성·전투:** 속성 시스템, 포션 효과(`update_mob_effect`), 방어구 경감, `interact` 처리·넉백·무기 쿨다운, 오래 쓰는 아이템(먹기·마시기·활·방패), `swing`/`animate`, `player_action` 5·6, `move_player_status_only`, `chunk_batch_received`.
4. **폭발:** `Level.explode`, `explode` 패킷, TNT 엔티티·침대/앵커 폭발, `onExplosionHit` 블록 훅 10개. (XP 구슬·떨어지는 블록은 이 앞뒤에 같이)
5. **빛 엔진**(독립, 병행 가능; region 경계 전파가 설계 쟁점)
6. **청크 티켓 체계:** 로드 레벨·simulation-distance·엔티티/블록 틱 구역·스폰 청크·forceload. 엔티티 틱이 들어왔으므로 이제 필요.
7. **몹 기초 + 자연 스폰:** 몹 기반(AI 목표·길찾기·피해/죽음), 수동 → 적대, `NaturalSpawner`·mob cap, 스포너.
8. **차원 + 포털:** 네더·엔드 `World` 다중 호스팅, 차원 이동, 포털.
9. **진행 시스템:** 통계 → 점수판·팀 → 보스바 → 타이틀/액션바 → 맵 → 침대/수면 → 게임 이벤트/스컬크 → 나머지 발전 과제 트리거.
10. **지형 생성 연결**(작음~중간, 아무 때나): 묘목→나무, 균류, 거대 버섯, 뼛가루(`TODO(지형 생성)` 7줄) — 월드젠 feature 를 게임 중 호출하는 통로 하나로 일괄 해소하는 "빠른 승리".
11. **운영·Paper 층:** `/tps`·`/version`·`/plugins`, Paper 설정 파일, RCON/Query, 리소스 팩/transfer/쿠키, 크래시 리포트·워치독, 배포 → 이후 `furnace-compat-paper` 어댑터(5단계).

의존 요약: 엔티티(완료) → 폭발·몹·효과/전투·포털. 메뉴 → 블록 엔티티 컨테이너 전부·제작/화로/모루/마법 부여. 빛·지형 생성 연결은 독립. 티켓은 엔티티 틱 시점, 차원은 티켓 이후.

### 7.3 작업 습관 체크리스트

- 한 조각 끝날 때마다: 단위 테스트 추가 → `ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test` 전체 통과 → (블록 영향이 있으면) 오라클 시나리오 4~5개 재실행 → 로드맵 문서에 결과 기록(`docs/feature-parity/roadmap-2026-10-09.md` 에 단계별 항목 추가, "남은 것"도 명시) → 한국어 커밋 → `git push -u origin claude/work-continuation-u597cw` 후 **출력 그대로 보여 주기.**
- 새 TODO 를 남길 땐 `TODO(태그)` 형식으로 기다리는 시스템을 명시(기존: 엔티티/블록 엔티티/메뉴/폭발/빛/드롭/통계/…).
- 바닐라와 다르게 만든 부분은 코드 주석 + 로드맵에 이유를 적는다(예: region 별 무작위 틱 난수).

---

## 8. 새 세션 첫 메시지 예시 (그대로 붙여넣기용)

```
Furnace 프로젝트 작업을 이어서 한다. 먼저 docs/handoff/2026-10-18-session-handoff.md 를 끝까지 읽고 맥락을 복원해 줘.

요약: Furnace 는 처음부터 새로 쓰는 멀티스레드 Minecraft 1.21.1 서버(Java 21/Gradle)이고, 목표는 "기존 버킷(바닐라/Paper)과 완전히 같은 결과물 + 성능 극대화"야. Folia 식 동적 region 스레딩을 쓰고, 바닐라 코드를 난수 호출 순서까지 그대로 이식해야 해.

규칙:
- 브랜치는 claude/work-continuation-u597cw 만 쓰고, PR 은 내가 시킬 때만 만들어.
- 커밋 전에 반드시 ORG_GRADLE_PROJECT_skipOracle=true ./gradlew --offline :furnace-server:test 가 통과해야 해(≈4분, 백그라운드로 돌리고 로그를 기다려). 테스트 안 돈 코드는 커밋하지 마.
- 커밋 메시지·코드 주석·나와의 대화는 한국어. 커밋 트레일러는 네 system reminder 가 알려 주는 것을 써(모델 ID 는 넣지 마).
- 푸시는 git push -u origin claude/work-continuation-u597cw 이고, 푸시 출력을 숨기지 말고 그대로 보여 줘.
- 포팅은 네가 직접 해(포팅 서브에이전트 금지). 서브에이전트는 조사용으로만.
- Mojang/papermc 호스트는 프록시가 막으니 우회하지 마. 바닐라 데이터·디컴파일 소스는 커밋하지 마.

지금 상태: HEAD 는 3738fd79 (4-1b 엔티티 틀 + 아이템 엔티티). 블록 오라클(tools/block-oracle/run.sh)은 items·break-chain·survival-dig·pistons 모두 순정과 일치.

이번 작업: 4-2 컨테이너 메뉴 프레임워크. 서버 권위 AbstractContainerMenu(InventoryMenu·ChestMenu 먼저), container_click 7종(PICKUP·QUICK_MOVE·SWAP·CLONE·THROW·QUICK_CRAFT·PICKUP_ALL)을 서버가 재현, stateId 검증과 broadcastChanges, container_set_data, 열기/닫기 흐름, 더블 상자와 통·셜커·엔더 상자·호퍼·디스펜서, 제작 격자와 place_recipe 순으로 가자. 현재 ConnectionHandler.applyInventoryClick(클라이언트 보고 수용)을 교체하는 게 핵심이야. 바닐라 디컴파일 소스는 새 컨테이너에서 사라졌으니 handoff 문서 3.5 절차로 재생성할 수 있는지부터 확인하고, 안 되면 어떻게 할지 먼저 알려 줘. 시작 전에 계획을 짧게 한국어로 정리해서 보여 줘.
```

---

## 9. 부록: 참고 파일

| 파일 | 용도 |
|---|---|
| `docs/feature-parity/roadmap-2026-10-09.md` | 단계별 상태·결정·검증 방법의 정본(로드맵). 새 단계 끝날 때마다 여기에 기록 |
| `docs/feature-parity/remaining-work-2026-10-08.md` | 이전 남은 일 목록(상당수 해결됨) |
| `docs/superpowers/specs/2026-10-17-dynamic-regionizer.md` | 동적 region 설계 |
| `tools/vanilla-data/README.md` | 바닐라 데이터 추출 도구 사용법 |
| `tools/block-oracle/README.md`, `run.sh`, `scenarios/*.txt` | 블록 오라클 |
| `furnace-server/src/test/.../oracle/ScenarioBot.java`, `WorldDump.java` | 오라클 봇/덤프(시나리오 문법은 ScenarioBot 머리말) |
| `furnace-server/src/main/resources/registries/entity_types-1.21.1.txt` | 엔티티 종류 id (줄 순서) |
| `furnace-server/src/main/resources/blocks/*.txt` | 바닐라에서 뽑은 블록 표(커밋 대상 파생 자료) |

### 알아 둘 환경 사실
- 직전 컨테이너에서 `tools/vanilla-data/minecraft_server.1.21.1.jar`(약 51MB)와 `tools/vanilla-data/server-1.21.1/`(클래스 12,951개)는 git 에 이미 있었다. 새 컨테이너 클론에도 있을 것이다(확인: `ls -la tools/vanilla-data/minecraft_server.1.21.1.jar`).
- 직전 세션에서 `/tmp/claude-0/jar/` 아래에 디컴파일·리포트·Vineflower 를 두었고(`work/gen/reports`, `work/block_class.json`, `tools/vineflower.jar`) 스크래치패드에 `decomp/src` 4,058개 파일이 있었다. 모두 새 컨테이너에는 없다.
- `.claude/worktrees/` 는 `.gitignore` 에 있다. `logs/`, `build/`, `furnace-server/world/` 도 무시된다.
