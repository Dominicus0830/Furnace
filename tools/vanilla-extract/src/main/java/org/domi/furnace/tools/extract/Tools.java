package org.domi.furnace.tools.extract;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.domi.furnace.tools.extract.Tool.Kind;
import org.domi.furnace.tools.extract.Tool.Output;

/** 추출 도구 목록(결과 위치는 지금 저장소에 커밋된 리소스 이름 그대로). */
public final class Tools {
    private static final String S = "furnace-server/src/main/resources/";
    private static final String T = "furnace-server/src/test/resources/";
    private static final String C = "furnace-compat-paper/src/test/resources/";
    private static final String SPEC = "furnace-compat-paper/src/shimspec/";
    private static final String API = "furnace-api/src/main/resources/org/domi/furnace/api/packet/schema/";

    private static final Map<String, Tool> ALL = new LinkedHashMap<>();

    private static void java(String id, String cls, String what, Output... outs) {
        ALL.put(id, new Tool(id, Kind.JAVA, "furnacetools." + cls, List.of(outs), List.of(), true, what));
    }

    private static void python(String id, String script, List<String> args, String what, Output out) {
        ALL.put(id, new Tool(id, Kind.PYTHON, script, List.of(out), args, true, what));
    }

    static {
        // 블록
        java("block-attrs", "ExportBlockAttrs", "상태별 블록 속성(공기·가림·교체·액체·모양 마스크…)", Output.of(S + "blocks/blockattrs_{mc}.txt"));
        java("block-hardness", "ExportBlockHardness", "블록별 destroyTime", Output.of(S + "blocks/hardness_{mc}.txt"));
        java("block-light", "ExportBlockLight", "상태별 빛 차단·가림 모양 사용·canOcclude", Output.of(S + "blocks/blocklight_{mc}.txt"));
        java("block-props", "ExportBlockProps", "블록 속성 + 상태별 깃발(stateflags)", Output.of(S + "blocks/blockprops_{mc}.txt"), Output.of(S + "blocks/stateflags_{mc}.txt.gz"));
        java("block-shapes", "ExportBlockShapes", "상태별 outline·collision·support·occlusion·interaction 모양", Output.gz(S + "blocks/blockshapes_{mc}.txt.gz"));
        java("block-transforms", "ExportBlockTransforms", "mirror·rotate 결과 + 담장 연결·지지 마스크",
                Output.gz(S + "blocks/blocktransforms_{mc}.txt.gz"), Output.gz(S + "blocks/blockwalltests_{mc}.txt.gz"), Output.gz(S + "blocks/blocksupports_{mc}.txt.gz"));
        java("block-sound-types", "ExportBlockSoundTypes", "블록별 SoundType(상태별 덮어쓰기 포함)", Output.of(S + "blocks/soundtypes_{mc}.txt"));
        java("map-colors", "ExportMapColors", "MapColor 번호 → RGB", Output.of(S + "blocks/mapcolors_{mc}.txt"));
        java("note-instruments", "ExportNoteInstruments", "소리 블록 악기", Output.of(S + "blocks/instruments_{mc}.txt"));
        java("pathfindable", "ExportPathfindable", "상태별 isPathfindable(땅·물·공중)", Output.of(S + "blocks/pathfindable_{mc}.txt"));
        // 아이템
        java("item-props", "ExportItemProps", "아이템 최대 스택·내구도·인챈트 수치", Output.of(S + "items/properties_{mc}.txt"));
        java("item-blocks", "ExportItemBlocks", "블록 아이템 표", Output.of(S + "items/itemblocks_{mc}.txt"));
        java("item-default-attributes", "ExportItemDefaultAttributes", "아이템 기본 속성 수정자", Output.of(S + "items/default_attributes_{mc}.txt"));
        java("craft-remainders", "ExportCraftRemainders", "제작 후 남는 아이템", Output.of(S + "items/craft_remainders_{mc}.txt"));
        java("stew-effects", "ExportStewEffects", "수상한 스튜 효과", Output.of(S + "items/stew_effects_{mc}.txt"));
        // 호환(shim 생성기 T-231)
        java("kind-classes", "ExportKindClasses", "블록·아이템 종류 → 바닐라 클래스", Output.of(SPEC + "kind-classes-{mc}.txt"));
        java("entity-data", "ExportEntityData", "엔티티 메타데이터 칸 배치·주인 부류·칸 번호 표(V-04)",
                Output.of(S + "entity_data/layouts-{mc}.txt"), Output.of(S + "entity_data/owners-{mc}.txt"), Output.of(S + "entity_data/ids-{mc}.txt"));
        java("entity-data-defaults", "ExportEntityDataDefaults", "엔티티 종류별 칸 처음 값 두 개(정의 값·생성 뒤 값, 엔티티 데이터 D1b)",
                Output.of(T + "entity_data/defaults-{mc}.txt"));   // 시험만 쓴다(운영은 서버 엔티티에서 만든다)
        // 프로토콜
        java("protocol-ids", "ExportProtocolIds", "프로토콜 패킷 종류·번호 표", Output.of(API + "protocol-{protocol}-ids.txt"));
        // 시험 정답표
        java("context-collision-shapes", "ExportContextCollisionShapes", "문맥별 충돌 모양(비계·물·용암) 정답표", Output.of(T + "collision/{mc}/context-shapes.tsv"));
        java("section-oracle", "ExportSectionOracle", "섹션 개수·하이트맵 정답표", Output.gz(T + "n2b/{mc}/section-oracle.txt.gz"));
        java("raw-write-oracle", "ExportRawWriteOracle", "원시 쓰기 뒤 하이트맵·섹션 개수 정답표", Output.gz(T + "n2b/{mc}/rawwrite-oracle.txt.gz"));
        ALL.put("palette-oracle", new Tool("palette-oracle", Kind.JAVA, "furnacetools.ExportPaletteOracle",
                List.of(Output.gz(C + "n2b/{mc}/palette-oracle.txt.gz")), List.of("{out0}", "{serverJar}"), true, "떨어진 PalettedContainer·섹션 직렬화 정답표"));
        // 데이터 생성기 출력(JSON)을 읽는 도구
        python("tags", "extract_tags.py", List.of("{generated}", "{repo}/" + S + "registries/{mc}.txt", "{out0}", "{mc}"),
                "Update Tags 리소스(숫자 id)", Output.of(S + "registries/tags-{mc}.txt"));
        python("builtin-registries", "extract_builtin_registries.py", List.of("{generated}/reports/registries.json", "{out0}", "{mc}"),
                "내장 레지스트리 protocol_id 순서", Output.of(S + "registries/builtin-{mc}.txt"));
        python("block-tags", "extract_block_tags.py", List.of("{generated}", "{out0}", "{mc}"),
                "월드젠용 블록 태그(블록 이름)", Output.of(S + "worldgen/{mc}/block_tags.txt"));
        python("item-components", "extract_item_components.py", List.of("{generated}/reports/items.json", "{out0}"),
                "아이템 기본 컴포넌트(gzip JSON)", Output.of(S + "items/components_{mc}.json.gz"));
        python("block-definitions", "extract_block_definitions.py", List.of("{generated}/reports/blocks.json", "{out0}", "{mc}"),
                "블록 정의 종류·상태 범위·속성 배치", Output.of(S + "blocks/definitions_{mc}.txt"));
        python("damage-types", "extract_damage_types.py", List.of("{generated}/data/minecraft/damage_type", "{out0}", "{mc}"),
                "피해 유형 표", Output.of(S + "registries/damage_types-{mc}.txt"));
        // 재료를 따로 받는 도구(전체 실행에 넣지 않는다)
        ALL.put("shape-vectors", new Tool("shape-vectors", Kind.JAVA, "furnacetools.ShapeVectors", List.of(), List.of("{scratch}/shape-vectors-{mc}.txt", "{tool-args}"), false,
                "BlockState.updateShape 차분 시험 벡터(--tool-arg shape-vectors=<값> 을 차례로 4개: 블록들, 이웃 블록들, 상태당 시도 수, 시드. 출력은 build/out/<판>/scratch/ 에 쓴다)"));
    }

    private Tools() {}

    public static Map<String, Tool> all() {
        return ALL;
    }

    public static String fill(String pattern, String mc, int protocol) {
        return pattern.replace("{mc}", mc).replace("{protocol}", protocol < 0 ? "unknown" : Integer.toString(protocol));
    }
}
