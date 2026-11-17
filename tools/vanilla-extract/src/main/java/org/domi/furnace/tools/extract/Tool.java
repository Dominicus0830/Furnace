package org.domi.furnace.tools.extract;

import java.util.List;

/**
 * 추출 도구 한 개의 선언.
 *
 * @param id        명령줄 이름
 * @param kind      Java(Mojang 이름 서버 클래스 위에서 도는 도구) / PYTHON(데이터 생성기 JSON 을 읽는 도구)
 * @param entry     Java 는 `furnacetools.<클래스>`, PYTHON 은 resources/python/ 의 파일 이름
 * @param outputs   저장소 기준 결과 경로(`{mc}`·`{protocol}` 치환)
 * @param args      도구 인자 틀. 토큰 {out0}.. 결과, {serverJar}, {generated}, {repo}, {mc}, {protocol}. 비면 결과 경로들을 차례로 준다.
 * @param byDefault 인자 없이 전체 실행할 때 포함하는가(재료를 따로 받는 도구는 false)
 * @param what      한 줄 설명
 */
public record Tool(String id, Kind kind, String entry, List<Output> outputs, List<String> args, boolean byDefault, String what) {
    public enum Kind { JAVA, PYTHON }

    /**
     * @param dest      저장소 기준 경로
     * @param gzipAfter 도구는 풀어쓴 파일(`.gz` 뺀 이름)을 쓰고, 실행기가 gzip 으로 묶는다
     */
    public record Output(String dest, boolean gzipAfter) {
        public static Output of(String dest) {
            return new Output(dest, false);
        }

        public static Output gz(String dest) {
            return new Output(dest, true);
        }
    }
}
