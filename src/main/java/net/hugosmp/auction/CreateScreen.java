package net.hugosmp.auction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Auktion erstellen (Tab 1) + Optionen (Tab 2). */
final class CreateScreen extends Screen {
    private static final int PW = 340, PH = 286;
    private static final int[] PRESET_S = {30, 60, 120, 300, 600, 1800};
    private static final String[] PRESET_L = {"30s", "1m", "2m", "5m", "10m", "30m"};
    private static final String[] POS = {"right", "left", "center"};
    private static final String[] POS_L = {"Rechts", "Links", "Mitte"};
    private static final int[] SCALES = {75, 100, 125, 150};
    private static final int[] SNIPE = {0, 5, 10, 15, 30};
    private static final int[] RES = {10, 20, 30, 60};

    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final List<Hit> hits = new ArrayList<>();
    private EditBox secs, min, buy, pay;
    private int tab = 0, ox, oy;
    private ItemStack stack = ItemStack.EMPTY;
    private String error = "";
    private long errorAt, openedAt = System.currentTimeMillis();

    CreateScreen() { super(Component.literal("Auktion erstellen")); }

    @Override
    protected void init() {
        ox = (width - PW) / 2;
        oy = Math.max(4, (height - PH) / 2);
        AuctionConfig c = HugoAuctionClient.CONFIG;
        secs = box(ox + 16, oy + 130, PW - 32, "z. B. 300, 5m oder 1h30m", s -> s.matches("[0-9hmsHMS ]*"), 12);
        secs.setValue(c.lastSeconds > 0 ? String.valueOf(c.lastSeconds) : "300");
        int half = (PW - 40) / 2;
        min = box(ox + 16, oy + 178, half, "z. B. 1k", s -> s.matches("[0-9.,kKmMbBtT$ ]*"), 16);
        min.setValue(c.lastMinBid == null ? "" : c.lastMinBid);
        buy = box(ox + 24 + half, oy + 178, half, "leer = aus", s -> s.matches("[0-9.,kKmMbBtT$ ]*"), 16);
        buy.setValue(c.lastBuyout == null ? "" : c.lastBuyout);
        pay = box(ox + 16, oy + 218, PW - 32, "Name für /pay", s -> s.matches("[\\w.*+ ]*"), 32);
        pay.setValue(c.payName);
        applyTab();
    }

    private EditBox box(int x, int y, int w, String hint, java.util.function.Predicate<String> filter, int max) {
        EditBox b = new EditBox(font, x + 6, y, w - 12, 16, Component.literal(""));
        b.setBordered(false);
        b.setMaxLength(max);
        b.setFilter(filter);
        b.setHint(Component.literal(hint));
        addRenderableWidget(b);
        return b;
    }

