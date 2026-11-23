# Mth 기대값 만들기 (영역 9)

`MthValues` 는 입력 목록만 담은 도구다(바닐라 코드·이름 없음). 같은 입력을 바닐라 `Mth` 와 호환 계층 `Mth` 에 먹여 출력을 견준다.

- 시험: `MthVanillaValuesTest`(소스 폴더에 이 디렉터리가 걸려 있다)가 호환 계층 쪽 줄을 만들어 `src/test/resources/mth-vanilla-values.txt` 와 줄 단위로 견준다.
- 기대값 다시 만들기(판을 올릴 때, 로컬 전용): 바닐라 jar(Mojang 이름, `vanilla-extract` 캐시의 `work/server-mojmap.jar`)와 라이브러리를 클래스패스에 놓고
  `javac -cp <jar>:<libs> -d out tools/compat/mth-values/MthValues.java && java -cp out:<jar>:<libs> org.domi.furnace.tools.mth.MthValues > furnace-compat-paper/src/test/resources/mth-vanilla-values.txt`
- 바닐라 쪽이 던지는 입력은 줄에 예외 종류·문구가 남는다. 판이 바뀌어 줄이 달라지면 그 줄이 바뀐 동작이다.
