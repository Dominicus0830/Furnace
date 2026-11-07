// 구조물 템플릿 API(불만 표 43번) — Furnace.server().structures().
// 바닐라 구조물 NBT(.nbt, 구조물 블록이 쓰고 읽는 그 파일)로 영역을 저장하고 다시 놓고, /locate 처럼 가까운 구조물을 찾는다.
//  - 템플릿 저장소는 월드 폴더 하나당 하나다(오버월드·네더·엔드·추가 월드가 같은 저장소를 쓴다 — 바닐라 StructureTemplateManager 와 같다).
//    찾는 순서: 메모리 저장소 → 월드 generated/&lt;네임스페이스&gt;/structures/ → 켜진 데이터 팩의 data/&lt;네임스페이스&gt;/structure/ → 내장(minecraft). 구조물 블록이 저장한 템플릿도 같은 저장소다.
//  - 놓기·저장은 영역의 청크가 모두 한 region 에 올라와 있어야 한다. 아직 안 올라온 청크는 먼저 불러오려 하지만(청크 티켓을 걸어 두면 확실하다),
//    올라오지 못하면 아무것도 바꾸지 않고 NOT_LOADED 로 끝난다.
//  - 놓기는 /place template 와 같은 경로라 BlockPlace 같은 플레이어 이벤트는 거치지 않는다.
package org.domi.furnace.api.structure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.World;

public interface Structures {

    /** 구조물 블록이 한 번에 저장하는 한 변 최대 칸. */
    int MAX_SAVE_SIZE = 48;

    // ------------------------------------------------------------------ 템플릿 저장소

    /** 이 월드에서 읽을 수 있는 템플릿 이름(월드 generated/ + 켜진 데이터 팩, 정렬·중복 없음). 아직 파일로 안 쓴 메모리 템플릿은 빠진다. 아무 스레드. */
    List<String> templates(World world);

    /** 그 이름의 템플릿을 읽을 수 있는가(메모리 포함). 처음 읽으면 파일을 읽으므로 입출력이 있다. */
    boolean hasTemplate(World world, String id);

    /** 템플릿 요약. 없으면 empty. */
    Optional<StructureTemplateInfo> templateInfo(World world, String id);

    /** 템플릿을 바닐라 구조물 NBT(DataVersion 포함, 구조물 블록이 저장하는 모양)로 낸다. 없으면 empty. */
    Optional<NbtCompound> exportTemplate(World world, String id);

    /** {@link #exportTemplate} 를 gzip 으로 묶은 .nbt 파일 내용. */
    Optional<byte[]> exportTemplateFile(World world, String id);

    /**
     * 바닐라 구조물 NBT 로 템플릿을 정의한다(같은 이름이 있으면 갈아 끼운다). 옛 DataVersion 은 서버의 구조물 데이터 픽서를 거친다
     * (픽서가 못 다루는 판은 경고 후 그대로 읽는다). toDisk 면 generated/ 에도 쓴다.
     *
     * @throws IllegalArgumentException 이름이 올바르지 않거나 NBT 가 구조물 템플릿 모양이 아니다
     * @return toDisk 로 파일 쓰기에 성공했거나 toDisk 가 거짓이면 true, 파일 쓰기만 실패했으면 false(메모리에는 들어 있다)
     */
    boolean importTemplate(World world, String id, NbtCompound nbt, boolean toDisk);

    /** {@link #importTemplate} 의 .nbt 파일(gzip) 판. 압축이나 NBT 를 못 읽으면 IllegalArgumentException. */
    boolean importTemplateFile(World world, String id, byte[] gzipNbt, boolean toDisk);

    /**
     * 메모리 저장소와 월드 generated/ 의 파일을 지운다. 데이터 팩·내장 템플릿은 못 지운다 — 같은 이름이 거기 있으면 그것이 다시 읽힌다.
     * 지운 것(메모리나 파일)이 있으면 true.
     */
    boolean deleteTemplate(World world, String id);

    // ------------------------------------------------------------------ 놓기·저장

    /**
     * 템플릿을 origin(템플릿 원점 = 최소 x·y·z 모서리, 회전·대칭은 이 점 둘레)에 놓는다. 영역의 청크를 먼저 불러오고, 그 청크를 가진 region
     * 스레드에서 /place template 와 같은 배치기(프로세서·물 처리·블록 엔티티 NBT·엔티티·모양 갱신)로 놓는다.
     */
    CompletableFuture<PlaceResult> place(World world, String id, BlockPos origin, PlaceOptions options);

    default CompletableFuture<PlaceResult> place(World world, String id, BlockPos origin) { return place(world, id, origin, PlaceOptions.DEFAULT); }

    /**
     * 월드의 정육면체 영역(min 에서 min+size-1 까지)을 id 라는 템플릿으로 저장한다(구조물 블록의 저장 모드와 같은 경로 —
     * 구조물 빈 칸은 빼고, 블록 엔티티 NBT 를 함께 담는다). 영역의 청크를 먼저 불러오고 그 region 스레드에서 읽는다.
     * size 는 각 축 1..{@link #MAX_SAVE_SIZE}.
     */
    CompletableFuture<SaveResult> save(World world, String id, BlockPos min, int sizeX, int sizeY, int sizeZ, SaveOptions options);

    default CompletableFuture<SaveResult> save(World world, String id, BlockPos min, int sizeX, int sizeY, int sizeZ) {
        return save(world, id, min, sizeX, sizeY, sizeZ, SaveOptions.DEFAULT);
    }

    // ------------------------------------------------------------------ 찾기

    /** 이 서버가 아는 구조물 종류(minecraft:village_plains …, 정렬). */
    List<String> structureIds();

    /**
     * /locate structure 와 같은 찾기: from 에서 가장 가까운 구조물 시작점. structures 의 원소는 구조물 id(minecraft:village_plains) 또는
     * 태그('#minecraft:village'). radius 는 청크 단위 탐색 반경(1..100, /locate 와 같은 100 이 최대). 틱 스레드 밖에서 계산해
     * 결과를 알려 주고, 이 월드의 생성기가 구조물을 만들지 않거나(평지 …) 반경 안에 없으면 empty. 모르는 구조물 id 는 IllegalArgumentException.
     * 청크를 만들거나 불러오지 않는다.
     */
    CompletableFuture<Optional<StructureLocation>> locate(World world, Collection<String> structures, BlockPos from, int radius);

    default CompletableFuture<Optional<StructureLocation>> locate(World world, String structure, BlockPos from) {
        return locate(world, List.of(structure), from, 100);
    }
}
