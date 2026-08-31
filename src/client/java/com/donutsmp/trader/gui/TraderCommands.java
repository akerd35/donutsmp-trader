package com.donutsmp.trader.gui;

import com.donutsmp.trader.DonutTraderMod;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import com.donutsmp.trader.market.AhListingManager;
import com.donutsmp.trader.market.Undercut;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.donutsmp.trader.update.Updater;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;

public class TraderCommands {

    private static final List<String> POPULAR_ITEMS = Arrays.asList(
            "ladder",
            "water_bucket",
            "totem_of_undying",
            "end_crystal",
            "respawn_anchor",
            "golden_apple",
            "experience_bottle",
            "firework_rocket",
            "obsidian",
            "crying_obsidian",
            "spruce_log",
            "oak_log",
            "shulker_box",
            "ender_pearl",
            "redstone",
            "iron_ingot",
            "cooked_beef"
    );

    private static final SuggestionProvider<FabricClientCommandSource> ITEM_SUGGESTIONS = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase();
        for (String item : POPULAR_ITEMS) {
            if (item.toLowerCase().startsWith(remaining)) {
                builder.suggest(item);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        registerBase(dispatcher, "trader");
        registerBase(dispatcher, "dtrader");
    }

    private static void registerBase(CommandDispatcher<FabricClientCommandSource> dispatcher, String rootName) {
        dispatcher.register(ClientCommands.literal(rootName)
                .executes(context -> showStatus(context.getSource()))
                .then(ClientCommands.literal("fullauto")
                        .executes(context -> fullAuto(context.getSource(), null))
                        .then(ClientCommands.literal("off").executes(context -> setEnabled(context.getSource(), false)))
                        .then(ClientCommands.argument("item", StringArgumentType.word())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(context -> fullAuto(context.getSource(), StringArgumentType.getString(context, "item")))))
                .then(ClientCommands.literal("help")
                        .executes(context -> showHelp(context.getSource())))
                .then(ClientCommands.literal("status")
                        .executes(context -> showStatus(context.getSource())))
                .then(ClientCommands.literal("on")
                        .executes(context -> setEnabled(context.getSource(), true)))
                .then(ClientCommands.literal("off")
                        .executes(context -> setEnabled(context.getSource(), false)))
                .then(ClientCommands.literal("reset")
                        .executes(context -> resetListings(context.getSource())))
                .then(ClientCommands.literal("item")
                        .then(ClientCommands.argument("name", StringArgumentType.word())
                                .suggests(ITEM_SUGGESTIONS)
                                .executes(context -> setItem(context.getSource(), StringArgumentType.getString(context, "name")))))
                .then(ClientCommands.literal("price")
                        .then(ClientCommands.argument("amount", DoubleArgumentType.doubleArg(1.0))
                                .executes(context -> setPrice(context.getSource(), DoubleArgumentType.getDouble(context, "amount")))))
                .then(ClientCommands.literal("lot")
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(context -> setLot(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommands.literal("slots")
                        .then(ClientCommands.argument("max", IntegerArgumentType.integer(1, 54))
                                .executes(context -> setSlots(context.getSource(), IntegerArgumentType.getInteger(context, "max")))))
                .then(ClientCommands.literal("active")
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(0, 54))
                                .executes(context -> setActive(context.getSource(), IntegerArgumentType.getInteger(context, "count")))))
                .then(ClientCommands.literal("floor")
                        .then(ClientCommands.argument("price", DoubleArgumentType.doubleArg(0.0))
                                .executes(context -> setFloor(context.getSource(), DoubleArgumentType.getDouble(context, "price")))))
                .then(ClientCommands.literal("undercut")
                        .then(ClientCommands.literal("on").executes(context -> setUndercut(context.getSource(), true)))
                        .then(ClientCommands.literal("off").executes(context -> setUndercut(context.getSource(), false)))
                        .then(ClientCommands.literal("percent")
                                .then(ClientCommands.argument("percent", DoubleArgumentType.doubleArg(0.0, 50.0))
                                        .executes(context -> setUndercutPercent(context.getSource(), DoubleArgumentType.getDouble(context, "percent")))))
                        .then(ClientCommands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                .executes(context -> setUndercutAmount(context.getSource(), DoubleArgumentType.getDouble(context, "amount")))))
                .then(ClientCommands.literal("sim")
                        .then(ClientCommands.literal("on").executes(context -> setSimulation(context.getSource(), true)))
                        .then(ClientCommands.literal("off").executes(context -> setSimulation(context.getSource(), false))))
                .then(ClientCommands.literal("dump")
                        .then(ClientCommands.literal("on").executes(context -> setDump(context.getSource(), true)))
                        .then(ClientCommands.literal("off").executes(context -> setDump(context.getSource(), false))))
                .then(ClientCommands.literal("update")
                        .executes(context -> checkUpdate(context.getSource())))
                .then(ClientCommands.literal("reload")
                        .executes(context -> reloadConfig(context.getSource())))
        );
    }

    /** Tek komutla çalışır hâle getirir; geri kalan ayarlar varsayılanıyla kalır. */
    /** Adı olmayan bir eşya hedef yapılırsa mod hiç satmaz, sebebini de söylemez. */
    private static boolean checkItem(FabricClientCommandSource source, String name) {
        if (InventoryActionHelper.itemExists(name)) return true;
        source.sendFeedback(Component.literal("§6[DonutTrader] §cBöyle bir eşya yok: §f" + name));
        source.sendFeedback(Component.literal("§7Minecraft eşya adı bekleniyor §8(ladder, water_bucket, totem_of_undying)"));
        source.sendFeedback(Component.literal("§7Kapatmak istediyseniz: §f/trader off"));
        return false;
    }

    private static int fullAuto(FabricClientCommandSource source, String item) {
        TraderConfig config = TraderConfig.get();
        if (item != null && !item.isBlank()) {
            if (!checkItem(source, item)) return 0;
            config.targetItem = item.toLowerCase().replace("minecraft:", "").trim();
        }
        config.autoUndercut = true;
        config.autoScan = true;
        config.undercutAmount = Math.max(1.0, config.undercutAmount);
        config.undercutPercent = 0.0;
        config.simulationMode = false;
        config.enabled = true;
        config.save();

        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) {
            mod.invalidateScan();
            mod.tickMarketLogic();
        }

        source.sendFeedback(Component.literal("§6§l[DonutTrader] §a§lTAM OTOMATİK AÇIK"));
        source.sendFeedback(Component.literal(String.format(
                "§7Satılacak: §f%dx %s §7| §f%s §7| Taban: §f$%,.0f §7| Slot: §f%d",
                config.lotSize, config.targetItem, undercutRule(config), config.minPriceFloor, config.maxSlots)));
        source.sendFeedback(Component.literal("§7Piyasayı kendisi soracak §8(/" + String.format(config.marketCommand, config.targetItem) + ")§7, fiyatı kendisi ayarlayacak."));
        source.sendFeedback(Component.literal("§7Hotbar'da bir boş slot bırakın. Durdurmak için: §f/trader off §7ya da §f'" + (mod != null ? mod.getKeyName() : "K") + "' §7tuşu."));

        if (config.minPriceFloor <= 0) {
            source.sendFeedback(Component.literal("§c§lUYARI: §cTaban fiyat yok. Zararına satmamak için: §f/trader floor <fiyat>"));
        }

        return 1;
    }

