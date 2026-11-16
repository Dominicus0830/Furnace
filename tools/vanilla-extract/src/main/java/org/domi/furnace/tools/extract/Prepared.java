package org.domi.furnace.tools.extract;

import java.nio.file.Path;
import java.util.List;

/**
 * 도구를 돌릴 준비가 끝난 판.
 *
 * @param mc        판 이름
 * @param serverJar Mojang 이름의 서버 jar(난독화가 없는 판은 내부 서버 jar 그대로)
 * @param innerJar  번들러에서 꺼낸 내부 서버 jar 원본(난독화 이름 그대로 — 이전 도구와 견줄 때 쓴다)
 * @param mappings  공식 대응표 파일(난독화가 없는 판은 null)
 * @param libraries 서버가 쓰는 라이브러리 jar
 * @param generated 바닐라 데이터 생성기 출력(data/, reports/)
 * @param protocol  프로토콜 번호(version.json, 알 수 없으면 -1)
 * @param remapped  대응표로 이름을 풀었는가
 */
public record Prepared(String mc, Path serverJar, Path innerJar, Path mappings, List<Path> libraries, Path generated, int protocol, boolean remapped) {}
