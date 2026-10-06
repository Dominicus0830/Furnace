// 바이옴 JSON 의 기후·색·강수 속성을 plains 와 나란히 비교한다
import com.google.gson.*;
import java.nio.file.*;
public class BiomeFx {
  public static void main(String[] a) throws Exception {
    String[] ids = {"plains","forest","birch_forest","old_growth_birch_forest","dark_forest","lush_caves","dripstone_caves","grove","snowy_taiga","jungle","savanna","river","beach","ocean","swamp","desert"};
    System.out.printf("%-24s %5s %5s %-10s %-8s %-8s %-8s %-8s%n","biome","temp","down","precip","water","grassMod","grass","foliage");
    for (String id : ids) {
      JsonObject o = JsonParser.parseString(Files.readString(Path.of("dp_src/data/minecraft/worldgen/biome/" + id + ".json"))).getAsJsonObject();
      JsonObject e = o.getAsJsonObject("effects");
      String prec = o.get("has_precipitation").getAsBoolean() ? "yes" : "no";
      double t = o.get("temperature").getAsDouble();
      String mod = e.has("grass_color_modifier") ? e.get("grass_color_modifier").getAsString() : "-";
      System.out.printf("%-24s %5.2f %5.2f %-10s %-8d %-8s %-8s %-8s%n", id, t, o.get("downfall").getAsDouble(), prec + (o.has("temperature_modifier") ? "/frozen" : ""),
        e.get("water_color").getAsInt(), mod, e.has("grass_color") ? Integer.toHexString(e.get("grass_color").getAsInt()) : "(climate)", e.has("foliage_color") ? Integer.toHexString(e.get("foliage_color").getAsInt()) : "(climate)");
    }
  }
}
