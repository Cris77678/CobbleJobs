package com.tuservidor.cobblejobs.config;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import com.tuservidor.cobblejobs.fishing.zone.FishingZone;
import lombok.Data;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Data
public class FisherConfig {

    private static FisherConfig INSTANCE = null;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = Paths.get("config", "cobblejobs", "config.json");

    private List<FishingZone> zones = defaultZones();
    private double rodPrice = 500.0;
    private double pricePerKg = 10.0;
    private double shinyMultiplier = 3.0;

    // ── Hieleras (Almacenamiento) ─────────────────────────────────────────
    private List<Double> coolerPrices = List.of(2000.0, 5000.0, 12000.0, 25000.0, 50000.0);
    private List<Integer> coolerLevels = List.of(5, 15, 25, 35, 45);

    // Tiempos de pesca más rápidos
    private int fishMinTicks = 40;  
    private int fishMaxTicks = 120; 
    
    private int fishCooldownTicks = 60;
    private int afkThresholdTicks = 200;
    private int eventDurationTicks  = 3600;
    private int eventCooldownTicks  = 24000;
    private int    rarityBonusEveryLevels  = 5;
    private double rarityBonusPerStep      = 0.02; 
    private boolean minigameEnabled = true;
    private double minigameSuccessMultiplier = 1.0;
    private double minigamePartialMultiplier = 0.6;
    private double minigameFailMultiplier    = 0.2;
    private List<CollectionMilestone> collectionMilestones = defaultMilestones();
    private boolean broadcastLegendary = true;
    private boolean broadcastEpic = true;

