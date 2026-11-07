package net.hugosmp.auction;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HugoAuctionClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("hugoauction");
    public static final AuctionManager MANAGER = new AuctionManager();
    public static AuctionConfig CONFIG = new AuctionConfig();
    private static KeyMapping openKey;

    @Override
    public void onInitializeClient() {
        CONFIG = AuctionConfig.load();
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("hugoauction", "main"));
        openKey = KeyBindingHelper.registerKeyBinding(
            new KeyMapping("key.hugoauction.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            MANAGER.tick();
            while (openKey.consumeClick()) openMenu(mc);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay && !CONFIG.acceptActionBar) return;
            MANAGER.onMessage(message.getString());
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("hugoauction", "auction_hud"), AuctionHud::render);
    }

    /** J: läuft Auktion oder ist sie max. resultSeconds her -> Leaderboard, sonst Erstellen. */
    static void openMenu(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        long now = System.currentTimeMillis();
        if (MANAGER.isRunning() || MANAGER.hasFreshResult(now)) mc.setScreen(new LiveScreen());
        else mc.setScreen(new CreateScreen());
    }
}
