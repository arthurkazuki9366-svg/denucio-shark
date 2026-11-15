package br.com.sharksmp.denuncias;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Guarda as denúncias em memória e as salva em plugins/SharkDenuncias/denuncias.yml. */
public final class ReportManager {
    private final DenunciasPlugin plugin;
    private final File file;
    private final List<Report> reports = new ArrayList<>();
    private int nextId = 1;

    public ReportManager(DenunciasPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "denuncias.yml");
    }

    public void load() {
        reports.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int maxId = 0;
        ConfigurationSection sec = y.getConfigurationSection("denuncias");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(key);
                if (c == null) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(key);
                    Report r = new Report(
                            id,
                            UUID.fromString(c.getString("denunciante-uuid", "")),
                            c.getString("denunciante-nome", "?"),
                            UUID.fromString(c.getString("denunciado-uuid", "")),
                            c.getString("denunciado-nome", "?"),
                            c.getString("motivo", ""),
                            c.getLong("data", 0L));
                    r.resolved = c.getBoolean("resolvida", false);
                    r.resolvedBy = c.getString("resolvida-por", null);
                    r.resolvedAt = c.getLong("resolvida-em", 0L);
                    reports.add(r);
                    maxId = Math.max(maxId, id);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Denúncia inválida ignorada: " + key);
                }
            }
        }
        reports.sort((a, b) -> Integer.compare(a.id, b.id));
        nextId = Math.max(y.getInt("proximo-id", 1), maxId + 1);
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("proximo-id", nextId);
        for (Report r : reports) {
            String p = "denuncias." + r.id + ".";
            y.set(p + "denunciante-uuid", r.reporterId.toString());
            y.set(p + "denunciante-nome", r.reporterName);
            y.set(p + "denunciado-uuid", r.targetId.toString());
            y.set(p + "denunciado-nome", r.targetName);
            y.set(p + "motivo", r.reason);
            y.set(p + "data", r.createdAt);
            y.set(p + "resolvida", r.resolved);
            if (r.resolved) {
                y.set(p + "resolvida-por", r.resolvedBy);
                y.set(p + "resolvida-em", r.resolvedAt);
            }
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Não foi possível salvar as denúncias: " + ex.getMessage());
        }
    }

    public Report add(UUID reporterId, String reporterName, UUID targetId, String targetName, String reason) {
        Report r = new Report(nextId++, reporterId, reporterName, targetId, targetName, reason,
                System.currentTimeMillis());
        reports.add(r);
        save();
        return r;
    }

    /**
     * Denúncias mais novas primeiro. Denúncias feitas contra o próprio visualizador
     * (caso ele seja OP) ficam ocultas, para que o denunciado nunca veja quem o denunciou.
     */
    public List<Report> filtered(Filter filter, UUID viewer) {
        List<Report> out = new ArrayList<>();
        for (int i = reports.size() - 1; i >= 0; i--) {
            Report r = reports.get(i);
            if (viewer != null && r.targetId.equals(viewer)) {
                continue;
            }
            if (filter == Filter.PENDENTES && r.resolved) {
                continue;
            }
            if (filter == Filter.RESOLVIDAS && !r.resolved) {
                continue;
            }
            out.add(r);
        }
        return out;
    }

    public boolean toggleResolved(int id, String admin) {
        for (Report r : reports) {
            if (r.id == id) {
                r.resolved = !r.resolved;
                r.resolvedBy = r.resolved ? admin : null;
                r.resolvedAt = r.resolved ? System.currentTimeMillis() : 0L;
                save();
                return true;
            }
        }
        return false;
    }

    public boolean delete(int id) {
        boolean removed = reports.removeIf(r -> r.id == id);
        if (removed) {
            save();
        }
        return removed;
    }

    /** Exclui todas as denúncias resolvidas (exceto as feitas contra o próprio visualizador). */
    public int deleteResolved(UUID viewer) {
        int count = 0;
        Iterator<Report> it = reports.iterator();
        while (it.hasNext()) {
            Report r = it.next();
            if (r.resolved && !r.targetId.equals(viewer)) {
                it.remove();
                count++;
            }
        }
        if (count > 0) {
            save();
        }
        return count;
    }
}
