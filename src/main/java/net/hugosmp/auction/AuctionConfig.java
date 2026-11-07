package net.hugosmp.auction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;

public class AuctionConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("hugoauction-neu.json");
    static final int VERSION = 10;

    /** Java- UND Bedrock-Namen (Praefix . * + , Leerzeichen/Unterstrich erlaubt). */
    static final String PLAYER_GROUP = "[.*+]?[\\w ]{2,31}?";
    static final String OLD_PLAYER_GROUP = "\\w{3,16}";
    static final String DEFAULT_PATTERN =
        "^.*Du hast \\$?(?<amount>[\\d.,]+ ?[kKmMbBtT]?) ?\\$? von (?<player>" + PLAYER_GROUP + ") erhalten\\.?\\s*$";

    public int configVersion = VERSION;
    public String paymentPattern = DEFAULT_PATTERN;
    public boolean cumulative = true;
    public boolean announceInChat = false;
    public boolean sounds = true;
    public boolean acceptActionBar = false;
    public boolean showBedrockBadge = true;
    public String payName = "Noel_shary";
    public String theme = "neoloni";
    public String hudPos = "right";       // right | left | center
    public int hudScale = 100;            // 75 | 100 | 125 | 150
    public int antiSnipeSec = 0;          // 0 = aus
    public int resultSeconds = 10;        // so lange nach Ende per J das Leaderboard
    public int lastSeconds = 300;
    public String lastMinBid = "";
    public String lastBuyout = "";

    public transient Pattern pattern;

    static AuctionConfig load() {
        AuctionConfig cfg = null;
        boolean migrated = false;
        try {
            if (Files.exists(FILE)) cfg = GSON.fromJson(Files.readString(FILE), AuctionConfig.class);
        } catch (Exception e) {
            HugoAuctionClient.LOGGER.error("Config konnte nicht geladen werden", e);
        }
        if (cfg == null) { cfg = new AuctionConfig(); migrated = true; }
        if (cfg.paymentPattern == null || cfg.paymentPattern.isBlank()) { cfg.paymentPattern = DEFAULT_PATTERN; migrated = true; }
        if (cfg.configVersion < VERSION) {
            // Bedrock-Fix: alte Namensgruppe \w{3,16} ersetzen
            cfg.paymentPattern = cfg.paymentPattern.replace(OLD_PLAYER_GROUP, PLAYER_GROUP);
            cfg.configVersion = VERSION;
            migrated = true;
        }
        if (cfg.payName == null || cfg.payName.isBlank()) cfg.payName = "Noel_shary";
        if (cfg.hudPos == null) cfg.hudPos = "right";
        if (cfg.theme == null) cfg.theme = "neoloni";
        cfg.compile();
        if (migrated) cfg.save();
        return cfg;
    }

    public void compile() {
        try {
            pattern = Pattern.compile(paymentPattern);
        } catch (Exception e) {
            HugoAuctionClient.LOGGER.error("Ungültiges paymentPattern, Gebote sind deaktiviert", e);
            pattern = null;
        }
    }

    public void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(this));
        } catch (Exception e) {
            HugoAuctionClient.LOGGER.error("Config konnte nicht gespeichert werden", e);
        }
    }
}
