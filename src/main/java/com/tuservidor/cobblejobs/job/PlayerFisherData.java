package com.tuservidor.cobblejobs.job;

import com.google.gson.*;
import com.tuservidor.cobblejobs.CobbleJobs;
import lombok.Data;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class PlayerFisherData {

    public static final int MAX_LEVEL = 50;
    private static final double XP_BASE  = 100.0;
    private static final double XP_SCALE = 1.25;

    private static final Map<UUID, PlayerFisherData> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private UUID playerId;
    private boolean isFisher = false;
    private int level  = 1;
    private double xp  = 0;

    // Evitar que el jugador pesque mientras ya está en un minijuego
    private transient boolean minigameActive = false;

    private int coolersOwned = 0;
    private List<List<com.tuservidor.cobblejobs.item.FishItem.FishData>> coolers = new ArrayList<>();

    private long totalFishCaught = 0;
    private long commonCaught    = 0;
    private long rareCaught      = 0;
    private long epicCaught      = 0;
    private long legendaryCaught = 0;
    private double totalMoneyEarned = 0;

    private double recordWeight = 0;
    private String recordWeightSpecies = "";
    private Map<String, Long> fishCollection = new LinkedHashMap<>();
    private Map<String, Double> fishRecordWeight = new LinkedHashMap<>();

    private transient long lastFishTick = -1;
    private transient double lastX = 0, lastZ = 0;
    private transient int afkCounter = 0;

    private PlayerFisherData() {}

    public static PlayerFisherData get(UUID uuid) {
        return CACHE.computeIfAbsent(uuid, id -> {
            PlayerFisherData d = load(id);
            d.playerId = id;
            return d;
        });
    }

    public double xpForNextLevel() {
        return Math.round(XP_BASE * Math.pow(level, XP_SCALE));
    }

    public boolean addXp(double amount) {
        if (amount <= 0) return false;
        xp += amount;
        boolean leveledUp = false;
        
        int safetyCounter = 0;
        while (xp >= xpForNextLevel() && safetyCounter < 100) {
            if (level < MAX_LEVEL) {
                xp -= xpForNextLevel();
                level++;
                leveledUp = true;
            } else {
                break;
            }
            safetyCounter++;
        }
        return leveledUp;
    }

    public void registerCatch(String species, double weight, int length,
                              com.tuservidor.cobblejobs.fishing.rarity.FishRarity rarity) {
        totalFishCaught++;
        switch (rarity) {
            case COMMON    -> commonCaught++;
            case RARE      -> rareCaught++;
            case EPIC      -> epicCaught++;
            case LEGENDARY -> legendaryCaught++;
        }
        fishCollection.merge(species, 1L, Long::sum);
        if (weight > recordWeight) {
            recordWeight = weight;
            recordWeightSpecies = species;
        }
        fishRecordWeight.merge(species, weight, Double::max);
    }

    public void addMoney(double amount) {
        totalMoneyEarned += amount;
    }

    public static void save(UUID uuid) {
        PlayerFisherData data = CACHE.get(uuid);
        if (data == null) return;
        String jsonSnapshot;
        synchronized (data) { jsonSnapshot = GSON.toJson(data); }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Path dir = Paths.get("config", "cobblejobs", "players");
                Files.createDirectories(dir);
                try (Writer w = Files.newBufferedWriter(dir.resolve(uuid + "_fisher.json"))) {
                    w.write(jsonSnapshot);
                }
            } catch (Exception e) {
                CobbleJobs.LOGGER.error("[CobbleJobs] Error guardando datos", e);
            }
        });
    }

    private static PlayerFisherData load(UUID uuid) {
        Path file = Paths.get("config", "cobblejobs", "players", uuid + "_fisher.json");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                PlayerFisherData d = GSON.fromJson(r, PlayerFisherData.class);
                if (d != null) {
                    if (d.fishCollection == null) d.fishCollection = new LinkedHashMap<>();
                    if (d.fishRecordWeight == null) d.fishRecordWeight = new LinkedHashMap<>();
                    if (d.coolers == null) d.coolers = new ArrayList<>();
                    return d;
                }
            } catch (Exception ignored) {}
        }
        return new PlayerFisherData();
    }

    public static void evict(UUID uuid) {
        save(uuid);
        CACHE.remove(uuid);
    }
}
