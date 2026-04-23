package com.tuservidor.cobblejobs.gui;

import com.tuservidor.cobblejobs.config.FisherConfig;
import com.tuservidor.cobblejobs.economy.EconomyBridge;
import com.tuservidor.cobblejobs.item.FishItem;
import com.tuservidor.cobblejobs.job.PlayerFisherData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import com.tuservidor.cobblejobs.util.MessageUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;

public class CoolerGui {

    public static void openMainMenu(ServerPlayer player) {
        PlayerFisherData data = PlayerFisherData.get(player.getUUID());
        FisherConfig cfg = FisherConfig.get();
        SimpleContainer inventory = new SimpleContainer(9);
        for (int i = 0; i < 5; i++) {
            ItemStack stack;
            if (i < data.getCoolersOwned()) {
                stack = new ItemStack(Items.BLUE_ICE);
                stack.set(DataComponents.CUSTOM_NAME, MessageUtil.literal("§b🧊 Hielera #" + (i + 1)));
                stack.set(DataComponents.LORE, new ItemLore(List.of(MessageUtil.literal("§7Click para abrir."))));
            } else if (i == data.getCoolersOwned()) {
                stack = new ItemStack(Items.CHEST);
                stack.set(DataComponents.CUSTOM_NAME, MessageUtil.literal("§e🛒 Comprar Hielera #" + (i + 1)));
                stack.set(DataComponents.LORE, new ItemLore(List.of(MessageUtil.literal("§7Precio: §a$" + cfg.getCoolerPrices().get(i)))));
            } else {
                stack = new ItemStack(Items.RED_STAINED_GLASS_PANE);
                stack.set(DataComponents.CUSTOM_NAME, MessageUtil.literal("§cBloqueado"));
            }
            inventory.setItem(i, stack);
        }
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x1, id, inv, inventory, 1) {
            @Override public void clicked(int slot, int b, net.minecraft.world.inventory.ClickType t, net.minecraft.world.entity.player.Player pl) {
                if (slot >= 0 && slot < 5) {
                    if (slot < data.getCoolersOwned()) player.getServer().execute(() -> openStorage(player, slot));
                    else if (slot == data.getCoolersOwned()) player.getServer().execute(() -> buyCooler(player, slot));
                }
                sendAllDataToRemote();
            }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player p) { return true; }
        }, MessageUtil.literal("§b🧊 Tus Hieleras")));
    }

    private static void buyCooler(ServerPlayer player, int coolerId) {
        PlayerFisherData data = PlayerFisherData.get(player.getUUID());
        FisherConfig cfg = FisherConfig.get();
        double price = cfg.getCoolerPrices().get(coolerId);
        int reqLevel = cfg.getCoolerLevels().get(coolerId);

        // 1. Avisar si no tiene el nivel necesario
        if (data.getLevel() < reqLevel) {
            player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] Necesitas nivel " + reqLevel + " de Pescador para comprar esta hielera."));
            return;
        }

        // 2. Avisar si la economía no funciona
        if (!EconomyBridge.isAvailable()) {
            player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] La economía no está conectada. Contacta a un administrador."));
            return;
        }

        // 3. Efectuar la compra
        if (EconomyBridge.withdraw(player, price)) {
            data.setCoolersOwned(data.getCoolersOwned() + 1);
            data.getCoolers().add(new ArrayList<>());
            PlayerFisherData.save(player.getUUID());
            openMainMenu(player); // Recarga el menú visualmente
            player.sendSystemMessage(MessageUtil.literal("§a[CobbleJobs] ¡Has comprado una nueva hielera!"));
        } else {
            player.sendSystemMessage(MessageUtil.literal("§c[CobbleJobs] No tienes suficiente dinero. Cuesta $" + price));
        }
    }

    public static void openStorage(ServerPlayer player, int coolerId) {
        PlayerFisherData data = PlayerFisherData.get(player.getUUID());
        List<FishItem.FishData> contents = data.getCoolers().get(coolerId);
        SimpleContainer inventory = new SimpleContainer(27);
        
        for (int i = 0; i < contents.size() && i < 27; i++) {
            FishItem.FishData fd = contents.get(i);
            if (fd != null) {
                // Ahora recreamos el ítem respetando el count guardado
                inventory.setItem(i, FishItem.create(fd.species(), fd.weight(), fd.length(), fd.shiny(), fd.rarity(), fd.minigameMult(), fd.count()));
            }
        }

        inventory.addListener(new ContainerListener() {
            private boolean isUpdating = false;
            @Override public void containerChanged(Container container) {
                if (isUpdating) return;
                List<FishItem.FishData> newContents = new ArrayList<>();
                isUpdating = true;
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (stack.isEmpty()) newContents.add(null);
                    else {
                        FishItem.FishData fd = FishItem.read(stack);
                        if (fd != null) newContents.add(fd);
                        else {
                            ItemStack copy = stack.copy(); container.setItem(i, ItemStack.EMPTY); newContents.add(null);
                            player.getServer().execute(() -> { if (!player.getInventory().add(copy)) player.drop(copy, false); player.containerMenu.sendAllDataToRemote(); });
                        }
                    }
                }
                isUpdating = false;
                data.getCoolers().set(coolerId, newContents);
                PlayerFisherData.save(data.getPlayerId());
            }
        });

        player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(MenuType.GENERIC_9x3, id, inv, inventory, 3), MessageUtil.literal("§b🧊 Hielera #" + (coolerId + 1))));
    }
}