    private void applyTab() {
        boolean a = tab == 0;
        for (EditBox b : new EditBox[]{secs, min, buy, pay}) { b.visible = a; if (!a) b.setFocused(false); }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        hits.clear();
        long now = System.currentTimeMillis();
        AuctionConfig cfg = HugoAuctionClient.CONFIG;
        Theme th = Theme.get(cfg.theme);
        Font f = font;
        stack = minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
        double open = Gfx.ease((now - openedAt) / 300.0);

        g.fill(0, 0, width, height, 0xA0070510);
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(0, (int) ((1 - open) * 14));
        Gfx.glow(g, ox, oy, PW, PH, th.b() & 0xFFFFFF, 70, 10);
        Gfx.rrect(g, ox, oy, PW, PH, 6, 0xF20D0B16);
        Gfx.gradHAnim(g, ox + 2, oy + 2, PW - 4, 30, Gfx.alpha(th.a(), 0.9), Gfx.alpha(th.b(), 0.9), now);
        Gfx.gradV(g, ox + 2, oy + 2, PW - 4, 30, 0x00000000, 0x88000000);
        Gfx.shimmer(g, ox + 2, oy + 2, PW - 4, 30, now, 3600, 40);
        Gfx.scaled(g, f, "AUKTION ERSTELLEN", ox + 14, oy + 9, 1.4f, 0xFFFFFFFF, true);
        Gfx.right(g, f, "HugoSMP", ox + PW - 12, oy + 12, 0xFFE8E6F0, true);

        // Tabs
        String[] tabs = {"Auktion", "Optionen"};
        for (int i = 0; i < 2; i++) {
            int tx = ox + 14 + i * 84, ty = oy + 38;
            boolean sel = tab == i, hov = mx >= tx && mx < tx + 78 && my >= ty && my < ty + 16;
            Gfx.rrect(g, tx, ty, 78, 16, 4, sel ? 0xFF000000 : (hov ? 0xFF2A2538 : 0xFF1B1727));
            if (sel) Gfx.gradHAnim(g, tx + 1, ty + 1, 76, 14, th.a(), th.b(), now);
            Gfx.center(g, f, tabs[i], tx + 39, ty + 4, 0xFFFFFFFF, true);
            final int ti = i;
            hits.add(new Hit(tx, ty, 78, 16, () -> { tab = ti; applyTab(); }, null));
        }

        if (tab == 0) drawCreate(g, mx, my, now, th); else drawOptions(g, mx, my, now, th);

        // Fehler
        if (!error.isEmpty() && now - errorAt < 4000 && tab == 0)
            Gfx.center(g, f, error, ox + PW / 2, oy + 241, 0xFFF87171, true);

        // Buttons
        int by = oy + PH - 30;
        button(g, mx, my, now, th, ox + 14, by, PW - 28 - 90, "Auktion starten", true, this::onStart);
        button(g, mx, my, now, th, ox + PW - 14 - 84, by, 84, "Schließen", false, this::onClose);
        pose.popMatrix();
    }

    private void drawCreate(GuiGraphics g, int mx, int my, long now, Theme th) {
        Font f = font;
        // Item-Karte
        Gfx.rrect(g, ox + 14, oy + 62, PW - 28, 44, 5, 0x55000000);
        Gfx.rrect(g, ox + 20, oy + 68, 32, 32, 5, 0x66000000);
        if (stack.isEmpty()) {
            Gfx.center(g, f, "?", ox + 36, oy + 80, 0xFF6B6580, true);
            Gfx.text(g, f, "Kein Item in der Hand", ox + 60, oy + 72, 0xFFF87171, true);
            Gfx.text(g, f, "Halte das Item, das du versteigern willst.", ox + 60, oy + 84, 0xFF8C86A0, false);
        } else {
            Gfx.glow(g, ox + 20, oy + 68, 32, 32, th.b() & 0xFFFFFF, 40, 4);
            Gfx.item(g, stack, ox + 24, oy + 72, 1.5f);
            Gfx.text(g, f, Gfx.trunc(f, stack.getHoverName().getString(), PW - 90), ox + 60, oy + 72, 0xFFFFFFFF, true);
            Gfx.text(g, f, "x" + stack.getCount() + "  (wird aus deiner Hand versteigert)", ox + 60, oy + 84, 0xFF8C86A0, false);
        }
        label(g, "Dauer", ox + 16, oy + 118);
        field(g, secs, th, now);
        int cw = (PW - 32 - 5 * 4) / 6;
        for (int i = 0; i < 6; i++) {
            int cx = ox + 16 + i * (cw + 4), cy = oy + 152;
            boolean hov = mx >= cx && mx < cx + cw && my >= cy && my < cy + 14;
            Gfx.rrect(g, cx, cy, cw, 14, 4, hov ? 0xFF2A2538 : 0xFF1B1727);
            Gfx.outline(g, cx + 1, cy + 1, cw - 2, 12, Gfx.alpha(th.b(), hov ? 0.9 : 0.35));
            Gfx.center(g, f, PRESET_L[i], cx + cw / 2, cy + 3, 0xFFFFFFFF, false);
            final int sec = PRESET_S[i];
            hits.add(new Hit(cx, cy, cw, 14, () -> secs.setValue(String.valueOf(sec)), null));
        }
        int half = (PW - 40) / 2;
        label(g, "Mindestgebot", ox + 16, oy + 168);
        label(g, "Sofortkauf (optional)", ox + 24 + half, oy + 168);
        field(g, min, th, now);
        field(g, buy, th, now);
        label(g, "Zahlungen gehen an (Name)", ox + 16, oy + 208);
        field(g, pay, th, now);
        Gfx.text(g, f, "Gebote: /pay " + (pay.getValue().isBlank() ? "?" : pay.getValue()), ox + 16, oy + 238, 0xFF6B6580, false);
    }

