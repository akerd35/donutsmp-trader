package com.donutsmp.trader.team;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamLinkTest {

    private static final long STALE_MS = 90_000;

    private static PeerState state(String name, long at) {
        return new PeerState(name, at, true, "ladder", 1, 9000, 64, 3, 4, 18);
    }

    @Test
    void aPublishedStateComesBackToTheOtherPlayer(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        TeamLink.publish(dir, state("Kaan", now));

        List<PeerState> peers = TeamLink.read(dir, "sinlech", now, STALE_MS);
        assertEquals(1, peers.size());
        assertEquals("Kaan", peers.get(0).name());
        assertEquals(9000, peers.get(0).price());
        assertEquals(64, peers.get(0).itemsLeft());
        assertEquals(3, peers.get(0).freeHotbarSlots());
    }

    @Test
    void weDoNotReadOurselves(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        TeamLink.publish(dir, state("sinlech", now));
        assertTrue(TeamLink.read(dir, "sinlech", now, STALE_MS).isEmpty());
    }

    @Test
    void aPlayerWhoLoggedOffStopsBeingLive(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        TeamLink.publish(dir, state("Kaan", now - 10 * 60_000));
        assertTrue(TeamLink.read(dir, "sinlech", now, STALE_MS).isEmpty());
    }

    /** Ilanlari duruyor: adi listede kalmali ki fiyati kirilmasin. */
    @Test
    void butTheirNameSurvivesForTheUndercutRule(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        TeamLink.publish(dir, state("Kaan", now - 10 * 60_000));
        assertEquals(List.of("Kaan"), TeamLink.names(dir, "sinlech"));
    }

    @Test
    void publishingTwiceLeavesOneFile(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        TeamLink.publish(dir, state("Kaan", now));
        TeamLink.publish(dir, state("Kaan", now + 1));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count(), "gecici dosya birakilmamali");
        }
    }

    @Test
    void junkInTheFolderIsIgnored(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        Files.writeString(dir.resolve("notes.txt"), "merhaba");
        Files.writeString(dir.resolve("broken.json"), "{ this is not json");
        Files.writeString(dir.resolve("empty.json"), "null");
        TeamLink.publish(dir, state("Kaan", now));

        List<PeerState> peers = TeamLink.read(dir, "sinlech", now, STALE_MS);
        assertEquals(1, peers.size());
        assertEquals("Kaan", peers.get(0).name());
    }

    @Test
    void anOversizedFileIsNotRead(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        Files.write(dir.resolve("Kaan.json"), "x".repeat(200_000).getBytes(StandardCharsets.UTF_8));
        assertTrue(TeamLink.read(dir, "sinlech", now, STALE_MS).isEmpty());
    }

    @Test
    void remoteFieldsAreSanitisedOnTheWayIn(@TempDir Path dir) throws Exception {
        long now = System.currentTimeMillis();
        Files.writeString(dir.resolve("Kaan.json"),
                "{\"name\":\"§cKaan\",\"updatedAt\":" + now
                        + ",\"enabled\":true,\"item\":\"ladder\",\"lotSize\":9999,"
                        + "\"price\":9000,\"itemsLeft\":-4,\"freeHotbarSlots\":50,"
                        + "\"activeListings\":4,\"maxSlots\":18}");

        List<PeerState> peers = TeamLink.read(dir, "sinlech", now, STALE_MS);
        assertEquals(1, peers.size());
        assertFalse(peers.get(0).name().contains("§"));
        assertEquals(64, peers.get(0).lotSize());
        assertEquals(0, peers.get(0).itemsLeft());
        assertEquals(9, peers.get(0).freeHotbarSlots());
    }

    @Test
    void aMissingFolderIsNotAnError() {
        Path nowhere = Path.of("/tmp/donut-trader-does-not-exist-" + System.nanoTime());
        assertTrue(TeamLink.read(nowhere, "sinlech", System.currentTimeMillis(), STALE_MS).isEmpty());
        assertTrue(TeamLink.names(nowhere, "sinlech").isEmpty());
    }

    @Test
    void aNameCannotEscapeTheFolder() {
        assertEquals("evil.json", TeamLink.fileName("../evil"));
        assertEquals("ab.json", TeamLink.fileName("a/b"));
        assertEquals("", TeamLink.fileName("../"));
        assertEquals("", TeamLink.fileName(null));
    }

    @Test
    void homeIsExpanded() {
        Path resolved = TeamLink.resolve("~/Dropbox/donuttrader");
        assertTrue(resolved.isAbsolute());
        assertFalse(resolved.toString().startsWith("~"));
    }

    @Test
    void anEmptyFolderSettingMeansOff() {
        assertEquals(null, TeamLink.resolve(""));
        assertEquals(null, TeamLink.resolve(null));
    }
}
