package br.com.sharksmp.denuncias;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Construção e atualização dos menus (GUIs). */
public final class Menus {

    static final int PER_PAGE_SELECT = 28;
    static final int PER_PAGE_ADMIN = 45;
    private static final int[] INNER = buildInner();
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private static int[] buildInner() {
        int[] a = new int[PER_PAGE_SELECT];
        int i = 0;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                a[i++] = row * 9 + col;
            }
        }
        return a;
    }

    /** Jogador alvo de uma denúncia. */
    record Target(UUID id, String name) {}

    abstract static class Holder implements InventoryHolder {
        Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    static final class PlayerSelectHolder extends Holder {
        int page;
        final Map<Integer, Target> targets = new HashMap<>();
    }

    static final class ReasonHolder extends Holder {
        Target target;
        List<DenunciasPlugin.QuickReason> reasons = new ArrayList<>();
    }

    static final class AdminHolder extends Holder {
        int page;
        Filter filter = Filter.PENDENTES;
        final Map<Integer, Integer> ids = new HashMap<>();
    }

    private final DenunciasPlugin plugin;

    Menus(DenunciasPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ helpers

    static Component t(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false);
    }

    static Component tb(String text, TextColor color) {
        return Component.text(text, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true);
    }

    static Component kv(String key, String value, TextColor valueColor) {
        return Component.text(key, NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, false)
                .append(Component.text(value, valueColor));
    }

    static ItemStack item(Material material, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name);
        if (lore != null) {
            meta.lore(lore);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane(Material material) {
        return item(material, t(" ", NamedTextColor.BLACK), null);
    }

    private static void border(Inventory inv) {
        int rows = inv.getSize() / 9;
        ItemStack top = pane(Material.RED_STAINED_GLASS_PANE);
        ItemStack other = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < inv.getSize(); i++) {
            int row = i / 9;
            int col = i % 9;
            if (row == 0) {
                inv.setItem(i, top);
            } else if (row == rows - 1 || col == 0 || col == 8) {
                inv.setItem(i, other);
            }
        }
    }

    static List<String> wrap(String text, int max) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String w = word;
            while (w.length() > max) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(w.substring(0, max));
                w = w.substring(max);
            }
            if (line.length() > 0 && line.length() + 1 + w.length() > max) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(w);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    // ------------------------------------------------ menu 1: escolher o jogador

    void openSelect(Player p, int page) {
        PlayerSelectHolder h = new PlayerSelectHolder();
        h.page = page;
        h.inventory = Bukkit.createInventory(h, 54,
                tb("Denunciar", NamedTextColor.RED).append(t(" • Escolha o jogador", NamedTextColor.GRAY)));
        renderSelect(h, p);
        p.openInventory(h.inventory);
    }

    void renderSelect(PlayerSelectHolder h, Player p) {
        Inventory inv = h.inventory;
        inv.clear();
        h.targets.clear();
        border(inv);

        List<Player> others = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (!o.getUniqueId().equals(p.getUniqueId())) {
                others.add(o);
            }
        }
        others.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (others.size() + PER_PAGE_SELECT - 1) / PER_PAGE_SELECT);
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        int start = h.page * PER_PAGE_SELECT;
        for (int i = 0; i < PER_PAGE_SELECT && start + i < others.size(); i++) {
            Player o = others.get(start + i);
            int slot = INNER[i];
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(o);
            sm.displayName(tb(o.getName(), NamedTextColor.YELLOW));
            sm.lore(List.of(
                    Component.empty(),
                    t("Clique para denunciar", NamedTextColor.GRAY),
                    t("este jogador.", NamedTextColor.GRAY)));
            head.setItemMeta(sm);
            inv.setItem(slot, head);
            h.targets.put(slot, new Target(o.getUniqueId(), o.getName()));
        }

        if (others.isEmpty()) {
            inv.setItem(22, item(Material.BARRIER, tb("Nenhum outro jogador online", NamedTextColor.RED),
                    List.of(Component.empty(),
                            t("Use o botão \"Digitar nome\" para", NamedTextColor.GRAY),
                            t("denunciar um jogador offline.", NamedTextColor.GRAY))));
        }

        if (h.page > 0) {
            inv.setItem(45, item(Material.ARROW, tb("« Página anterior", NamedTextColor.YELLOW), null));
        }
        if (h.page < pages - 1) {
            inv.setItem(53, item(Material.ARROW, tb("Próxima página »", NamedTextColor.YELLOW), null));
        }
        inv.setItem(47, item(Material.BOOK, tb("Como denunciar", NamedTextColor.AQUA), List.of(
                Component.empty(),
                t("1. Escolha o jogador", NamedTextColor.WHITE),
                t("2. Escolha o motivo", NamedTextColor.WHITE),
                Component.empty(),
                t("Seu nome nunca é mostrado", NamedTextColor.GREEN),
                t("ao jogador denunciado.", NamedTextColor.GREEN),
                Component.empty(),
                t("Página " + (h.page + 1) + "/" + pages, NamedTextColor.DARK_GRAY))));
        inv.setItem(49, item(Material.NAME_TAG, tb("Digitar nome", NamedTextColor.GOLD), List.of(
                Component.empty(),
                t("Para jogadores offline ou", NamedTextColor.GRAY),
                t("que não aparecem na lista.", NamedTextColor.GRAY),
                Component.empty(),
                t("» Clique para digitar no chat", NamedTextColor.YELLOW))));
        inv.setItem(51, item(Material.BARRIER, tb("Fechar", NamedTextColor.RED), null));
    }

    // ----------------------------------------------------- menu 2: o motivo

    void openReason(Player p, Target target) {
        ReasonHolder h = new ReasonHolder();
        h.target = target;
        h.reasons = plugin.quickReasons();
        h.inventory = Bukkit.createInventory(h, 36,
                tb("Denunciar", NamedTextColor.RED).append(t(" • Escolha o motivo", NamedTextColor.GRAY)));
        Inventory inv = h.inventory;
        border(inv);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(Bukkit.getOfflinePlayer(target.id()));
        sm.displayName(tb(target.name(), NamedTextColor.YELLOW));
        sm.lore(List.of(
                Component.empty(),
                t("Jogador que será denunciado", NamedTextColor.GRAY),
                Component.empty(),
                t("Ele não saberá quem denunciou.", NamedTextColor.GREEN)));
        head.setItemMeta(sm);
        inv.setItem(4, head);

        for (int i = 0; i < h.reasons.size() && i < 7; i++) {
            DenunciasPlugin.QuickReason qr = h.reasons.get(i);
            inv.setItem(10 + i, item(qr.icon(), tb(qr.text(), NamedTextColor.WHITE), List.of(
                    Component.empty(),
                    t("» Clique para enviar a denúncia", NamedTextColor.YELLOW))));
        }
        inv.setItem(22, item(Material.WRITABLE_BOOK, tb("Outro motivo", NamedTextColor.GOLD), List.of(
                Component.empty(),
                t("Escreva o motivo no chat.", NamedTextColor.GRAY),
                Component.empty(),
                t("» Clique para digitar", NamedTextColor.YELLOW))));
        inv.setItem(30, item(Material.ARROW, tb("Voltar", NamedTextColor.YELLOW), null));
        inv.setItem(32, item(Material.BARRIER, tb("Cancelar", NamedTextColor.RED), null));
        p.openInventory(inv);
    }

    // ----------------------------------------- menu 3: painel dos administradores

    void openAdmin(Player p, int page, Filter filter) {
        AdminHolder h = new AdminHolder();
        h.page = page;
        h.filter = filter;
        h.inventory = Bukkit.createInventory(h, 54, tb("Painel de Denúncias", NamedTextColor.RED));
        renderAdmin(h, p);
        p.openInventory(h.inventory);
    }

    void renderAdmin(AdminHolder h, Player viewer) {
        Inventory inv = h.inventory;
        inv.clear();
        h.ids.clear();

        ReportManager mgr = plugin.manager();
        UUID vid = viewer.getUniqueId();
        List<Report> list = mgr.filtered(h.filter, vid);
        int pages = Math.max(1, (list.size() + PER_PAGE_ADMIN - 1) / PER_PAGE_ADMIN);
        h.page = Math.max(0, Math.min(h.page, pages - 1));

        int start = h.page * PER_PAGE_ADMIN;
        for (int i = 0; i < PER_PAGE_ADMIN && start + i < list.size(); i++) {
            Report r = list.get(start + i);
            inv.setItem(i, reportItem(r));
            h.ids.put(i, r.id);
        }

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, filler);
        }
        if (h.page > 0) {
            inv.setItem(45, item(Material.ARROW, tb("« Página anterior", NamedTextColor.YELLOW), null));
        }
        if (h.page < pages - 1) {
            inv.setItem(53, item(Material.ARROW, tb("Próxima página »", NamedTextColor.YELLOW), null));
        }

        int pend = mgr.filtered(Filter.PENDENTES, vid).size();
        int resol = mgr.filtered(Filter.RESOLVIDAS, vid).size();

        inv.setItem(47, item(Material.HOPPER, tb("Filtro: " + h.filter.label(), NamedTextColor.AQUA), List.of(
                Component.empty(),
                t("» Clique para alternar", NamedTextColor.YELLOW),
                t("  Pendentes → Resolvidas → Todas", NamedTextColor.DARK_GRAY))));
        inv.setItem(49, item(Material.NETHER_STAR, tb("Resumo", NamedTextColor.GOLD), List.of(
                Component.empty(),
                kv("Pendentes: ", String.valueOf(pend), NamedTextColor.RED),
                kv("Resolvidas: ", String.valueOf(resol), NamedTextColor.GREEN),
                Component.empty(),
                t("Página " + (h.page + 1) + "/" + pages, NamedTextColor.DARK_GRAY))));
        inv.setItem(51, item(Material.LAVA_BUCKET, tb("Limpar resolvidas", NamedTextColor.RED), List.of(
                Component.empty(),
                t("Exclui todas as denúncias", NamedTextColor.GRAY),
                t("já resolvidas.", NamedTextColor.GRAY),
                Component.empty(),
                t("» Shift + clique para confirmar", NamedTextColor.YELLOW))));
    }

    private ItemStack reportItem(Report r) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(kv("Denunciante: ", r.reporterName, NamedTextColor.YELLOW));
        lore.add(kv("Denunciado: ", r.targetName, NamedTextColor.RED));
        lore.add(Component.empty());
        lore.add(t("Motivo:", NamedTextColor.GRAY));
        for (String line : wrap(r.reason, 34)) {
            lore.add(t(line, NamedTextColor.WHITE));
        }
        lore.add(Component.empty());
        lore.add(kv("Data: ", DATE.format(Instant.ofEpochMilli(r.createdAt)), NamedTextColor.AQUA));
        if (r.resolved) {
            lore.add(kv("Resolvida por: ", String.valueOf(r.resolvedBy), NamedTextColor.GREEN));
        }
        lore.add(Component.empty());
        lore.add(t("» Clique esquerdo: " + (r.resolved ? "reabrir" : "marcar como resolvida"),
                NamedTextColor.GREEN));
        lore.add(t("» Shift + direito: excluir", NamedTextColor.RED));

        if (r.resolved) {
            return item(Material.EMERALD, tb("#" + r.id + " • Resolvida", NamedTextColor.GREEN), lore);
        }
        return item(Material.WRITABLE_BOOK, tb("#" + r.id + " • Pendente", NamedTextColor.RED), lore);
    }
}
