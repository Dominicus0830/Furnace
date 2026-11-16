# 블록 상태 번호 상수 목록 — StateIds(org.domi.furnace.server.world.StateIds)를 만드는 입력. 판이 바뀌면 이 파일은 그대로 두고
# blocks/definitions_<mc.resourceTag>.txt 만 새 판 것으로 바뀐다(번호는 거기서 풀린다). 서버 코드는 숫자를 직접 쓰지 않는다
# (tools/ci/check-raw-state-ids.py 가 CI 에서 강제한다). 상수라 javac 가 쓰는 자리에 값을 그대로 넣는다 — 실행 중 조회 비용이 없다.
#
# 한 줄 = 상수 하나:   이름  종류  블록  [속성]  [±가감]
#   first   그 블록의 첫 상태 번호          last    그 블록의 마지막 상태 번호
#   count   그 블록의 상태 수              stride  그 블록에서 속성 값이 하나 늘 때 번호가 느는 보폭(속성 필수)
#   ±N      결과에 더하거나 뺀다. 지금 동작을 그대로 두려고 남은 어긋남(아래 "알려진 어긋남")에만 쓴다.

# 공기 — 바닐라 BlockState.isAir() 는 air·cave_air·void_air 셋 다 참이다.
AIR                  first  minecraft:air
VOID_AIR             first  minecraft:void_air
CAVE_AIR             first  minecraft:cave_air

# 유체 — level 0~15 가 연속 16칸(0 이 원천).
WATER_SOURCE         first  minecraft:water
WATER_FIRST          first  minecraft:water
WATER_LAST           last   minecraft:water
LAVA_FIRST           first  minecraft:lava
LAVA_LAST            last   minecraft:lava
BEDROCK              first  minecraft:bedrock
MAGMA_BLOCK          first  minecraft:magma_block

# 잎 — 열 종류가 oak 부터 flowering_azalea 까지 이어 붙어 있다. 블록마다 28칸, 그 안에서 distance 가 4칸 보폭.
LEAVES_FIRST         first  minecraft:oak_leaves
LEAVES_LAST          last   minecraft:flowering_azalea_leaves
LEAVES_STATES_PER_BLOCK count minecraft:oak_leaves
LEAVES_DISTANCE_STEP stride minecraft:oak_leaves distance

# 통나무(원목) — oak·spruce·birch·jungle·acacia·cherry·dark_oak 가 이어 있고, cherry 는 TreeFeature 가 따로 뺀다.
LOG_FIRST            first  minecraft:oak_log
LOG_LAST             last   minecraft:dark_oak_log
CHERRY_LOG_FIRST     first  minecraft:cherry_log
CHERRY_LOG_LAST      last   minecraft:cherry_log

# 덩굴·발광 이끼
VINE_FIRST           first  minecraft:vine
VINE_LAST            last   minecraft:vine
GLOW_LICHEN_FIRST    first  minecraft:glow_lichen
GLOW_LICHEN_LAST     last   minecraft:glow_lichen

# 나무가 덮어 쓸 수 있는 풀·뿌리
SHORT_GRASS          first  minecraft:short_grass
FERN                 first  minecraft:fern
DEAD_BUSH            first  minecraft:dead_bush
SEAGRASS             first  minecraft:seagrass
TALL_SEAGRASS_FIRST  first  minecraft:tall_seagrass
TALL_SEAGRASS_LAST   last   minecraft:tall_seagrass
# sunflower·lilac·rose_bush·peony·tall_grass·large_fern 이 이어 있다.
TALL_FLOWERS_FIRST   first  minecraft:sunflower
TALL_FLOWERS_LAST    last   minecraft:large_fern
PITCHER_PLANT_FIRST  first  minecraft:pitcher_plant
PITCHER_PLANT_LAST   last   minecraft:pitcher_plant
WARPED_ROOTS         first  minecraft:warped_roots
NETHER_SPROUTS       first  minecraft:nether_sprouts
CRIMSON_ROOTS        first  minecraft:crimson_roots
HANGING_ROOTS_FIRST  first  minecraft:hanging_roots
HANGING_ROOTS_LAST   last   minecraft:hanging_roots

# 해초 심을 자리(SeagrassFeature.isSolidFloor)가 "딱딱하지 않은 바닥"으로 치는 범위.
# 알려진 어긋남(지금 동작 유지, docs/user-todo/entries/2026-11-16-v02-state-ids.md):
#  - 해초~키다리 해초 범위가 뒤로 3칸 더 늘어 piston(extended=true,facing=north·east·south 앞 3칸)까지 "딱딱하지 않음"으로 친다.
#  - 켈프 범위가 age=22~25 + kelp_plant 뿐이라 켈프 age 0~21 은 "딱딱함"으로 친다(블록 전체는 kelp ~ kelp_plant).
SEAGRASS_FLOOR_NONSOLID_LAST   last   minecraft:tall_seagrass +3
KELP_FLOOR_NONSOLID_FIRST      first  minecraft:kelp +22
KELP_FLOOR_NONSOLID_LAST       last   minecraft:kelp_plant
