package br.com.sharksmp.denuncias;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

public final class Listeners implements Listener {

    private final DenunciasPlugin plugin;
    private final Menus menus;

    Listeners(DenunciasPlugin plugin, Menus menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    // ------------------------------------------------------------- menus

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        if (!(top.getHolder() instanceof Menus.Holder holder)) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Inventory clicked = e.getClickedInventory();
        if (clicked == null || !clicked.equals(top)) {
            return;
        }
        int slot = e.getSlot();

        if (holder instanceof Menus.AdminHolder ah) {
            if (!p.isOp()) {
                Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
                return;
            }
            onAdmin(p, ah, slot, e);
        } else if (holder instanceof Menus.PlayerSelectHolder sh) {
            onSelect(p, sh, slot);
        } else if (holder instanceof Menus.ReasonHolder rh) {
            onReason(p, rh, slot);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menus.Holder) {
            e.setCancelled(true);
        }
    }

    private void onSelect(Player p, Menus.PlayerSelectHolder h, int slot) {
        Menus.Target target = h.targets.get(slot);
        if (target != null) {
            plugin.sound(p, "ui.button.click", 1f);
            Bukkit.getScheduler().runTask(plugin, () -> menus.openReason(p, target));
            return;
        }
        switch (slot) {
            case 45 -> {
                h.page--;
                menus.renderSelect(h, p);
            }
            case 53 -> {
                h.page++;
                menus.renderSelect(h, p);
            }
            case 49 -> Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.startInput(p, DenunciasPlugin.InputType.NAME, null));
            case 51 -> Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            default -> { }
        }
    }

    private void onReason(Player p, Menus.ReasonHolder h, int slot) {
        int idx = slot - 10;
        if (slot >= 10 && slot <= 16 && idx < h.reasons.size()) {
            String reason = h.reasons.get(idx).text();
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                plugin.submit(p, h.target, reason);
            });
            return;
        }
        switch (slot) {
            case 22 -> Bukkit.getScheduler().runTask(plugin,
                    () -> plugin.startInput(p, DenunciasPlugin.InputType.REASON, h.target));
            case 30 -> Bukkit.getScheduler().runTask(plugin, () -> menus.openSelect(p, 0));
            case 32 -> Bukkit.getScheduler().runTask(plugin, () -> p.closeInventory());
            default -> { }
        }
    }

    private void onAdmin(Player p, Menus.AdminHolder h, int slot, InventoryClickEvent e) {
        Integer id = h.ids.get(slot);
        if (id != null) {
            if (e.isShiftClick() && e.isRightClick()) {
                plugin.manager().delete(id);
                plugin.sound(p, "entity.item.break", 1f);
                menus.renderAdmin(h, p);
            } else if (e.isLeftClick() && !e.isShiftClick()) {
                plugin.manager().toggleResolved(id, p.getName());
                plugin.sound(p, "entity.experience_orb.pickup", 1.5f);
                menus.renderAdmin(h, p);
            }
            return;
        }
        switch (slot) {
            case 45 -> {
                h.page--;
                menus.renderAdmin(h, p);
            }
            case 53 -> {
                h.page++;
                menus.renderAdmin(h, p);
            }
            case 47 -> {
                h.filter = h.filter.next();
                h.page = 0;
                menus.renderAdmin(h, p);
            }
            case 51 -> {
                if (e.isShiftClick()) {
                    int n = plugin.manager().deleteResolved(p.getUniqueId());
                    plugin.say(p, n + " denúncia(s) resolvida(s) excluída(s).", NamedTextColor.GRAY);
                    menus.renderAdmin(h, p);
                }
            }
            default -> { }
        }
    }

    // -------------------------------------------------------------- chat

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (plugin.activePending(p.getUniqueId()) == null) {
            return;
        }
        e.setCancelled(true);
        String msg = PlainTextComponentSerializer.plainText().serialize(e.message());
        Bukkit.getScheduler().runTask(plugin, () -> plugin.handleInput(p, msg));
    }

    // -------------------------------------------------------- entrar / sair

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.isOp()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) {
                return;
            }
            int n = plugin.manager().filtered(Filter.PENDENTES, p.getUniqueId()).size();
            if (n > 0) {
                p.sendMessage(Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text("Denúncias", NamedTextColor.RED))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                        .append(Component.text("Há " + n + " denúncia(s) pendente(s). Use /denuncias.",
                                NamedTextColor.YELLOW)));
            }
        }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.clearPending(e.getPlayer().getUniqueId());
    }
}
