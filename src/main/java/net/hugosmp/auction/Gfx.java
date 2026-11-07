package net.hugosmp.auction;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Zeichen-Helfer: Verläufe, Glow, Text, Partikel. */
final class Gfx {
    private Gfx() {}

    static double clamp01(double v) { return v < 0 ? 0 : Math.min(v, 1); }
    static double ease(double t) { t = clamp01(t); return 1 - Math.pow(1 - t, 3); }
    static double pingPong(double t) { t = t % 2; if (t < 0) t += 2; return t > 1 ? 2 - t : t; }
    static int argb(int a, int rgb) { return (Math.max(0, Math.min(255, a)) << 24) | (rgb & 0xFFFFFF); }
    static int fix(int c) { return (c >>> 24) == 0 ? (c | 0xFF000000) : c; }
    static int alpha(int c, double mul) {
        int a = (int) (((c >>> 24) & 255) * clamp01(mul));
        return (a << 24) | (c & 0xFFFFFF);
    }
    static int lerp(int c1, int c2, double t) {
        t = clamp01(t);
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    static double hash(int i) {
        double s = Math.sin(i * 12.9898 + 78.233) * 43758.5453;
        return s - Math.floor(s);
    }

    // ---- Flächen ----
    static void rect(GuiGraphics g, int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) g.fill(x, y, x + w, y + h, c);
    }

    static void rrect(GuiGraphics g, int x, int y, int w, int h, int r, int c) {
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { rect(g, x, y, w, h, c); return; }
        rect(g, x, y + r, w, h - 2 * r, c);
        for (int i = 0; i < r; i++) {
            double d = r - i - 0.5;
            int ins = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
            rect(g, x + ins, y + i, w - 2 * ins, 1, c);
            rect(g, x + ins, y + h - 1 - i, w - 2 * ins, 1, c);
        }
    }

