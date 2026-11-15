package br.com.sharksmp.denuncias;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DenunciasPlugin extends JavaPlugin implements CommandExecutor, TabCompleter {

    /** Motivo rápido exibido no menu. */
    record QuickReason(Material icon, String text) {}

    enum InputType { NAME, REASON }

    /** Jogador que está digitando algo no chat (nome ou motivo). */
    record Pending(InputType type, Menus.Target target, long expiresAt) {}

    private ReportManager manager;
    private Menus menus;
    private List<QuickReason> quickReasons = List.of();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReport = new HashMap<>();
    private int cooldownSeconds;
    private int maxReason;
    private int inputSeconds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        cooldownSeconds = Math.max(0, getConfig().getInt("cooldown-segundos", 30));
        maxReason = Math.max(10, getConfig().getInt("tamanho-maximo-motivo", 100));
        inputSeconds = Math.max(10, getConfig().getInt("tempo-resposta-chat-segundos", 60));
        quickReasons = loadQuickReasons();

        manager = new ReportManager(this);
        manager.load();
        menus = new Menus(this);

        getServer().getPluginManager().registerEvents(new Listeners(this, menus), this);
        bind("denunciar");
        bind("denuncias");
        getLogger().info("SharkDenuncias ativado. Denúncias carregadas.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
        }
    }

    private void bind(String name) {
        PluginCommand c = getCommand(name);
        if (c != null) {
            c.setExecutor(this);
            c.setTabCompleter(this);
        }
    }

    private List<QuickReason> loadQuickReasons() {
        List<QuickReason> list = new ArrayList<>();
        for (String s : getConfig().getStringList("motivos-rapidos")) {
            String[] parts = s.split("\\|", 2);
            if (parts.length < 2) {
                continue;
            }
            Material m = Material.matchMaterial(parts[0].trim());
            if (m == null || !m.isItem()) {
                m = Material.PAPER;
            }
            String text = parts[1].trim();
            if (text.isEmpty()) {
                continue;
            }
            list.add(new QuickReason(m, text));
            if (list.size() >= 7) {
                break;
            }
        }
        return list;
    }

    // ------------------------------------------------------------ acessores

    ReportManager manager() {
        return manager;
    }

    List<QuickReason> quickReasons() {
        return quickReasons;
    }

    // ------------------------------------------------------------- mensagens

    private static Component prefix() {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("Denúncias", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    void say(Player p, String text, TextColor color) {
        p.sendMessage(prefix().append(Component.text(text, color)));
    }

    void sound(Player p, String key, float pitch) {
        p.playSound(p.getLocation(), key, 1f, pitch);
    }

    // --------------------------------------------------------------- comandos

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "denunciar":
                return cmdDenunciar(sender, args);
            case "denuncias":
                return cmdDenuncias(sender);
            default:
                return false;
        }
    }

    private boolean cmdDenunciar(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        if (args.length == 0) {
            menus.openSelect(p, 0);
            return true;
        }
        Menus.Target target = resolveTarget(args[0]);
        if (target == null) {
            say(p, "Jogador não encontrado. Use o nome exato de quem já entrou no servidor.", NamedTextColor.RED);
            return true;
        }
        if (target.id().equals(p.getUniqueId())) {
            say(p, "Você não pode denunciar a si mesmo.", NamedTextColor.RED);
            return true;
        }
        if (args.length == 1) {
            menus.openReason(p, target);
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        submit(p, target, reason);
        return true;
    }

    private boolean cmdDenuncias(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Este comando só pode ser usado por jogadores.");
            return true;
        }
        if (!p.isOp()) {
            say(p, "Você não tem permissão para usar este comando.", NamedTextColor.RED);
            return true;
        }
        menus.openAdmin(p, 0, Filter.PENDENTES);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("denunciar") && args.length == 1
                && sender instanceof Player self) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!o.getUniqueId().equals(self.getUniqueId())
                        && o.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(o.getName());
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------- entrada pelo chat

    void startInput(Player p, InputType type, Menus.Target target) {
        p.closeInventory();
        pending.put(p.getUniqueId(),
                new Pending(type, target, System.currentTimeMillis() + inputSeconds * 1000L));
        p.sendMessage(Component.empty());
        if (type == InputType.NAME) {
            say(p, "Digite no chat o nome do jogador que deseja denunciar.", NamedTextColor.YELLOW);
        } else {
            say(p, "Digite no chat o motivo da denúncia contra " + target.name() + ".", NamedTextColor.YELLOW);
        }
        p.sendMessage(Component.text("   Digite \"cancelar\" para desistir (" + inputSeconds + "s).",
                NamedTextColor.GRAY));
        p.sendMessage(Component.empty());
    }

    /** Retorna a entrada pendente ainda válida do jogador (remove se expirou). */
    Pending activePending(UUID id) {
        Pending pd = pending.get(id);
        if (pd == null) {
            return null;
        }
        if (System.currentTimeMillis() > pd.expiresAt()) {
            pending.remove(id);
            return null;
        }
        return pd;
    }

    void clearPending(UUID id) {
        pending.remove(id);
    }

    /** Chamado na thread principal com o texto que o jogador digitou. */
    void handleInput(Player p, String message) {
        Pending pd = activePending(p.getUniqueId());
        if (pd == null) {
            return;
        }
        String msg = message.trim();
        if (msg.equalsIgnoreCase("cancelar")) {
            pending.remove(p.getUniqueId());
            say(p, "Denúncia cancelada.", NamedTextColor.GRAY);
            return;
        }
        if (pd.type() == InputType.NAME) {
            Menus.Target target = resolveTarget(msg);
            if (target == null) {
                say(p, "Jogador não encontrado. Digite o nome exato ou \"cancelar\".", NamedTextColor.RED);
                return;
            }
            if (target.id().equals(p.getUniqueId())) {
                say(p, "Você não pode denunciar a si mesmo. Digite outro nome ou \"cancelar\".",
                        NamedTextColor.RED);
                return;
            }
            pending.remove(p.getUniqueId());
            menus.openReason(p, target);
        } else {
            if (msg.isEmpty()) {
                say(p, "O motivo é obrigatório. Digite o motivo ou \"cancelar\".", NamedTextColor.RED);
                return;
            }
            if (msg.length() > maxReason) {
                say(p, "Motivo muito longo (máximo " + maxReason + " caracteres). Tente de novo.",
                        NamedTextColor.RED);
                return;
            }
            pending.remove(p.getUniqueId());
            submit(p, pd.target(), msg);
        }
    }

    // ---------------------------------------------------------------- denúncia

    Menus.Target resolveTarget(String name) {
        if (name == null || name.isBlank() || name.length() > 16) {
            return null;
        }
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Menus.Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(name);
        if (off != null && off.getName() != null) {
            return new Menus.Target(off.getUniqueId(), off.getName());
        }
        return null;
    }

    boolean submit(Player p, Menus.Target target, String rawReason) {
        String reason = rawReason == null ? "" : rawReason.trim().replaceAll("\\s+", " ");
        if (reason.isEmpty()) {
            say(p, "O motivo da denúncia é obrigatório.", NamedTextColor.RED);
            return false;
        }
        if (reason.length() > maxReason) {
            say(p, "Motivo muito longo (máximo " + maxReason + " caracteres).", NamedTextColor.RED);
            return false;
        }
        if (target.id().equals(p.getUniqueId()) || target.name().equalsIgnoreCase(p.getName())) {
            say(p, "Você não pode denunciar a si mesmo.", NamedTextColor.RED);
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastReport.get(p.getUniqueId());
        long cd = cooldownSeconds * 1000L;
        if (last != null && now - last < cd) {
            long left = (long) Math.ceil((cd - (now - last)) / 1000.0);
            say(p, "Aguarde " + left + "s antes de enviar outra denúncia.", NamedTextColor.RED);
            return false;
        }

        Report r = manager.add(p.getUniqueId(), p.getName(), target.id(), target.name(), reason);
        lastReport.put(p.getUniqueId(), now);
        notifyAdmins(r);
        getLogger().info("Nova denúncia #" + r.id + ": " + r.reporterName + " -> " + r.targetName
                + " | " + r.reason);

        sound(p, "entity.player.levelup", 1.2f);
        say(p, "Denúncia enviada à equipe. Obrigado!", NamedTextColor.GREEN);
        return true;
    }

    private void notifyAdmins(Report r) {
        for (Player op : Bukkit.getOnlinePlayers()) {
            if (!op.isOp()) {
                continue;
            }
            // Se o OP é o próprio denunciado, ele não recebe nada (não pode ver quem o denunciou).
            if (op.getUniqueId().equals(r.targetId)) {
                continue;
            }
            op.sendMessage(Component.empty());
            op.sendMessage(prefix().append(Component.text("Nova denúncia #" + r.id, NamedTextColor.RED,
                    TextDecoration.BOLD)));
            op.sendMessage(Component.text("  Denunciante: ", NamedTextColor.GRAY)
                    .append(Component.text(r.reporterName, NamedTextColor.YELLOW)));
            op.sendMessage(Component.text("  Denunciado: ", NamedTextColor.GRAY)
                    .append(Component.text(r.targetName, NamedTextColor.RED)));
            op.sendMessage(Component.text("  Motivo: ", NamedTextColor.GRAY)
                    .append(Component.text(r.reason, NamedTextColor.WHITE)));
            op.sendMessage(Component.text("  [Abrir painel]", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand("/denuncias"))
                    .hoverEvent(HoverEvent.showText(Component.text("Clique para abrir /denuncias",
                            NamedTextColor.GRAY))));
            op.sendMessage(Component.empty());
            sound(op, "entity.experience_orb.pickup", 1.0f);
        }
    }
}
