package com.atakmap.android.plugintemplate.grid;

public class SearchTeamStyle {

    public static final class ColorChoice {
        private final String name;
        private final int argb;

        ColorChoice(String name, int argb) {
            this.name = name;
            this.argb = argb;
        }

        public String getName() { return name; }
        public int getArgb() { return argb; }
    }

    private static final ColorChoice[] TEAM_COLORS = new ColorChoice[] {
            new ColorChoice("Blue", 0xFF4AA3FF),
            new ColorChoice("Cyan", 0xFF33D6D0),
            new ColorChoice("Green", 0xFF42C36A),
            new ColorChoice("Yellow", 0xFFD8B64C),
            new ColorChoice("Magenta", 0xFFFF5AD1),
            new ColorChoice("Orange", 0xFFFF9A3D),
            new ColorChoice("Purple", 0xFFB47CFF),
            new ColorChoice("Red", 0xFFD8544C)
    };

    private static final ColorChoice[] MEMBER_COLORS = new ColorChoice[] {
            new ColorChoice("White", 0xFFFFFFFF),
            new ColorChoice("Yellow", 0xFFFFD966),
            new ColorChoice("Green", 0xFF42C36A),
            new ColorChoice("Magenta", 0xFFFF5AD1),
            new ColorChoice("Orange", 0xFFFF9A3D),
            new ColorChoice("Purple", 0xFFB47CFF),
            new ColorChoice("Cyan", 0xFF33D6D0),
            new ColorChoice("Red", 0xFFD8544C)
    };

    public static ColorChoice teamColorFor(String teamId, String teamName,
            String atakGroupName) {
        ColorChoice fromAtak = matchColor(atakGroupName);
        if (fromAtak != null)
            return fromAtak;
        String key = safe(teamId).length() > 0 ? teamId : teamName;
        int index = Math.abs(safe(key).hashCode()) % TEAM_COLORS.length;
        return TEAM_COLORS[index];
    }

    public static ColorChoice memberColorFor(String uid, int laneNumber,
            boolean leader) {
        if (leader)
            return MEMBER_COLORS[0];
        int index = Math.max(1, laneNumber);
        if (safe(uid).length() > 0)
            index = Math.abs(uid.hashCode());
        return MEMBER_COLORS[1 + index % (MEMBER_COLORS.length - 1)];
    }

    public static ColorChoice colorByNameOrArgb(String name, int argb,
            ColorChoice fallback) {
        ColorChoice matched = matchColor(name);
        if (matched != null)
            return matched;
        if (argb != 0)
            return new ColorChoice(safe(name).length() == 0
                    ? "Shared" : safe(name), argb);
        return fallback;
    }

    public static ColorChoice matchColor(String value) {
        String normalized = safe(value).toLowerCase();
        if (normalized.length() == 0)
            return null;
        for (ColorChoice color : TEAM_COLORS) {
            if (normalized.contains(color.name.toLowerCase()))
                return color;
        }
        for (ColorChoice color : MEMBER_COLORS) {
            if (normalized.contains(color.name.toLowerCase()))
                return color;
        }
        return null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
