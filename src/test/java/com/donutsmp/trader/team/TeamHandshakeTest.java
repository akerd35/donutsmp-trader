package com.donutsmp.trader.team;

import com.donutsmp.trader.market.MarketListing;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iki oyuncu, bir paylasilan klasor: parcalar birlikte calisiyor mu?
 *
 * Bir oyuncuyu tek bir alan olarak tutuyoruz — kendi fiyati, envanteri ve
 * gordugu arkadaslar. Tur tur isletince modun gercekte yaptigi sey cikiyor.
 */
class TeamHandshakeTest {

    private static final long STALE_MS = 90_000;
    private static final Set<Long> NO_OWN_PRICES = Set.of();

    /** Tek bir oyuncunun modu. */
    private static final class Player {
        final String name;
        final Path folder;
        final List<String> teammates = new ArrayList<>();
        String item = "ladder";
        int lotSize = 1;
        long basePrice;
        int itemsLeft;
        int freeHotbar;
        List<PeerState> peers = List.of();

        Player(String name, Path folder, long basePrice, int itemsLeft, int freeHotbar) {
            this.name = name;
            this.folder = folder;
            this.basePrice = basePrice;
            this.itemsLeft = itemsLeft;
            this.freeHotbar = freeHotbar;
        }

        /** Modun her uc saniyede yaptigi: kendini yaz, otekileri oku, adlari topla. */
        void sync(long now) throws Exception {
            TeamLink.publish(folder, new PeerState(name, now, true, item, lotSize,
                    basePrice, itemsLeft, freeHotbar, 4, 18));
            List<PeerState> everyone = TeamLink.readAll(folder, name);
            List<PeerState> live = new ArrayList<>();
            for (PeerState peer : everyone) {
                if (peer.fresh(now, STALE_MS)) live.add(peer);
            }
            peers = live;
            for (String other : TeamLink.names(everyone)) Team.add(teammates, other);
        }

        long listedPrice() {
            return (long) TeamPrice.floor(basePrice, item, lotSize, peers);
        }

        PeerState see(String other) {
            return peers.stream().filter(p -> p.name().equals(other)).findFirst().orElse(null);
        }
    }

    @Test
    void eachSeesTheOthersItemCountAndHotbar(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player burak = new Player("Burak", folder, 9000, 128, 2);
        Player kaan = new Player("Kaan", folder, 9500, 7, 0);

        burak.sync(now);
        kaan.sync(now);
        burak.sync(now);

        PeerState seen = burak.see("Kaan");
        assertEquals("ladder", seen.item());
        assertEquals(7, seen.itemsLeft(), "arkadasin kac tane kaldigi gorunmeli");
        assertEquals(0, seen.freeHotbarSlots(), "hotbar dolu: lot ayiramaz");
        assertEquals(9500, seen.price());

        assertEquals(128, kaan.see("Burak").itemsLeft());
        assertEquals(2, kaan.see("Burak").freeHotbarSlots());
    }

    @Test
    void eachAdoptsTheOthersNameFromTheFolder(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player burak = new Player("Burak", folder, 9000, 128, 2);
        Player kaan = new Player("Kaan", folder, 9500, 64, 3);

        burak.sync(now);
        kaan.sync(now);
        burak.sync(now);

        assertEquals(List.of("Kaan"), burak.teammates);
        assertEquals(List.of("Burak"), kaan.teammates);
        assertFalse(burak.teammates.contains("Burak"), "kendimizi listeye almiyoruz");
    }

    /** Asil mesele: klasoru paylasan iki oyuncu birbirinin ilanini kirmamali. */
    @Test
    void neitherUndercutsTheOther(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player burak = new Player("Burak", folder, 8000, 128, 2);
        Player kaan = new Player("Kaan", folder, 9500, 64, 3);

        for (int round = 0; round < 3; round++) {
            burak.sync(now);
            kaan.sync(now);
        }

        assertEquals(9500, burak.listedPrice(), "ucuz olan pahalinin fiyatina cikar");
        assertEquals(9500, kaan.listedPrice());

        // Ekran taramasi da ayni sonucu vermeli: arkadasin ilani rakip degil.
        List<String> kaaninIlani = List.of("§7Seller: §fKaan", "§7Price: §a$9,500");
        assertTrue(MarketListing.competitorPrice(kaaninIlani,
                Team.ourNames(burak.teammates, burak.name), NO_OWN_PRICES) < 0);
    }

    @Test
    void aStrangerIsStillUndercut(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player burak = new Player("Burak", folder, 8000, 128, 2);
        Player kaan = new Player("Kaan", folder, 9500, 64, 3);
        burak.sync(now);
        kaan.sync(now);
        burak.sync(now);

        List<String> yabanci = List.of("§7Seller: §fNotch", "§7Price: §a$7,000");
        assertEquals(7000.0, MarketListing.competitorPrice(yabanci,
                Team.ourNames(burak.teammates, burak.name), NO_OWN_PRICES), 0.001);
    }

    /** Piyasa duserse ikisi birlikte inebilmeli, birbirini yukarida tutmamali. */
    @Test
    void bothFollowTheMarketDown(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player burak = new Player("Burak", folder, 9000, 128, 2);
        Player kaan = new Player("Kaan", folder, 9000, 64, 3);
        burak.sync(now);
        kaan.sync(now);

        burak.basePrice = 6999;   // ikisi de ayni ucuz rakibi gordu
        kaan.basePrice = 6999;
        for (int round = 0; round < 3; round++) {
            burak.sync(now);
            kaan.sync(now);
        }

        assertEquals(6999, burak.listedPrice());
        assertEquals(6999, kaan.listedPrice());
    }

    @Test
    void aFriendWhoLoggedOffStopsSettingOurPriceButKeepsTheirProtection(@TempDir Path folder) throws Exception {
        long now = System.currentTimeMillis();
        Player kaan = new Player("Kaan", folder, 12_000, 64, 3);
        kaan.sync(now - 10 * 60_000);

        Player burak = new Player("Burak", folder, 8000, 128, 2);
        burak.sync(now);

        assertTrue(burak.peers.isEmpty(), "cikmis oyuncu cevrimici gorunmemeli");
        assertEquals(8000, burak.listedPrice(), "cikmis oyuncunun fiyati bizi baglamaz");
        assertEquals(List.of("Kaan"), burak.teammates, "ama ilanlari duruyor, adi listede kalmali");
    }
}