    public static FisherConfig get() {
        if (INSTANCE == null) load();
        return INSTANCE;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            if (Files.exists(CONFIG_FILE)) {
                try (Reader r = Files.newBufferedReader(CONFIG_FILE)) {
                    FisherConfig loaded = GSON.fromJson(r, FisherConfig.class);
                    if (loaded != null) {
                        INSTANCE = loaded;
                        ensureDefaults(INSTANCE);
                        CobbleJobs.LOGGER.info("[CobbleJobs] Config cargada desde disco.");
                        return;
                    }
                }
            }
            INSTANCE = new FisherConfig();
            save();
            CobbleJobs.LOGGER.info("[CobbleJobs] Config de ejemplo generada.");
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Error cargando config", e);
            INSTANCE = new FisherConfig();
        }
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_FILE.getParent());
            try (Writer w = Files.newBufferedWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Error guardando config", e);
        }
    }

    private static void ensureDefaults(FisherConfig c) {
        if (c.zones == null || c.zones.isEmpty())
            c.zones = defaultZones();
        if (c.collectionMilestones == null)
            c.collectionMilestones = defaultMilestones();
    }

    private static List<FishingZone> defaultZones() {
        FishingZone lake  = new FishingZone("lago_principal", FishingZone.ZoneType.LAKE);
        lake.setFishTable(lakeFishTable());

        FishingZone ocean = new FishingZone("oceano_sur", FishingZone.ZoneType.OCEAN);
        ocean.setFishTable(oceanFishTable());

        FishingZone spec  = new FishingZone("zona_evento", FishingZone.ZoneType.SPECIAL);
        spec.setFishTable(specialFishTable());

        return new ArrayList<>(List.of(lake, ocean, spec));
    }

    private static List<FishingZone.ZoneFishEntry> lakeFishTable() {
        return new ArrayList<>(List.of(
            new FishingZone.ZoneFishEntry("cobblemon:magikarp", FishRarity.COMMON,    0.45, 0.1,  8.0,   3,  60,  1),
            new FishingZone.ZoneFishEntry("cobblemon:goldeen",  FishRarity.COMMON,    0.20, 0.5,  4.0,   5,  40,  1),
            new FishingZone.ZoneFishEntry("cobblemon:psyduck",  FishRarity.COMMON,    0.15, 5.0,  15.0,  30, 80,  5),
            new FishingZone.ZoneFishEntry("cobblemon:poliwag",  FishRarity.RARE,      0.08, 0.4,  3.5,   4,  35,  3),
            new FishingZone.ZoneFishEntry("cobblemon:feebas",   FishRarity.RARE,      0.06, 0.3,  5.0,   5,  45,  8),
            new FishingZone.ZoneFishEntry("cobblemon:slowpoke", FishRarity.RARE,      0.04, 10.0, 36.0,  50, 120, 12),
            new FishingZone.ZoneFishEntry("cobblemon:dratini",  FishRarity.EPIC,      0.015, 0.8,  6.0,   30, 100, 15),
            new FishingZone.ZoneFishEntry("cobblemon:gyarados", FishRarity.EPIC,      0.005, 100.0,250.0, 400,650, 25)
        ));
    }

    private static List<FishingZone.ZoneFishEntry> oceanFishTable() {
        return new ArrayList<>(List.of(
            new FishingZone.ZoneFishEntry("cobblemon:tentacool",  FishRarity.COMMON,    0.35, 0.2,  5.0,   5,  50,  1),
            new FishingZone.ZoneFishEntry("cobblemon:horsea",     FishRarity.COMMON,    0.20, 0.1,  3.0,   3,  30,  1),
            new FishingZone.ZoneFishEntry("cobblemon:shellder",   FishRarity.COMMON,    0.15, 0.5,  4.0,   10, 30,  5),
            new FishingZone.ZoneFishEntry("cobblemon:staryu",     FishRarity.RARE,      0.12, 0.3,  4.0,   8,  40,  8),
            new FishingZone.ZoneFishEntry("cobblemon:corsola",    FishRarity.RARE,      0.08, 0.5,  5.0,   10, 50,  10),
            new FishingZone.ZoneFishEntry("cobblemon:wailmer",    FishRarity.RARE,      0.04, 80.0, 150.0, 150,200, 15),
            new FishingZone.ZoneFishEntry("cobblemon:lapras",     FishRarity.EPIC,      0.04, 50.0, 220.0, 100,250, 20),
            new FishingZone.ZoneFishEntry("cobblemon:lugia",      FishRarity.LEGENDARY, 0.015, 100.0,250.0, 300,600, 30),
            new FishingZone.ZoneFishEntry("cobblemon:kyogre",     FishRarity.LEGENDARY, 0.005, 150.0,352.0, 350,700, 40)
        ));
    }

    private static List<FishingZone.ZoneFishEntry> specialFishTable() {
        return new ArrayList<>(List.of(
            new FishingZone.ZoneFishEntry("cobblemon:feebas",    FishRarity.RARE,       0.30, 0.3,  5.0,   5,  45,  1),
            new FishingZone.ZoneFishEntry("cobblemon:milotic",   FishRarity.EPIC,       0.25, 3.0,  15.0,  80, 300, 5),
            new FishingZone.ZoneFishEntry("cobblemon:dratini",   FishRarity.EPIC,       0.20, 0.8,  6.0,   30, 100, 5),
            new FishingZone.ZoneFishEntry("cobblemon:kyogre",    FishRarity.LEGENDARY,  0.15, 150.0,352.0, 350,700, 20),
            new FishingZone.ZoneFishEntry("cobblemon:manaphy",   FishRarity.LEGENDARY,  0.10, 5.0,  15.0,  100,200, 25)
        ));
    }

    private static List<CollectionMilestone> defaultMilestones() {
        return new ArrayList<>(List.of(
            new CollectionMilestone("first_fish",    "total_fish",  1,   "¡Primer Pez!",           "Capturaste tu primer pez.",              50),
            new CollectionMilestone("fish_10",       "total_fish",  10,  "Pescador Novato",        "10 peces capturados.",                  200),
            new CollectionMilestone("fish_50",       "total_fish",  50,  "Pescador Experto",       "50 peces capturados.",                  800),
            new CollectionMilestone("fish_100",      "total_fish",  100, "Pescador Profesional",   "100 peces capturados.",                2000),
            new CollectionMilestone("fish_500",      "total_fish",  500, "Maestro Pescador",       "500 peces capturados.",                8000),
            new CollectionMilestone("first_rare",    "rare_fish",   1,   "¡Raro!",                 "Capturaste tu primer pez raro.",         300),
            new CollectionMilestone("first_epic",    "epic_fish",   1,   "¡Épico!",                "Capturaste tu primer pez épico.",       1000),
            new CollectionMilestone("first_legend",  "legendary",   1,   "¡LEGENDARIO!",           "Capturaste un pez legendario.",         5000),
            new CollectionMilestone("unique_5",      "unique_fish", 5,   "Coleccionista",          "5 especies distintas capturadas.",       400),
            new CollectionMilestone("unique_10",     "unique_fish", 10,  "Gran Coleccionista",     "10 especies distintas capturadas.",     1500)
        ));
    }

    @Data
    public static class CollectionMilestone {
        private String id          = "milestone";
        private String type        = "total_fish";
        private long   required    = 10;
        private String title       = "Hito";
        private String description = "";
        private double moneyReward = 0;

        public CollectionMilestone() {}
        public CollectionMilestone(String id, String type, long required,
                                   String title, String desc, double money) {
            this.id = id; this.type = type; this.required = required;
            this.title = title; this.description = desc; this.moneyReward = money;
        }
    }
}
