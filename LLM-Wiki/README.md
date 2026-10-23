# LLM Wiki
개발 프로젝트 지식 베이스.

## 구조
- raw/: 웹 클리퍼 또는 외부에서 수집한 원본 자료
- wiki/[프로젝트명]/: 각 프로젝트별 정리된 지식 (caveman 압축 형식)
- output/: 결과물 (코드 스니펫, 설계 메모 등)

## 슬래시 커맨드
- /ingest: raw/ -> wiki/ 정리
- /query: wiki/ 기반 질문 답변
- /lint: wiki/ 전체 점검
- /save: 현재 세션 작업 내용 저장

## 프로젝트 폴더
- wiki/DailyTown/: DailyTown Minecraft 서버
- wiki/[새 프로젝트]/: 작업 시 자동 생성
