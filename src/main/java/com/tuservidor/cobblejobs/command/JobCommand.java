package com.tuservidor.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobblejobs.config.JobsConfig;
import com.tuservidor.cobblejobs.gui.SellGui;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerJobData;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class JobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("job");

        // ── Player commands ────────────────────────────────────────────────
        base.then(Commands.literal("join")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> joinJob(ctx.getSource(), StringArgumentType.getString(ctx, "job")))));

        base.then(Commands.literal("leave")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> leaveJob(ctx.getSource())));

        base.then(Commands.literal("info")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> showInfo(ctx.getSource())));

        base.then(Commands.literal("sell")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> openSell(ctx.getSource())));

        base.then(Commands.literal("shop")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> openShop(ctx.getSource())));

        base.then(Commands.literal("buy")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.literal("rod")
                .executes(ctx -> buyRod(ctx.getSource()))));

        // ── Admin commands (Modificados para soportar IDs y Tipos) ────────
        base.then(Commands.literal("setzone")
            .requires(src -> src.hasPermission(2))
            // Comando para el Carnicero (usa JobsConfig)
            .then(Commands.literal("butcher")
                .then(Commands.literal("pos1").executes(ctx -> setButcherZone(ctx.getSource(), true)))
                .then(Commands.literal("pos2").executes(ctx -> setButcherZone(ctx.getSource(), false)))
            )
            // Comando para el Pescador (usa FisherConfig y requiere ID y Tipo)
            .then(Commands.literal("fisher")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, b) -> { 
                            b.suggest("LAKE"); b.suggest("OCEAN"); b.suggest("RIVER"); b.suggest("SPECIAL"); 
                            return b.buildFuture(); 
                        })
                        .then(Commands.literal("pos1").executes(ctx -> setFisherZone(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), true)))
                        .then(Commands.literal("pos2").executes(ctx -> setFisherZone(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), false)))
                    )
                )
            )
        );

        base.then(Commands.literal("stopzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> toggleZone(ctx.getSource(), StringArgumentType.getString(ctx, "job"), false))));

        base.then(Commands.literal("startzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> toggleZone(ctx.getSource(), StringArgumentType.getString(ctx, "job"), true))));

        base.then(Commands.literal("reload")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                JobsConfig.load();
                com.tuservidor.cobblejobs.config.FisherConfig.load();
                ctx.getSource().sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Configs recargadas."));
                return 1;
            }));

        dispatcher.register(base);
    }

    // ── Player handlers ────────────────────────────────────────────────────

    private static int joinJob(CommandSourceStack src, String jobName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = switch (jobName.toLowerCase()) {
                case "butcher" -> PlayerJobData.Job.BUTCHER;
                case "fisher"  -> PlayerJobData.Job.FISHER;
                default -> null;
            };
            if (job == null) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Trabajo desconocido. Usa: butcher, fisher"));
                return 0;
            }
            PlayerJobData data = PlayerJobData.get(player.getUUID());
            if (data.getActiveJob() == job) {
                player.sendSystemMessage(MessageUtil.literal("§e[CobbleJobs] Ya tienes ese trabajo."));
                return 0;
            }
            data.setActiveJob(job);
            PlayerJobData.save(player.getUUID());

            String label = job == PlayerJobData.Job.BUTCHER ? "§c🗡 Carnicero" : "§b🎣 Pescador";
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Ahora eres: " + label));
            if (job == PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§7Compra la §bCaña Pokémon §7con §f/job shop §7para empezar a pescar."));
            }
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int leaveJob(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.get(player.getUUID()).setActiveJob(PlayerJobData.Job.NONE);
            PlayerJobData.save(player.getUUID());
            player.sendSystemMessage(MessageUtil.literal("§7[CobbleJobs] Has dejado tu trabajo."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int showInfo(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            String label = switch (job) {
                case BUTCHER -> "§c🗡 Carnicero";
                case FISHER  -> "§b🎣 Pescador";
                case NONE    -> "§7Ninguno";
            };
            JobsConfig cfg = JobsConfig.get();
            String butcherStatus = cfg.isButcherEnabled() ? "§aActiva" : "§cDetenida";
            String fisherStatus  = com.tuservidor.cobblejobs.config.FisherConfig.get().getZones().isEmpty() ? "§cSin Zonas" : "§aActiva";
            player.sendSystemMessage(MessageUtil.literal(
                "§6§l[CobbleJobs] §r§fTrabajo: " + label + "\n" +
                "§7Zona Carnicero: " + butcherStatus + " §7| Zona Pesca: " + fisherStatus));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int openSell(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            if (job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] No tienes ningún trabajo activo."));
                return 0;
            }
            if (job == PlayerJobData.Job.BUTCHER) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§c[Carnicero] Los carniceros cobran al matar — no hay nada que vender manualmente."));
                return 0;
            }
            SellGui.open(player);
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int openShop(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();

            player.sendSystemMessage(MessageUtil.literal("§6§l[CobbleJobs — Tienda] ═══════════"));

            if (job == PlayerJobData.Job.FISHER || job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§b🎣 Caña Pokémon §7— §a$" + String.format("%.0f", com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice())));
                player.sendSystemMessage(MessageUtil.literal(
                    "§7  Necesaria para pescar en la zona."));
                player.sendSystemMessage(MessageUtil.literal(
                    "§7  Escribe §f/job buy rod §7para comprarla."));
            }

            if (job == PlayerJobData.Job.NONE) {
                player.sendSystemMessage(MessageUtil.literal(
                    "§7Únete a un trabajo con §f/job join <trabajo> §7para acceder a la tienda."));
            }

            player.sendSystemMessage(MessageUtil.literal("§6§l═══════════════════════════"));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int buyRod(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = PlayerJobData.get(player.getUUID()).getActiveJob();
            if (job != PlayerJobData.Job.FISHER) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Solo los §bPescadores §cpueden comprar la caña."));
                return 0;
            }
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (FishItem.isCustomRod(player.getInventory().getItem(i))) {
                    player.sendSystemMessage(MessageUtil.literal("§e[CobbleJobs] Ya tienes una Caña Pokémon."));
                    return 0;
                }
            }
            if (!EconomyBridge.isAvailable()) {
                player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Economía no disponible."));
                return 0;
            }
            EconomyBridge.withdraw(player, com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice());
            ItemStack rod = FishItem.createCustomRod();
            if (!player.addItem(rod)) player.drop(rod, false);
            player.sendSystemMessage(MessageUtil.literal(
                "§a[CobbleJobs] §f¡Compraste la §bCaña Pokémon §fpor §a$" +
                String.format("%.0f", com.tuservidor.cobblejobs.config.FisherConfig.get().getRodPrice()) + "§f!"));
            return 1;
        } catch (Exception e) { return 0; }
    }

    // ── Admin handlers ─────────────────────────────────────────────────────

    private static int setButcherZone(CommandSourceStack src, boolean isPos1) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            JobsConfig.ZoneConfig zone = com.tuservidor.cobblejobs.config.JobsConfig.get().getButcherZone();
            double x = player.getX(), y = player.getY(), z = player.getZ();

            if (isPos1) zone.set(x, y, z, zone.getX2(), zone.getY2(), zone.getZ2());
            else {
                zone.set(zone.getX1(), zone.getY1(), zone.getZ1(), x, y, z);
                zone.setConfigured(true);
            }

            com.tuservidor.cobblejobs.config.JobsConfig.save();
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Zona de carnicero " + (isPos1 ? "pos1" : "pos2") + " guardada."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int setFisherZone(CommandSourceStack src, String id, String typeStr, boolean isPos1) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            com.tuservidor.cobblejobs.config.FisherConfig cfg = com.tuservidor.cobblejobs.config.FisherConfig.get();
            double x = player.getX(), y = player.getY(), z = player.getZ();

            com.tuservidor.cobblejobs.fishing.zone.FishingZone.ZoneType type;
            try { 
                type = com.tuservidor.cobblejobs.fishing.zone.FishingZone.ZoneType.valueOf(typeStr.toUpperCase()); 
            } catch (Exception e) { 
                player.sendSystemMessage(MessageUtil.literal("§cTipo inválido. Usa LAKE, OCEAN, RIVER o SPECIAL.")); 
                return 0; 
            }

            // Busca si la zona ya existe en la configuración real de pesca, si no, la crea.
            com.tuservidor.cobblejobs.fishing.zone.FishingZone zone = cfg.getZones().stream()
                .filter(fz -> fz.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
                
            if (zone == null) {
                zone = new com.tuservidor.cobblejobs.fishing.zone.FishingZone(id, type);
                cfg.getZones().add(zone);
            } else {
                zone.setType(type); // Actualiza el tipo por si decidiste cambiarlo
            }

            if (isPos1) zone.setPos1(x, y, z);
            else zone.setPos2(x, y, z);

            com.tuservidor.cobblejobs.config.FisherConfig.save();
            String corner = isPos1 ? "pos1" : "pos2";
            player.sendSystemMessage(MessageUtil.literal("§a[Pesca] Zona §e" + id + " (" + type.name() + ") §a— " + corner + " §fguardada."));
            if (zone.isConfigured()) player.sendSystemMessage(MessageUtil.literal("§a✔ Zona configurada y lista."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int toggleZone(CommandSourceStack src, String jobName, boolean enable) {
        try {
            JobsConfig cfg = JobsConfig.get();
            switch (jobName.toLowerCase()) {
                case "butcher" -> cfg.setButcherEnabled(enable);
                case "fisher"  -> cfg.setFisherEnabled(enable); // Nota: En tu JobsConfig original tenías esto
                default -> {
                    src.sendSystemMessage(MessageUtil.literal("§cTrabajo desconocido: " + jobName));
                    return 0;
                }
            }
            JobsConfig.save();
            String action = enable ? "§aactivada" : "§cdetenida";
            src.sendSystemMessage(MessageUtil.literal(
                "§6[CobbleJobs] §fZona global de §e" + jobName + " " + action + "§f."));
            return 1;
        } catch (Exception e) { return 0; }
    }
}
