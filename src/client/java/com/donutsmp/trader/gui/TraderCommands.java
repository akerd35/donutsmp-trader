package com.donutsmp.trader.gui;

import com.donutsmp.trader.DonutTraderMod;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import com.donutsmp.trader.license.LicenseVerifier;
import com.donutsmp.trader.market.AhListingManager;
import com.donutsmp.trader.market.SellGate;
import com.donutsmp.trader.market.Undercut;
import com.donutsmp.trader.team.PeerState;
import com.donutsmp.trader.team.Team;
import com.donutsmp.trader.team.TeamLink;
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

import java.nio.file.Files;
import java.nio.file.Path;
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
                .then(ClientCommands.literal("license")
                        .executes(context -> showLicense(context.getSource()))
                        .then(ClientCommands.argument("key", StringArgumentType.greedyString())
                                .executes(context -> setLicense(context.getSource(), StringArgumentType.getString(context, "key")))))
                .then(ClientCommands.literal("why")
                        .executes(context -> showWhy(context.getSource())))
                .then(ClientCommands.literal("pace")
                        .executes(context -> showPace(context.getSource()))
                        .then(ClientCommands.literal("off").executes(context -> setPace(context.getSource(), -1, 0)))
                        .then(ClientCommands.literal("on").executes(context -> setPace(context.getSource(), -1, -1)))
                        .then(ClientCommands.argument("work", IntegerArgumentType.integer(0, 86400))
                                .then(ClientCommands.argument("rest", IntegerArgumentType.integer(0, 86400))
                                        .executes(context -> setPace(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "work"),
                                                IntegerArgumentType.getInteger(context, "rest"))))))
                .then(ClientCommands.literal("team")
                        .executes(context -> showTeam(context.getSource()))
                        .then(ClientCommands.literal("add")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> addTeammate(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(ClientCommands.literal("remove")
                                .then(ClientCommands.argument("name", StringArgumentType.word())
                                        .executes(context -> removeTeammate(context.getSource(), StringArgumentType.getString(context, "name")))))
                        .then(ClientCommands.literal("clear")
                                .executes(context -> clearTeam(context.getSource())))
                        .then(ClientCommands.literal("folder")
                                .executes(context -> showFolder(context.getSource()))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> setFolder(context.getSource(), "")))
                                .then(ClientCommands.argument("path", StringArgumentType.greedyString())
                                        .executes(context -> setFolder(context.getSource(), StringArgumentType.getString(context, "path"))))))
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
        source.sendFeedback(Component.literal("  §a/trader why §7-> Mod neden satmıyor, tek satırda söyler"));
        source.sendFeedback(Component.literal("  §f/trader pace off §7-> 5dk çalış / 1dk mola döngüsünü kapatır"));
        source.sendFeedback(Component.literal("  §f/trader team §7-> Arkadaşınızın durumu §8(eşya, kalan adet, boş hotbar)"));
        source.sendFeedback(Component.literal("  §f/trader team add <ad> §7-> O oyuncunun fiyatının altına inilmez"));
        source.sendFeedback(Component.literal("  §f/trader team folder <yol> §7-> Ortak klasörle durum paylaşımı"));
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
        source.sendFeedback(Component.literal("§eMaksimum Slot: §f" + config.maxSlots
                + " §7| §eDöngü: " + (paused(config)
                ? "§cmola yok §7(kesintisiz)"
                : "§f" + config.workSeconds + "sn çalış / " + config.restSeconds + "sn mola")));
        if (lm != null) {
            source.sendFeedback(Component.literal("§eAktif İlanlar: §f" + lm.getActiveListings() + "/" + lm.getMaxSlots()));
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
        int previous = config.lotSize;
        config.lotSize = Math.max(1, Math.min(64, lotSize));
        config.save();

        // Taban ve sabit fiyat BIR ILANIN tamami icindir. Lot buyudugunde ayni
        // taban esya basina bolunur: 1 esya icin konan 9.000'lik taban, 16'lik
        // lotta esyayi 562'ye korumaya calisir.
        if (config.lotSize != previous && config.minPriceFloor > 0) {
            source.sendFeedback(Component.literal(String.format(
                    "§6[DonutTrader] §eTaban fiyat §f$%,.0f §ebir ilanın tamamı içindir — şimdi eşya başına §f$%,.0f§e.",
                    config.minPriceFloor, config.minPriceFloor / config.lotSize)));
            source.sendFeedback(Component.literal(
                    "§7Lot boyutunu değiştirdiniz; gerekirse: §f/trader floor <fiyat> §7ve §f/trader price <fiyat>"));
        }
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

    private static int setLicense(FabricClientCommandSource source, String key) {
        TraderConfig config = TraderConfig.get();
        config.licenseKey = key.trim();
        config.save();

        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) mod.clearLicenceCache();
        return showLicense(source);
    }

    private static int showLicense(FabricClientCommandSource source) {
        DonutTraderMod mod = DonutTraderMod.getInstance();
        String name = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.getGameProfile().name() : "";
        if (mod == null) return 0;

        LicenseVerifier.Result result = mod.licenceStatus(name);
        source.sendFeedback(Component.literal("§6[DonutTrader] §eLisans: "
                + (result.allowed() ? "§a" : "§c") + result.message()));
        return 1;
    }


    /**
     * Mod neden satmiyor?
     *
     * Sebep tick dongusunun icinde kaliyordu ve yalnizca kodu okuyarak
     * bulunabiliyordu; artik kapi ne dediyse o yaziliyor.
     */
    private static int showWhy(FabricClientCommandSource source) {
        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod == null) return 0;
        TraderConfig config = TraderConfig.get();
        SellGate.Reason reason = mod.lastReason();

        if (reason == SellGate.Reason.GO) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §aSatışa hazır: §f"
                    + config.lotSize + "x " + config.targetItem
                    + " §7@ §a$" + String.format("%,.0f", mod.getCurrentRecommendedPrice())));
        } else {
            source.sendFeedback(Component.literal("§6[DonutTrader] §eŞu an satmıyor: §c" + reason.text()));
            String fix = remedy(reason, config);
            if (fix != null) source.sendFeedback(Component.literal("§7" + fix));
        }
        source.sendFeedback(Component.literal("§7Fiyat kaynağı: §f" + mod.priceSource()));
        return 1;
    }

    /** Oyuncunun yapabilecegi sey; gecici engellerde null. */
    private static String remedy(SellGate.Reason reason, TraderConfig config) {
        return switch (reason) {
            case DISABLED -> "Başlatmak için: §f/trader on §7ya da §f/trader fullauto <eşya>";
            case RESTING -> "Molayı kapatmak için: §f/trader pace off";
            case COMBAT -> "Savaş bitince kendiliğinden devam eder.";
            case SLOTS_FULL -> "İlanlarınız dolu. 15 sn'de bir yine denenir; §f/ah listings §7açarsanız sayaç anında düzelir.";
            case IN_AIR -> "Yere inin; sunucu havadayken §f/ah sell §7kabul etmiyor.";
            case BAD_ITEM -> "Hedefi düzeltin: §f/trader item <eşya>";
            case NO_HOTBAR -> "Hotbar'da en az bir slot boşaltın.";
            case NO_ITEMS -> "Envanterde " + config.lotSize + "x " + config.targetItem + " kalmadı.";
            case NO_PRICE -> "Fiyat yok: §f/trader price <fiyat> §7ya da §f/trader floor <fiyat>";
            default -> null;
        };
    }

    // ---------- calisma temposu ----------

    private static final int DEFAULT_WORK_SECONDS = 300;
    private static final int DEFAULT_REST_SECONDS = 60;

    private static int showPace(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.get();
        if (paused(config)) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §eMola: §cKAPALI §7(kesintisiz çalışır)"));
        } else {
            source.sendFeedback(Component.literal(String.format(
                    "§6[DonutTrader] §eMola: §a%d sn çalış / %d sn dur",
                    config.workSeconds, config.restSeconds)));
        }
        source.sendFeedback(Component.literal("§f/trader pace off §7-> molayı kapatır"));
        source.sendFeedback(Component.literal("§f/trader pace <çalış> <dur> §7-> süreleri saniye olarak verir"));
        return 1;
    }

    /** @param work -1 ise dokunulmaz; rest -1 ise varsayılana döner */
    private static int setPace(FabricClientCommandSource source, int work, int rest) {
        TraderConfig config = TraderConfig.get();

        if (rest < 0) {
            config.workSeconds = config.workSeconds > 0 ? config.workSeconds : DEFAULT_WORK_SECONDS;
            config.restSeconds = config.restSeconds > 0 ? config.restSeconds : DEFAULT_REST_SECONDS;
        } else {
            if (work >= 0) config.workSeconds = work;
            config.restSeconds = rest;
        }
        config.save();

        DonutTraderMod mod = DonutTraderMod.getInstance();
        if (mod != null) mod.resetCycle();

        if (paused(config)) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §eMola §ckapatıldı§e: kesintisiz çalışacak."));
            source.sendFeedback(Component.literal("§7Sunucuya aralıksız komut gider; hız sınırına takılırsan §f/trader pace on"));
        } else {
            source.sendFeedback(Component.literal(String.format(
                    "§6[DonutTrader] §eMola: §a%d sn çalış / %d sn dur",
                    config.workSeconds, config.restSeconds)));
        }
        return 1;
    }

    /** Iki sureden biri sifirsa mola diye bir sey yoktur. */
    private static boolean paused(TraderConfig config) {
        return config.workSeconds <= 0 || config.restSeconds <= 0;
    }

    // ---------- takım ----------

    private static int showTeam(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.get();
        DonutTraderMod mod = DonutTraderMod.getInstance();

        source.sendFeedback(Component.literal("§6§l==================== [Takım] ===================="));
        source.sendFeedback(Component.literal("§eFiyat kırılmayacaklar: §f"
                + (config.teammates.isEmpty() ? "§7(kimse yok)" : String.join(", ", config.teammates))));
        source.sendFeedback(Component.literal("§ePaylaşım klasörü: §f"
                + (config.teamFolder.isBlank() ? "§7kapalı §8(/trader team folder <yol>)" : config.teamFolder)));

        if (mod == null) return 1;
        String error = mod.getTeam().lastError();
        if (!error.isBlank() && !"null".equals(error)) {
            source.sendFeedback(Component.literal("§cKlasör hatası: §f" + error));
        }

        List<PeerState> peers = mod.getTeam().peers();
        if (peers.isEmpty()) {
            source.sendFeedback(Component.literal("§7Çevrimiçi arkadaş yok."
                    + (config.teamFolder.isBlank() ? "" : " §8(dosyalar " + config.teamStaleSeconds + " sn'de bir tazelenir)")));
        }
        long now = System.currentTimeMillis();
        for (PeerState peer : peers) {
            source.sendFeedback(Component.literal(String.format(
                    "§b%s %s §7| §f%dx %s §7| §a$%,d §7| §eKalan: §f%d §7| §eBoş hotbar: §f%d §7| §eİlan: §f%d/%d §8(%d sn önce)",
                    peer.name(), peer.enabled() ? "§a●" : "§7○",
                    peer.lotSize(), peer.item(), peer.price(),
                    peer.itemsLeft(), peer.freeHotbarSlots(),
                    peer.activeListings(), peer.maxSlots(), peer.ageSeconds(now))));
            if (peer.enabled() && peer.item() != null && peer.item().equalsIgnoreCase(config.targetItem)) {
                if (peer.itemsLeft() == 0) {
                    source.sendFeedback(Component.literal("  §7" + peer.name() + " §7için satacak eşya kalmadı."));
                }
                if (peer.freeHotbarSlots() == 0) {
                    source.sendFeedback(Component.literal("  §c" + peer.name() + " §7hotbar'ında boş slot yok, lot ayıramaz."));
                }
            }
        }
        source.sendFeedback(Component.literal("§6§l================================================"));
        return 1;
    }

    private static int addTeammate(FabricClientCommandSource source, String name) {
        TraderConfig config = TraderConfig.get();
        if (!Team.validName(name)) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §cGeçersiz oyuncu adı: §f" + name));
            source.sendFeedback(Component.literal("§73-16 karakter, harf rakam ve alt çizgi."));
            return 0;
        }
        if (!Team.add(config.teammates, name)) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §e" + name + " §7zaten listede."));
            return 0;
        }
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §a" + name.trim() + " §7eklendi; ilanlarının altına inilmeyecek."));
        return 1;
    }

    private static int removeTeammate(FabricClientCommandSource source, String name) {
        TraderConfig config = TraderConfig.get();
        boolean removed = Team.remove(config.teammates, name);
        config.save();
        source.sendFeedback(Component.literal(removed
                ? "§6[DonutTrader] §e" + name + " §7listeden çıkarıldı."
                : "§6[DonutTrader] §7" + name + " zaten listede değildi."));
        return removed ? 1 : 0;
    }

    private static int clearTeam(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.get();
        config.teammates.clear();
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §eTakım listesi boşaltıldı."));
        if (!config.teamFolder.isBlank()) {
            source.sendFeedback(Component.literal("§7Klasör hâlâ açık; oradaki adlar birkaç saniye içinde geri eklenir."));
        }
        return 1;
    }

    private static int showFolder(FabricClientCommandSource source) {
        TraderConfig config = TraderConfig.get();
        source.sendFeedback(Component.literal("§6[DonutTrader] §ePaylaşım klasörü: §f"
                + (config.teamFolder.isBlank() ? "§7kapalı" : config.teamFolder)));
        source.sendFeedback(Component.literal("§7İkiniz de aynı eşitlenen klasörü gösterin §8(Dropbox, Drive, iCloud, Syncthing)"));
        source.sendFeedback(Component.literal("§7Örnek: §f/trader team folder ~/Dropbox/donuttrader"));
        return 1;
    }

    private static int setFolder(FabricClientCommandSource source, String path) {
        TraderConfig config = TraderConfig.get();
        if (path == null || path.isBlank()) {
            config.teamFolder = "";
            config.save();
            source.sendFeedback(Component.literal("§6[DonutTrader] §ePaylaşım kapatıldı. §7İsim listesi duruyor."));
            return 1;
        }

        Path dir = TeamLink.resolve(path);
        if (dir == null) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §cYol okunamadı: §f" + path));
            return 0;
        }
        try {
            Files.createDirectories(dir);
        } catch (Exception e) {
            source.sendFeedback(Component.literal("§6[DonutTrader] §cKlasör oluşturulamadı: §f" + e.getMessage()));
            return 0;
        }

        config.teamFolder = path.trim();
        config.save();
        source.sendFeedback(Component.literal("§6[DonutTrader] §aPaylaşım açık: §f" + dir));
        source.sendFeedback(Component.literal("§7Arkadaşınız da aynı klasörü göstersin. Adlar kendiliğinden eklenir."));
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