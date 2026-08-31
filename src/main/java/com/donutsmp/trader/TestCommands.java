package com.donutsmp.trader;

import com.donutsmp.trader.config.TraderConfig;
import com.donutsmp.trader.gui.TraderCommands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestions;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.concurrent.CompletableFuture;

public class TestCommands {
    public static void main(String[] args) throws Exception {
        System.out.println("=== TraderCommands ve Tab Tamamlama Testi Baslatiliyor ===");

        CommandDispatcher<FabricClientCommandSource> dispatcher = new CommandDispatcher<>();
        TraderCommands.register(dispatcher);

        System.out.println("\n[1/3] Root Komutlar Kontrol Ediliyor...");
        assert dispatcher.getRoot().getChild("trader") != null : "/trader bulunamadı!";
        assert dispatcher.getRoot().getChild("dtrader") != null : "/dtrader bulunamadı!";
        System.out.println("  -> /trader ve /dtrader başarıyla kaydedildi!");

        System.out.println("\n[2/3] Tab Tamamlama (Auto-Completion) Testi...");
        // Test tab completion for "/trader "
        CompletableFuture<Suggestions> rootSuggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("trader ", null));
        Suggestions s1 = rootSuggestions.get();
        System.out.println("  -> /trader [TAB] Önerileri: " + s1.getList());
        assert s1.getList().stream().anyMatch(s -> s.getText().equals("item")) : "'item' önerilmeli!";
        assert s1.getList().stream().anyMatch(s -> s.getText().equals("on")) : "'on' önerilmeli!";
        assert s1.getList().stream().anyMatch(s -> s.getText().equals("off")) : "'off' önerilmeli!";
        assert s1.getList().stream().anyMatch(s -> s.getText().equals("lot")) : "'lot' önerilmeli!";

        // Test tab completion for "/trader item "
        CompletableFuture<Suggestions> itemSuggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("trader item ", null));
        Suggestions s2 = itemSuggestions.get();
        System.out.println("  -> /trader item [TAB] Popüler Eşya Önerileri: " + s2.getList());
        assert s2.getList().stream().anyMatch(s -> s.getText().equals("ladder")) : "'ladder' önerilmeli!";
        assert s2.getList().stream().anyMatch(s -> s.getText().equals("water_bucket")) : "'water_bucket' önerilmeli!";
        assert s2.getList().stream().anyMatch(s -> s.getText().equals("totem_of_undying")) : "'totem_of_undying' önerilmeli!";

        // Test tab completion for "/trader item wa" -> should suggest "water_bucket"
        CompletableFuture<Suggestions> partialSuggestions = dispatcher.getCompletionSuggestions(dispatcher.parse("trader item wa", null));
        Suggestions s3 = partialSuggestions.get();
        System.out.println("  -> /trader item wa[TAB] Filtreli Öneriler: " + s3.getList());
        assert s3.getList().stream().anyMatch(s -> s.getText().equals("water_bucket")) : "'water_bucket' önerilmeli!";

        System.out.println("\n[3/3] Config Entegrasyonu Doğrulanıyor...");
        TraderConfig cfg = TraderConfig.get();
        System.out.println("  -> Mevcut Hedef Eşya: " + cfg.targetItem);
        System.out.println("  -> Mevcut Lot Boyutu: " + cfg.lotSize + "x");
        System.out.println("  -> Mevcut Slot Limiti: " + cfg.maxSlots);

        System.out.println("\n=== TUM KOMUT VE TAB TAMAMLAMA TESTLERI BASARIYLA TAMAMLANDI! ===");
    }
}