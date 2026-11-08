package com.iekcorbar.gouw;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The real job list, read from the live Jobs Reborn config (plugins/Jobs/config.yml → "jobs:").
 * Deliberately file-based (like the Lands importer): no compile or API dependency on Jobs itself,
 * and if Jobs is missing the list is simply empty - salaries then use only the names set in
 * Gouw's own config salaries map.
 */
final class JobsReader {
    private final GouwPlugin plugin;
    private List<String> jobs = new ArrayList<>();

    JobsReader(GouwPlugin plugin) {
        this.plugin = plugin;
    }

    /** (Re)read the configured Jobs config. @return the job names found. */
    List<String> reload() {
        jobs = new ArrayList<>();
        File f = new File(plugin.getConfig().getString("jobs-config", "plugins/Jobs/config.yml"));
        if (!f.isFile()) {
            plugin.getLogger().warning("No Jobs config at " + f.getPath() + " - taak list will use Gouw config salaries only.");
            return jobs;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            var jobsSec = y.getConfigurationSection("jobs");
            if (jobsSec != null) jobs.addAll(new ArrayList<>(jobsSec.getKeys(false)));
            jobs.sort(String.CASE_INSENSITIVE_ORDER);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not read " + f.getPath() + ": " + ex.getMessage());
        }
        return jobs;
    }

    List<String> all() {
        return jobs;
    }

    boolean known(String name) {
        return jobs.stream().anyMatch(j -> j.equalsIgnoreCase(name));
    }
}
