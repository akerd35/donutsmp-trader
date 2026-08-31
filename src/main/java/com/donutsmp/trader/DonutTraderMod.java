package com.donutsmp.trader;

import com.donutsmp.trader.api.AhPriceParser;
import com.donutsmp.trader.api.DonutAuctionClient;
import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.gui.TraderCommands;
import com.donutsmp.trader.gui.TraderHud;
import com.donutsmp.trader.inventory.InventoryActionHelper;
import com.donutsmp.trader.market.AhListingManager;
import com.donutsmp.trader.market.AutoRelister;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    private static DonutTraderMod INSTANCE;

    private DonutAuctionClient apiClient;
    private AhListingManager listingManager;
    private AutoRelister autoRelister;
    private TraderConfig config;
    private ScheduledExecutorService backgroundExecutor;
    private double currentRecommendedPrice = 35000.0;

    private KeyMapping toggleKey;
    private boolean isScreenOpen = false;
    private long lastActionTime = 0;
    private long lastCommandTime = 0;
    private long lastWarningTime = 0;
    private static final long COMMAND_COOLDOWN_MS = 1400; // Guvenli anti-spam araligi

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        this.config = TraderConfig.get();
        this.config.enabled = false; // Oyuna her girildiÄŸinde DAÄ°MA PASÄ°F baÅŸlar (GÃ¼venlik KorumasÄ±)
        this.apiClient = new DonutAuctionClient();
        this.listingManager = new AhListingManager(config.maxSlots);
        this.autoRelister = new AutoRelister(apiClient, config.minPriceFloor, 100.0);
        this.currentRecommendedPrice = config.fallbackPrice;

        LOGGER.info("[DonutSMP Trader] Mod baslatiliyor... Hedef: {} (Lot: {}x, Limit: {} slot)",
                config.targetItem, config.lotSize, config.maxSlots);

        // 1. K / Ozel Tus Atamasi Kaydi
        try {
            this.toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.donutsmp_trader.toggle",
                    InputConstants.Type.KEYSYM,
                    75, // Varsayilan: 'K'
                    KeyMapping.Category.MISC
            ));
            LOGGER.info("[DonutSMP Trader] Tus atamasi basariyla kaydedildi!");
        } catch (Throwable t) {
            LOGGER.warn("KeyMapping kaydedilemedi: {}", t.getMessage());
        }

        // 2. Oyun Ici Komutlari Kaydet (/trader ve /dtrader)
        try {
            ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
                TraderCommands.register(dispatcher);
                LOGGER.info("[DonutSMP Trader] /trader komutlari basariyla kaydedildi!");
            });
        } catch (Throwable t) {
            LOGGER.warn("ClientCommandRegistrationCallback kaydedilemedi: {}", t.getMessage());
        }

        // 3. Canli Chat Mesajlarini Dinle (Satis, Limit ve Savas bildirimleri)
        try {
            ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {
                if (message != null) {
                    handleChatMessage(message.getString());
                }
            });
            ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
                if (message != null) {
                    handleChatMessage(message.getString());
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("Chat dinleyici kaydedilemedi: {}", t.getMessage());
        }

        // 4. GUI Ekran Dinleyicisi (Chat/ESC Duraklatma, CanlÄ± /ah Fiyat TaramasÄ±)
        try {
            ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
                this.isScreenOpen = true;
                ScreenEvents.remove(screen).register(s -> this.isScreenOpen = false);

                if (client.player == null) return;

                if (screen instanceof AbstractContainerScreen<?> containerScreen) {
                    AbstractContainerMenu menu = containerScreen.getMenu();
                    if (menu == null) return;

                    List<Slot> slots = menu.slots;
                    String target = DonutAuctionClient.normalizeItemName(config.targetItem);
                    double lowestCompetitor = Double.MAX_VALUE;

                    // /ah pazarÄ±ndaki eÅŸyalarÄ±n lore/fiyatlarÄ±nÄ± tara
                    int maxSlots = Math.min(45, slots.size());
                    for (int i = 0; i < maxSlots; i++) {
                        ItemStack stack = slots.get(i).getItem();
                        if (!stack.isEmpty()) {
                            String name = DonutAuctionClient.normalizeItemName(stack.getItem().toString());
                            if (name.contains(target)) {
                                ItemLore lore = stack.get(DataComponents.LORE);
                                if (lore != null) {
                                    for (Component line : lore.lines()) {
                                        double parsed = AhPriceParser.parsePrice(line.getString());
                                        if (parsed > 0 && parsed < lowestCompetitor) {
                                            lowestCompetitor = parsed;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // EÄŸer piyasada rakipten daha ucuz fiyat bulunduysa otomatik -1$ altÄ±na kÄ±r
                    if (lowestCompetitor < Double.MAX_VALUE && lowestCompetitor > config.minPriceFloor) {
                        double newOptimal = Math.max(config.minPriceFloor, lowestCompetitor - 1.0);
                        if (newOptimal < currentRecommendedPrice) {
                            currentRecommendedPrice = newOptimal;
                            config.fallbackPrice = newOptimal;
                            config.save();
                            client.player.sendSystemMessage(Component.literal(String.format(
                                    "Â§6[DonutTrader] Â§aPiyasa tarandÄ±! En ucuz rakip: Â§e$%,.0f Â§a-> Yeni satÄ±ÅŸ hedefimiz: Â§6$%,.0f",
                                    lowestCompetitor, newOptimal)));
                        }
                    }
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("ScreenEvents kaydedilemedi: {}", t.getMessage());
        }

        // 5. Client Tick Olaylarini Dinle (Savas / Combat Duraklatma & Otonom Satis)
        try {
            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        } catch (Throwable t) {
            LOGGER.warn("ClientTickEvents kaydedilemedi: {}", t.getMessage());
        }

        startBackgroundService();
    }

    private void onClientTick(Minecraft client) {
        // TuÅŸa BasÄ±ldÄ± mÄ±?
        if (toggleKey != null && toggleKey.consumeClick()) {
            toggleEnabled();
            if (client.player != null) {
                client.player.sendSystemMessage(Component.literal("Â§6[DonutTrader] Â§eMod Durumu: " + (config.enabled ? "Â§a[AKTÄ°F]" : "Â§c[PASÄ°F]")));
            }
        }

        // KURAL 1: Chat, ESC (Pause MenÃ¼sÃ¼), Envanter veya herhangi bir ekran aÃ§Ä±kken MOD Ä°ÅLEM YAPMAZ
        if (isScreenOpen || client.isPaused()) {
            return;
        }

        if (!config.enabled || client.player == null || client.getConnection() == null) {
            return;
        }

        long now = System.currentTimeMillis();

        // KURAL 2: Oyuncu SavaÅŸta (Combat Tag) Ä°ken Kesinlikle Komut GÃ¶nderme
        if (listingManager.isInCombat()) {
            if (now - lastWarningTime > 6000) {
                client.player.sendSystemMessage(Component.literal("Â§6[DonutTrader] Â§eSavaÅŸ modu (Combat Tag) aktif, iÅŸlem 20 saniye duraklatÄ±ldÄ±."));
                lastWarningTime = now;
            }
            return;
        }

        // KURAL 3: GÃ¼venli Anti-Spam Bekleme SÃ¼resi (En az 1.4 saniye)
        if (now - lastCommandTime < COMMAND_COOLDOWN_MS || now - lastActionTime < 350) {
            return;
        }

        // KURAL 4: Limit Doluysa Komut GÃ¶nderme
        if (!listingManager.canListMore()) {
            return;
        }

        String target = DonutAuctionClient.normalizeItemName(config.targetItem);

        // 1. Oyuncunun elindeki eÅŸyayÄ± kontrol et
        ItemStack currentHand = client.player.getInventory().getSelectedItem();
        boolean handReady = false;

        if (!currentHand.isEmpty()) {
            String handName = DonutAuctionClient.normalizeItemName(currentHand.getItem().toString());
            if (handName.contains(target) && currentHand.getCount() == config.lotSize) {
                handReady = true;
            }
        }

        // 2. Elinde 1x yoksa, envanterden 1x ayÄ±rÄ±p boÅŸ hotbar slotuna al
        if (!handReady) {
            int emptyHotbar = InventoryActionHelper.findEmptyHotbarIndex(client.player);
            if (emptyHotbar == -1) {
                if (now - lastWarningTime > 5000) {
                    client.player.sendSystemMessage(Component.literal("Â§6[DonutTrader] Â§cHotbar'Ä±nÄ±zda en az 1 boÅŸ slot bÄ±rakÄ±nÄ±z!"));
                    lastWarningTime = now;
                }
                return;
            }

            int sourceSlot = InventoryActionHelper.findTargetSlot(client.player, target);
            if (sourceSlot == -1) {
                if (now - lastWarningTime > 5000) {
                    client.player.sendSystemMessage(Component.literal("Â§6[DonutTrader] Â§cEnvanterinizde satÄ±lacak " + config.targetItem + " kalmadÄ±!"));
                    lastWarningTime = now;
                }
                return;
            }

            // 64'lÃ¼k yÄ±ÄŸÄ±ndan 1 adet ayÄ±rÄ±p boÅŸ hotbar slotuna koy
            InventoryActionHelper.splitToHotbar(client, sourceSlot, emptyHotbar, config.lotSize);
            client.player.getInventory().setSelectedSlot(emptyHotbar);
            lastActionTime = now;
            return;
        }

        // 3. Elinde artÄ±k tam olarak 1x var -> GÃ¼venli aralÄ±kla /ah sell gÃ¶nder
        long sellPrice = (long) currentRecommendedPrice;
        if (sellPrice > 0) {
            client.player.connection.sendCommand("ah sell " + sellPrice);
            listingManager.onListingAttempt();
            lastCommandTime = now;
            lastActionTime = now;
            LOGGER.info("[DonutSMP Trader] /ah sell {} gÃ¶nderildi! (Hedef: $%.0f, Aktif: {}/{})",
                    sellPrice, currentRecommendedPrice, listingManager.getActiveListings(), listingManager.getMaxSlots());
        }
    }

    private void startBackgroundService() {
        this.backgroundExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DonutTrader-Worker");
            t.setDaemon(true);
            return t;
        });

        // Her 2 saniyede bir piyasa fiyatÄ±nÄ± kontrol et
        this.backgroundExecutor.scheduleWithFixedDelay(this::tickMarketLogic, 1, 2, TimeUnit.SECONDS);
    }

    public void tickMarketLogic() {
        try {
            double optimalPrice = apiClient.calculateOptimalSellPrice(
                    config.targetItem,
                    config.lotSize,
                    config.fallbackPrice,
                    config.minPriceFloor
            );
            this.currentRecommendedPrice = optimalPrice;
        } catch (Exception e) {
            LOGGER.error("Market logic tick hatasÄ±: {}", e.getMessage());
        }
    }

    public void toggleEnabled() {
        config.enabled = !config.enabled;
        config.save();
        LOGGER.info("[DonutSMP Trader] Mod Durumu: {}", config.enabled ? "AKTÄ°F" : "PASÄ°F");
    }

    public void handleChatMessage(String rawMessage) {
        listingManager.onChatMessage(rawMessage);
    }

    public List<String> getHudInfo() {
        return TraderHud.getHudLines(config, listingManager, currentRecommendedPrice);
    }

    public String getKeyName() {
        if (toggleKey != null) {
            return toggleKey.getTranslatedKeyMessage().getString();
        }
        return "K";
    }

    public static DonutTraderMod getInstance() {
        return INSTANCE;
    }

    public DonutAuctionClient getApiClient() { return apiClient; }
    public AhListingManager getListingManager() { return listingManager; }
    public AutoRelister getAutoRelister() { return autoRelister; }
    public TraderConfig getConfig() { return config; }
    public double getCurrentRecommendedPrice() { return currentRecommendedPrice; }
}