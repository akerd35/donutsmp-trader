package com.donutsmp.trader.team;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamTest {

    @Test
    void acceptsMinecraftNames() {
        assertTrue(Team.validName("sinlech"));
        assertTrue(Team.validName("Kaan_42"));
        assertTrue(Team.validName("abc"));
    }

    @Test
    void rejectsNamesTooShortToSearchLoreWith() {
        assertFalse(Team.validName("ah"));
        assertFalse(Team.validName(""));
        assertFalse(Team.validName(null));
        assertFalse(Team.validName("a".repeat(17)));
    }

    @Test
    void rejectsNamesThatCouldEscapeAFilePath() {
        assertFalse(Team.validName("../evil"));
        assertFalse(Team.validName("a/b"));
        assertFalse(Team.validName("with space"));
    }

    @Test
    void addingIsIdempotentAndCaseInsensitive() {
        List<String> members = new ArrayList<>();
        assertTrue(Team.add(members, "Kaan"));
        assertFalse(Team.add(members, "kaan"));
        assertEquals(List.of("Kaan"), members);
    }

    @Test
    void addingIsBounded() {
        List<String> members = new ArrayList<>();
        for (int i = 0; i < 40; i++) Team.add(members, "player" + i);
        assertTrue(members.size() <= 16);
    }

    @Test
    void removeIsCaseInsensitive() {
        List<String> members = new ArrayList<>(List.of("Kaan"));
        assertTrue(Team.remove(members, "kaan"));
        assertTrue(members.isEmpty());
    }

    @Test
    void ourNamesCarriesSelfAndTeamInLowerCase() {
        Set<String> names = Team.ourNames(List.of("Kaan"), "SinLech");
        assertEquals(Set.of("sinlech", "kaan"), names);
    }

    /**
     * Arka plandaki paylasim gorevi listeye yazarken oyun donguSu ayni listeyi
     * geziyor. Calisma zamanindaki tip kopyalayarak yazan liste; duz bir
     * ArrayList burada ConcurrentModificationException atardi.
     */
    @Test
    void theListSurvivesBeingWrittenWhileItIsRead() throws Exception {
        List<String> members = new CopyOnWriteArrayList<>(List.of("Kaan"));
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            for (int i = 0; i < 2000; i++) {
                Team.add(members, "player" + i);
                Team.remove(members, "player" + i);
            }
        });
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 2000; i++) Team.ourNames(members, "sinlech");
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        writer.start();
        reader.start();
        writer.join(10_000);
        reader.join(10_000);
        assertNull(failure.get(), "okuma sirasinda yazma patlamamali");
    }

    @Test
    void concurrentAddsNeverDuplicateAName() throws Exception {
        List<String> members = new CopyOnWriteArrayList<>();
        Thread[] threads = new Thread[4];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int n = 0; n < 500; n++) Team.add(members, "Kaan");
            });
            threads[i].start();
        }
        for (Thread thread : threads) thread.join(10_000);
        assertEquals(1, members.size(), "ayni ad iki kez girmemeli");
    }

    @Test
    void ourNamesDropsAnythingTooShortToMatchSafely() {
        // Iki harflik bir ad her ilanda gecer; hepsini bizim sanip rakip goremezdik.
        assertEquals(Set.of("sinlech"), Team.ourNames(List.of("ab", ""), "sinlech"));
        assertTrue(Team.ourNames(List.of(), "").isEmpty());
        assertTrue(Team.ourNames(null, null).isEmpty());
    }
}
