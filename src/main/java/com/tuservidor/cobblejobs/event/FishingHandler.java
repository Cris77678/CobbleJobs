package com.tuservidor.cobblejobs.event;

import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.fishing.collection.FishCollection;
import com.tuservidor.cobblejobs.fishing.leaderboard.LeaderboardManager;
import com.tuservidor.cobblejobs.fishing.minigame.FishingMinigame;
import com.tuservidor.cobblejobs.fishing.rarity.FishRarity;
import com.tuservidor.cobblejobs.fishing.zone.DynamicFishingEvent;
import com.tuservidor.cobblejobs.fishing.zone.FishingZone;
import com.tuservidor.cobblejobs.fishing.zone.FishingZoneManager;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.job.PlayerJobData; // Importación añadida para leer el trabajo real
import com.tuservidor.cobblejobs.util.FisherTips;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class FishingHandler {

    private static final Map<UUID, Long>    CATCH_AT     = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_HOOK_ID = new ConcurrentHashMap<>();
    private static final Map<UUID, String>  ACTIVE_TIPS  = new ConcurrentHashMap<>();
    private static final Map<UUID, Long>    COOLDOWN_NOTIFY = new ConcurrentHashMap<>();
    
    private static final Random RNG = new Random();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(FishingHandler::tick);
        DynamicFishingEvent.register();
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> { 
            UUID id = h.player.getUUID(); clearState(id); PlayerFisherData.evict(id); 
        });
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();
        FishingMinigame.tick(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid = player.getUUID();
            PlayerFisherData data = PlayerFisherData.get(uuid);
            
            // CORRECCIÓN: Comprobar el trabajo desde PlayerJobData en lugar de isFisher()
            if (PlayerJobData.get(uuid).getActiveJob() != PlayerJobData.Job.FISHER || data.isMinigameActive()) { 
                clearState(uuid); 
                continue; 
            }
            
            FishingHook hook = player.fishing;
            if (hook == null) { clearState(uuid); continue; }
            
            FishingZone zone = FishingZoneManager.getZoneAt(hook.getX(), hook.getY(), hook.getZ());
            if (zone == null) { clearState(uuid); continue; }
            
            boolean isMainHand = player.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD);
            ItemStack activeRod = isMainHand ? player.getMainHandItem() : player.getOffhandItem();
            
            if (!FishItem.isCustomRod(activeRod)) { clearState(uuid); continue; }

            int hookId = hook.getId();
            if (LAST_HOOK_ID.getOrDefault(uuid, -1) != hookId) {
                if (player.getInventory().getFreeSlot() == -1) { 
                    player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡Inventario lleno!")); 
                    hook.discard(); clearState(uuid); continue; 
                }
                LAST_HOOK_ID.put(uuid, hookId);
                long lastFish = data.getLastFishTick();
                FisherConfig cfg = FisherConfig.get();
                if (lastFish > 0 && now - lastFish < cfg.getFishCooldownTicks()) {
                    if (now - COOLDOWN_NOTIFY.getOrDefault(uuid, 0L) >= 20) {
                        long rem = (cfg.getFishCooldownTicks() - (now - lastFish))/20;
                        FisherTips.sendTip(player, "§cCooldown: §f" + rem + "s.");
                        COOLDOWN_NOTIFY.put(uuid, now);
                    }
                    CATCH_AT.remove(uuid); continue;
                }

                int lureLevel = EnchantmentHelper.getItemEnchantmentLevel(
                    player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.LURE), activeRod);
                
                long baseDelay = cfg.getFishMinTicks() + RNG.nextInt(Math.max(1, cfg.getFishMaxTicks() - cfg.getFishMinTicks()));
                long finalDelay = Math.max(20, baseDelay - (lureLevel * 100L));
                
                CATCH_AT.put(uuid, now + finalDelay);
                ACTIVE_TIPS.put(uuid, FisherTips.getRandomWaitTip());
                continue;
            }

            Long fireAt = CATCH_AT.get(uuid);
            if (fireAt != null && now >= fireAt && !hook.isRemoved()) {
                clearState(uuid); hook.discard(); 
                fireCatch(player, data, zone, now, FisherConfig.get(), activeRod, isMainHand);
            } else if (now % 40 == 0 && ACTIVE_TIPS.containsKey(uuid)) {
                FisherTips.sendTip(player, ACTIVE_TIPS.get(uuid));
            }
        }
    }

    private static void fireCatch(ServerPlayer player, PlayerFisherData data, FishingZone zone, long now, FisherConfig cfg, ItemStack rod, boolean isMainHand) {
        FishingZone.ZoneFishEntry entry = rollFish(zone, data.getLevel());
        if (entry == null) return;
        
        data.setMinigameActive(true);

        FishingMinigame.openRandomMinigame(player, entry.getRarity(), data.getLevel(), result -> {
            data.setMinigameActive(false);
            data.setLastFishTick(player.serverLevel().getGameTime());
            
            if (result == FishingMinigame.MinigameResult.FAIL) { 
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] ¡El pez escapó!")); 
                return; 
            }

            EquipmentSlot slot = isMainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            rod.hurtAndBreak(1, player, slot);

            double mult = (result == FishingMinigame.MinigameResult.SUCCESS) ? cfg.getMinigameSuccessMultiplier() : cfg.getMinigamePartialMultiplier();
            double sizeBonus = 1.0 + (data.getLevel() * 0.005);
            double weight = Math.round((entry.getMinWeight() + RNG.nextDouble() * (entry.getMaxWeight() * sizeBonus - entry.getMinWeight())) * 100.0) / 100.0;
            int length = entry.getMinLength() + RNG.nextInt(Math.max(1, (int)(entry.getMaxLength() * sizeBonus) - entry.getMinLength() + 1));
            
            ItemStack stack = FishItem.create(entry.getSpecies(), weight, length, RNG.nextDouble() < 0.03, entry.getRarity(), mult);
            deliverCatch(player, data, FishItem.read(stack), stack, entry.getRarity(), mult);
        });
    }

    private static void deliverCatch(ServerPlayer player, PlayerFisherData data, FishItem.FishData fd, ItemStack stack, FishRarity rar, double mult) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        double xp = rar.xpReward * DynamicFishingEvent.getXpMultiplier() * mult;
        if (data.addXp(xp)) player.sendSystemMessage(MessageUtil.literal("\n§6§l╔══ ⬆ NIVEL SUPERIOR ══╗\n§e§l  ¡Nivel " + data.getLevel() + " de Pescador!\n§6§l╚═══════════════════════╝"));
        data.registerCatch(fd.species(), fd.weight(), fd.length(), rar);
        LeaderboardManager.update(player, data); 
        FishCollection.checkMilestones(player, data); 
        PlayerFisherData.save(player.getUUID());
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1f, 1f);
        player.sendSystemMessage(MessageUtil.literal("§b[Pesca] §r" + rar.color + MessageUtil.capitalize(fd.species().replace("cobblemon:","")) + " §a$" + FishItem.calculatePrice(fd)));
    }

    private static FishingZone.ZoneFishEntry rollFish(FishingZone zone, int playerLevel) {
        List<FishingZone.ZoneFishEntry> table = zone.getFishTable();
        if (table == null || table.isEmpty()) return null;
        List<FishingZone.ZoneFishEntry> eligible = table.stream().filter(e -> e.getMinLevel() <= playerLevel).toList();
        
        double totalWeight = 0;
        Map<FishingZone.ZoneFishEntry, Double> dynamicWeights = new HashMap<>();
        double bonus = (playerLevel / FisherConfig.get().getRarityBonusEveryLevels()) * FisherConfig.get().getRarityBonusPerStep() + DynamicFishingEvent.getRarityBonus();

        for (FishingZone.ZoneFishEntry e : eligible) {
            double weight = e.getChance() + (e.getRarity() != FishRarity.COMMON ? bonus : 0) + (e.getRarity() == FishRarity.LEGENDARY ? DynamicFishingEvent.getLegendaryBonus() * 0.01 : 0);
            dynamicWeights.put(e, weight);
            totalWeight += weight;
        }

        double roll = RNG.nextDouble() * totalWeight;
        double cumulative = 0;
        for (FishingZone.ZoneFishEntry e : eligible) {
            cumulative += dynamicWeights.get(e);
            if (roll <= cumulative) return e;
        }
        return eligible.get(eligible.size() - 1);
    }

    private static boolean checkAntiAfk(ServerPlayer p, PlayerFisherData d, long now, FisherConfig c) {
        if (Math.abs(p.getX()-d.getLastX()) + Math.abs(p.getZ()-d.getLastZ()) > 0.5) { 
            d.setLastX(p.getX()); d.setLastZ(p.getZ()); d.setAfkCounter(0); return true; 
        }
        d.setAfkCounter(d.getAfkCounter() + 1);
        return d.getAfkCounter() <= c.getAfkThresholdTicks();
    }

    private static void clearState(UUID uuid) {
        CATCH_AT.remove(uuid); LAST_HOOK_ID.remove(uuid); ACTIVE_TIPS.remove(uuid); COOLDOWN_NOTIFY.remove(uuid);
    }
}
