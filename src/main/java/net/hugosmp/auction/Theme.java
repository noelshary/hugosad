package net.hugosmp.auction;

/** Farbschemata. a/b = Verlauf, accent = Highlight (Gold etc.). */
record Theme(String id, String name, int a, int b, int accent) {
    static final Theme[] ALL = {
        new Theme("neoloni", "Neoloni (Rot-Lila)", 0xFFE5245C, 0xFF8E44FF, 0xFFFFC857),
        new Theme("ozean", "Ozean", 0xFF00C2FF, 0xFF3D5AFE, 0xFF9BFFEA),
        new Theme("gold", "Königlich", 0xFFFFB300, 0xFFFF5E3A, 0xFFFFF1B8),
        new Theme("toxic", "Toxic", 0xFF39FF88, 0xFF00B3A4, 0xFFF4FF6B),
        new Theme("sunset", "Sunset", 0xFFFF5F6D, 0xFFFFC371, 0xFFFFFFFF),
        new Theme("sakura", "Sakura", 0xFFFF7EB6, 0xFFB57BFF, 0xFFFFE3F1),
    };

    static Theme get(String id) {
        for (Theme t : ALL) if (t.id.equals(id)) return t;
        return ALL[0];
    }

    static Theme next(String id, int dir) {
        int i = 0;
        for (int k = 0; k < ALL.length; k++) if (ALL[k].id.equals(id)) i = k;
        return ALL[Math.floorMod(i + dir, ALL.length)];
    }
}
