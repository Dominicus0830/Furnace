# SNBT 바닐라 기준 파일 뽑기

`furnace-server/src/test/resources/snbt/vanilla-tagparser-1.21.1.tsv` 를 만든 작은 하니스다(바닐라 jar 는 커밋하지 않는다).

1. 바닐라 1.21.1 서버 jar(공식 launcher manifest 의 `server`)를 받아 `META-INF/versions/1.21.1/server-1.21.1.jar` 와 `META-INF/libraries/*.jar` 를 푼다. 서명 파일(`META-INF/*.SF|RSA|DSA`)을 뺀 사본을 쓴다(기본 패키지 클래스와 서명이 충돌한다).
2. 1.21.1 서버 jar 는 난독화돼 있다. 공식 `server_mappings` 로 `net.minecraft.nbt.TagParser`(= `uz`)·`Tag`(= `uy`)를 확인한다. 이름이 다르면 `Dump.java` 를 고친다.
3. `javac -cp <클래스경로> Dump.java`, `java -cp .:<클래스경로> Dump 입력.txt > 출력.tsv`(입력은 한 줄에 한 개).
4. 출력의 `OK` 줄은 `입력, OK, Tag.toString, 읽은 위치`, `ERR` 줄은 `입력, ERR, 오류 문구` 열만 남긴다(종류 열 제거).

기준 파일 뽑은 날짜: 2026-11-21, 입력 206개(허용 109·거절 97).
