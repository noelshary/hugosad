package net.hugosmp.auction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

/** Zustand + Logik einer Auktion. Läuft komplett im Client. */
public final class AuctionManager {
    public record Bid(String player, double amount) {}
    public record Event(String title, String detail, int color, long at) {}
    public record Pay(String player, double paid, double total, boolean valid, long at) {}
    /** Ergebnis der letzten Auktion (für J-Leaderboard nach dem Ende). */
    public record Snapshot(ItemStack item, String winner, double amount, boolean buyout, List<Bid> top,
                           double received, int payments, int bidders, long endedAt, long totalMs) {
        boolean hasWinner() { return winner != null; }
    }

    static final int EVENT_MS = 4500;
    static final int C_GOOD = 0xFF4ADE80, C_BAD = 0xFFF87171, C_GOLD = 0xFFFFC857, C_INFO = 0xFF93C5FD;

    private boolean running;
    private long startMs, endMs, totalMs;
    private ItemStack item = ItemStack.EMPTY;
    private double minBid, buyout;
    private final Map<String, Double> totals = new LinkedHashMap<>();
    private final Map<String, Long> since = new HashMap<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Pay> history = new ArrayList<>();
    private int payCount;
    private double totalReceived, prevTop, curTop;
    private long topChangedMs, lastBidMs;
    private Snapshot last;
    private int lastBeepSec = -1;

    static AuctionConfig cfg() { return HugoAuctionClient.CONFIG; }

    // ---- Getter ----
    public boolean isRunning() { return running; }
    public long startedAt() { return startMs; }
    public long totalMs() { return totalMs; }
    public long remainingMs() { return running ? Math.max(0, endMs - System.currentTimeMillis()) : 0; }
    public ItemStack item() { return item; }
    public double minBid() { return minBid; }
    public double buyout() { return buyout; }
    public long lastBidAt() { return lastBidMs; }
    public int paymentCount() { return payCount; }
    public double totalReceived() { return totalReceived; }
    public int bidderCount() { return totals.size(); }
    public Snapshot last() { return last; }
    public Pay lastPay() { return history.isEmpty() ? null : history.get(0); }

    /** Ergebnis noch im J-Fenster (cfg.resultSeconds nach Ende)? */
    public boolean hasFreshResult(long now) {
        return !running && last != null && now - last.endedAt() <= cfg().resultSeconds * 1000L;
    }
    public long resultLeftMs(long now) {
        return last == null ? 0 : Math.max(0, cfg().resultSeconds * 1000L - (now - last.endedAt()));
    }

    public List<Bid> top(int n) {
        List<Bid> list = new ArrayList<>();
        for (Map.Entry<String, Double> e : totals.entrySet())
            if (e.getValue() >= minBid) list.add(new Bid(e.getKey(), e.getValue()));
        list.sort(Comparator.<Bid>comparingDouble(Bid::amount).reversed()
            .thenComparingLong(b -> since.getOrDefault(b.player(), 0L)));
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    /** Höchstgebot mit Zähl-Animation. */
    public double shownTop(long now) {
        double t = Gfx.ease((now - topChangedMs) / 700.0);
        return prevTop + (curTop - prevTop) * t;
    }

    public List<Event> recentEvents(long now, int max) {
        List<Event> out = new ArrayList<>();
        for (int i = events.size() - 1; i >= 0 && out.size() < max; i--)
            if (now - events.get(i).at() <= EVENT_MS) out.add(events.get(i));
        return out;
    }

    // ---- Steuerung ----
    public boolean start(ItemStack stack, int seconds, double min, double buy) {
        if (running || stack == null || stack.isEmpty()) return false;
        long now = System.currentTimeMillis();
        item = stack.copy();
        minBid = Math.max(0, min);
        buyout = Math.max(0, buy);
        totals.clear(); since.clear(); events.clear(); history.clear();
        payCount = 0; totalReceived = 0; prevTop = 0; curTop = 0; topChangedMs = 0; lastBidMs = 0;
        last = null; lastBeepSec = -1;
        startMs = now; totalMs = seconds * 1000L; endMs = now + totalMs;
        running = true;
        push("Auktion gestartet", item.getHoverName().getString(), C_GOLD);
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 0.7f);
        if (cfg().announceInChat) {
            String msg = "Auktion: " + item.getHoverName().getString() + " | Mindestgebot " + Fmt.cash(minBid)
                + (buyout > 0 ? " | Sofortkauf " + Fmt.cash(buyout) : "")
                + " | Dauer " + Fmt.time(totalMs) + " | Bieten: /pay " + cfg().payName;
            say(msg);
        }
        return true;
    }

    public void cancel() {
        if (!running) return;
        running = false;
        last = null;
        sound(SoundEvents.VILLAGER_NO, 1f, 0.8f);
    }

