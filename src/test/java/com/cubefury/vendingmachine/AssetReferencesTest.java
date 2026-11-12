package com.cubefury.vendingmachine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Every model and texture the mod's models and blockstates name is shipped, so none loads as missing. */
class AssetReferencesTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/vendingmachine");

    private static List<Path> json(String folder) throws IOException {
        try (Stream<Path> files = Files.walk(ASSETS.resolve(folder))) {
            return files.filter(
                f -> f.toString()
                    .endsWith(".json"))
                .toList();
        }
    }

    private static void collect(JsonElement json, List<String> models, List<String> textures) {
        if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(child -> collect(child, models, textures));
        } else if (json.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                collectEntry(entry.getKey(), entry.getValue(), models, textures);
                collect(entry.getValue(), models, textures);
            }
        }
    }

    private static void collectEntry(String key, JsonElement value, List<String> models, List<String> textures) {
        if (value.isJsonPrimitive() && ("model".equals(key) || "parent".equals(key))) {
            models.add(value.getAsString());
        } else if (value.isJsonPrimitive() && "texture".equals(key)) {
            textures.add(value.getAsString());
        } else if ("textures".equals(key) && value.isJsonObject()) {
            value.getAsJsonObject().entrySet().forEach(t -> textures.add(t.getValue().getAsString()));
        }
    }

    private static void assertShipped(Path file, String id, String folder, String extension) {
        int colon = id.indexOf(':');
        assertTrue(colon > 0, () -> file + " names " + id + ", which has no namespace");
        if (!id.startsWith("vendingmachine:")) {
            return;
        }
        Path asset = ASSETS.resolve(folder).resolve(id.substring(colon + 1) + extension);
        assertTrue(Files.exists(asset), () -> file + " names " + id + ", which is not shipped");
    }

    @Test
    void everyModelAndTextureReferenceIsShipped() throws IOException {
        List<Path> files = Stream.concat(json("models").stream(), json("blockstates").stream())
            .toList();
        for (Path file : files) {
            List<String> models = new ArrayList<>();
            List<String> textures = new ArrayList<>();
            collect(JsonParser.parseString(Files.readString(file)), models, textures);
            models.forEach(model -> assertShipped(file, model, "models", ".json"));
            textures.stream()
                .filter(texture -> !texture.startsWith("#"))
                .forEach(texture -> assertShipped(file, texture, "textures", ".png"));
        }
    }
}
