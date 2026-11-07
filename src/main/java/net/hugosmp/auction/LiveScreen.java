package net.hugosmp.auction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/** Live-Leaderboard und (bis resultSeconds nach Ende) das Ergebnis der letzten Auktion. */
final class LiveScreen extends Screen {
    private static final int PW = 440, PH = 272;
    private static final int ROW_X = 166, ROW_W = 260, ROW_Y = 70, ROW_H = 21, MAX_ROWS = 8;
    private static final int[] GOOD = {0xFF4ADE80, 0xFFFFC857, 0xFFFFFFFF};
    private static final int[] BAD = {0xFFF87171, 0xFFE5245C, 0xFF8E44FF};
    private static final int[] CONF = {0xFFFFC857, 0xFFE5245C, 0xFF8E44FF, 0xFFFFFFFF, 0xFF4ADE80};

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean in(int mx, int my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private final Map<String, Double> shown = new HashMap<>();
    private final long openedAt = System.currentTimeMillis();
    private long lastFrame = openedAt, burstAt, copiedAt;
    private int lastPayCount = -1;
    private boolean burstGood;
    private String copiedLabel = "";
    private float sc = 1f;
    private int ox, oy;

    LiveScreen() { super(Component.literal("HugoSMP Auktion")); }

    @Override
    protected void init() {
        sc = (float) Math.min(1.0, Math.min((width - 16) / (double) PW, (height - 16) / (double) PH));
        ox = (int) ((width - PW * sc) / 2);
        oy = (int) ((height - PH * sc) / 2);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        g.fill(0, 0, width, height, 0xA0070510);
        var p = g.pose();
        p.pushMatrix();
        p.translate(ox, oy);
        p.scale(sc, sc);
        draw(g, (int) ((mx - ox) / sc), (int) ((my - oy) / sc), System.currentTimeMillis());
        p.popMatrix();
    }

    private void draw(GuiGraphics g, int mx, int my, long now) {
        AuctionManager m = HugoAuctionClient.MANAGER;
        AuctionConfig cfg = HugoAuctionClient.CONFIG;
        Theme th = Theme.get(cfg.theme);
        Font f = font;
        boolean live = m.isRunning();
        AuctionManager.Snapshot s = m.last();
        if (!live && s == null) { onClose(); return; }

        long dt = Math.max(1, Math.min(100, now - lastFrame));
        lastFrame = now;
        double k = 1 - Math.exp(-dt / 140.0);
        double open = Gfx.ease((now - openedAt) / 350.0);

        // Burst bei neuer Zahlung
        int pc = m.paymentCount();
        if (live && lastPayCount >= 0 && pc != lastPayCount && m.lastPay() != null) {
            burstAt = now; burstGood = m.lastPay().valid();
        }
        lastPayCount = pc;

        // Panel
        int slideY = (int) ((1 - open) * 16);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0, slideY);
        Gfx.glow(g, 0, 0, PW, PH, th.b() & 0xFFFFFF, 70, 10);
        Gfx.rrect(g, 0, 0, PW, PH, 6, 0xF20D0B16);
        // Header
        Gfx.gradHAnim(g, 2, 2, PW - 4, 40, Gfx.alpha(th.a(), 0.9), Gfx.alpha(th.b(), 0.9), now);
        Gfx.gradV(g, 2, 2, PW - 4, 40, 0x00000000, 0x88000000);
        Gfx.shimmer(g, 2, 2, PW - 4, 40, now, 3600, 40);
        var item = live ? m.item() : s.item();
        Gfx.rrect(g, 12, 6, 32, 32, 5, 0x66000000);
        Gfx.item(g, item, 16, 10, 1.5f);
        String title = live ? "LIVE-AUKTION" : (s.buyout() ? "SOFORTKAUF!" : "AUKTION BEENDET");
        Gfx.scaled(g, f, title, 54, 9, 1.6f, 0xFFFFFFFF, true);
        Gfx.text(g, f, Gfx.trunc(f, item.getHoverName().getString(), 250), 54, 27, 0xFFE8E6F0, true);
        if (live) {
            boolean pulse = ((now / 500) & 1) == 0;
            Gfx.rrect(g, PW - 62, 15, 8, 8, 4, pulse ? 0xFFFF3B3B : 0xFFA02020);
            Gfx.text(g, f, "LIVE", PW - 50, 15, 0xFFFFFFFF, true);
        } else {
            long left = m.resultLeftMs(now);
            Gfx.right(g, f, "J-Fenster: " + (int) Math.ceil(left / 1000.0) + "s", PW - 12, 15, 0xFFE8E6F0, true);
        }
        Gfx.burst(g, PW / 2, 22, burstAt, now, burstGood ? GOOD : BAD, 40, 70, 900);

        // Linke Karte
        Gfx.rrect(g, 14, 52, 140, 186, 5, 0x55000000);
        Gfx.rrect(g, 46, 58, 76, 56, 5, 0x44000000);
        Gfx.glow(g, 46, 58, 76, 56, th.b() & 0xFFFFFF, 40, 5);
        Gfx.item(g, item, 84 - 24, 62, 3f);
        String nm = Gfx.trunc(f, item.getHoverName().getString(), 128);
        Gfx.center(g, f, nm, 84, 118, 0xFFFFFFFF, true);
        if (item.getCount() > 1) Gfx.center(g, f, "x" + item.getCount(), 84, 129, 0xFFA8A3B8, false);

        double minBid = m.minBid();
        int sy = 142;
        Gfx.text(g, f, "Mindestgebot", 22, sy, 0xFF8C86A0, false);
        Gfx.right(g, f, minBid > 0 ? Fmt.cash(minBid) : "-", 146, sy, 0xFFFFFFFF, false);
        Gfx.text(g, f, "Sofortkauf", 22, sy + 11, 0xFF8C86A0, false);
        Gfx.right(g, f, m.buyout() > 0 ? Fmt.cash(m.buyout()) : "-", 146, sy + 11, 0xFFFFFFFF, false);
        Gfx.text(g, f, "Zahlungen", 22, sy + 22, 0xFF8C86A0, false);
        Gfx.right(g, f, String.valueOf(live ? m.paymentCount() : s.payments()), 146, sy + 22, 0xFFFFFFFF, false);
        Gfx.text(g, f, "Bieter", 22, sy + 33, 0xFF8C86A0, false);
        Gfx.right(g, f, String.valueOf(live ? m.bidderCount() : s.bidders()), 146, sy + 33, 0xFFFFFFFF, false);
        Gfx.text(g, f, "Gesamt", 22, sy + 44, 0xFF8C86A0, false);
        Gfx.right(g, f, Fmt.cash(live ? m.totalReceived() : s.received()), 146, sy + 44, th.accent(), true);

        if (live) {
            long rem = m.remainingMs();
            int tcol = Gfx.lerp(0xFFFFFFFF, 0xFFFF4B4B, Gfx.timeColorMix(rem) * (0.6 + 0.4 * Math.sin(now / 120.0)));
            Gfx.scaledCenter(g, f, Fmt.time(rem), 84, 200, 1.8f, tcol, true);
            Gfx.bar(g, 22, 222, 124, 6, m.totalMs() <= 0 ? 0 : (double) rem / m.totalMs(), th.a(), th.b(), now);
        } else if (s.hasWinner()) {
            Gfx.crown(g, 77, 196, 14, 0xFFFFC857);
            String n = Gfx.trunc(f, Fmt.display(s.winner()), 120);
            Gfx.scaledCenter(g, f, n, 84, 208, 1.2f, th.accent(), true);
            Gfx.center(g, f, Fmt.cash(s.amount()), 84, 224, 0xFFFFFFFF, true);
        } else {
            Gfx.center(g, f, "Keine Gebote", 84, 210, 0xFFF87171, true);
        }

        // Leaderboard
        Gfx.gradText(g, f, "LEADERBOARD", ROW_X, 57, th.accent(), th.a(), true, now);
        Gfx.right(g, f, "Klick auf Namen = kopieren", ROW_X + ROW_W, 58, 0xFF8C86A0, false);
        List<AuctionManager.Bid> rows = live ? m.top(MAX_ROWS) : s.top();
        if (rows.size() > MAX_ROWS) rows = rows.subList(0, MAX_ROWS);
        double max = rows.isEmpty() ? 1 : Math.max(1, rows.get(0).amount());
        if (rows.isEmpty()) {
            Gfx.center(g, f, "Noch keine Gebote", ROW_X + ROW_W / 2, ROW_Y + 50, 0xFFA8A3B8, true);
            Gfx.center(g, f, "Bieten per /pay " + cfg.payName, ROW_X + ROW_W / 2, ROW_Y + 64, 0xFF8C86A0, false);
        }
        for (int i = 0; i < rows.size(); i++) {
            AuctionManager.Bid b = rows.get(i);
            double cur = shown.getOrDefault(b.player(), 0.0);
            cur += (b.amount() - cur) * k;
            shown.put(b.player(), cur);
            int ry = ROW_Y + i * ROW_H;
            boolean hov = mx >= ROW_X && mx < ROW_X + ROW_W && my >= ry && my < ry + ROW_H - 2;
            int rank = Math.min(i, 3);
            int rc = rank == 0 ? 0xFFFFC857 : rank == 1 ? 0xFFCBD5E1 : rank == 2 ? 0xFFE08A4B : th.b();
            Gfx.rrect(g, ROW_X, ry, ROW_W, ROW_H - 2, 4, hov ? 0x66241E36 : 0x40000000);
            int bw = (int) ((ROW_W - 4) * Gfx.clamp01(cur / max));
            if (bw > 2) {
                Gfx.gradH(g, ROW_X + 2, ry + ROW_H - 6, bw, 2, Gfx.alpha(th.a(), 0.9), Gfx.alpha(th.b(), 0.9));
                Gfx.gradH(g, ROW_X + 2, ry + 1, bw, ROW_H - 7, Gfx.alpha(rc, 0.20), 0x00000000 | (rc & 0xFFFFFF));
            }
            if (hov) Gfx.outline(g, ROW_X, ry, ROW_W, ROW_H - 2, Gfx.alpha(th.accent(), 0.7));
            Gfx.medal(g, f, ROW_X + 14, ry + 9, i, th.b());
            Gfx.name(g, f, b.player(), ROW_X + 30, ry + 5, 150, 0xFFFFFFFF, now, th);
            Gfx.right(g, f, Fmt.cash(cur), ROW_X + ROW_W - 8, ry + 5, i == 0 ? th.accent() : 0xFFE8E6F0, true);
            if (i == 0 && !live) Gfx.crown(g, ROW_X + ROW_W - 62, ry + 2, 12, 0xFFFFC857);
            final String raw = b.player();
            hits.add(new Hit(ROW_X, ry, ROW_W, ROW_H - 2, () -> copy(raw, Fmt.display(raw))));
        }

        // Buttons
        buttons(g, mx, my, now, th, live, rows);

        // Konfetti beim Ergebnis
        if (!live && s.hasWinner()) Gfx.confetti(g, 2, 2, PW - 4, PH - 4, s.endedAt(), now, CONF, 5000);

        // "Kopiert"-Toast
        double ct = now - copiedAt;
        if (ct < 2200) {
            double a = ct > 1700 ? (2200 - ct) / 500.0 : 1;
            String t = "Kopiert: " + copiedLabel;
            int tw = f.width(t) + 20;
            Gfx.rrect(g, PW / 2 - tw / 2, 228 - (int) (Gfx.ease(ct / 200.0) * 6), tw, 16, 5, Gfx.alpha(0xEE14301E, a));
            Gfx.outline(g, PW / 2 - tw / 2, 228 - (int) (Gfx.ease(ct / 200.0) * 6), tw, 16, Gfx.alpha(0xFF4ADE80, a));
            Gfx.center(g, f, t, PW / 2, 232 - (int) (Gfx.ease(ct / 200.0) * 6), Gfx.alpha(0xFFFFFFFF, a), false);
        }
        pose.popMatrix();
    }