    private static int showHelp(FabricClientCommandSource source) {
        DonutTraderMod mod = DonutTraderMod.getInstance();
        String keyName = (mod != null) ? mod.getKeyName() : "K";

        source.sendFeedback(Component.literal("§6§l============== [DonutSMP Trader Rehberi] =============="));
        source.sendFeedback(Component.literal("§e§l1. Nasıl Çalışır?"));
        source.sendFeedback(Component.literal("  §7- Envanterinize 64'lük satmak istediğiniz eşyayı alın (örn: Merdiven, Su Kovası, Totem)."));
        source.sendFeedback(Component.literal("  §7- Mod 64'lük yığından otomatik olarak 1x ayırıp hotbar'a alır ve /ah sell yapar."));
        source.sendFeedback(Component.literal("  §7- 18 slot dolana kadar arka arkaya listeler; eşya satıldıkça yenisini koyar."));
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§e§l2. Tek Komutla Başlatma:"));
        source.sendFeedback(Component.literal("  §a/trader fullauto <eşya> §7-> Hedefi ayarlar, piyasayı kendisi okur, satmaya başlar"));
        source.sendFeedback(Component.literal("  §8Diğer komutlar sadece ince ayar içindir, zorunlu değildir."));
        source.sendFeedback(Component.literal(""));
        source.sendFeedback(Component.literal("§e§l3. Hızlı Kısayollar & Komutlar:"));
        source.sendFeedback(Component.literal("  §a'" + keyName + "' Tuşu §7-> Modu anında Açar / Kapatır (Toggle)"));
        source.sendFeedback(Component.literal("  §f/trader on / off §7-> Modu başlatır / duraklatır"));
        source.sendFeedback(Component.literal("  §f/trader item <ad> §7-> Hedef eşyayı değiştirir §8(Tab ile seçebilirsiniz)"));
        source.sendFeedback(Component.literal("  §f/trader price <fiyat> §7-> Satış fiyatını belirler §8(Örn: /trader price 25000)"));
        source.sendFeedback(Component.literal("  §f/trader active <sayı> §7-> Aktif ilan sayısını eşitler §8(Örn: /trader active 0)"));
        source.sendFeedback(Component.literal("  §f/trader reset §7-> Aktif slot sayacını sıfırlar"));
        source.sendFeedback(Component.literal("  §f/trader slots <sayı> §7-> Slot limitinizi ayarlar §8(Varsayılan: 18)"));
        source.sendFeedback(Component.literal("  §f/trader floor <fiyat> §7-> Taban fiyat koruması §8(Zararına satış engeli)"));
        source.sendFeedback(Component.literal("  §f/trader undercut on|off §7-> Piyasayı takip et ya da sabit fiyat kullan"));
        source.sendFeedback(Component.literal("  §f/trader undercut <dolar> §7-> Rakipten kaç dolar ucuz §8(Varsayılan: 1)"));
        source.sendFeedback(Component.literal("  §f/trader undercut percent <yüzde> §7-> Sabit yerine yüzdesel fark"));
        source.sendFeedback(Component.literal("  §f/trader sim on|off §7-> Simülasyon: komut göndermeden dene"));
        source.sendFeedback(Component.literal("  §f/trader update §7-> GitHub'daki son sürümü indirir §8(yeniden başlatınca uygulanır)"));
        source.sendFeedback(Component.literal("  §f/trader status §7-> Anlık durumu, aktif slotları ve toplam kazancı gösterir"));
        source.sendFeedback(Component.literal("§6§l======================================================"));
        return 1;
    }