    static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        rect(g, x, y, w, 1, c); rect(g, x, y + h - 1, w, 1, c);
        rect(g, x, y + 1, 1, h - 2, c); rect(g, x + w - 1, y + 1, 1, h - 2, c);
    }

    static void gradH(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        int step = 3;
        for (int i = 0; i < w; i += step) {
            int sw = Math.min(step, w - i);
            g.fill(x + i, y, x + i + sw, y + h, lerp(c1, c2, (i + sw / 2.0) / Math.max(1, w)));
        }
    }

    static void gradHAnim(GuiGraphics g, int x, int y, int w, int h, int c1, int c2, long now) {
        int step = 3;
        for (int i = 0; i < w; i += step) {
            int sw = Math.min(step, w - i);
            double t = pingPong((i + sw / 2.0) / Math.max(1, w) * 0.9 + now / 2600.0);
            g.fill(x + i, y, x + i + sw, y + h, lerp(c1, c2, t));
        }
    }

    static void gradV(GuiGraphics g, int x, int y, int w, int h, int c1, int c2) {
        g.fillGradient(x, y, x + w, y + h, c1, c2);
    }

    static void glow(GuiGraphics g, int x, int y, int w, int h, int rgb, int maxAlpha, int spread) {
        for (int i = 1; i <= spread; i++) {
            double f = 1 - (double) i / (spread + 1);
            int c = argb((int) (maxAlpha * f * f), rgb);
            rect(g, x - i, y - i, w + 2 * i, 1, c);
            rect(g, x - i, y + h + i - 1, w + 2 * i, 1, c);
            rect(g, x - i, y - i + 1, 1, h + 2 * i - 2, c);
            rect(g, x + w + i - 1, y - i + 1, 1, h + 2 * i - 2, c);
        }
    }

    static void shimmer(GuiGraphics g, int x, int y, int w, int h, long now, int period, int alpha) {
        int band = Math.max(14, w / 5);
        double pos = (now % period) / (double) period;
        int bx = x - band + (int) (pos * (w + band));
        for (int i = 0; i < 3; i++) {
            int x0 = Math.max(bx + i * band / 3, x), x1 = Math.min(bx + (i + 1) * band / 3, x + w);
            double f = i == 1 ? 1.0 : 0.5;
            if (x1 > x0) g.fill(x0, y, x1, y + h, argb((int) (alpha * f), 0xFFFFFF));
        }
    }

    static void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int c1, int c2, long now) {
        rrect(g, x, y, w, h, Math.min(3, h / 2), 0x66000000);
        int fw = (int) Math.round(w * clamp01(frac));
        if (fw <= 0) return;
        gradH(g, x, y, fw, h, c1, c2);
        shimmer(g, x, y, fw, h, now, 1800, 70);
    }

    // ---- Text ----
    static void text(GuiGraphics g, Font f, String s, int x, int y, int c, boolean shadow) {
        g.drawString(f, s, x, y, fix(c), shadow);
    }
    static void center(GuiGraphics g, Font f, String s, int cx, int y, int c, boolean shadow) {
        g.drawString(f, s, cx - f.width(s) / 2, y, fix(c), shadow);
    }
    static void right(GuiGraphics g, Font f, String s, int rx, int y, int c, boolean shadow) {
        g.drawString(f, s, rx - f.width(s), y, fix(c), shadow);
    }
    static void scaled(GuiGraphics g, Font f, String s, float x, float y, float sc, int c, boolean shadow) {
        var p = g.pose();
        p.pushMatrix(); p.translate(x, y); p.scale(sc, sc);
        g.drawString(f, s, 0, 0, fix(c), shadow);
        p.popMatrix();
    }
    static void scaledCenter(GuiGraphics g, Font f, String s, float cx, float y, float sc, int c, boolean shadow) {
        scaled(g, f, s, cx - f.width(s) * sc / 2f, y, sc, c, shadow);
    }
    static void scaledRight(GuiGraphics g, Font f, String s, float rx, float y, float sc, int c, boolean shadow) {
        scaled(g, f, s, rx - f.width(s) * sc, y, sc, c, shadow);
    }
    static void item(GuiGraphics g, ItemStack st, float x, float y, float sc) {
        if (st == null || st.isEmpty()) return;
        var p = g.pose();
        p.pushMatrix(); p.translate(x, y); p.scale(sc, sc);
        g.renderItem(st, 0, 0);
        p.popMatrix();
    }
    static String trunc(Font f, String s, int maxW) {
        if (f.width(s) <= maxW) return s;
        String t = s;
        while (t.length() > 1 && f.width(t + "...") > maxW) t = t.substring(0, t.length() - 1);
        return t + "...";
    }
    static int gradText(GuiGraphics g, Font f, String s, int x, int y, int c1, int c2, boolean shadow, long now) {
        int cx = x, n = s.length();
        for (int i = 0; i < n; i++) {
            String ch = s.substring(i, i + 1);
            double t = pingPong(i / (double) Math.max(1, n - 1) * 0.8 + now / 1600.0);
            g.drawString(f, ch, cx, y, fix(lerp(c1, c2, t)), shadow);
            cx += f.width(ch);
        }
        return cx - x;
    }
    static int gradTextWidth(Font f, String s) { return f.width(s); }

    /** Name zeichnen: Bedrock-Badge, eigener Name als Verlauf. Gibt die genutzte Breite zurück. */
    static int name(GuiGraphics g, Font f, String raw, int x, int y, int maxW, int color, long now, Theme th) {
        boolean mine = Fmt.isMine(raw);
        boolean be = Fmt.isBedrock(raw) && AuctionManager.cfg().showBedrockBadge;
        int used = 0;
        if (be) {
            rrect(g, x, y - 1, 16, 10, 2, argb(0xC0, 0x1FA463));
            scaledCenter(g, f, "BE", x + 8, y + 1, 0.75f, 0xFFFFFFFF, false);
            used += 19;
        }
        String d = Fmt.display(raw);
        if (mine) d = "★ " + d;
        d = trunc(f, d, Math.max(10, maxW - used));
        if (mine) used += gradText(g, f, d, x + used, y, th.accent(), th.a(), true, now);
        else { g.drawString(f, d, x + used, y, fix(color), true); used += f.width(d); }
        return used;
    }

    // ---- Deko ----
    static void crown(GuiGraphics g, int x, int y, int w, int c) {
        rect(g, x, y + 5, w, 3, c);
        rect(g, x, y + 1, 2, 4, c);
        rect(g, x + w / 2 - 1, y, 2, 5, c);
        rect(g, x + w - 2, y + 1, 2, 4, c);
    }

    static void medal(GuiGraphics g, Font f, int cx, int cy, int rank, int fallback) {
        int c = switch (rank) { case 0 -> 0xFFFFC857; case 1 -> 0xFFCBD5E1; case 2 -> 0xFFE08A4B; default -> fallback; };
        rrect(g, cx - 8, cy - 7, 16, 14, 6, c);
        rrect(g, cx - 7, cy - 6, 14, 12, 5, lerp(c, 0xFF000000, 0.12));
        rect(g, cx - 6, cy - 5, 12, 5, argb(0x55, 0xFFFFFF));
        center(g, f, String.valueOf(rank + 1), cx, cy - 4, 0xFF1A1020, false);
    }

    static void burst(GuiGraphics g, int cx, int cy, long start, long now, int[] pal, int count, double spread, long dur) {
        double age = now - start;
        if (age < 0 || age > dur) return;
        double t = age / dur;
        for (int i = 0; i < count; i++) {
            double ang = hash(i) * Math.PI * 2;
            double d = (0.35 + hash(i + 97) * 0.65) * spread * ease(t);
            int px = (int) (cx + Math.cos(ang) * d);
            int py = (int) (cy + Math.sin(ang) * d + 14 * t * t);
            int size = 1 + (int) (hash(i + 13) * 2.5);
            g.fill(px, py, px + size, py + size, alpha(pal[i % pal.length], 1 - t));
        }
    }

    static void confetti(GuiGraphics g, int x, int y, int w, int h, long start, long now, int[] pal, long dur) {
        double ageMs = now - start;
        if (ageMs < 0 || ageMs > dur) return;
        double t = ageMs / 1000.0;
        double fade = ageMs > dur - 800 ? (dur - ageMs) / 800.0 : 1;
        for (int i = 0; i < 80; i++) {
            double fx = x + hash(i) * w + Math.sin(t * 3 + i) * 7;
            double fy = y - 12 + t * (50 + hash(i + 31) * 90);
            if (fy < y || fy > y + h) continue;
            int cw = 2 + (int) (hash(i + 7) * 2), ch = 3 + (int) (hash(i + 11) * 3);
            g.fill((int) fx, (int) fy, (int) fx + cw, (int) fy + ch, alpha(pal[i % pal.length], fade));
        }
    }

    static double timeColorMix(long remainingMs) { return remainingMs < 10_000 ? 1 - remainingMs / 10_000.0 : 0; }
}