    private void buttons(GuiGraphics g, int mx, int my, long now, Theme th, boolean live, List<AuctionManager.Bid> rows) {
        AuctionManager m = HugoAuctionClient.MANAGER;
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        List<Integer> styles = new ArrayList<>(); // 0 normal, 1 primary, 2 danger
        String lead = rows.isEmpty() ? null : rows.get(0).player();
        labels.add(live ? "Führenden kopieren" : "Gewinner kopieren"); styles.add(1);
        acts.add(() -> { if (lead != null) copy(lead, Fmt.display(lead)); });
        labels.add("Alle Namen"); styles.add(0);
        acts.add(() -> { if (!rows.isEmpty()) copy(AuctionManager.namesCsv(rows), rows.size() + " Namen"); });
        if (live) {
            labels.add("+30s"); styles.add(0); acts.add(() -> m.addTime(30));
            labels.add("+1 Min"); styles.add(0); acts.add(() -> m.addTime(60));
            labels.add("Abbrechen"); styles.add(2); acts.add(() -> { m.cancel(); onClose(); });
        } else {
            labels.add("Neue Auktion"); styles.add(0);
            acts.add(() -> minecraft.setScreen(new CreateScreen()));
        }
        labels.add("Schließen"); styles.add(0); acts.add(this::onClose);

        int n = labels.size(), gap = 6, total = PW - 28;
        int bw = (total - gap * (n - 1)) / n, by = PH - 30, bx = 14;
        for (int i = 0; i < n; i++) {
            int x = bx + i * (bw + gap);
            boolean hov = mx >= x && mx < x + bw && my >= by && my < by + 22;
            int st = styles.get(i);
            int c1 = st == 2 ? 0xFFB3243A : th.a(), c2 = st == 2 ? 0xFF6E1B2C : th.b();
            if (st == 0) {
                Gfx.rrect(g, x, by, bw, 22, 4, hov ? 0xFF2A2538 : 0xFF1B1727);
                Gfx.outline(g, x + 1, by + 1, bw - 2, 20, Gfx.alpha(th.b(), hov ? 0.9 : 0.4));
            } else {
                Gfx.rrect(g, x, by, bw, 22, 4, 0xFF000000);
                Gfx.gradHAnim(g, x + 1, by + 1, bw - 2, 20, hov ? Gfx.lerp(c1, 0xFFFFFFFF, 0.2) : c1,
                    hov ? Gfx.lerp(c2, 0xFFFFFFFF, 0.2) : c2, now);
                Gfx.shimmer(g, x + 1, by + 1, bw - 2, 20, now, 2800, 45);
            }
            if (hov) Gfx.glow(g, x, by, bw, 22, (st == 2 ? c1 : th.b()) & 0xFFFFFF, 60, 4);
            Gfx.center(g, font, Gfx.trunc(font, labels.get(i), bw - 6), x + bw / 2, by + 7, 0xFFFFFFFF, true);
            hits.add(new Hit(x, by, bw, 22, acts.get(i)));
        }
    }

