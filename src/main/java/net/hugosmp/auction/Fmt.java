package net.hugosmp.auction;

import java.util.Locale;
import net.minecraft.client.Minecraft;

/** Zahlen, Zeit und Namen formatieren / parsen. */
final class Fmt {
    private Fmt() {}

    static String clean(String s) {
        return s == null ? "" : s.replaceAll("§.", "").trim();
    }

    static String trim(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s;
    }

    /** Kurzform: 1.5K, 2.3M, 4B ... */
    static String money(double v) {
        double a = Math.abs(v);
        if (a >= 1e12) return trim(v / 1e12) + "T";
        if (a >= 1e9) return trim(v / 1e9) + "B";
        if (a >= 1e6) return trim(v / 1e6) + "M";
        if (a >= 1e3) return trim(v / 1e3) + "K";
        return trim(v);
    }

    static String cash(double v) {
        return "$" + money(v);
    }

    static String time(long ms) {
        long sec = (long) Math.ceil(Math.max(0, ms) / 1000.0);
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.ROOT, "%d:%02d", m, s);
    }

    /** "300", "5m", "1h30m", "90s" -> Sekunden, -1 bei Fehler. */
    static int parseDuration(String raw) {
        if (raw == null) return -1;
        String s = raw.toLowerCase(Locale.ROOT).replace(" ", "");
        if (s.isEmpty()) return -1;
        if (s.matches("\\d+")) return safeInt(s, 1);
        if (!s.matches("(\\d+[hms])+")) return -1;
        long total = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)([hms])").matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            total += switch (m.group(2)) {
                case "h" -> n * 3600;
                case "m" -> n * 60;
                default -> n;
            };
            if (total > Integer.MAX_VALUE) return -1;
        }
        return (int) total;
    }

    private static int safeInt(String s, int mult) {
        try {
            long v = Long.parseLong(s) * mult;
            return v > Integer.MAX_VALUE ? -1 : (int) v;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Geld parsen: 1.5k, 2m, 1.000, 1,000.50, $500 ... NaN bei Fehler. */
    static double parse(String raw) {
        if (raw == null) return Double.NaN;
        String s = raw.toLowerCase(Locale.ROOT).replace("$", "").replace(" ", "").replace("_", "").trim();
        if (s.isEmpty()) return Double.NaN;
        double mult = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> mult = 1e3;
            case 'm' -> mult = 1e6;
            case 'b' -> mult = 1e9;
            case 't' -> mult = 1e12;
            default -> { }
        }
        boolean suffix = mult != 1;
        if (suffix) s = s.substring(0, s.length() - 1);
        if (!s.matches("[0-9.,]+")) return Double.NaN;
        int dot = s.lastIndexOf('.'), comma = s.lastIndexOf(',');
        String norm;
        if (dot >= 0 && comma >= 0) {
            char dec = dot > comma ? '.' : ',';
            char grp = dec == '.' ? ',' : '.';
            norm = s.replace(String.valueOf(grp), "").replace(dec, '.');
        } else if (dot >= 0 || comma >= 0) {
            final char sep = dot >= 0 ? '.' : ',';
            long count = s.chars().filter(c -> c == sep).count();
            int after = s.length() - s.lastIndexOf(sep) - 1;
            int first = s.indexOf(sep);
            boolean grouping = count > 1 || (!suffix && after == 3 && first > 0 && first <= 3);
            norm = grouping ? s.replace(String.valueOf(sep), "") : s.replace(sep, '.');
        } else {
            norm = s;
        }
        try {
            double v = Double.parseDouble(norm) * mult;
            return Double.isFinite(v) ? v : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    // ---- Namen (Java + Bedrock) ----

    /** Bedrock-Spieler haben ueber Floodgate/Geyser meist ein Praefix wie "." */
    static boolean isBedrock(String name) {
        return name != null && !name.isEmpty() && ".*+".indexOf(name.charAt(0)) >= 0;
    }

    static String display(String name) {
        if (name == null) return "";
        return isBedrock(name) ? name.substring(1) : name;
    }

    /** Eigener Name oder der eingestellte Zahlungsname -> wird hervorgehoben. */
    static boolean isMine(String name) {
        if (name == null) return false;
        String d = display(name);
        if (d.equalsIgnoreCase(AuctionManager.cfg().payName)) return true;
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && d.equalsIgnoreCase(mc.player.getName().getString());
    }
}
