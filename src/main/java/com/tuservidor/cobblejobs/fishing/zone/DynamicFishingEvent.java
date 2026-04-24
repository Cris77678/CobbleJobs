package com.tuservidor.cobblejobs.fishing.zone;

import com.tuservidor.cobblejobs.CobbleJobs;
import com.tuservidor.cobblejobs.config.FisherConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.Random;

public class DynamicFishingEvent {

    public enum EventType {
        FRENZY    ("§c§l⚡ FRENESÍ DE PESCA §r§7— ¡Los peces raros aparecen más!",      0.5,  1.0,  0.5),
        BLESSING  ("§a§l✨ ZONA BENDECIDA §r§7— ¡Doble XP al pescar!",                  0.0,  2.0,  0.0),
        LEGENDARY ("§6§l★ LLUVIA LEGENDARIA §r§7— ¡Los legendarios están cerca!",       0.3,  1.5,  2.0);

        public final String announcement;
        public final double rarityBonus;
        public final double xpMultiplier;
        public final double legendaryBonus;

        EventType(String announcement, double rarityBonus,
                  double xpMultiplier, double legendaryBonus) {
            this.announcement  = announcement;
            this.rarityBonus   = rarityBonus;
            this.xpMultiplier  = xpMultiplier;
            this.legendaryBonus = legendaryBonus;
        }
    }

    private static EventType  activeEvent     = null;
    private static long       eventEndTick    = -1;
    private static long       nextEventTick   = -1;

    private static final Random RNG = new Random();

    private static int eventDurationTicks() {
        return FisherConfig.get().getEventDurationTicks();
    }

    private static int eventCooldownTicks() {
        return FisherConfig.get().getEventCooldownTicks();
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DynamicFishingEvent::tick);
    }

    private static void tick(MinecraftServer server) {
        long now = server.overworld().getGameTime();

        if (activeEvent != null && now >= eventEndTick) {
            endEvent(server);
            return;
        }

        if (activeEvent == null) {
            if (nextEventTick < 0) {
                nextEventTick = now + 6000;
                CobbleJobs.LOGGER.info("[CobbleJobs] Primer evento de la sesión programado en 5 minutos.");
            } else if (now >= nextEventTick) {
                startRandomEvent(server, now);
            }
        }
    }

    private static void scheduleNext(long now) {
        long variance = (long)(eventCooldownTicks() * 0.25);
        nextEventTick = now + eventCooldownTicks() + (long)(RNG.nextDouble() * variance);
        CobbleJobs.LOGGER.info("[CobbleJobs] Próximo evento de pesca en {} ticks", nextEventTick - now);
    }

    private static void startRandomEvent(MinecraftServer server, long now) {
        EventType[] types = EventType.values();
        startSpecificEvent(server, types[RNG.nextInt(types.length)]);
    }

    public static void startSpecificEvent(MinecraftServer server, EventType type) {
        long now = server.overworld().getGameTime();
        activeEvent = type;
        eventEndTick = now + eventDurationTicks();

        Component msg = MessageUtil.literal(
            "\n§8§m                    §r\n" +
            activeEvent.announcement + "\n" +
            "§7Duración: §f" + (eventDurationTicks() / 20) + " segundos\n" +
            "§8§m                    §r"
        );

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (com.tuservidor.cobblejobs.job.PlayerJobData.get(player.getUUID()).getActiveJob() == com.tuservidor.cobblejobs.job.PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(msg);
                player.serverLevel().playSound(null,
                    player.getX(), player.getY(), player.getZ(),
                    SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1f, 2f);
                spawnEventParticles(player, activeEvent);
            }
        }
        CobbleJobs.LOGGER.info("[CobbleJobs] Evento iniciado: {}", activeEvent.name());
    }

    private static void endEvent(MinecraftServer server) {
        if (activeEvent == null) return;
        CobbleJobs.LOGGER.info("[CobbleJobs] Evento terminado: {}", activeEvent.name());
        activeEvent = null;
        eventEndTick = -1;
        scheduleNext(server.overworld().getGameTime());

        Component msg = MessageUtil.literal("§8[CobbleJobs] §7El evento de pesca ha terminado.");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (com.tuservidor.cobblejobs.job.PlayerJobData.get(player.getUUID()).getActiveJob() == com.tuservidor.cobblejobs.job.PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(msg);
            }
        }
    }

    private static void spawnEventParticles(ServerPlayer player, EventType event) {
        var particleType = switch (event) {
            case FRENZY    -> ParticleTypes.FLAME;
            case BLESSING  -> ParticleTypes.HAPPY_VILLAGER;
            case LEGENDARY -> ParticleTypes.TOTEM_OF_UNDYING;
        };
        for (int i = 0; i < 20; i++) {
            double ox = (RNG.nextDouble() - 0.5) * 4;
            double oy = RNG.nextDouble() * 3;
            double oz = (RNG.nextDouble() - 0.5) * 4;
            player.serverLevel().sendParticles(player, particleType, true,
                player.getX() + ox, player.getY() + oy, player.getZ() + oz,
                1, 0, 0, 0, 0.1);
        }
    }

    public static boolean hasActiveEvent() { return activeEvent != null; }
    public static EventType getActiveEvent() { return activeEvent; }
    public static double getRarityBonus() { return activeEvent != null ? activeEvent.rarityBonus : 0; }
    public static double getXpMultiplier() { return activeEvent != null ? activeEvent.xpMultiplier : 1.0; }
    public static double getLegendaryBonus() { return activeEvent != null ? activeEvent.legendaryBonus : 0; }
}
