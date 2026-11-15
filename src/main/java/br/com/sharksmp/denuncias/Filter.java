package br.com.sharksmp.denuncias;

/** Filtro de exibição do painel de denúncias. */
public enum Filter {
    PENDENTES("Pendentes"),
    RESOLVIDAS("Resolvidas"),
    TODAS("Todas");

    private final String label;

    Filter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Filter next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
