// 모든 바이옴의 features 를 비우는 순정 데이터팩(지형 단독 정답지용)을 생성하는 도구
import com.google.gson.*;
import java.nio.file.*;
public class MakePack {
  public static void main(String[] a) throws Exception {
    Path src = Path.of("dp_src/data/minecraft/worldgen/biome");
    Path out = Path.of("nofeat");
    Files.createDirectories(out.resolve("data/minecraft/worldgen/biome"));
    Files.writeString(out.resolve("pack.mcmeta"), "{\"pack\":{\"pack_format\":48,\"description\":\"no features (terrain-only ground truth)\"}}");
    int n = 0;
    try (var s = Files.list(src)) {
      for (Path p : (Iterable<Path>) s::iterator) {
        JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
        JsonArray empty = new JsonArray();
        for (int i = 0; i < 11; i++) empty.add(new JsonArray());
        o.add("features", empty);
        Files.writeString(out.resolve("data/minecraft/worldgen/biome").resolve(p.getFileName()), new Gson().toJson(o));
        n++;
      }
    }
    System.out.println("wrote " + n + " biome overrides");
  }
}
