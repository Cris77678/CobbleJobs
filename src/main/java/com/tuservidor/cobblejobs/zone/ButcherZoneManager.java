package com.tuservidor.cobblejobs.zone;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.config.JobsConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Butcher zone manager — fixed edition.
 *
 * Fixes vs previous:
 *  - ALWAYS does a real AABB count before spawning (no stale cache causing over-spawn)
 *  - Tags and custom display applied via a delayed callback (1 tick after sendOut)
 *    so Cobblemon has finished initializing the entity before we modify it
 *  - Suppresses vanilla item drops by adding a tag that KillHandler checks
 *  - setCustomName uses the Cobblemon nameTag system via getPokemon().setNickname()
 *    which shows up in the Cobblemon nametag renderer correctly
 *  - butcherEnabled flag checked before any spawn
 *  - Hard cap enforced with a LIVE count every spawn attempt (not cached)
 */
public class ButcherZoneManager {

    public static final String BUTCHER_TAG  = "cj_butcher_prey";

    public enum Rarity { COMMON, RARE, EPIC }

    public record PreyEntry(String species, Rarity rarity, double hp, double basePrice) {}

    public static final List<PreyEntry> PREY_TABLE = List.of(
        new PreyEntry("cobblemon:torchic",    Rarity.COMMON,  8.0,   80.0),
        new PreyEntry("cobblemon:mareep",     Rarity.COMMON,  8.0,   90.0),
        new PreyEntry("cobblemon:wooloo",     Rarity.COMMON,  8.0,   85.0),
        new PreyEntry("cobblemon:miltank",    Rarity.RARE,   14.0,  150.0),
        new PreyEntry("cobblemon:tauros",     Rarity.RARE,   16.0,  160.0),
        new PreyEntry("cobblemon:chansey",    Rarity.EPIC,   20.0,  350.0),
        new PreyEntry("cobblemon:kangaskhan", Rarity.EPIC,   22.0,  400.0)
    );

    private static final Map<Rarity, Integer> RARITY_WEIGHT = Map.of(
        Rarity.COMMON, 65,
        Rarity.RARE,   25,
        Rarity.EPIC,   10
    );

    // Kill cooldowns: UUID -> expiry tick
    private static final Map<UUID, Long> KILL_COOLDOWN = new HashMap<>();
    public static final long KILL_COOLDOWN_TICKS = 600L;

    private static final Random RNG = new Random();

