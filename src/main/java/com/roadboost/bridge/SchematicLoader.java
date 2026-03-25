package com.roadboost.bridge;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads and caches WorldEdit clipboards from .schem / .schematic files.
 */
public class SchematicLoader {

    private final File schematicsFolder;
    private final Logger logger;
    private final Map<String, Clipboard> cache = new HashMap<>();

    public SchematicLoader(File schematicsFolder, Logger logger) {
        this.schematicsFolder = schematicsFolder;
        this.logger = logger;
        if (!schematicsFolder.exists()) {
            schematicsFolder.mkdirs();
        }
    }

    /**
     * Load (or return cached) a clipboard by schematic name (without extension).
     * Tries .schem first, then .schematic.
     */
    public Clipboard load(String name) {
        if (cache.containsKey(name)) return cache.get(name);

        File file = findFile(name);
        if (file == null) {
            logger.warning("Schematic not found: " + name + " (tried .schem and .schematic in " + schematicsFolder.getPath() + ")");
            return null;
        }

        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) {
            logger.warning("Could not detect format for schematic: " + file.getName());
            return null;
        }

        try (FileInputStream fis = new FileInputStream(file);
             ClipboardReader reader = format.getReader(fis)) {
            Clipboard clipboard = reader.read();
            cache.put(name, clipboard);
            logger.info("Loaded schematic: " + file.getName() + " (" +
                    clipboard.getDimensions().getBlockX() + "x" +
                    clipboard.getDimensions().getBlockY() + "x" +
                    clipboard.getDimensions().getBlockZ() + ")");
            return clipboard;
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to load schematic: " + file.getName(), e);
            return null;
        }
    }

    /** Returns the names of all available schematics (without extension). */
    public Set<String> getAvailableNames() {
        Map<String, Boolean> found = new HashMap<>();
        File[] files = schematicsFolder.listFiles();
        if (files != null) {
            for (File f : files) {
                String n = f.getName();
                if (n.endsWith(".schem")) found.put(n.replace(".schem", ""), true);
                else if (n.endsWith(".schematic")) found.put(n.replace(".schematic", ""), true);
            }
        }
        return found.keySet();
    }

    /** Clears the cache so schematics are re-read from disk on next use. */
    public void clearCache() {
        cache.clear();
    }

    private File findFile(String name) {
        File schem = new File(schematicsFolder, name + ".schem");
        if (schem.exists()) return schem;
        File schematic = new File(schematicsFolder, name + ".schematic");
        if (schematic.exists()) return schematic;
        return null;
    }
}
