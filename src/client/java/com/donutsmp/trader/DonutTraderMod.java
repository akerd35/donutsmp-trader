package com.donutsmp.trader;

import com.donutsmp.trader.api.AhPriceParser;
import com.donutsmp.trader.api.DonutAuctionClient;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.gui.TraderCommands;
import com.donutsmp.trader.gui.ScreenDump;
import com.donutsmp.trader.gui.TraderHud;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import com.donutsmp.trader.market.AhListingManager;
import com.donutsmp.trader.market.AhScreens;
import com.donutsmp.trader.market.AutoRelister;
import com.donutsmp.trader.market.Undercut;
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

    private static DonutTraderMod INSTANCE;

    private DonutAuctionClient apiClient;
    private AhListingManager listingManager;
    private AutoRelister autoRelister;
    private TraderConfig config;
    private ScheduledExecutorService backgroundExecutor;

    private volatile double apiPrice = 35000.0;
    private volatile double scanPrice = -1;
    private volatile long scanPriceAt = 0;

    private KeyMapping toggleKey;
    private String screenKey = null;
    private int screenTicks = 0;
    private boolean screenHandled = false;
    private long marketRequestedAt = 0;
    private long nextMarketScanAt = 0;
    private int marketFailures = 0;
    private int lastSyncedListings = -1;
    private long verifyAt = 0;
    private int verifySlot = -1;
    private int verifyCount = 0;
    private long lastActionTime = 0;
    private long lastCommandTime = 0;
    private long lastWarningTime = 0;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        this.config = TraderConfig.get();
        this.config.enabled = false; // Oyuna her girildiğinde daima PASİF başlar
        this.config.save();
        this.apiClient = new DonutAuctionClient();
        this.listingManager = new AhListingManager(config.maxSlots);
        this.autoRelister = new AutoRelister(apiClient, config.minPriceFloor, config.undercutPercent);
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
                marketFailures = 0;
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
        marketFailures++;
        LOGGER.warn("[DonutSMP Trader] Piyasa ekrani acilmadi ({}. deneme): /{}",
                marketFailures, String.format(config.marketCommand, config.targetItem));

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

        nextMarketScanAt = now + scanIntervalMs();
        marketRequestedAt = now;
        client.player.connection.sendCommand(String.format(config.marketCommand, config.targetItem));
        LOGGER.info("[DonutSMP Trader] Piyasa soruldu: /{}", String.format(config.marketCommand, config.targetItem));
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

        listingManager.syncActiveListings(counted);
        if (counted != lastSyncedListings) {
            lastSyncedListings = counted;
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§6[DonutTrader] §eAktif ilanlarınız okundu: §f%d/%d",
                    listingManager.getActiveListings(), listingManager.getMaxSlots())));
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
        double lowestCompetitor = Double.MAX_VALUE;

        int scanned = Math.min(45, slots.size());
        for (int i = 0; i < scanned; i++) {
            ItemStack stack = slots.get(i).getItem();
            if (stack.isEmpty() || !InventoryActionHelper.idOf(stack).equals(target)) continue;

            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore == null) continue;

            for (Component line : lore.lines()) {
                double parsed = AhPriceParser.parsePrice(line.getString());
                if (parsed > 0 && parsed < lowestCompetitor) {
                    lowestCompetitor = parsed;
                }
            }
        }

        if (lowestCompetitor == Double.MAX_VALUE || lowestCompetitor <= config.minPriceFloor) return;

        double newOptimal = Undercut.target(lowestCompetitor, config.undercutPercent, config.minPriceFloor);
        double previous = effectivePrice();
        scanPrice = newOptimal;
        scanPriceAt = System.currentTimeMillis();

        LOGGER.info("[DonutSMP Trader] Piyasa: en ucuz rakip {} -> hedef {}", lowestCompetitor, newOptimal);
        if (Math.abs(newOptimal - previous) >= Math.max(1.0, previous * 0.01)) {
            client.player.sendSystemMessage(Component.literal(String.format(
                    "§6[DonutTrader] §aPiyasa tarandı! En ucuz rakip: §e$%,.0f §a-> Yeni satış hedefimiz: §6$%,.0f",
                    lowestCompetitor, newOptimal)));
        }
    }

    private void onClientTick(Minecraft client) {
        if (toggleKey != null && toggleKey.consumeClick()) {
            toggleEnabled();
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("§6[DonutTrader] §eMod Durumu: " + (config.enabled ? "§a[AKTİF]" : "§c[PASİF]")));
            }
        }

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
        if (!config.enabled || client.player == null || client.getConnection() == null) return;

        // Sanal tıklamalar oyuncunun kendi envanter menüsüne gider; başka bir
        // konteyner açıksa slot numaraları bambaşka bir şeye denk gelir.
        if (client.player.containerMenu != client.player.inventoryMenu) return;

        long now = System.currentTimeMillis();

        if (listingManager.isInCombat()) {
            if (now - lastWarningTime > 6000) {
                client.player.sendSystemMessage(Component.literal("§6[DonutTrader] §eSavaş modu (Combat Tag) aktif, işlem 20 saniye duraklatıldı."));
                lastWarningTime = now;
            }
            return;
        }

        if (verifyAt > 0) {
            if (now < verifyAt) return;
            verifyListing(client);
        }

        // Piyasa fiyatı istenir ama satış ona bağlanmaz: tarama tutmadığında
        // mod hiç satmaz hâle geliyordu. API fiyatı ve taban fiyat zaten var.
        requestMarketScan(client, now);
        if (marketRequestedAt > 0 && !scanFresh()) return; // elde fiyat yoksa cevabı bekle

        if (now - lastCommandTime < COMMAND_COOLDOWN_MS || now - lastActionTime < Math.max(120, config.clickDelayMs)) return;
        if (!listingManager.canListMore()) return;

        // Sunucu havadayken /ah sell kabul etmiyor; gondermeden once yere in.
        if (!client.player.onGround()) {
            warn(client, now, "§6[DonutTrader] §eHavadasınız, satış için yere inilmesi bekleniyor.");
            return;
        }

        String target = DonutAuctionClient.normalizeItemName(config.targetItem);
        int selected = client.player.getInventory().getSelectedSlot();
        ItemStack held = client.player.getInventory().getSelectedItem();
        boolean handReady = InventoryActionHelper.idOf(held).equals(target) && held.getCount() == config.lotSize;

        if (!handReady) {
            int emptyHotbar = InventoryActionHelper.findEmptyHotbarIndex(client.player);
            if (emptyHotbar == -1) {
                warn(client, now, "§6[DonutTrader] §cHotbar'ınızda en az 1 boş slot bırakınız!");
                return;
            }

            int destination = InventoryActionHelper.HOTBAR_MENU_START + emptyHotbar;
            int sourceSlot = InventoryActionHelper.findTargetSlot(client.player, target, config.lotSize, destination);
            if (sourceSlot == -1) {
                warn(client, now, "§6[DonutTrader] §cEnvanterinizde satılacak " + config.lotSize + "x " + config.targetItem + " kalmadı!");
                return;
            }

            InventoryActionHelper.splitToHotbar(client, sourceSlot, emptyHotbar, config.lotSize);
            client.player.getInventory().setSelectedSlot(emptyHotbar);
            lastActionTime = now;
            return;
        }

        long sellPrice = (long) effectivePrice();
        if (sellPrice <= 0) return;

        if (config.simulationMode) {
            warn(client, now, String.format("§6[DonutTrader] §e[SİMÜLASYON] /ah sell %d gönderilmedi (slot %d).", sellPrice, selected));
            lastCommandTime = now;
            lastActionTime = now;
            return;
        }

        client.player.connection.sendCommand("ah sell " + sellPrice);
        listingManager.onListingSent();
        verifyAt = now + VERIFY_AFTER_MS;
        verifySlot = selected;
        verifyCount = held.getCount();
        lastCommandTime = now;
        lastActionTime = now;
        LOGGER.info("[DonutSMP Trader] /ah sell {} gonderildi! (Aktif: {}/{})",
                sellPrice, listingManager.getActiveListings(), listingManager.getMaxSlots());
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
            listingManager.onListingRejected();
            LOGGER.info("[DonutSMP Trader] Ilan girmedi, esya elde kaldi. Aktif: {}/{}",
                    listingManager.getActiveListings(), listingManager.getMaxSlots());
            warn(client, System.currentTimeMillis(),
                    "§6[DonutTrader] §eSatış gerçekleşmedi, eşya elinizde kaldı. Slot sayacı geri alındı.");
        } else {
            listingManager.onListingVerified();
        }
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
    }

    public void tickMarketLogic() {
        try {
            this.apiPrice = apiClient.calculateOptimalSellPrice(
                    config.targetItem,
                    config.lotSize,
                    config.fallbackPrice,
                    config.minPriceFloor,
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
        if (!config.autoUndercut) {
            return Math.max(config.fallbackPrice, config.minPriceFloor);
        }
        double price = scanFresh() ? scanPrice : apiPrice;
        return Math.max(price, config.minPriceFloor);
    }

    /** Fiyatın nereden geldiği; "undercut çalışmıyor" şikayetini teşhis etmek için. */
    public String priceSource() {
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
        return Math.max(15, config.scanIntervalSeconds) * 1000L;
    }

    private boolean scanFresh() {
        return scanPrice > 0 && System.currentTimeMillis() - scanPriceAt < scanTtlMs();
    }

    /** Hedef ya da lot değişince eski taramanın fiyatı artık bu eşyaya ait değildir. */
    public void invalidateScan() {
        scanPrice = -1;
        scanPriceAt = 0;
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
        return TraderHud.getHudLines(config, listingManager, effectivePrice());
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
