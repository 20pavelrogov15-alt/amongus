package local.amongus;

import net.kyori.adventure.text.format.NamedTextColor;

public enum Role {
    CREWMATE("Член экипажа", NamedTextColor.AQUA),
    IMPOSTOR("Предатель", NamedTextColor.RED);

    private final String title;
    private final NamedTextColor color;

    Role(String title, NamedTextColor color) {
        this.title = title;
        this.color = color;
    }

    public String title() {
        return title;
    }

    public NamedTextColor color() {
        return color;
    }
}