    public void addTime(int seconds) {
        if (!running) return;
        endMs += seconds * 1000L;
        totalMs += seconds * 1000L;
        push("Zeit verlängert", "+" + Fmt.time(seconds * 1000L), C_INFO);
        sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.7f);
    }

    public void tick() {
        if (!running) return;
        long rem = endMs - System.currentTimeMillis();
        if (rem <= 0) { finishByTime(); return; }
        int sec = (int) Math.ceil(rem / 1000.0);
        if (cfg().sounds && sec <= 5 && sec != lastBeepSec) {
            lastBeepSec = sec;
            sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f + (5 - sec) * 0.12f, 0.6f);
        }
    }

    // ---- Zahlungen ----
    public void onMessage(String raw) {
        if (!running) return;
        Pattern p = cfg().pattern;
        if (p == null) return;
        String player;
        double amt;
        try {
            Matcher m = p.matcher(Fmt.clean(raw));
            if (!m.matches()) return;
            player = m.group("player").trim();
            amt = Fmt.parse(m.group("amount"));
        } catch (RuntimeException e) {
            return;
        }
        if (player.isEmpty() || Double.isNaN(amt) || amt <= 0) return;

        long now = System.currentTimeMillis();
        double old = totals.getOrDefault(player, 0.0);
        double total = cfg().cumulative ? old + amt : Math.max(old, amt);
        String oldLeader = leaderName();
        totals.put(player, total);
        since.put(player, now);
        payCount++;
        totalReceived += amt;
        lastBidMs = now;
        boolean valid = total >= minBid;
        history.add(0, new Pay(player, amt, total, valid, now));
        if (history.size() > 40) history.remove(history.size() - 1);

        String shown = Fmt.display(player);
        if (!valid) {
            push("Zu niedrig", shown + "  " + Fmt.cash(total) + " / min. " + Fmt.cash(minBid), C_BAD);
            sound(SoundEvents.VILLAGER_NO, 1.1f, 0.6f);
            return;
        }
        if (buyout > 0 && total >= buyout) { finish(player, total, true); return; }

        List<Bid> t = top(1);
        Bid lead = t.get(0);
        prevTop = shownTop(now);
        curTop = lead.amount();
        topChangedMs = now;
        if (!lead.player().equals(oldLeader)) {
            push("Neuer Führender", Fmt.display(lead.player()) + "  " + Fmt.cash(lead.amount()), C_GOLD);
            sound(SoundEvents.PLAYER_LEVELUP, 1.3f, 0.6f);
        } else {
            push("Gebot erhöht", shown + "  " + Fmt.cash(total), C_GOOD);
            sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 0.7f);
        }
        int snipe = cfg().antiSnipeSec;
        if (snipe > 0 && endMs - now < snipe * 1000L) {
            long newEnd = now + snipe * 1000L;
            totalMs += newEnd - endMs;
            endMs = newEnd;
            push("Anti-Snipe", "Zeit auf " + snipe + "s zurückgesetzt", C_INFO);
        }
    }

    private String leaderName() {
        List<Bid> t = top(1);
        return t.isEmpty() ? null : t.get(0).player();
    }

    private void finishByTime() {
        List<Bid> t = top(1);
        if (t.isEmpty()) finish(null, 0, false);
        else finish(t.get(0).player(), t.get(0).amount(), false);
    }

    private void finish(String winner, double amount, boolean byBuyout) {
        long now = System.currentTimeMillis();
        running = false;
        List<Bid> ranking = top(10);
        if (byBuyout && winner != null) {
            // Sofortkauf-Gewinner steht sicher ganz oben
            ranking.removeIf(b -> b.player().equals(winner));
            ranking.add(0, new Bid(winner, amount));
        }
        last = new Snapshot(item.copy(), winner, amount, byBuyout, ranking, totalReceived, payCount,
            totals.size(), now, totalMs);
        if (winner == null) {
            push("Beendet", "Keine Gebote", C_BAD);
            sound(SoundEvents.VILLAGER_NO, 1f, 0.7f);
        } else {
            push(byBuyout ? "SOFORTKAUF!" : "Beendet!", Fmt.display(winner) + "  " + Fmt.cash(amount), C_GOLD);
            fanfare();
        }
        if (cfg().announceInChat) {
            say(winner == null ? "Auktion beendet: keine Gebote."
                : "Auktion beendet! Gewinner: " + Fmt.display(winner) + " mit " + Fmt.cash(amount));
        }
    }

    // ---- Helfer ----
    private void push(String title, String detail, int color) {
        events.add(new Event(title, detail, color, System.currentTimeMillis()));
        if (events.size() > 30) events.remove(0);
    }

    private static void say(String msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.connection != null) mc.player.connection.sendChat(msg);
    }

    static void sound(SoundEvent s, float pitch, float vol) {
        if (!cfg().sounds) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    private static void fanfare() {
        sound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.9f);
        sound(SoundEvents.PLAYER_LEVELUP, 0.9f, 0.8f);
        sound(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f);
    }

    static String namesCsv(List<Bid> bids) {
        StringBuilder sb = new StringBuilder();
        for (Bid b : bids) { if (sb.length() > 0) sb.append(", "); sb.append(b.player()); }
        return sb.toString();
    }
}