    private static int showStatus(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.get();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        AhListingManager lm = (mod != null) ? mod.getListingManager() : null;
        String keyName = (mod != null) ? mod.getKeyName() : "K";
        double recPrice = (mod != null) ? mod.getCurrentRecommendedPrice() : config.fallbackPrice;

        source.sendFeedback(Component.literal("§6§l================== [DonutSMP Trader] =================="));
        source.sendFeedback(Component.literal("§eDurum: " + (config.enabled ? "§a[AKTİF]" : "§c[PASİF]") + " §7(Kısayol Tuşu: '" + keyName + "')"));
        source.sendFeedback(Component.literal("§eHedef Eşya: §f" + config.targetItem + " §7(Lot Boyutu: §f" + config.lotSize + "x§7)"));
        source.sendFeedback(Component.literal("§eSatış Fiyatı: §a$" + String.format("%,.0f", recPrice) + " §7| §eTaban Fiyat: §a$" + String.format("%,.0f", config.minPriceFloor)));
        source.sendFeedback(Component.literal("§eAuto-undercut: " + (config.autoUndercut ? "§aAÇIK §7(" + undercutRule(config) + ")" : "§cKAPALI") + (config.simulationMode ? " §7| §eSimülasyon: §eAÇIK" : "")));
        source.sendFeedback(Component.literal("§eMaksimum Slot: §f" + config.maxSlots));
        if (lm != null) {
            source.sendFeedback(Component.literal("§eAktif İlanlar: §f" + lm.getActiveListings() + "/" + lm.getMaxSlots() + " §7| §eKuyruk: §f" + lm.getQueueSize()));
            source.sendFeedback(Component.literal("§eToplam Kasa Kazancı: §a+$" + String.format("%,d", lm.getTotalEarned()) + " §7(Satılan: §f" + lm.getItemsSold() + "x§7)"));
        }
        if (mod != null) {
            source.sendFeedback(Component.literal("§eFiyat kaynağı: §f" + mod.priceSource()));
        }
        source.sendFeedback(Component.literal("§7Detaylı Rehber İçin: §f/trader help"));
        source.sendFeedback(Component.literal("§6§l======================================================"));
        return 1;
    }