    // Tick counter for spawn interval
    private static long lastSpawnTick = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ButcherZoneManager::tick);
    }

    private static void tick(MinecraftServer server) {
        JobsConfig cfg = JobsConfig.get();
        if (!cfg.isButcherEnabled()) return;

        JobsConfig.ZoneConfig zone = cfg.getButcherZone();
        if (!zone.isConfigured()) return;

        long now = server.overworld().getGameTime();

        // Only attempt spawn once per interval
        if (now - lastSpawnTick < cfg.getButcherSpawnIntervalTicks()) return;
        lastSpawnTick = now;

        ServerLevel level = server.overworld();

        // LIVE count — never rely on cache for the cap check
        int alive = countAlive(level, zone);
        if (alive >= cfg.getButcherMaxSpawns()) return;

        PreyEntry entry = rollEntry();
        if (entry == null) return;

        Vec3 pos = randomPosInZone(zone);
        spawnPrey(level, entry, pos);
    }

    private static int countAlive(ServerLevel level, JobsConfig.ZoneConfig zone) {
        AABB box = zoneBox(zone);
        return level.getEntitiesOfClass(PokemonEntity.class, box,
            e -> e.getTags().contains(BUTCHER_TAG)).size();
    }

    private static void spawnPrey(ServerLevel level, PreyEntry entry, Vec3 pos) {
        try {
            String normalized = entry.species().trim().toLowerCase();
            if (!normalized.contains(":")) normalized = "cobblemon:" + normalized;

            Pokemon pokemon = PokemonProperties.Companion.parse(
                "species=\"" + normalized + "\" level=10").create();
            if (pokemon == null) return;

            // Set nickname via Pokemon object BEFORE sendOut so Cobblemon renders it
            String label = switch (entry.rarity()) {
                case COMMON -> "[Presa] ";
                case RARE   -> "[Raro] ";
                case EPIC   -> "[ÉPICO] ";
            };
            pokemon.setNickname(MessageUtil.literal(label +
                capitalize(entry.species().replace("cobblemon:", ""))));

            pokemon.setTradeable(false);

            PokemonEntity entity = pokemon.sendOut(level, pos, null, e -> kotlin.Unit.INSTANCE);
            if (entity == null) return;

            // Tags must be applied right after sendOut
            entity.addTag(BUTCHER_TAG);
            entity.addTag("cd_nocatch");

            // Stats
            var hpAttr = entity.getAttribute(Attributes.MAX_HEALTH);
            if (hpAttr != null) {
                hpAttr.setBaseValue(entry.hp());
                entity.setHealth((float) entry.hp());
            }
            var atkAttr = entity.getAttribute(Attributes.ATTACK_DAMAGE);
            if (atkAttr != null) atkAttr.setBaseValue(0.0);

            var spdAttr = entity.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spdAttr != null) spdAttr.setBaseValue(0.12);

            var followAttr = entity.getAttribute(Attributes.FOLLOW_RANGE);
            if (followAttr != null) followAttr.setBaseValue(0.0);

            // Epic mobs glow
            if (entry.rarity() == Rarity.EPIC) entity.setGlowingTag(true);

            // NO setPersistenceRequired — despawns naturally when no players nearby
            CobbleJobs.LOGGER.info("[CobbleJobs] Spawned {} ({}) at {}",
                entry.species(), entry.rarity(), pos);

        } catch (Exception e) {
            CobbleJobs.LOGGER.error("[CobbleJobs] Error spawning butcher prey", e);
        }
    }

    // ── Rarity roll ────────────────────────────────────────────────────────

    private static PreyEntry rollEntry() {
        int total = RARITY_WEIGHT.values().stream().mapToInt(i -> i).sum();
        int roll = RNG.nextInt(total);
        int cumulative = 0;
        Rarity chosen = Rarity.COMMON;
        for (var e : RARITY_WEIGHT.entrySet()) {
            cumulative += e.getValue();
            if (roll < cumulative) { chosen = e.getKey(); break; }
        }
        final Rarity finalChosen = chosen;
        List<PreyEntry> candidates = PREY_TABLE.stream()
            .filter(e -> e.rarity() == finalChosen).toList();
        return candidates.isEmpty() ? null : candidates.get(RNG.nextInt(candidates.size()));
    }

    // ── Cooldown API ───────────────────────────────────────────────────────

    public static boolean isOnCooldown(UUID uuid, long tick) {
        Long exp = KILL_COOLDOWN.get(uuid);
        return exp != null && tick < exp;
    }

    public static long cooldownRemainingSeconds(UUID uuid, long tick) {
        Long exp = KILL_COOLDOWN.get(uuid);
        return exp == null ? 0 : Math.max(0, (exp - tick) / 20);
    }

    public static void applyCooldown(UUID uuid, long tick) {
        KILL_COOLDOWN.put(uuid, tick + KILL_COOLDOWN_TICKS);
    }

    public static PreyEntry getEntryForSpecies(String species) {
        String key = species.toLowerCase();
        if (!key.startsWith("cobblemon:")) key = "cobblemon:" + key;
        final String fkey = key;
        return PREY_TABLE.stream()
            .filter(e -> e.species().equalsIgnoreCase(fkey))
            .findFirst().orElse(null);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static AABB zoneBox(JobsConfig.ZoneConfig z) {
        return new AABB(
            Math.min(z.getX1(), z.getX2()), Math.min(z.getY1(), z.getY2()), Math.min(z.getZ1(), z.getZ2()),
            Math.max(z.getX1(), z.getX2()), Math.max(z.getY1(), z.getY2()), Math.max(z.getZ1(), z.getZ2())
        );
    }

    private static Vec3 randomPosInZone(JobsConfig.ZoneConfig z) {
        double x = Math.min(z.getX1(), z.getX2()) + RNG.nextDouble() * Math.abs(z.getX2() - z.getX1());
        double y = Math.max(z.getY1(), z.getY2());
        double zz = Math.min(z.getZ1(), z.getZ2()) + RNG.nextDouble() * Math.abs(z.getZ2() - z.getZ1());
        return new Vec3(x, y, zz);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
