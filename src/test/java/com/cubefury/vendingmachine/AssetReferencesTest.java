package com.cubefury.vendingmachine;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Every model, texture and sound file the mod's assets name is shipped, so none loads as missing. */
class AssetReferencesTest {

    private static final Path ASSETS = Path.of("src/main/resources/assets/vendingmachine");
    private static final Pattern REFERENCE = Pattern.compile("\"vendingmachine:((?:block|item)/[a-z0-9_/]+)\"");
    private static final Pattern SOUND = Pattern.compile("\"name\"\\s*:\\s*\"vendingmachine:([a-z0-9_/]+)\"");

    private static List<Path> json(String folder) throws IOException {
        try (Stream<Path> files = Files.walk(ASSETS.resolve(folder))) {
            return files.filter(
                f -> f.toString()
                    .endsWith(".json"))
                .toList();
        }
    }

    @Test
    void everyModelAndTextureReferenceIsShipped() throws IOException {
        List<Path> files = Stream.concat(json("models").stream(), json("blockstates").stream())
            .toList();
        for (Path file : files) {
            Matcher m = REFERENCE.matcher(Files.readString(file));
            while (m.find()) {
                String name = m.group(1);
                boolean shipped = Files.exists(ASSETS.resolve("models/" + name + ".json"))
                    || Files.exists(ASSETS.resolve("textures/" + name + ".png"));
                assertTrue(shipped, file + " names " + name + ", which is not shipped");
            }
        }
    }

    @Test
    void everySoundFileIsShipped() throws IOException {
        Matcher m = SOUND.matcher(Files.readString(ASSETS.resolve("sounds.json")));
        while (m.find()) {
            String name = m.group(1);
            assertTrue(Files.exists(ASSETS.resolve("sounds/" + name + ".ogg")), "sounds.json names " + name);
        }
    }
}
