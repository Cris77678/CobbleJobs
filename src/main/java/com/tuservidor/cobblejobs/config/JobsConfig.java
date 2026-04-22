package com.tuservidor.cobblejobs.config;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import lombok.Data;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Data
public class JobsConfig {

    private static JobsConfig INSTANCE = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Paths.get("config", "cobblejobs", "config.json");

    // Butcher
    private ZoneConfig butcherZone = new ZoneConfig();
    private boolean butcherEnabled = true;
    private Map<String, Double> butcherSpeciesPrices = new LinkedHashMap<>(Map.of(
        "cobblemon:miltank",  150.0,
        "cobblemon:torchic",   80.0,
        "cobblemon:mareep",    90.0,
        "cobblemon:wooloo",    85.0,
        "cobblemon:tauros",   160.0
    ));
    private int butcherMaxSpawns = 6;
    private int butcherSpawnIntervalTicks = 600;

    // Fisher
    private ZoneConfig fisherZone = new ZoneConfig();
    private boolean fisherEnabled = true;
    private List<FishEntry> fishingTable = new ArrayList<>(List.of(
        new FishEntry("cobblemon:magikarp", 0.60, 0.1, 8.0,  3,  60, 0.05),
        new FishEntry("cobblemon:magikarp", 0.25, 8.0, 20.0, 50, 100, 0.08),
        new FishEntry("cobblemon:feebas",   0.10, 0.5, 5.0,  5,  40, 0.12),
        new FishEntry("cobblemon:goldeen",  0.05, 0.5, 3.0,  3,  30, 0.10)
    ));
    private double pricePerKg = 10.0;
    private double shinyMultiplier = 5.0;
    private int fishMinTicks = 100;
    private int fishMaxTicks = 300;

    @Data
    public static class ZoneConfig {
        private String world = "minecraft:overworld";
        private double x1 = 0, y1 = 0, z1 = 0;
        private double x2 = 0, y2 = 0, z2 = 0;
        private boolean configured = false;

        public boolean contains(double x, double y, double z) {
            if (!configured) return false;
            return x >= Math.min(x1,x2) && x <= Math.max(x1,x2)
                && y >= Math.min(y1,y2) && y <= Math.max(y1,y2)
                && z >= Math.min(z1,z2) && z <= Math.max(z1,z2);
        }

        /** 2D check (ignores Y) — used for fishing zones where player Y varies */
        public boolean containsXZ(double x, double z) {
            if (!configured) return false;
            return x >= Math.min(x1,x2) && x <= Math.max(x1,x2)
                && z >= Math.min(z1,z2) && z <= Math.max(z1,z2);
        }

        public void set(double ax, double ay, double az, double bx, double by, double bz) {
            x1 = ax; y1 = ay; z1 = az;
            x2 = bx; y2 = by; z2 = bz;
            configured = true;
        }
    }

    @Data
    public static class FishEntry {
        private String species    = "cobblemon:magikarp";
        private double chance     = 0.6;
        private double minWeight  = 0.1;
        private double maxWeight  = 8.0;
        private int    minLength  = 3;
        private int    maxLength  = 60;
        private double shinyChance = 0.05;

        public FishEntry() {}
        public FishEntry(String sp, double ch, double minW, double maxW,
                         int minL, int maxL, double shiny) {
            species = sp; chance = ch; minWeight = minW; maxWeight = maxW;
            minLength = minL; maxLength = maxL; shinyChance = shiny;
        }
    }

    public static JobsConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            if (Files.exists(CONFIG_FILE)) {
                try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                    INSTANCE = GSON.fromJson(r, JobsConfig.class);
                }
            } else {
                INSTANCE = new JobsConfig();
                save();
            }
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Failed to load config", e);
            INSTANCE = new JobsConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Failed to save config", e);
        }
    }
}
