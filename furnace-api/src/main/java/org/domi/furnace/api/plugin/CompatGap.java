package org.domi.furnace.api.plugin;

/**
 * 호환 어댑터가 모은 "이 서버에 아직 없는 것" 한 줄(T-233). 운영자가 {@code /furnace compat gaps} 로 표 파일을 내보내
 * 어떤 구멍이 실제로 자주 걸리는지 알리는 데 쓴다. 형식 어댑터 SPI({@link PluginFormat#compatGaps})의 자료형이다 — 플러그인이 만들거나 읽을 일은 없다. 서버는 이것을 스스로 밖에 보내지 않는다. 실험적 API 다 — 열 구성은 바뀔 수 있다.
 *
 * @param plugin     부른 플러그인 이름
 * @param kind       {@code class}·{@code method}·{@code field}·{@code extends}(상속 거부) 중 하나. 모르면 {@code unknown}
 * @param target     대상 심볼(점 이름). 메서드 {@code 클래스#이름(인자 형)}, 필드 {@code 클래스#이름 : 형}, extends 는 NMS 부모 클래스
 * @param referenced 플러그인 바이트코드가 이것을 가리킨다고 로드 때 확인했는가
 * @param calls      실행 중 실제로 걸려 던진 횟수(구멍을 밟은 횟수)
 */
public record CompatGap(String plugin, String kind, String target, boolean referenced, long calls) {}