    private void drawOptions(GuiGraphics g, int mx, int my, long now, Theme th) {
        AuctionConfig c = HugoAuctionClient.CONFIG;
        Font f = font;
        Gfx.text(g, f, "Linksklick = weiter, Rechtsklick = zurück", ox + 16, oy + 60, 0xFF8C86A0, false);
        int y = oy + 74, step = 19;
        int i = 0;
        opt(g, mx, my, now, th, y + step * i++, "Farbschema", Theme.get(c.theme).name(),
            () -> { c.theme = Theme.next(c.theme, 1).id(); c.save(); }, () -> { c.theme = Theme.next(c.theme, -1).id(); c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Overlay-Position", POS_L[idx(POS, c.hudPos)],
            () -> { c.hudPos = POS[Math.floorMod(idx(POS, c.hudPos) + 1, POS.length)]; c.save(); },
            () -> { c.hudPos = POS[Math.floorMod(idx(POS, c.hudPos) - 1, POS.length)]; c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Overlay-Größe", c.hudScale + "%",
            () -> { c.hudScale = cyc(SCALES, c.hudScale, 1); c.save(); }, () -> { c.hudScale = cyc(SCALES, c.hudScale, -1); c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Sounds", onOff(c.sounds), () -> { c.sounds = !c.sounds; c.save(); }, () -> { c.sounds = !c.sounds; c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Gebote addieren", onOff(c.cumulative), () -> { c.cumulative = !c.cumulative; c.save(); }, () -> { c.cumulative = !c.cumulative; c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Anti-Snipe", c.antiSnipeSec == 0 ? "Aus" : c.antiSnipeSec + "s",
            () -> { c.antiSnipeSec = cyc(SNIPE, c.antiSnipeSec, 1); c.save(); }, () -> { c.antiSnipeSec = cyc(SNIPE, c.antiSnipeSec, -1); c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Leaderboard nach Ende (J)", c.resultSeconds + "s",
            () -> { c.resultSeconds = cyc(RES, c.resultSeconds, 1); c.save(); }, () -> { c.resultSeconds = cyc(RES, c.resultSeconds, -1); c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Bedrock-Badge (BE)", onOff(c.showBedrockBadge), () -> { c.showBedrockBadge = !c.showBedrockBadge; c.save(); }, () -> { c.showBedrockBadge = !c.showBedrockBadge; c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Zahlungen aus Actionbar", onOff(c.acceptActionBar), () -> { c.acceptActionBar = !c.acceptActionBar; c.save(); }, () -> { c.acceptActionBar = !c.acceptActionBar; c.save(); });
        opt(g, mx, my, now, th, y + step * i++, "Start/Ende im Chat ansagen", onOff(c.announceInChat), () -> { c.announceInChat = !c.announceInChat; c.save(); }, () -> { c.announceInChat = !c.announceInChat; c.save(); });
    }

    private void opt(GuiGraphics g, int mx, int my, long now, Theme th, int y, String label, String value, Runnable l, Runnable r) {
        Font f = font;
        int x = ox + 14, w = PW - 28;
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 17;
        Gfx.rrect(g, x, y, w, 17, 4, hov ? 0xFF241F34 : 0xFF181424);
        if (hov) Gfx.outline(g, x, y, w, 17, Gfx.alpha(th.b(), 0.7));
        Gfx.text(g, f, label, x + 8, y + 5, 0xFFE8E6F0, false);
        int vw = f.width(value) + 14;
        Gfx.rrect(g, x + w - vw - 6, y + 2, vw, 13, 4, 0xFF000000);
        Gfx.gradH(g, x + w - vw - 5, y + 3, vw - 2, 11, Gfx.alpha(th.a(), 0.6), Gfx.alpha(th.b(), 0.6));
        Gfx.center(g, f, value, x + w - vw / 2 - 6, y + 5, 0xFFFFFFFF, true);
        hits.add(new Hit(x, y, w, 17, l, r));
    }

    private void button(GuiGraphics g, int mx, int my, long now, Theme th, int x, int y, int w, String text, boolean primary, Runnable r) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 22;
        if (primary) {
            Gfx.rrect(g, x, y, w, 22, 4, 0xFF000000);
            Gfx.gradHAnim(g, x + 1, y + 1, w - 2, 20, hov ? Gfx.lerp(th.a(), 0xFFFFFFFF, 0.2) : th.a(), hov ? Gfx.lerp(th.b(), 0xFFFFFFFF, 0.2) : th.b(), now);
            Gfx.shimmer(g, x + 1, y + 1, w - 2, 20, now, 2600, 50);
        } else {
            Gfx.rrect(g, x, y, w, 22, 4, hov ? 0xFF2A2538 : 0xFF1B1727);
            Gfx.outline(g, x + 1, y + 1, w - 2, 20, Gfx.alpha(th.b(), hov ? 0.9 : 0.4));
        }
        if (hov) Gfx.glow(g, x, y, w, 22, th.b() & 0xFFFFFF, 60, 4);
        Gfx.center(g, font, text, x + w / 2, y + 7, 0xFFFFFFFF, true);
        hits.add(new Hit(x, y, w, 22, r, null));
    }

    private void label(GuiGraphics g, String s, int x, int y) { Gfx.text(g, font, s, x, y, 0xFF8C86A0, false); }

    private void field(GuiGraphics g, EditBox b, Theme th, long now) {
        int x = b.getX() - 6, y = b.getY(), w = b.getWidth() + 12;
        Gfx.rrect(g, x, y - 1, w, 18, 4, b.isFocused() ? 0xFF000000 : 0xFF14101F);
        if (b.isFocused()) Gfx.gradHAnim(g, x + 3, y + 16, w - 6, 1, th.a(), th.b(), now);
        else Gfx.outline(g, x, y - 1, w, 18, Gfx.alpha(th.b(), 0.35));
        b.setTextColor(0xFFFFFFFF);
    }

    private static int idx(String[] a, String v) { for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i; return 0; }
    private static int cyc(int[] a, int cur, int dir) {
        int i = 0;
        for (int k = 0; k < a.length; k++) if (a[k] == cur) i = k;
        return a[Math.floorMod(i + dir, a.length)];
    }
    private static String onOff(boolean b) { return b ? "An" : "Aus"; }

    private void fail(String msg) { error = msg; errorAt = System.currentTimeMillis(); AuctionManager.sound(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1f, 0.7f); }

    private void onStart() {
        AuctionConfig c = HugoAuctionClient.CONFIG;
        AuctionManager m = HugoAuctionClient.MANAGER;
        if (m.isRunning()) { fail("Es läuft bereits eine Auktion."); return; }
        if (stack.isEmpty()) { fail("Halte ein Item in der Hand!"); return; }
        int sec = Fmt.parseDuration(secs.getValue());
        if (sec < 0) { fail("Bitte Dauer eingeben (z. B. 300 oder 5m)"); return; }
        if (sec < 5 || sec > 86400) { fail("Dauer: 5 Sekunden bis 24 Stunden"); return; }
        double mn = min.getValue().isBlank() ? 0 : Fmt.parse(min.getValue());
        if (Double.isNaN(mn)) { fail("Mindestgebot ungültig"); return; }
        double bu = buy.getValue().isBlank() ? 0 : Fmt.parse(buy.getValue());
        if (Double.isNaN(bu)) { fail("Sofortkauf ungültig"); return; }
        if (bu > 0 && bu < mn) { fail("Sofortkauf muss mindestens so hoch sein wie das Mindestgebot"); return; }
        if (pay.getValue().isBlank()) { fail("Bitte Zahlungsname eingeben"); return; }
        c.payName = pay.getValue().trim();
        c.lastSeconds = sec;
        c.lastMinBid = min.getValue().trim();
        c.lastBuyout = buy.getValue().trim();
        c.save();
        if (m.start(stack, sec, mn, bu)) onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent ev, boolean dbl) {
        if (super.mouseClicked(ev, dbl)) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.in(ev.x(), ev.y())) {
                Runnable r = ev.button() == 1 && h.right() != null ? h.right() : h.left();
                if (r != null && (ev.button() == 0 || h.right() != null)) { r.run(); return true; }
            }
        }
        return false;
    }
}
