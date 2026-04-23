package com.tuservidor.cobblejobs.fishing.zone;

import com.tuservidor.cobblejobs.config.FisherConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public class FishingZoneManager {

    private static final int PARTICLE_INTERVAL_TICKS = 40; 
    private static long tickCounter = 0;

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(FishingZoneManager::tick);
    }

    public static FishingZone getZoneAt(double x, double y, double z) {
        for (FishingZone zone : FisherConfig.get().getZones()) {
            if (zone.isEnabled() && zone.isConfigured() && zone.containsXYZ(x, y, z)) {
                return zone;
            }
        }
        return null;
    }

    public static List<FishingZone> all() {
        return FisherConfig.get().getZones();
    }

    private static void tick(MinecraftServer server) {
        tickCounter++;
        if (tickCounter % PARTICLE_INTERVAL_TICKS != 0) return;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (FishingZone zone : FisherConfig.get().getZones()) {
                if (!zone.isEnabled() || !zone.isConfigured() || !zone.isShowParticles()) continue;

                double px = player.getX(), pz = player.getZ();
                double minX = Math.min(zone.getX1(), zone.getX2());
                double maxX = Math.max(zone.getX1(), zone.getX2());
                double minZ = Math.min(zone.getZ1(), zone.getZ2());
                double maxZ = Math.max(zone.getZ1(), zone.getZ2());

                if (px < minX - 32 || px > maxX + 32 || pz < minZ - 32 || pz > maxZ + 32) {
                    continue;
                }

                spawnZoneParticles(player, zone);
            }
        }
    }

    private static void spawnZoneParticles(ServerPlayer player, FishingZone zone) {
        double minX = Math.min(zone.getX1(), zone.getX2());
        double maxX = Math.max(zone.getX1(), zone.getX2());
        double minZ = Math.min(zone.getZ1(), zone.getZ2());
        double maxZ = Math.max(zone.getZ1(), zone.getZ2());
        
        double y = Math.max(zone.getY1(), zone.getY2()) + 0.1;

        // Aquí aplicamos el cambio a partículas mágicas y brillantes en la oscuridad:
        // GLOW (calamar brillante) para las zonas especiales.
        // SCRAPE (chispas de cobre) para las zonas normales (lago, océano, etc).
        var particle = zone.getType() == FishingZone.ZoneType.SPECIAL
            ? ParticleTypes.GLOW
            : ParticleTypes.SCRAPE;

        int steps = (int) Math.min(40, (maxX - minX + maxZ - minZ) * 0.25);
        if (steps < 4) steps = 4;

        drawEdge(player, particle, minX, maxX, minZ, minZ, y, steps);
        drawEdge(player, particle, minX, maxX, maxZ, maxZ, y, steps);
        drawEdge(player, particle, minX, minX, minZ, maxZ, y, steps);
        drawEdge(player, particle, maxX, maxX, minZ, maxZ, y, steps);
    }

    private static void drawEdge(ServerPlayer player,
                                  net.minecraft.core.particles.SimpleParticleType type,
                                  double x1, double x2, double z1, double z2,
                                  double y, int steps) {
        for (int i = 0; i <= steps; i++) {
            double t  = (double) i / steps;
            double px = x1 + t * (x2 - x1);
            double pz = z1 + t * (z2 - z1);
            // 1 sola partícula brillante por paso para no saturar la red
            player.serverLevel().sendParticles(player, type, false, px, y, pz, 1, 0, 0, 0, 0);
        }
    }
}
