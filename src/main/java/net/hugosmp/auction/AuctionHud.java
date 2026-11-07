package net.hugosmp.auction;

import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Overlay für Streamer + Zuschauer. */
final class AuctionHud {
    private AuctionHud() {}
    private static final int W = 204;
    private static final int[] SPARK = {0xFFFFC857, 0xFFFFFFFF, 0xFFE5245C, 0xFF8E44FF};

    static void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        AuctionManager m = HugoAuctionClient.MANAGER;
        AuctionConfig c = HugoAuctionClient.CONFIG;
        long now = System.currentTimeMillis();
        boolean run = m.isRunning();
        boolean res = !run && m.hasFreshResult(now);
        if (!run && !res) return;

        Font f = mc.font;
        Theme th = Theme.get(c.theme);
        float sc = c.hudScale / 100f;
        double in = Gfx.ease((now - (run ? m.startedAt() : m.last().endedAt())) / 500.0);
        int sw = (int) (g.guiWidth() / sc);
        int slide = (int) ((1 - in) * (W + 24));
        int x = switch (c.hudPos) {
            case "left" -> 8 - slide;
            case "center" -> (sw - W) / 2;
            default -> sw - W - 8 + slide;
        };
        int y = 22;

        var p = g.pose();
        p.pushMatrix();
        p.scale(sc, sc);
        int bottom = run ? drawRunning(g, f, m, x, y, now, th) : drawResult(g, f, m, x, y, now, th);
        drawToasts(g, f, m, x, bottom + 6, now);
        p.popMatrix();
    }

    private static int panel(GuiGraphics g, int x, int y, int h, Theme th, long now) {
        Gfx.glow(g, x, y, W, h, th.b() & 0xFFFFFF, 60, 7);
        Gfx.rrect(g, x, y, W, h, 5, 0xEE0C0A14);
        Gfx.gradHAnim(g, x + 3, y, W - 6, 2, th.a(), th.b(), now);
        Gfx.gradHAnim(g, x + 3, y + h - 2, W - 6, 2, th.b(), th.a(), now);
        return y + h;
    }

    private static int drawRunning(GuiGraphics g, Font f, AuctionManager m, int x, int y, long now, Theme th) {
        int H = 200;
        panel(g, x, y, H, th, now);
        // Header
        Gfx.gradHAnim(g, x + 3, y + 2, W - 6, 18, alpha(th.a()), alpha(th.b()), now);
        Gfx.gradV(g, x + 3, y + 2, W - 6, 18, 0x00000000, 0x77000000);
        Gfx.gradText(g, f, "HUGO AUKTION", x + 10, y + 7, 0xFFFFFFFF, th.accent(), true, now);
        boolean pulse = ((now / 500) & 1) == 0;
        Gfx.rrect(g, x + W - 50, y + 6, 6, 6, 3, pulse ? 0xFFFF3B3B : 0xFFA02020);
        Gfx.text(g, f, "LIVE", x + W - 41, y + 6, 0xFFFFFFFF, true);

        // Item
        Gfx.rrect(g, x + 8, y + 26, 28, 28, 4, 0x55000000);
        Gfx.item(g, m.item(), x + 11, y + 29, 1.4f);
        String name = Gfx.trunc(f, m.item().getHoverName().getString(), W - 56);
        Gfx.text(g, f, name, x + 42, y + 30, 0xFFFFFFFF, true);
        String sub = m.minBid() > 0 ? "Mindestgebot " + Fmt.cash(m.minBid()) : "Kein Mindestgebot";
        Gfx.text(g, f, sub, x + 42, y + 42, 0xFFA8A3B8, false);

        // Timer
        long rem = m.remainingMs();
        double frac = m.totalMs() <= 0 ? 0 : (double) rem / m.totalMs();
        int tcol = Gfx.lerp(0xFFFFFFFF, 0xFFFF4B4B, Gfx.timeColorMix(rem) * (0.6 + 0.4 * Math.sin(now / 120.0)));
        Gfx.scaledCenter(g, f, Fmt.time(rem), x + W / 2f, y + 62, 2f, tcol, true);
        Gfx.bar(g, x + 10, y + 82, W - 20, 5, frac, th.a(), th.b(), now);

        // Höchstgebot
        List<AuctionManager.Bid> top = m.top(3);
        Gfx.text(g, f, "HÖCHSTGEBOT", x + 10, y + 94, 0xFF8C86A0, false);
        double shown = m.shownTop(now);
        double pop = Gfx.clamp01(1 - (now - m.lastBidAt()) / 500.0);
        Gfx.scaled(g, f, shown <= 0 ? "-" : Fmt.cash(shown), x + 10, y + 104, 1.5f + (float) pop * 0.25f, th.accent(), true);
        if (!top.isEmpty()) {
            String lead = Gfx.trunc(f, Fmt.display(top.get(0).player()), 80);
            Gfx.right(g, f, lead, x + W - 10, y + 106, 0xFFFFFFFF, true);
        }

        // Top 3
        int ry = y + 126;
        if (top.isEmpty()) {
            Gfx.center(g, f, "Noch keine Gebote", x + W / 2, ry + 12, 0xFF8C86A0, false);
        }
        for (int i = 0; i < top.size(); i++) {
            AuctionManager.Bid b = top.get(i);
            int yy = ry + i * 15;
            Gfx.rrect(g, x + 8, yy, W - 16, 13, 3, i == 0 ? Gfx.alpha(th.a(), 0.28) : 0x33000000);
            Gfx.medal(g, f, x + 18, yy + 6, i, th.b());
            Gfx.name(g, f, b.player(), x + 30, yy + 3, 98, 0xFFFFFFFF, now, th);
            Gfx.right(g, f, Fmt.cash(b.amount()), x + W - 12, yy + 3, i == 0 ? th.accent() : 0xFFE8E6F0, true);
        }

        // Footer
        int fy = y + 176;
        Gfx.scaledCenter(g, f, "Bieten: /pay " + HugoAuctionClient.CONFIG.payName + " <Betrag>", x + W / 2f, fy, 0.9f, 0xFFD9D5E8, true);
        String foot = m.buyout() > 0 ? "Sofortkauf " + Fmt.cash(m.buyout()) + "  |  Gesamt " + Fmt.cash(m.totalReceived())
            : "Gesamt " + Fmt.cash(m.totalReceived());
        Gfx.scaledCenter(g, f, foot, x + W / 2f, fy + 11, 0.8f, 0xFF8C86A0, false);
        Gfx.shimmer(g, x + 3, y + 2, W - 6, 18, now, 3200, 40);
        return y + H;
    }

    private static int drawResult(GuiGraphics g, Font f, AuctionManager m, int x, int y, long now, Theme th) {
        AuctionManager.Snapshot s = m.last();
        int H = 132;
        panel(g, x, y, H, th, now);
        Gfx.gradHAnim(g, x + 3, y + 2, W - 6, 18, alpha(th.a()), alpha(th.b()), now);
        Gfx.gradV(g, x + 3, y + 2, W - 6, 18, 0x00000000, 0x77000000);
        Gfx.center(g, f, s.buyout() ? "SOFORTKAUF!" : "AUKTION BEENDET", x + W / 2, y + 7, 0xFFFFFFFF, true);

        Gfx.rrect(g, x + 8, y + 26, 28, 28, 4, 0x55000000);
        Gfx.item(g, s.item(), x + 11, y + 29, 1.4f);
        Gfx.text(g, f, Gfx.trunc(f, s.item().getHoverName().getString(), W - 56), x + 42, y + 34, 0xFFFFFFFF, true);
        Gfx.text(g, f, s.bidders() + " Bieter  |  " + s.payments() + " Zahlungen", x + 42, y + 46, 0xFFA8A3B8, false);

        if (s.hasWinner()) {
            Gfx.crown(g, x + W / 2 - 7, y + 60, 14, 0xFFFFC857);
            String n = Fmt.display(s.winner());
            int nw = (int) (f.width(n) * 1.4f);
            if (Fmt.isBedrock(s.winner()) && HugoAuctionClient.CONFIG.showBedrockBadge) {
                Gfx.rrect(g, x + W / 2 - nw / 2 - 20, y + 72, 16, 10, 2, Gfx.argb(0xC0, 0x1FA463));
                Gfx.scaledCenter(g, f, "BE", x + W / 2f - nw / 2f - 12, y + 74, 0.75f, 0xFFFFFFFF, false);
            }
            Gfx.scaledCenter(g, f, n, x + W / 2f, y + 72, 1.4f, th.accent(), true);
            Gfx.scaledCenter(g, f, Fmt.cash(s.amount()), x + W / 2f, y + 90, 1.2f, 0xFFFFFFFF, true);
            Gfx.confetti(g, x + 3, y + 2, W - 6, H - 4, s.endedAt(), now, SPARK, 3500);
        } else {
            Gfx.center(g, f, "Keine Gebote", x + W / 2, y + 78, 0xFFF87171, true);
        }
        long left = m.resultLeftMs(now);
        Gfx.bar(g, x + 10, y + H - 26, W - 20, 3, left / (double) (HugoAuctionClient.CONFIG.resultSeconds * 1000L), th.a(), th.b(), now);
        Gfx.center(g, f, "J = Leaderboard (" + (int) Math.ceil(left / 1000.0) + "s)", x + W / 2, y + H - 18, 0xFFD9D5E8, true);
        return y + H;
    }

    private static void drawToasts(GuiGraphics g, Font f, AuctionManager m, int x, int y, long now) {
        List<AuctionManager.Event> ev = m.recentEvents(now, 3);
        int yy = y;
        for (AuctionManager.Event e : ev) {
            double age = now - e.at();
            double in = Gfx.ease(age / 300.0);
            double out = age > AuctionManager.EVENT_MS - 500 ? (AuctionManager.EVENT_MS - age) / 500.0 : 1;
            int off = (int) ((1 - in) * 30);
            int a = (int) (235 * Gfx.clamp01(out));
            int bx = x + off;
            Gfx.rrect(g, bx, yy, W, 26, 4, Gfx.argb(a, 0x0C0A14));
            Gfx.rect(g, bx, yy + 3, 3, 20, Gfx.alpha(e.color(), out));
            Gfx.text(g, f, e.title(), bx + 9, yy + 4, Gfx.alpha(e.color(), out), true);
            Gfx.text(g, f, Gfx.trunc(f, e.detail(), W - 16), bx + 9, yy + 15, Gfx.alpha(0xFFE8E6F0, out), false);
            yy += 29;
        }
    }

    private static int alpha(int c) { return Gfx.alpha(c, 0.85); }
}
