package com.donutsmp.trader;

import com.donutsmp.trader.api.AhPriceParser;
import com.donutsmp.trader.api.DonutAuctionClient;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.gui.TraderCommands;
import com.donutsmp.trader.gui.ScreenDump;
import com.donutsmp.trader.gui.TraderHud;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import com.donutsmp.trader.license.LicenseVerifier;
import com.donutsmp.trader.market.AhListingManager;
import com.donutsmp.trader.market.AhScreens;
import com.donutsmp.trader.market.AutoRelister;
import com.donutsmp.trader.market.MarketListing;
import com.donutsmp.trader.market.Pacing;
import com.donutsmp.trader.market.PricePolicy;
import com.donutsmp.trader.market.SellGate;
import com.donutsmp.trader.team.PeerState;
import com.donutsmp.trader.team.TeamBoard;
import com.donutsmp.trader.team.TeamPrice;
import com.donutsmp.trader.update.Updater;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class DonutTraderMod implements ClientModInitializer {
    public static final String MOD_ID = "donutsmp_trader";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final long COMMAND_COOLDOWN_MS = 1400; // Sunucu antispam eşiği
    /** /ah sell sonrası eşyanın elden gitmesi için tanınan süre. */
    private static final long VERIFY_AFTER_MS = 2000;

    /** Sunucu konteyner içeriğini ekran açıldıktan birkaç tick sonra gönderir. */
    private static final int SCREEN_SETTLE_TICKS = 10;

    /** Modun kendi açtığı piyasa ekranını beklediği süre. */
    private static final long MARKET_REQUEST_TIMEOUT_MS = 4000;

    /**
     * Arama komutu sunucudan sunucuya değişiyor ve tahmin tutmayınca mod fiyatı
     * hiç okuyamıyor. Hangisinin menü açtığını denemek, doğru komutu sormaktan
     * hızlı: her aday bir kez denenir, tutan config'e yazılır.
     */
    private static final String[] MARKET_COMMANDS = {
            "ah search %s", "ah %s", "auction search %s", "ah browse %s", "ah sell search %s"
    };


    private static DonutTraderMod INSTANCE;

    private DonutAuctionClient apiClient;
    private AhListingManager listingManager;
    private AutoRelister autoRelister;
    private TraderConfig config;
    private ScheduledExecutorService backgroundExecutor;

    private volatile double apiPrice = 35000.0;
    /**
     * Kendi astığımız fiyatlar.
     *
     * /ah listesinde kendi ilanımız da görünür. Onu rakip sayarsak her taramada
     * kendi fiyatımızın altına ineriz ve fiyat dibe doğru spiral yapar.
     */
    private final java.util.Set<Long> ownPrices = java.util.Collections.newSetFromMap(new java.util.LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<Long, Boolean> eldest) {
            return size() > 64;
        }
    });

    private volatile double scanPrice = -1;
    private volatile long scanPriceAt = 0;

    private final TeamBoard team = new TeamBoard();
    private long nextSnapshotAt = 0;

    private KeyMapping toggleKey;
    private String screenKey = null;
    private int screenTicks = 0;
    private boolean screenHandled = false;
    private long marketRequestedAt = 0;
    private long nextMarketScanAt = 0;
    private int marketFailures = 0;
    private int candidateIndex = 0;
    private String triedCommand = null;
    private long verifyAt = 0;
    private int verifySlot = -1;
    private int verifyCount = 0;
    private long lastActionTime = 0;
    private long lastCommandTime = 0;
    private long lastWarningTime = 0;
    private long lastApiWarnAt = 0;
    private long cycleStart = 0;
    private long restAnnouncedAt = 0;
    private int consecutiveFailures = 0;
    private long backoffUntil = 0;
    private LicenseVerifier.Result license;
    private long lastLicenceWarnAt = 0;
    /** Kapının son verdiği cevap; /trader why bunu okur. */
    private volatile SellGate.Reason lastReason = SellGate.Reason.DISABLED;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        this.config = TraderConfig.get();
        this.config.enabled = false; // Oyuna her girildiğinde daima PASİF başlar
        this.config.save();
        this.apiClient = new DonutAuctionClient();
        this.listingManager = new AhListingManager(config.maxSlots);
        this.autoRelister = new AutoRelister(apiClient, config.minPriceFloor, config.undercutAmount, config.undercutPercent);
        this.apiPrice = config.fallbackPrice;

        LOGGER.info("[DonutSMP Trader] Mod baslatiliyor... Hedef: {} (Lot: {}x, Limit: {} slot)",
                config.targetItem, config.lotSize, config.maxSlots);

        try {
            this.toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.donutsmp_trader.toggle",
                    InputConstants.Type.KEYSYM,
                    InputConstants.KEY_K,
                    KeyMapping.Category.MISC
            ));
        } catch (Throwable t) {
            LOGGER.warn("KeyMapping kaydedilemedi: {}", t.getMessage());
        }

        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> TraderCommands.register(dispatcher));
        } catch (Throwable t) {
            LOGGER.warn("ClientCommandRegistrationCallback kaydedilemedi: {}", t.getMessage());
        }

        try {
            ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                if (message != null) handleChatMessage(message.getString());
            });
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (message != null && !overlay) handleChatMessage(message.getString());
            });
        } catch (Throwable t) {
            LOGGER.warn("Chat dinleyici kaydedilemedi: {}", t.getMessage());
        }

        try {
            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        } catch (Throwable t) {
            LOGGER.warn("ClientTickEvents kaydedilemedi: {}", t.getMessage());
        }

        try {
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Updater.applyStagedUpdate());
        } catch (Throwable t) {
            LOGGER.warn("ClientLifecycleEvents kaydedilemedi: {}", t.getMessage());
        }

        TraderHud.register();
        startBackgroundService();
    }

    /**
     * Açık konteyner ekranı işlenir; ama hemen değil.
     *
     * Sunucu slot içeriğini ekran açıldıktan sonraki paketlerle yolluyor.
     * Açılış anında taramak boş menü taramaktır — piyasa fiyatı hiç okunmaz,
     * mod da API'nin eski fiyatına düşer.
     */
    private void onContainerOpen(Minecraft client, Screen screen, AbstractContainerScreen<?> containerScreen) {
        String title = screen.getTitle() == null ? "" : screen.getTitle().getString();
        if (!title.equals(screenKey)) {
            screenKey = title;
            screenTicks = 0;
            screenHandled = false;
        }

        if (screenHandled || ++screenTicks < SCREEN_SETTLE_TICKS) return;
        screenHandled = true;

        if (config.dumpScreens) {
            ScreenDump.capture(screen.getTitle(), containerScreen.getMenu());
        }

        boolean requested = marketRequestedAt > 0
                && System.currentTimeMillis() - marketRequestedAt < MARKET_REQUEST_TIMEOUT_MS;

        if (AhScreens.isMyListings(title)) {
            syncListingsFromScreen(client, containerScreen.getMenu());
            return;
        }

        if (requested || AhScreens.isMarket(title)) {
            scanMarketScreen(client, containerScreen.getMenu());
            if (requested) {
                marketRequestedAt = 0;
                onMarketCommandWorked(client);
                client.player.closeContainer();
            }
        }
    }

    /**
     * Komut yanlışsa sunucuya boşuna komut yağdırmayı bırak.
     *
     * Arama komutu sunucuya göre değişiyor; tahmin tutmazsa hiçbir menü
     * açılmaz. Üç denemeden sonra susup oyuncuya söylemek, doksan saniyede bir
     * geçersiz komut göndermeye devam etmekten iyidir.
     */
    private void onMarketRequestFailed(Minecraft client) {
        LOGGER.warn("[DonutSMP Trader] Piyasa ekrani acilmadi: /{}",
                triedCommand == null ? "?" : String.format(triedCommand, config.targetItem));

        if (!config.marketCommandFound && candidateIndex + 1 < MARKET_COMMANDS.length) {
            candidateIndex++;
            return; // sıradaki adayı dene
        }

        marketFailures++;
        if (marketFailures < 3) return;

        config.autoScan = false;
        config.save();
        marketFailures = 0;
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6[DonutTrader] §cPiyasa araması çalışmadı, otomatik tarama kapatıldı."));
            client.player.sendSystemMessage(Component.literal(
                    "§7Denenen komut: §f/" + String.format(config.marketCommand, config.targetItem)));
            client.player.sendSystemMessage(Component.literal(
                    "§7Doğru komutu §fconfig/donutsmp_trader.json §7içindeki §fmarketCommand §7alanına yazıp §f/trader reload §7deyin."));
        }
    }

    /**
     * Piyasa fiyatını modun kendisi sorar.
     *
     * Oyuncunun /ah menüsünü elle açmasını beklemek fiyatı API'ye bağımlı
     * bırakıyordu; API birkaç dakika geride kalınca eşya ucuza gidiyordu.
     */
    private void requestMarketScan(Minecraft client, long now) {
        if (!config.autoScan || !config.autoUndercut) return;
        if (now < nextMarketScanAt || marketRequestedAt > 0) return;
        // Arama da bir komut: satışla aynı antispam aralığını paylaşmazsa
        // ikisi aynı tick'te gidip sunucunun hız sınırına takılır.
        if (now - lastCommandTime < COMMAND_COOLDOWN_MS) return;

        String template = config.marketCommandFound ? config.marketCommand : MARKET_COMMANDS[candidateIndex];
        String command = String.format(template, config.targetItem);

        nextMarketScanAt = now + (config.marketCommandFound ? scanIntervalMs() : 2000L);
        marketRequestedAt = now;
        lastCommandTime = now;
        triedCommand = template;
        client.player.connection.sendCommand(command);
        LOGGER.info("[DonutSMP Trader] Piyasa soruldu: /{}", command);
    }

    /** Menü açıldı: denenen komut doğruymuş, kalıcı olarak onu kullan. */
    private void onMarketCommandWorked(Minecraft client) {
        marketFailures = 0;
        if (config.marketCommandFound || triedCommand == null) return;

        config.marketCommand = triedCommand;
        config.marketCommandFound = true;
        config.save();
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(
                    "§6[DonutTrader] §aPiyasa komutu bulundu: §f/" + String.format(triedCommand, config.targetItem)));
        }
    }

    /**
     * 18 slot limiti hesabımızın tamamı içindir, sadece traderın koyduklarının
     * değil. Elle koyduğunuz bir ilan da slot yer; sayacı yalnızca kendi
     * gönderdiklerimizden yürütmek boş olmayan slotu boş gösterir.
     */
    private void syncListingsFromScreen(Minecraft client, AbstractContainerMenu menu) {
        if (menu == null) return;

        int containerSlots = Math.max(0, menu.slots.size() - 36);
        int counted = 0;
        for (int i = 0; i < containerSlots; i++) {
            if (hasPricedLore(menu.slots.get(i).getItem())) counted++;
        }

        // Duyurunun olcusu SAYACIN duzelmesi, okunan sayinin degismesi degil.
        // Onceki hâli, iki okuma ayni cikinca susuyordu — oysa en cok bilinmesi
        // gereken an tam olarak oydu: sayac 18'de takiliyken gercek 8 idi ve
        // duzeltme sessizce gecti.
        int before = listingManager.syncActiveListings(counted);
        if (before != counted) {
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§6[DonutTrader] §eAktif ilanlar: §f%d/%d §7(sayaç %d idi, düzeltildi)",
                    listingManager.getActiveListings(), listingManager.getMaxSlots(), before)));
        }
    }

    /** Dekoratif cam panellerin lore'unda fiyat olmaz; ilanların olur. */
    private static boolean hasPricedLore(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (AhPriceParser.parsePrice(line.getString()) > 0) return true;
        }
        return false;
    }

    /** Açılan /ah menüsündeki rakip fiyatlarını lore üzerinden okur. */
    private void scanMarketScreen(Minecraft client, AbstractContainerMenu menu) {
        if (menu == null || !config.autoUndercut) return;

        List<Slot> slots = menu.slots;
        String target = DonutAuctionClient.normalizeItemName(config.targetItem);
        String self = client.player.getGameProfile().name();

        List<MarketListing.Entry> entries = new java.util.ArrayList<>();
        int scanned = Math.min(45, slots.size());
        for (int i = 0; i < scanned; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty()) continue;
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;
            entries.add(new MarketListing.Entry(
                    InventoryActionHelper.idOf(stack),
                    stack.getCount(),
                    lore.lines().stream().map(Component::getString).toList()));
        }

        MarketListing.Board board = MarketListing.scan(entries, target, config.lotSize,
                team.ourNames(config, self), ownPrices);

        if (board.empty()) {
            LOGGER.info("[DonutSMP Trader] {}x {} icin kiyaslanabilir rakip yok "
                            + "(kendi/takim ilani: {}, farkli yigin boyutu: {})",
                    config.lotSize, target, board.skippedOwn(), board.skippedSize());
            if (board.skippedSize() > 0) {
                warn(client, System.currentTimeMillis(), String.format(
                        "§6[DonutTrader] §e%s için %dx boyutunda ilan yok §7(%d ilan farklı boyutta, kıyaslanmadı)",
                        target, config.lotSize, board.skippedSize()));
            }
            return;
        }

        double lowestCompetitor = board.cheapest();
        double previous = scanFresh() ? scanPrice : 0;
        int below = board.below(previous);

        PricePolicy.Decision decision = PricePolicy.decide(previous, lowestCompetitor, below,
                config.minPriceFloor, config.undercutAmount, config.undercutPercent,
                config.minRepriceStep, config.minUndercutGap, config.minCompetitorsBelow);

        scanPrice = decision.price();
        scanPriceAt = System.currentTimeMillis();

        LOGGER.info("[DonutSMP Trader] Piyasa ({}x): rakip {} ({} tanesi altimizda, {} kiyaslanabilir ilan) "
                        + "| {} -> {} ({}) (kendi/takim: {}, farkli boyut: {})",
                config.lotSize, lowestCompetitor, below, board.prices().size(),
                previous, decision.price(), decision.reason(), board.skippedOwn(), board.skippedSize());

        if (decision.changed() && previous > 0) {
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§6[DonutTrader] §e%s: §f$%,.0f §7-> §6$%,.0f §7(rakip $%,.0f)",
                    decision.action() == PricePolicy.Action.RAISE ? "Piyasa yükseldi" : "Rakip altımıza girdi",
                    previous, decision.price(), lowestCompetitor)));
        }
    }

    private void onClientTick(Minecraft client) {
        if (toggleKey != null && toggleKey.consumeClick()) {
            toggleEnabled();
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§6[DonutTrader] §eMod Durumu: " + (config.enabled ? "§a[AKTİF]" : "§c[PASİF]")));
            }
        }

        publishState(client);

        Screen screen = client.gui.screen();
        if (screen != null) {
            if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                onContainerOpen(client, screen, containerScreen);
            }
            return;
        }

        screenKey = null;
        screenTicks = 0;
        screenHandled = false;
        if (client.isPaused()) return;

        if (marketRequestedAt > 0 && System.currentTimeMillis() - marketRequestedAt >= MARKET_REQUEST_TIMEOUT_MS) {
            marketRequestedAt = 0;
            onMarketRequestFailed(client);
        }
        if (client.player == null || client.getConnection() == null) return;

        // Sanal tıklamalar oyuncunun kendi envanter menüsüne gider; başka bir
        // konteyner açıksa slot numaraları bambaşka bir şeye denk gelir.
        if (client.player.containerMenu != client.player.inventoryMenu) return;

        long now = System.currentTimeMillis();
        if (cycleStart == 0) cycleStart = now;

        SellGate.Reason blocked = SellGate.blocking(config.enabled, resting(now),
                now < backoffUntil, listingManager.isInCombat());
        if (blocked == SellGate.Reason.DISABLED) {
            lastReason = blocked;
            return;
        }
        if (!licenceValid(client, now)) return;
        if (blocked != null) {
            lastReason = blocked;
            announceBlock(client, now, blocked);
            return;
        }

        // Onceki ilanin girip girmedigi belli olmadan sunucuya baska komut
        // gitmez: dogrulama penceresi boyunca piyasa sorgusu da beklemeli.
        boolean verifying = verifyAt > 0 && now < verifyAt;
        if (!verifying) {
            if (verifyAt > 0) verifyListing(client);
            requestMarketScan(client, now);
        }

        SellGate.Verdict verdict = SellGate.next(facts(client, now, verifying));
        lastReason = verdict.reason();
        switch (verdict.action()) {
            case SPLIT -> buildLot(client, now);
            case LIST -> sendListing(client, now);
            case WAIT -> announceWait(client, now, verdict.reason());
        }
    }

    private boolean resting(long now) {
        return Pacing.resting(now, cycleStart, config.workSeconds * 1000L, config.restSeconds * 1000L);
    }

    /** Kapının baktığı her şey, tek yerde toplanmış hâlde. */
    private SellGate.Facts facts(Minecraft client, long now, boolean verifying) {
        String target = DonutAuctionClient.normalizeItemName(config.targetItem);
        boolean exists = InventoryActionHelper.itemExists(target);
        ItemStack held = client.player.getInventory().getSelectedItem();
        boolean handReady = InventoryActionHelper.idOf(held).equals(target)
                && held.getCount() == config.lotSize;

        int emptyHotbar = handReady ? 0 : InventoryActionHelper.findEmptyHotbarIndex(client.player);
        boolean haveItems = false;
        if (!handReady && emptyHotbar >= 0 && exists) {
            int destination = InventoryActionHelper.HOTBAR_MENU_START + emptyHotbar;
            haveItems = InventoryActionHelper.findTargetSlot(
                    client.player, target, config.lotSize, destination) >= 0;
        }

        return new SellGate.Facts(
                verifying,
                marketRequestedAt > 0 && !scanFresh(),
                now - lastCommandTime < COMMAND_COOLDOWN_MS
                        || now - lastActionTime < Math.max(120, config.clickDelayMs),
                listingManager.canListMore(),
                client.player.onGround(),
                exists,
                handReady,
                emptyHotbar >= 0,
                haveItems,
                (long) effectivePrice());
    }

    /** Yığından bir lot ayırıp elimize al. */
    private void buildLot(Minecraft client, long now) {
        String target = DonutAuctionClient.normalizeItemName(config.targetItem);
        int emptyHotbar = InventoryActionHelper.findEmptyHotbarIndex(client.player);
        if (emptyHotbar < 0) return;

        int destination = InventoryActionHelper.HOTBAR_MENU_START + emptyHotbar;
        int sourceSlot = InventoryActionHelper.findTargetSlot(client.player, target, config.lotSize, destination);
        if (sourceSlot < 0) return;

        InventoryActionHelper.splitToHotbar(client, sourceSlot, emptyHotbar, config.lotSize);
        client.player.getInventory().setSelectedSlot(emptyHotbar);
        lastActionTime = now;
    }

    private void sendListing(Minecraft client, long now) {
        long sellPrice = (long) effectivePrice();
        int selected = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getInventory().getSelectedItem();

        // Piyasa okunamadan API fiyatiyla listelemek sessizce yanlis fiyata
        // satmaktir; oyuncu bunu ilanlar satilmayinca fark ediyor.
        if (config.autoUndercut && !scanFresh() && now - lastApiWarnAt > 60_000) {
            lastApiWarnAt = now;
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§6[DonutTrader] §ePiyasa okunamadı, fiyat API'den: §f$%,d§e. Yanlışsa: §f/trader undercut off §e+ §f/trader price <fiyat>",
                    sellPrice)));
        }

        if (config.simulationMode) {
            warn(client, now, String.format("§6[DonutTrader] §e[SİMÜLASYON] /ah sell %d gönderilmedi (slot %d).", sellPrice, selected));
            lastCommandTime = now;
            lastActionTime = now;
            return;
        }

        client.player.connection.sendCommand("ah sell " + sellPrice);
        ownPrices.add(sellPrice);
        listingManager.onListingSent();
        verifyAt = now + VERIFY_AFTER_MS;
        verifySlot = selected;
        verifyCount = held.getCount();
        lastCommandTime = now;
        lastActionTime = now;
        LOGGER.info("[DonutSMP Trader] /ah sell {} gonderildi! (Aktif: {}/{})",
                sellPrice, listingManager.getActiveListings(), listingManager.getMaxSlots());
    }

    /** Molayı ve savaşı kendi temposunda duyurur; geri kalanı sessizdir. */
    private void announceBlock(Minecraft client, long now, SellGate.Reason reason) {
        long restMs = config.restSeconds * 1000L;
        if (reason == SellGate.Reason.RESTING && now - restAnnouncedAt > restMs) {
            restAnnouncedAt = now;
            long left = Pacing.restRemainingMs(now, cycleStart,
                    config.workSeconds * 1000L, restMs) / 1000;
            client.player.sendSystemMessage(Component.literal(
                    "§6[DonutTrader] §7Mola: §f" + left + " sn §7sonra devam edecek."));
        } else if (reason == SellGate.Reason.COMBAT && now - lastWarningTime > 6000) {
            lastWarningTime = now;
            client.player.sendSystemMessage(Component.literal(
                    "§6[DonutTrader] §eSavaş modu (Combat Tag) aktif, işlem 20 saniye duraklatıldı."));
        }
    }

    /** Oyuncunun düzeltebileceği engeller söylenir; geçici olanlar susar. */
    private void announceWait(Minecraft client, long now, SellGate.Reason reason) {
        switch (reason) {
            case IN_AIR -> warn(client, now, "§6[DonutTrader] §eHavadasınız, satış için yere inilmesi bekleniyor.");
            case BAD_ITEM -> warn(client, now, "§6[DonutTrader] §cHedef eşya geçersiz: §f" + config.targetItem
                    + " §c— düzeltmek için: §f/trader item <eşya>");
            case NO_HOTBAR -> warn(client, now, "§6[DonutTrader] §cHotbar'ınızda en az 1 boş slot bırakınız!");
            case NO_ITEMS -> warn(client, now, "§6[DonutTrader] §cEnvanterinizde satılacak "
                    + config.lotSize + "x " + config.targetItem + " kalmadı!");
            default -> { }
        }
    }

    /**
     * İlan gerçekten girdi mi?
     *
     * Sunucu bazen komuta hiç cevap vermiyor; sohbeti dinlemek yetmiyor.
     * İlan girdiyse eşya elden gider — envantere bakmak kesin cevaptır.
     */
    private void verifyListing(Minecraft client) {
        int slot = verifySlot;
        int expected = verifyCount;
        verifyAt = 0;
        verifySlot = -1;
        verifyCount = 0;

        if (client.player == null || slot < 0) return;

        ItemStack stack = client.player.getInventory().getItem(slot);
        boolean stillThere = !stack.isEmpty()
                && InventoryActionHelper.idOf(stack).equals(DonutAuctionClient.normalizeItemName(config.targetItem))
                && stack.getCount() == expected;

        if (stillThere) {
            consecutiveFailures++;
            backoffUntil = System.currentTimeMillis()
                    + Pacing.backoffMs(consecutiveFailures, 3000, 120_000);
            listingManager.onListingRejected();
            LOGGER.info("[DonutSMP Trader] Ilan girmedi, esya elde kaldi. Aktif: {}/{}",
                    listingManager.getActiveListings(), listingManager.getMaxSlots());
            warn(client, System.currentTimeMillis(),
                    "§6[DonutTrader] §eSatış gerçekleşmedi, eşya elinizde kaldı. Slot sayacı geri alındı.");
        } else {
            consecutiveFailures = 0;
            listingManager.onListingVerified();
        }
    }

    /** Lisans oyuncu adına bağlı olduğu için oyuna girmeden doğrulanamaz. */
    private boolean licenceValid(Minecraft client, long now) {
        if (license == null || !license.allowed()) {
            license = LicenseVerifier.verify(config.licenseKey, LicenseVerifier.embeddedPublicKey(),
                    client.player.getGameProfile().name(), java.time.LocalDate.now());
        }

        if (license.allowed()) return true;

        if (now - lastLicenceWarnAt > 60_000) {
            lastLicenceWarnAt = now;
            client.player.sendSystemMessage(Component.literal("§6[DonutTrader] §cLisans geçersiz: §f" + license.message()));
            client.player.sendSystemMessage(Component.literal("§7Anahtarı girmek için: §f/trader license <anahtar>"));
        }
        return false;
    }

    public LicenseVerifier.Result licenceStatus(String playerName) {
        return LicenseVerifier.verify(config.licenseKey, LicenseVerifier.embeddedPublicKey(),
                playerName, java.time.LocalDate.now());
    }

    public void clearLicenceCache() {
        license = null;
    }

    private void warn(Minecraft client, long now, String message) {
        if (now - lastWarningTime <= 5000) return;
        lastWarningTime = now;
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }

    private void startBackgroundService() {
        this.backgroundExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DonutTrader-Worker");
            t.setDaemon(true);
            return t;
        });

        int period = Math.max(5, config.marketPollSeconds);
        this.backgroundExecutor.scheduleWithFixedDelay(this::tickMarketLogic, 1, period, TimeUnit.SECONDS);
        this.backgroundExecutor.scheduleWithFixedDelay(this::tickTeam, 2, 3, TimeUnit.SECONDS);
    }

    /**
     * Kendi durumumuzu takım tablosuna bırak.
     *
     * Sadece bırakılır; diske yazmayı arka plan yapar. Mod kapalıyken de
     * yazılır, arkadaş "şu an satmıyor" bilgisini de görebilsin.
     */
    private void publishState(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now < nextSnapshotAt || client.player == null) return;
        nextSnapshotAt = now + 1000;

        String target = DonutAuctionClient.normalizeItemName(config.targetItem);
        team.setMine(new PeerState(
                client.player.getGameProfile().name(),
                now,
                config.enabled,
                config.targetItem,
                config.lotSize,
                (long) basePrice(),
                InventoryActionHelper.countItem(client.player, target),
                InventoryActionHelper.countEmptyHotbar(client.player),
                listingManager.getActiveListings(),
                listingManager.getMaxSlots()));
    }

    private void tickTeam() {
        try {
            PeerState mine = team.mine();
            if (mine != null) team.poll(config, mine.name());
        } catch (Exception e) {
            LOGGER.warn("[Takim] Paylasim tick hatasi: {}", e.getMessage());
        }
    }

    public TeamBoard getTeam() { return team; }

    public SellGate.Reason lastReason() { return lastReason; }

    public void tickMarketLogic() {
        try {
            this.apiPrice = apiClient.calculateOptimalSellPrice(
                    config.targetItem,
                    config.lotSize,
                    config.fallbackPrice,
                    config.minPriceFloor,
                    config.undercutAmount,
                    config.undercutPercent
            );
        } catch (Exception e) {
            LOGGER.error("Market logic tick hatasi: {}", e.getMessage());
        }
    }

    /**
     * Auto-undercut kapalıyken /trader price ile verilen fiyat geçerlidir.
     * Açıkken taze tarama API'nin YERİNE geçer, ucuzu seçilmez: menüde görülen
     * fiyat canlıdır, ikisinin küçüğünü almak piyasa yükselince bizi eski
     * API fiyatına kilitler ve ucuza sattırır.
     */
    public double effectivePrice() {
        return TeamPrice.floor(basePrice(), config.targetItem, config.lotSize, team.peers());
    }

    /**
     * Takım kuralı uygulanmadan önceki kendi fiyatımız. Arkadaşlara YAYINLANAN
     * budur, listelenen değil.
     *
     * Fark önemli: taban dahil bir fiyat yayınlarsak iki mod birbirinin
     * tabanını besler. Piyasa 7.000'e inse bile ikisi de "ötekinin fiyatı
     * 9.000" görüp orada kilitlenir ve hiçbiri inemez.
     */
    public double basePrice() {
        double price = config.autoUndercut
                ? (scanFresh() ? scanPrice : apiPrice)
                : config.fallbackPrice;
        return Math.max(price, config.minPriceFloor);
    }

    /** Fiyatın nereden geldiği; "undercut çalışmıyor" şikayetini teşhis etmek için. */
    public String priceSource() {
        if (TeamPrice.binding(basePrice(), config.targetItem, config.lotSize, team.peers())) {
            return String.format("takım arkadaşının fiyatı ($%,.0f) — altına inilmiyor", effectivePrice());
        }
        if (!config.autoUndercut) {
            return String.format("sabit fiyat ($%,.0f) — /trader undercut on ile piyasaya bağlanır", config.fallbackPrice);
        }
        if (scanFresh()) {
            long age = (System.currentTimeMillis() - scanPriceAt) / 1000;
            return String.format("oyun içi tarama ($%,.0f, %d sn önce)", scanPrice, age);
        }
        return String.format("donut.auction API ($%,.0f) — henüz piyasa taraması yok", apiPrice);
    }

    /** Tarama aralığının üç katından eski fiyat artık piyasayı temsil etmiyor. */
    private long scanTtlMs() {
        return Math.max(60_000L, scanIntervalMs() * 3);
    }

    private long scanIntervalMs() {
        return Math.max(3, config.scanIntervalSeconds) * 1000L;
    }

    private boolean scanFresh() {
        return scanPrice > 0 && System.currentTimeMillis() - scanPriceAt < scanTtlMs();
    }

    /** Hedef ya da lot değişince eski taramanın fiyatı artık bu eşyaya ait değildir. */
    public void invalidateScan() {
        scanPrice = -1;
        scanPriceAt = 0;
    }

    /**
     * Döngüyü baştan başlat.
     *
     * Tempo değiştiğinde gerekli: eski cycleStart'a göre faz hesaplanınca
     * molayı yeni açan oyuncu doğrudan molanın ortasına düşebiliyor.
     */
    public void resetCycle() {
        cycleStart = 0;
        restAnnouncedAt = 0;
    }

    public void toggleEnabled() {
        config.enabled = !config.enabled;
        config.save();
        LOGGER.info("[DonutSMP Trader] Mod Durumu: {}", config.enabled ? "AKTIF" : "PASIF");
    }

    public void handleChatMessage(String rawMessage) {
        listingManager.onChatMessage(rawMessage);
    }

    public List<String> getHudInfo() {
        return TraderHud.getHudLines(config, listingManager, effectivePrice(), team.peers());
    }

    public String getKeyName() {
        return toggleKey != null ? toggleKey.getTranslatedKeyMessage().getString() : "K";
    }

    public static DonutTraderMod getInstance() {
        return INSTANCE;
    }

    public DonutAuctionClient getApiClient() { return apiClient; }
    public AhListingManager getListingManager() { return listingManager; }
    public AutoRelister getAutoRelister() { return autoRelister; }
    public TraderConfig getConfig() { return config; }
    public double getCurrentRecommendedPrice() { return effectivePrice(); }
}