    private void copy(String text, String label) {
        minecraft.keyboardHandler.setClipboard(text);
        copiedLabel = label;
        copiedAt = System.currentTimeMillis();
        AuctionManager.sound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.6f, 0.6f);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        if (ev.button() == 0) {
            int lx = (int) ((ev.x() - ox) / sc), ly = (int) ((ev.y() - oy) / sc);
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit h = hits.get(i);
                if (h.in(lx, ly)) { h.action().run(); return true; }
            }
        }
        return super.mouseClicked(ev, dbl);
    }

    @Override
    public boolean keyPressed(KeyEvent ev) {
        int k = ev.key();
        AuctionManager m = HugoAuctionClient.MANAGER;
        if (k == GLFW.GLFW_KEY_J) { onClose(); return true; }
        List<AuctionManager.Bid> rows = m.isRunning() ? m.top(MAX_ROWS) : (m.last() == null ? List.of() : m.last().top());
        if (k == GLFW.GLFW_KEY_C && !rows.isEmpty()) { copy(rows.get(0).player(), Fmt.display(rows.get(0).player())); return true; }
        if (k == GLFW.GLFW_KEY_A && !rows.isEmpty()) { copy(AuctionManager.namesCsv(rows), rows.size() + " Namen"); return true; }
        return super.keyPressed(ev);
    }
}
