# 생성자 인자가 있는 열거형(T-230): 상수 줄을 `이름 = (인자)` 로 적는다. 생성자·필드·메서드는 src/shims/java 조각이 쓴다. 상수는 전부 바닐라 순서로.
class net.minecraft.network.protocol.PacketFlow
  SERVERBOUND = ("serverbound")
  CLIENTBOUND = ("clientbound")

# 직소 블록 이음 종류(중첩 열거형 — 바깥 JigsawBlockEntity 는 src/shims/java 조각)
class net.minecraft.world.level.block.entity.JigsawBlockEntity$JointType
  ROLLABLE
  ALIGNED
