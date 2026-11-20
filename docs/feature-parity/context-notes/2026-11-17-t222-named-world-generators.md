# T-222 이름별 월드 생성기 — 함정 기록 (2026-11-17)

설계: `docs/feature-parity/compat-t222-named-world-generators-2026-11-17.md`

- **Paper jar 대조법(새 컨테이너)**: 공유 폴더의 paperclip jar 를 풀어 `META-INF/versions/1.21.1/server-1.21.1.jar.patch` 를 piston-data 모장 jar
  (`META-INF/download-context` 의 URL)에 `furnace/scripts/paperclip-bspatch.py` 로 적용 → sha256 이 `versions.list` 와 같은지 확인 → `javap -p -c` 로
  `CraftServer#createWorld`·`getGenerator`·`getBiomeProvider`. 문구는 `javap -v` 의 상수 풀에서 본다.
- **Paper 는 설정을 이름 존재 검사 뒤, level.dat 읽기 앞에서 읽는다.** 이미 있는 월드를 다시 `createWorld` 하면 플러그인 `getDefaultWorldGenerator` 가
  불리지 않는다. 생성기와 공급자는 독립이라 생성기를 직접 줘도 `getBiomeProvider(이름)` 은 불린다.
- **로그 문구의 "default world" 는 추가 월드에도 그대로다**(같은 메서드). T-219 가 기본 월드용으로 쓴 문구를 그대로 재사용.
- **키 이스케이프**: 가져오기가 쓰는 `furnace.properties` 는 `Properties.load`(ISO-8859-1)라 한글 월드 이름 키는 `\uXXXX` 로 써야 읽힌다. 이전 `escapeKey` 는
  빈칸·`=`·`:` 만 처리해 ASCII 밖 키가 깨졌다 — 이름별 키 때문에 처음으로 필요해져 고쳤다(역슬래시·줄바꿈 포함).
- **`worlds.<점 든 이름>`**: Bukkit 설정은 경로를 `.` 로 나눠 `a.b` 이름이 중첩 `a → b` 로 저장·조회된다. 가져오기는 잎 경로에서 이름을 뽑아 둘 다 같은 이름이 된다.
- **기본 세 월드 이름이 level-name 에 따라 달라진다**: `server.properties level-name=survival` 이면 `worlds.world` 는 추가 월드 이름이다(T-219 시험이 "대응 없음"으로
  단언하던 자리를 이름별 키로 바꿨다).
