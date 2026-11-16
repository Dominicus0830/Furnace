# A15 Bukkit·Paper API 표면 호환 — 3차 전체 점검 조사 보고서 (2026-11-12)

- 기준: 브랜치 `claude/quirky-wright-lra834`, 커밋 `6730d558`(PR #241 H4 묶음 C 병합 시점, 묶음 D PR #238 도 들어 있다). 코드는 고치지 않았다.
- 대조 원문: Paper 1.21.1 API·CraftBukkit 본문은 `raw.githubusercontent.com/MohistMC/Youer/1.21.1/src/main/java/org/bukkit/...`(Paper 패치 표식 `// Paper` 가 그대로 있는 1.21.1 사본)에서, Paper 서버 쪽 일부(`PaperPluginInstanceManager`·`PlayerMetadataStore`)는 `PaperMC/Paper` main 에서 읽어 1.21.1 사본과 맞춰 봤다. Spigot 만의 동작은 대조하지 못했다.
- 재현 시험: `A15-repro/` 의 두 파일, 시험 14개. 기준 커밋에서 **14개 모두 실패**한다(차이 재현). 쓰는 법: 확장자 `.txt` 를 떼고 `furnace-compat-paper/src/test/java/org/domi/furnace/compat/audit/` 에 넣고 `./gradlew :furnace-compat-paper:test --tests 'org.domi.furnace.compat.audit.*'`.
  - `A15ConfigMetadataReproTest` — 서버 없이 도는 11개(설정·직렬화·메타데이터·ChatColor·이벤트 getter·멤버 유무).
  - `A15PluginSurfaceReproTest` — 헤드리스 서버에 시험 중 컴파일한 Bukkit 플러그인 jar 두 개를 올리는 3개(리소스 찾기, 같은 우선순위 순서, `getOnlinePlayers`).

## 요약

| 심각도 | 수 | 항목 |
|---|---|---|
| 치명 | 0 | |
| 높음 | 1 | A15-01 |
| 중간 | 5 | A15-02, A15-03, A15-04, A15-05, A15-06 |
| 낮음 | 5 | A15-07, A15-08, A15-09, A15-10, A15-11 |
| 참고 | 4 | A15-12, A15-13, A15-14, A15-15 |

가장 큰 것은 A15-01 이다. `getConfig().set("home", player.getLocation())` 가 `IllegalArgumentException` 을 던진다. 집·워프·스폰·경기장 위치를 설정 파일에 저장하는 플러그인은 아주 흔하고, 그 명령이 매번 실패한다. Paper 가 쓴 설정 파일의 `Vector`·`PotionEffect` 도 객체로 되살아나지 않는다.

그다음은 리소스 찾기(A15-02)다. 플러그인의 `getResource` 가 자기 jar 보다 서버 jar 를 먼저 본다. 기준 커밋에서 플러그인이 자기 `lang/en_us.json` 대신 서버의 바닐라 언어 파일을 받았다.

설정 API(A15-03·A15-05)는 기본값 구역이 있을 때 `getInt(path, def)` 가 def 를 무시하는 등 Paper 와 다르게 값을 돌려준다. `JavaPlugin#getConfig()` 는 늘 jar 의 `config.yml` 을 기본값 구역으로 달기 때문에, 사용자가 지운 키에서 플러그인이 정한 기본값 대신 jar 의 값이 나온다.

## 조사한 범위

- **설정**(`org/bukkit/configuration/**`, `furnace-api` `ConfigSection`·`YamlScalars`·`YamlWriter`): `MemorySection` 의 get·getX·isX·목록 getter·기본값 구역·copyDefaults·경로 구분자, `YamlConfiguration` 읽기·쓰기(주석·순서·스칼라 해석·빈 설정), `ConfigurationSerialization` 등록부. Paper 1.21.1 `MemorySection`·`YamlConfiguration`·`ConfigurationSerialization` 본문과 줄 단위로 맞춰 봤다.
- **플러그인 로딩·수명**(`JavaPlugin`, `BukkitPluginAdapter`, 서버 `PluginManagerImpl` 의 켜기·끄기 순서, `PluginClassLoader`): `getConfig`·`reloadConfig`·`saveDefaultConfig`·`saveResource`·`getResource`, 끄기 순서(이벤트 → onDisable → 작업·서비스·처리기·채널·청크 티켓 정리)를 Paper `PaperPluginInstanceManager#disablePlugin` 과 대조했다. 끄기 순서·정리 목록은 같았다.
- **이벤트 다리**(`EventBridge`, `SimplePluginManager#registerEvents/callEvent`, `HandlerList`·`RegisteredListener`): 우선순위·`ignoreCancelled`·취소 되돌림, 같은 우선순위 순서, 플러그인이 직접 부르는 이벤트, 읽기 getter 의 실패 방식.
- **메타데이터**(`MetadataStoreBase`·CraftBukkit `*MetadataStore`): Paper 본문과 몸통 비교, 열쇠 만드는 법.
- **스케줄러**(`compat/sched/LegacyScheduler`·`PaperSchedulers`, `CompatScheduler`): CraftScheduler 의 인자 규칙·지연 0·같은 틱 안의 `runTask`·꺼진 플러그인 예약 예외(`IllegalPluginAccessException`)·비동기 풀 모양을 원문과 대조했다. 새 차이를 찾지 못했다(기존 시험 `LegacySchedulerTest`·`PaperSchedulerParityTest` 가 덮는다).
- **값 클래스**(`Location`·`Vector`·`NumberConversions`·`ChatColor`·`NamespacedKey`·`PersistentDataType`): Paper 1.21.1 본문과 공개 멤버 목록을 기계적으로 견줬다(멤버 이름/인자 수).
- **서버 보조**(`CraftServer#getOnlinePlayers/getPlayer/getOfflinePlayer`), **영속 데이터**(`ItemPdc` — 저장 자리 `PublicBukkitValues` 는 Paper 와 같음).

## 조사하지 못한 범위

- **`plugin.yml`·`paper-plugin.yml` 해석, 권한(`PermissibleBase`), 플러그인 메시징, `SimplePluginManager`, 명령 지도**: H1~H4·#146·#148·#150·#161·#210 검토와 PR #153·#156·#158 에서 원문 대조가 끝나 있어(계획서 2장 A15 "제외") 새로 파지 않았다. 묶음 C(#241)·D(#238)도 검토가 끝난 지적은 다시 올리지 않았다.
- **실제 플러그인 jar 기동**(결정 8): 받은 jar 가 아직 없다. Maven Central 표본 13개의 정적 대조는 `docs/feature-parity/compat-h4-coverage-2026-11-07.md` 에 있다.
- **Spigot 원문**: 막혀 있다. Spigot 과 Paper 가 다를 수 있는 곳은 Paper 를 기준으로 적었다.
- **`isPrimaryThread()` 와 region 스레드 리스너**: 이미 결정 항목(`docs/user-todo/entries/2026-11-08-legacy-sync-region-access.md` "나중에, D1 과 함께")이라 다시 올리지 않았다. 아래 A15-15 에 남은 영향만 적었다.

## 항목

### A15-01 Location·Vector·OfflinePlayer 를 설정에 저장할 수 없고, Paper 기본 직렬화 별칭 대부분이 없다

| 칸 | 내용 |
|---|---|
| 심각도 | 높음 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `furnace-compat-paper/src/main/java/org/bukkit/Location.java:8` 은 `Cloneable` 만, `util/Vector.java` 는 아무것도 구현하지 않는다(`serialize`·`deserialize` 없음). `OfflinePlayer.java:7` 은 `ServerOperator` 만 잇는다. `MemorySection.set` → `ConfigSection.set` → `ConfigSection.normalize`(`furnace-api/.../config/ConfigSection.java:584`)가 모르는 종류에 `IllegalArgumentException("지원하지 않는 설정 값 타입: org.bukkit.Location")`. `ConfigurationSerialization` 정적 등록(`configuration/serialization/ConfigurationSerialization.java` static 블록)은 ItemStack·ItemMeta·Color·FireworkEffect·AttributeModifier 다섯뿐. Paper 1.21.1 `ConfigurationSerialization` static 블록은 `Vector`·`BlockVector`·`ItemStack`·`Color`·`PotionEffect`·`FireworkEffect`·`Pattern`·`Location`·`AttributeModifier`·`BoundingBox`·`SpawnRule` 을, `CraftServer` static 블록은 `CraftOfflinePlayer`(별칭 `Player`)·`CraftPlayerProfile` 을 등록한다. `PotionEffect` 는 Paper 에서 `@SerializableAs("PotionEffect")` 인데 Furnace 는 별칭이 없어 클래스 이름으로 쓰고, 등록도 안 돼 되읽지 못한다. `MemorySection#getLocation/getVector/getOfflinePlayer/getColor`(와 `isX`)도 없다 |
| 재현 | `A15ConfigMetadataReproTest.locationAndVectorRoundTripThroughYaml` — 기준 커밋 실패(`Location implements ConfigurationSerializable` 거짓). `cfg.set("home", new Location(...))` 는 `IllegalArgumentException`(시험 작성 중 직접 확인). `paperWrittenSerializablesAreRevived` — Paper 가 쓴 `==: Vector`·`==: PotionEffect` 가 `MemorySection` 으로 남음. `commonlyUsedMembersExist` — `MemorySection#getLocation/getVector/getOfflinePlayer/getColor` 없음 |
| 제안 | `Location`·`Vector`(+`BlockVector`)에 `ConfigurationSerializable`(Paper 와 같은 키: Location 은 `world`(이름)·`x`·`y`·`z`·`yaw`·`pitch`, `deserialize` 는 모르는 월드면 `IllegalArgumentException("unknown world")`), `PotionEffect` 에 `@SerializableAs("PotionEffect")`, 등록부에 Paper 목록(있는 클래스만), `MemorySection` 의 getLocation·getVector·getOfflinePlayer·getColor 와 isX. `OfflinePlayer` 직렬화(`Player` 별칭, `UUID` 키)는 `OfflinePlayer` 구현체가 여럿이라 따로. 옛 Furnace 가 클래스 이름 별칭으로 쓴 파일은 Paper 처럼 클래스 이름도 등록하므로 그대로 읽힌다. 크기 중간, Sonnet high |

### A15-02 플러그인 `getResource` 가 서버 jar 를 먼저 본다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `JavaPlugin#getResource`(`plugin/java/JavaPlugin.java:111`)는 `loader.getResourceAsStream`. 서버의 `PluginManagerImpl.PluginClassLoader`(`furnace-server/.../plugin/PluginManagerImpl.java:579`)는 `URLClassLoader` 기본(부모 먼저)이고 `getResource`·`getResources` 를 고치지 않았다. 부모는 서버 제공 라이브러리 로더 → 서버 클래스로더(fatJar). Paper 1.21.1 `PluginClassLoader#getResource` 는 `findResource(name)`(자기 jar) → 없으면 `libraryLoader.getResource`, `getResources` 도 자기 jar + 라이브러리만이다(`// Paper start` 표식). 서버 jar 에는 `lang/en_us.json`(바닐라 언어 파일), `logback.xml`, `META-INF/MANIFEST.MF`, 의존 라이브러리의 `META-INF/services/*` 등이 있다 |
| 재현 | `A15PluginSurfaceReproTest.getResourceReadsThePluginJarFirstAndNeverTheServer` — 기준 커밋 실패: 플러그인 jar 에 `lang/en_us.json`("A-OWN")을 넣었는데 `getResource` 가 서버의 바닐라 언어 JSON 을 돌려줬다. 서버에만 있는 `registries/builtin-1.21.1.txt` 도 보인다(Paper 는 null). `getClassLoader().getResource` 도 같다 |
| 제안 | 호환(Bukkit) 플러그인 로더만 `getResource`=`findResource`(플러그인 jar + `libraries:` jar 가 같은 로더의 URL 이라 함께 덮인다), `getResources`=`findResources` 로 고친다. 네이티브 플러그인 로더 동작은 그대로. 자기 매니페스트를 `getResource("META-INF/MANIFEST.MF")` 로 읽는 플러그인이 Furnace 매니페스트를 받던 것도 함께 풀린다. 크기 작음, Sonnet medium |

### A15-03 `MemorySection` 값 읽기 규칙이 Paper 와 다르다(명시한 기본값·문자열 숫자·틀린 종류)

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `configuration/MemorySection.java` 의 `get(path, def)`·`getString(path, def)`·`getInt(path, def)` 등은 `ConfigSection` 에 그대로 넘기고, `ConfigSection.get` 은 값이 없으면 **기본값 구역**을 먼저 보고 그다음 def 를 쓴다(`furnace-api/.../ConfigSection.java:60-73`). `ConfigSection.getInt/getLong/getDouble/getBoolean` 은 문자열도 숫자·참거짓으로 읽는다(`:95-147`). Paper 1.21.1 `MemorySection`: `get(path, def)` 는 자기 데이터에 없으면 **def**(기본값 구역을 보지 않음), 인자 하나짜리만 `getDefault(path)` 를 def 로 넘긴다. `getInt(path, def)` 는 `val instanceof Number ? toInt(val) : def`(문자열 `'5'` → def), `getInt(path)` 는 `getInt(path, 기본값 구역 값이 Number 면 그것, 아니면 0)`. getBoolean·getDouble·getLong 도 같은 꼴 |
| 재현 | `A15ConfigMetadataReproTest.explicitDefaultWinsOverDefaultsSection`(기대 3, 실제 5 — jar 기본값), `quotedNumbersAndBooleansAreNotNumbers`(기대 0, 실제 5), `wrongTypeFallsBackToDefaultsSectionNumber`(기대 4, 실제 0) — 셋 다 기준 커밋 실패 |
| 제안 | `ConfigSection`(Furnace API 의미)은 두고 호환 `MemorySection` 에서 Paper 규칙으로 읽는다: 자기 데이터 조회(`contains(path, true)` 로 기본값 제외) → 없으면 def, getX 는 `instanceof Number/Boolean/String` 만. 목록 getter(`getBooleanList` 의 대소문자, `Character` 원소)도 함께 맞춘다. 크기 작음, Sonnet medium |

### A15-04 읽기 getter 가 `UnsupportedFeature` 를 던진다(`ChunkLoadEvent#isNewChunk`, `BlockBreakEvent#isDropItems/getExpToDrop`)

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `event/world/ChunkLoadEvent.java:11`, `event/block/BlockBreakEvent.java:21,29` 는 읽기에서 던진다. `docs/api/compat-paper.md` "지원하지 않는 것과 실패 방식" 은 "읽기는 정직한 기본값, 쓰기는 `UnsupportedFeature`" 라고 약속한다. `isNewChunk` 는 청크 처리기마다 부르는 흔한 getter 라(새 청크에만 무언가를 놓는 플러그인), 지금은 청크를 읽을 때마다 처리기가 예외로 끝나고 SEVERE 로그가 쌓인다. `isDropItems` 는 Paper 기본 true 이고 Furnace 는 취소하지 않으면 늘 떨구므로 true 가 정직한 값이다 |
| 재현 | `A15ConfigMetadataReproTest.unsupportedEventReadsDoNotThrow` — 기준 커밋에서 `isDropItems` 가 던짐 |
| 제안 | `isDropItems` → true(쓰기는 그대로 `UnsupportedFeature`). `isNewChunk` 는 서버의 `ChunkLoadEvent` 에 "새로 만듦" 을 더해 잇는다(서버가 생성 레인에서 끝낸 청크인지 안다) — 서버 이벤트 필드 하나와 다리 한 줄, 크기 작음~중간. `getExpToDrop` 은 서버가 경험치를 이벤트로 알릴 때까지 던지는 편이 거짓 값보다 낫다(문서에 예외로 적는다). Sonnet medium |

### A15-05 `options().pathSeparator` 를 바꾸면 던진다

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `configuration/ConfigurationOptions.java:16` 이 `'.'` 이 아니면 `UnsupportedOperationException`. Paper 는 그 문자로 경로를 나눈다. 점이 든 키(권한 노드·`minecraft:stone` 같은 키·도메인 이름)를 쓰는 플러그인이 `pathSeparator('/')` 나 `'|'` 를 건다 — onEnable 에서 부르면 플러그인이 켜지지 않는다 |
| 재현 | `A15ConfigMetadataReproTest.customPathSeparatorIsHonoured` — 기준 커밋에서 `UnsupportedOperationException` |
| 제안 | `ConfigSection` 에 구분자 인자를 받는 조회·쓰기(또는 `setLiteral` 처럼 한 단계씩 내려가는 길)를 더하고 `MemorySection` 이 루트 옵션의 구분자로 경로를 나눠 한 단계씩 넘긴다. 크기 중간, Sonnet high |

### A15-06 흔히 쓰는 값 클래스 멤버가 빠져 있다(Location·Vector·PersistentDataType)

| 칸 | 내용 |
|---|---|
| 심각도 | 중간 |
| 종류 | Paper/Spigot 호환 |
| 근거 | Paper 1.21.1 본문과 공개 멤버(이름/인자 수)를 견준 결과 `Location` 72개 중 38개, `Vector` 56개 중 30개가 없다. 흔한 것: `Location#setDirection`·`toBlockLocation`·`toCenterLocation`·`isWorldLoaded`·`isChunkLoaded`·`getNearbyPlayers`/`getNearbyEntitiesByType`(Paper)·`x()/y()/z()`·`serialize`, `Vector#rotateAroundX/Y/Z/Axis`·`getCrossProduct`·`getMidpoint`·`isInSphere`·`angle`·`toLocation`. `PersistentDataType.LIST`(1.20.4+)·`TAG_CONTAINER_ARRAY` 와 `PersistentDataContainer#serializeToBytes/readFromBytes`(Paper)도 없다. 이 클래스들은 Maven Central 표본 13개가 거의 안 불러 `compat-h4-coverage` 표에 잡히지 않았다. 없는 멤버를 부르는 줄에서 `NoSuchMethodError`/`NoSuchFieldError` 로 그 처리기·명령이 죽는다 |
| 재현 | `A15ConfigMetadataReproTest.commonlyUsedMembersExist` — 기준 커밋에서 15개 없음 |
| 제안 | 값 클래스(월드 상태가 필요 없는 수학 메서드)는 Paper 의미 그대로 채운다: Location·Vector·BlockVector·BoundingBox. 월드가 필요한 Paper 편의 메서드(`getNearby*`, `toHighestLocation`)는 `World` 어댑터 위에. PDC `LIST` 는 Furnace 영속 데이터에 목록 종류가 있는지 먼저 확인(ItemPdc 는 목록을 아직 못 읽는다). 크기 중간, Sonnet medium. 더 넓게는 `compatReport` 에 "표본 jar 와 무관하게 흔히 쓰는 API" 목록(위치·벡터·설정·스코어보드·Paper 이벤트)을 넣어 빈칸을 숫자로 보이게 하는 것을 권한다 |

### A15-07 같은 우선순위 처리기 순서가 플러그인별로 묶인다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `EventBridge.ensure`(`compat/paper/EventBridge.java`)는 (플러그인, 우선순위)마다 처음 등록할 때 Furnace 버스에 한 번 구독하고, `deliver` 는 그 플러그인의 그 우선순위 처리기를 모두 부른다. Bukkit `HandlerList` 는 같은 우선순위 안에서 **등록 순**이다(플러그인을 가리지 않음). 플러그인 A 가 B 보다 먼저 켜진 뒤 나중에(첫 틱·리로드 명령·월드 적재 때) 같은 우선순위 처리기를 하나 더 달면, Paper 는 A1→B1→A2, Furnace 는 A1→A2→B1 |
| 재현 | `A15PluginSurfaceReproTest.samePriorityHandlersRunInRegistrationOrderAcrossPlugins` — 기준 커밋 `[A1, A2, B1]` |
| 제안 | 결과가 갈리는 경우는 둘 다 같은 이벤트 값을 고칠 때뿐이라 낮음. 고치려면 (Bukkit 이벤트, 우선순위)마다 구독 하나를 두고 `HandlerList` 순서대로 부르되, 프로파일러·자동 해제를 위해 처리기 플러그인별 시간 기록만 따로 한다. 크기 중간, Sonnet high |

### A15-08 `getOnlinePlayers()` 가 매번 새 목록(고칠 수 있고 살아 있지 않음)

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | Paper/Spigot 호환 · 성능 |
| 근거 | `craftbukkit/CraftServer.java:101` 이 부를 때마다 `ArrayList` 를 새로 만든다. Paper `CraftServer#getOnlinePlayers` 는 `playerView`(접속자 목록 위의 `Collections.unmodifiableList` 보기)를 돌려준다. 필드에 받아 두고 계속 쓰는 플러그인은 새 접속자를 못 보고, `remove`·`clear` 가 조용히 성공한다. 틱마다 부르는 스코어보드류는 매번 N 개짜리 목록과 어댑터 조회를 만든다 |
| 재현 | `A15PluginSurfaceReproTest.onlinePlayersIsALiveUnmodifiableView` — 기준 커밋에서 앞서 받은 컬렉션 크기 0 |
| 제안 | 접속·퇴장 때 고치는 `CopyOnWriteArrayList<Player>`(호환 어댑터 객체) 위의 고칠 수 없는 보기 하나를 돌려준다. 크기 작음, Sonnet medium |

### A15-09 플레이어 메타데이터를 이름으로 가른다(Paper 는 UUID)

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `craftbukkit/metadata/PlayerMetadataStore.java:12` `player.getName().toLowerCase() + ":" + key`, 주석은 "Paper 와 같다" 고 적었지만 Paper 1.21.1(Youer 사본·Paper main 둘 다)은 `player.getUniqueId() + ":" + key` 다. 정품 서버에서 이름을 바꾼 사람은 메타데이터를 잃고, 이름 없는 `OfflinePlayer`(UUID 로만 만든 것)는 `NullPointerException` |
| 재현 | `A15ConfigMetadataReproTest.playerMetadataIsKeyedByUuidNotName` — 기준 커밋 실패 |
| 제안 | 한 줄(UUID). 크기 작음, Haiku low |

### A15-10 `ChatColor` 가 16진 색을 모른다(`getLastColors`·`stripColor`)

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `ChatColor.java:13` 지우기 무늬가 `[0-9A-FK-OR]`(Paper `[0-9A-FK-ORX]`) — `stripColor` 뒤에 `§x` 가 남는다. `getLastColors`(`:57`)는 16진 색(`§x§r§r§g§g§b§b`)을 보지 않아 마지막 자리 `§0` 만 돌려준다(Paper 는 `getHexColor` 로 통째). 줄을 나눠 색을 이어 붙이는 플러그인·`ChatPaginator` 류가 16진 색을 검정으로 바꾼다 |
| 재현 | `A15ConfigMetadataReproTest.hexColoursInChatColorHelpers` — 기준 커밋 `§0` |
| 제안 | 무늬에 X, `getLastColors` 에 Paper 의 16진 확인. 크기 작음, Haiku low |

### A15-11 copyDefaults 가 켜진 채 `getKeys`·`getValues` 를 부르면 기본값이 데이터로 옮겨진다, 빈 설정은 `{}` 로 저장된다

| 칸 | 내용 |
|---|---|
| 심각도 | 낮음 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `MemorySection.applyCopyDefaults`(`configuration/MemorySection.java`)가 `delegate.copyDefaults()` 로 실제 데이터를 바꾼다. Paper `getKeys/getValues` 는 기본값 구역을 **보기로만** 합친다. 차이는 copyDefaults 를 다시 끈 뒤 `contains(path, true)`·`isSet` 이 true 가 되는 것. 또 빈 설정의 `saveToString()` 이 `"{}\n"` — Paper 는 `BLANK_CONFIG` 를 `""` 로 바꾼다 |
| 재현 | `A15ConfigMetadataReproTest.getKeysWithCopyDefaultsDoesNotMaterialiseDefaults` — 기준 커밋 실패. 빈 설정 `{}` 는 시험 작성 중 직접 확인 |
| 제안 | getKeys·getValues 는 데이터 + 기본값 구역 키의 합집합만 돌려주고, 실제로 옮기는 것은 `saveToString` 에서만(Paper 의 결과와 같다). 빈 설정 `""`. 크기 작음, Sonnet medium |

### A15-12 (참고) YAML 스칼라·키 해석의 의도된 차이

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `YamlScalars.resolve` 주석대로 날짜(`2024-01-01` → SnakeYAML 은 `Date`)·60진법을 문자열로 둔다. 그 밖에 시험 중 본 것: long 범위를 넘는 정수는 문자열(SnakeYAML `BigInteger`), **키** `yes:` 는 문자열 `"yes"`(SnakeYAML 은 Boolean true → Bukkit 은 키를 `"true"` 로), `yEs` 처럼 섞인 대소문자도 참거짓(SnakeYAML 은 세 꼴만), 인자 하나짜리 `getString` 이 구역에 null(Paper 는 `MemorySection.toString()`), `==` 키를 `'=='` 로 따옴표 쳐서 쓴다(Paper 는 안 친다 — 읽기는 둘 다 됨), 목록 들여쓰기 `  - x`(SnakeYAML 기본 `- x`). 모두 읽고 쓰기는 된다 |
| 재현 | 시험 작성 중 출력으로 확인(별도 시험 없음) |
| 제안 | 고치지 않아도 된다. 날짜를 `Date` 로 바라는 플러그인이 보이면 그때 |

### A15-13 (참고) Paper 전용 이벤트·스코어보드 API 가 비어 있다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | Paper/Spigot 호환 |
| 근거 | `io.papermc.paper.event` 에는 `PlayerSignCommandPreprocessEvent` 하나뿐이다(`AsyncChatEvent`·`PlayerJumpEvent`·`EntityRemoveFromWorldEvent`·`ServerTickStartEvent` 등 없음 — 처리기 클래스 하나에 섞이면 그 리스너 전체가 `NoClassDefFoundError` 로 빠진다). `org.bukkit.scoreboard` 패키지는 없다. 둘 다 `compat-h4-coverage-2026-11-07.md` 와 `docs/api/compat-paper.md` 에 "아직 없는 영역" 으로 이미 적혀 있어 새 발견이 아니다 |
| 재현 | — |
| 제안 | 다음 호환 묶음 우선순위: 스코어보드(사이드바·이름표 색 팀은 아주 흔하다) → Paper `AsyncChatEvent`(Adventure 받아오기 묶음 B 뒤) |

### A15-14 (참고) 스케줄러·끄기 순서는 Paper 와 같다

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | — |
| 근거 | CraftScheduler 의 인자 규칙(지연 < 0 → 0, 주기 0 → 1), 지연 0 과 1 이 둘 다 다음 박동, 작업 안의 `runTask` 가 같은 박동에 이어 도는 것(`parsePending`), 비동기 관리 스레드·풀 모양, 꺼진 플러그인 예약의 `IllegalPluginAccessException`, `PluginDisableEvent` → onDisable → 정리 순서와 정리 목록(작업·서비스·처리기·채널·청크 티켓)이 원문과 같았다 |
| 재현 | 기존 `LegacySchedulerTest`·`PaperSchedulerParityTest`·`SimplePluginManagerTest` |
| 제안 | — |

### A15-15 (참고) region 스레드 리스너의 `isPrimaryThread()` — 결정 대기 항목의 남은 영향

| 칸 | 내용 |
|---|---|
| 심각도 | 참고 |
| 종류 | Paper/Spigot 호환 · 스레드 안전 |
| 근거 | `EventBridge.deliver` 는 `PrimaryThread.enter()` 를 하지 않는다(전역 스레드 이벤트만 주 스레드). 결정 항목(`2026-11-08-legacy-sync-region-access.md` "나중에, D1 과 함께")에 이미 있다. 덧붙일 관찰: 그 결과 region 스레드 리스너 안에서 플러그인이 **비동기** 이벤트(`isAsynchronous()==true`)를 `callEvent` 해도 Paper 처럼 `IllegalStateException` 이 나지 않는다(`SimplePluginManager.callEvent` 는 `isPrimaryThread()` 로 판단) — 결정을 내릴 때 함께 바뀐다 |
| 재현 | — |
| 제안 | 결정 D1 을 낼 때 함께 본다 |

## 수정 묶음 제안

- **작은 묶음 하나(Sonnet medium)**: A15-02(호환 로더 리소스 자기 jar 먼저), A15-03(MemorySection 읽기 규칙), A15-04 의 `isDropItems`, A15-08, A15-09, A15-10, A15-11. 모두 호환 모듈 안(02 만 서버 로더 한 곳)이고 재현 시험이 그대로 회귀 시험이 된다.
- **A15-01(Sonnet high)**: Location·Vector·PotionEffect 직렬화와 등록부, MemorySection getter. A15-06 의 값 클래스 멤버를 같은 묶음에 넣으면 Location·Vector 를 한 번만 고친다.
- **A15-05(Sonnet high)**: `ConfigSection` 구분자 지원 — Furnace API 를 건드리므로 따로.
- **A15-04 `isNewChunk`·A15-07**: 서버 이벤트 필드·다리 구조를 바꾸므로 따로.

## 사용자 확인·결정

없음. 모두 Paper 원문으로 정답이 정해지는 차이다.