    private static int resetListings(FabricClientCommandSource source) {
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null && mod.getListingManager() != null) {
            mod.getListingManager().resetAll();
            source.sendFeedback(Component.literal("§6[DonutTrader] §aİlan sayacı sıfırlandı: §f0/" + mod.getListingManager().getMaxSlots()));
        }
        return 1;
    }

    private static int setPrice(FabricClientCommandSource source, double price) {
        TraderConfig config = TraderConfig.get();
        config.fallbackPrice = Math.max(1.0, price);
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eSatış fiyatı güncellendi: §a$" + String.format("%,.0f", config.fallbackPrice)));
        if (config.autoUndercut) {
            source.sendFeedback(Component.literal("§7Auto-undercut açık; bu fiyat yalnızca piyasa okunamazsa kullanılır. Sabitlemek için: §f/trader undercut off"));
        }
        return 1;
    }

    private static int setUndercut(FabricClientCommandSource source, boolean enabled) {
        TraderConfig config = TraderConfig.get();
        config.autoUndercut = enabled;
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eAuto-undercut: " + (enabled ? "§aAÇIK" : "§cKAPALI §7(sabit fiyat: $" + String.format("%,.0f", config.fallbackPrice) + ")")));
        return 1;
    }

    /** Sabit tutar: rakipten şu kadar dolar ucuz. Yüzde kuralını da sıfırlar. */
    private static int setUndercutAmount(FabricClientCommandSource source, double amount) {
        TraderConfig config = TraderConfig.get();
        config.undercutAmount = Math.max(0.0, amount);
        config.undercutPercent = 0.0;
        return applyUndercut(source, config);
    }

    private static int setUndercutPercent(FabricClientCommandSource source, double percent) {
        TraderConfig config = TraderConfig.get();
        config.undercutPercent = Math.max(0.0, Math.min(50.0, percent));
        return applyUndercut(source, config);
    }

    private static int applyUndercut(FabricClientCommandSource source, TraderConfig config) {
        config.autoUndercut = true;
        config.save();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) {
            mod.getAutoRelister().setUndercut(config.undercutAmount, config.undercutPercent);
            mod.invalidateScan();
            mod.tickMarketLogic();
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §eUndercut kuralı: §a" + undercutRule(config)));
        source.sendFeedback(Component.literal(String.format(
                "§7Rakip $10.000 ise bizim fiyat: §f$%,.0f",
                Undercut.target(10000.0, config.undercutAmount, config.undercutPercent, 0.0))));
        return 1;
    }

    private static String undercutRule(TraderConfig config) {
        if (config.undercutPercent > 0) {
            return String.format("rakipten $%,.0f ya da %%%.2f — hangisi büyükse", config.undercutAmount, config.undercutPercent);
        }
        return String.format("rakipten $%,.0f ucuz", Math.max(1.0, config.undercutAmount));
    }

    private static int setSimulation(FabricClientCommandSource source, boolean enabled) {
        TraderConfig config = TraderConfig.get();
        config.simulationMode = enabled;
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eSimülasyon modu: " + (enabled ? "§eAÇIK §7(komut gönderilmez)" : "§aKAPALI")));
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        TraderConfig config = TraderConfig.get();
        config.enabled = enabled;
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eMod durumu güncellendi: " + (enabled ? "§aAKTİF" : "§cPASİF")));
        return 1;
    }

    private static int setItem(FabricClientCommandSource source, String itemName) {
        TraderConfig config = TraderConfig.get();
        if (!checkItem(source, itemName)) return 0;
        config.targetItem = itemName.toLowerCase().replace("minecraft:", "").trim();
        config.save();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) {
            mod.invalidateScan();
            mod.tickMarketLogic();
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §eHedef eşya güncellendi: §f" + config.targetItem));
        return 1;
    }

    private static int setLot(FabricClientCommandSource source, int lotSize) {
        TraderConfig config = TraderConfig.get();
        config.lotSize = Math.max(1, Math.min(64, lotSize));
        config.save();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) {
            mod.invalidateScan();
            mod.tickMarketLogic();
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §eLot boyutu güncellendi: §f" + config.lotSize + "x"));
        return 1;
    }

    private static int setSlots(FabricClientCommandSource source, int maxSlots) {
        TraderConfig config = TraderConfig.get();
        config.maxSlots = Math.max(1, Math.min(54, maxSlots));
        config.save();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null && mod.getListingManager() != null) {
            mod.getListingManager().setMaxSlots(config.maxSlots);
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §eMaksimum slot limiti güncellendi: §f" + config.maxSlots));
        return 1;
    }

    private static int setActive(FabricClientCommandSource source, int activeCount) {
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null && mod.getListingManager() != null) {
            mod.getListingManager().setActiveListings(activeCount);
            source.sendFeedback(Component.literal("§6[DonutTrader] §eAktif ilan sayısı güncellendi: §f" + mod.getListingManager().getActiveListings() + "/" + mod.getListingManager().getMaxSlots()));
        }
        return 1;
    }

    private static int setFloor(FabricClientCommandSource source, double floorPrice) {
        TraderConfig config = TraderConfig.get();
        config.minPriceFloor = Math.max(0.0, floorPrice);
        config.save();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null && mod.getAutoRelister() != null) {
            mod.getAutoRelister().setMinPriceFloor(config.minPriceFloor);
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §eTaban fiyat koruması güncellendi: §a$" + String.format("%.0f", config.minPriceFloor)));
        return 1;
    }

    private static int setDump(FabricClientCommandSource source, boolean enabled) {
        TraderConfig config = TraderConfig.get();
        config.dumpScreens = enabled;
        config.save();
        if (enabled) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §eEkran kaydı §aAÇIK§e. Şimdi /ah ve /ah listings menülerini açın."));
            source.sendFeedback(Component.literal("§7Kayıt dosyası: §f" + ScreenDump.file()));
        } else {
            source.sendFeedback(Component.literal("§6[DonutTrader] §eEkran kaydı §cKAPALI§e."));
        }
        return 1;
    }

    private static int checkUpdate(FabricClientCommandSource source) {
        Minecraft client = Minecraft.getInstance();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eSürüm kontrol ediliyor... §7(mevcut: " + Updater.currentVersion() + ")"));

        Thread worker = new Thread(() -> Updater.run(line ->
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§6[DonutTrader] " + line));
                    }
                })), "DonutTrader-Updater");
        worker.setDaemon(true);
        worker.start();
        return 1;
    }

    private static int reloadConfig(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.reload();
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) {
            mod.getListingManager().setMaxSlots(config.maxSlots);
            mod.getAutoRelister().setMinPriceFloor(config.minPriceFloor);
            mod.invalidateScan();
            mod.tickMarketLogic();
        }
        source.sendFeedback(Component.literal("§6[DonutTrader] §aAyar dosyası yeniden yüklendi!"));
        return 1;
    }
}