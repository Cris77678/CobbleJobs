package com.tuservidor.cobblejobs.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tuservidor.cobblejobs.config.JobsConfig;
import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.gui.SellGui;
import com.tuservidor.cobblejobs.gui.CoolerGui;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerJobData;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.fishing.collection.FishCollection;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class JobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var base = Commands.literal("job");

        // ── Comandos de Jugador ──
        base.then(Commands.literal("join")
            .requires(CommandSourceStack::isPlayer)
            .then(Commands.argument("job", StringArgumentType.word())
                .suggests((ctx, b) -> { b.suggest("butcher"); b.suggest("fisher"); return b.buildFuture(); })
                .executes(ctx -> joinJob(ctx.getSource(), StringArgumentType.getString(ctx, "job")))));

        base.then(Commands.literal("leave")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> leaveJob(ctx.getSource())));

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

        base.then(Commands.literal("cooler")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> {
                com.tuservidor.cobblejobs.gui.CoolerGui.openMainMenu(ctx.getSource().getPlayerOrException());
                return 1;
            }));

        // NUEVO: Comando para ver la colección de peces
        base.then(Commands.literal("collection")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                PlayerFisherData d = PlayerFisherData.get(p.getUUID());
                if (!d.isFisher()) {
                    p.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Solo los §bPescadores §cpueden ver su colección."));
                    return 0;
                }
                FishCollection.showCollection(p, d);
                return 1;
            }));

        // ── Comandos de Administrador ──
        base.then(Commands.literal("setlevel")
            .requires(src -> src.hasPermission(2))
            .then(Commands.argument("nivel", IntegerArgumentType.integer(1, 50))
                .executes(ctx -> setLevel(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "nivel")))));

        base.then(Commands.literal("setzone")
            .requires(src -> src.hasPermission(2))
            .then(Commands.literal("butcher")
                .then(Commands.literal("pos1").executes(ctx -> setButcherZone(ctx.getSource(), true)))
                .then(Commands.literal("pos2").executes(ctx -> setButcherZone(ctx.getSource(), false))))
            .then(Commands.literal("fisher")
                .then(Commands.argument("id", StringArgumentType.word())
                    .then(Commands.argument("type", StringArgumentType.word())
                        .suggests((ctx, b) -> { 
                            b.suggest("LAKE"); b.suggest("OCEAN"); b.suggest("RIVER"); b.suggest("SPECIAL"); 
                            return b.buildFuture(); 
                        })
                        .then(Commands.literal("pos1").executes(ctx -> setFisherZone(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), true)))
                        .then(Commands.literal("pos2").executes(ctx -> setFisherZone(ctx.getSource(), StringArgumentType.getString(ctx, "id"), StringArgumentType.getString(ctx, "type"), false)))))));

        base.then(Commands.literal("reload")
            .requires(src -> src.hasPermission(2))
            .executes(ctx -> {
                JobsConfig.load();
                FisherConfig.load();
                ctx.getSource().sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Configuraciones recargadas."));
                return 1;
            }));

        dispatcher.register(base);
    }

    private static int setLevel(CommandSourceStack src, int level) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerFisherData data = PlayerFisherData.get(player.getUUID());
            data.setLevel(level);
            data.setXp(0);
            PlayerFisherData.save(player.getUUID());
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Nivel de pescador establecido a: " + level));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int joinJob(CommandSourceStack src, String jobName) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            PlayerJobData.Job job = jobName.equalsIgnoreCase("butcher") ? PlayerJobData.Job.BUTCHER : 
                                   jobName.equalsIgnoreCase("fisher") ? PlayerJobData.Job.FISHER : null;
            if (job == null) return 0;
            PlayerJobData data = PlayerJobData.get(player.getUUID());
            data.setActiveJob(job);
            PlayerJobData.save(player.getUUID());
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] Ahora eres " + jobName));
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

    private static int openSell(CommandSourceStack src) {
        try { SellGui.open(src.getPlayerOrException()); return 1; } catch (Exception e) { return 0; }
    }

    private static int openShop(CommandSourceStack src) {
        src.sendSystemMessage(MessageUtil.literal("§6§l[Tienda] §fUsa §b/job buy rod §fpara comprar la caña ($" + FisherConfig.get().getRodPrice() + ")"));
        return 1;
    }

    private static int buyRod(CommandSourceStack src) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            double price = FisherConfig.get().getRodPrice();
            if (EconomyBridge.withdraw(player, price)) {
                ItemStack rod = FishItem.createCustomRod();
                if (!player.addItem(rod)) player.drop(rod, false);
                player.sendSystemMessage(MessageUtil.literal("§a¡Has comprado la Caña Pokémon!"));
                return 1;
            }
            player.sendSystemMessage(MessageUtil.literal("§cNo tienes suficiente dinero."));
            return 0;
        } catch (Exception e) { return 0; }
    }

    private static int setButcherZone(CommandSourceStack src, boolean isPos1) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            JobsConfig.ZoneConfig zone = JobsConfig.get().getButcherZone();
            if (isPos1) zone.set(player.getX(), player.getY(), player.getZ(), zone.getX2(), zone.getY2(), zone.getZ2());
            else {
                zone.set(zone.getX1(), zone.getY1(), zone.getZ1(), player.getX(), player.getY(), player.getZ());
                zone.setConfigured(true);
            }
            JobsConfig.save();
            player.sendSystemMessage(MessageUtil.literal("§aPosición " + (isPos1 ? "1" : "2") + " de carnicero guardada."));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int setFisherZone(CommandSourceStack src, String id, String typeStr, boolean isPos1) {
        try {
            ServerPlayer player = src.getPlayerOrException();
            FisherConfig cfg = FisherConfig.get();
            com.tuservidor.cobblejobs.fishing.zone.FishingZone.ZoneType type = com.tuservidor.cobblejobs.fishing.zone.FishingZone.ZoneType.valueOf(typeStr.toUpperCase());
            
            com.tuservidor.cobblejobs.fishing.zone.FishingZone zone = cfg.getZones().stream()
                .filter(fz -> fz.getId().equalsIgnoreCase(id)).findFirst().orElse(null);
            
            if (zone == null) {
                zone = new com.tuservidor.cobblejobs.fishing.zone.FishingZone(id, type);
                cfg.getZones().add(zone);
            }
            
            if (isPos1) zone.setPos1(player.getX(), player.getY(), player.getZ());
            else zone.setPos2(player.getX(), player.getY(), player.getZ());
            
            FisherConfig.save();
            player.sendSystemMessage(MessageUtil.literal("§aZona de pesca '" + id + "' guardada."));
            return 1;
        } catch (Exception e) { return 0; }
    }
}
