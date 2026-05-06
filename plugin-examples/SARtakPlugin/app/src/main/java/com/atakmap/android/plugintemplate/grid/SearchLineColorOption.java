package com.atakmap.android.plugintemplate.grid;

public enum SearchLineColorOption {
    CYAN("Cyan", 0xE64AA3FF),
    YELLOW("Yellow", 0xE6D8B64C),
    MAGENTA("Magenta", 0xE6FF5AD1),
    GREEN("Green", 0xE642C36A),
    WHITE("White", 0xE6FFFFFF);

    private final String label;
    private final int argb;

    SearchLineColorOption(String label, int argb) {
        this.label = label;
        this.argb = argb;
    }

    public String getLabel() {
        return label;
    }

    public int getArgb() {
        return argb;
    }

    public SearchLineColorOption next() {
        SearchLineColorOption[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
