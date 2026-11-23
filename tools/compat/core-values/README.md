# 좌표 계열 기대값 만들기 (영역 9)

`CoreValues` 는 입력 목록만 담은 도구다(바닐라 코드·이름 없음). 같은 입력을 바닐라 서버 jar 와 호환 계층에 먹여 출력을 견준다.
대상: BlockPos 반복자(betweenClosed·withinManhattan·randomBetweenClosed·spiralAround·breadthFirstTraversal…), AxisCycle, Cursor3D, Direction, SectionPos 스트림, ResourceLocation 읽기, Mth.lerp3.

- 시험: `CoreVanillaValuesTest`(소스 폴더에 이 디렉터리가 걸려 있다)가 호환 계층 쪽 줄을 만들어 `src/test/resources/core-vanilla-values.txt` 와 줄 단위로 견준다.
- 기대값 다시 만들기(판을 올릴 때, 로컬 전용): 바닐라 jar(Mojang 이름, `vanilla-extract` 캐시의 `work/server-mojmap.jar`)와 `work/libs` 의 라이브러리를 클래스패스에 놓고
  `javac -cp <jar>:<libs> -d out tools/compat/core-values/CoreValues.java && java -cp out:<jar>:<libs> org.domi.furnace.tools.core.CoreValues furnace-compat-paper/src/test/resources/core-vanilla-values.txt`
- 판이 바뀌어 줄이 달라지면 그 줄이 바뀐 동작이다. 반복자가 같은 객체를 고쳐 쓰는지(`*.reuse`)·스트림 특성(`spl`) 줄도 견준다.
