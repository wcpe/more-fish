package me.elsiff.morefish;

import me.elsiff.morefish.command.GeneralCommands;
import me.elsiff.morefish.hooker.CitizensHooker;
import me.elsiff.morefish.hooker.PlaceholderAPIHooker;
import me.elsiff.morefish.hooker.VaultHooker;
import me.elsiff.morefish.listener.FishShopGUI;
import me.elsiff.morefish.listener.FishingListener;
import me.elsiff.morefish.listener.PlayerListener;
import me.elsiff.morefish.listener.SignListener;
import me.elsiff.morefish.manager.BossBarManager;
import me.elsiff.morefish.manager.ContestManager;
import me.elsiff.morefish.manager.FishManager;
import org.bukkit.Material;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MoreFish extends JavaPlugin {
    private static MoreFish instance;
    private PluginManager manager;
    private int taskId = -1;

    private Locale locale;
    private FishShopGUI fishShopGUI;
    private FishManager fishManager;
    private ContestManager contestManager;
    private BossBarManager bossBarManager;
    private VaultHooker vaultHooker;
    private CitizensHooker citizensHooker;
    private PlaceholderAPIHooker placeholderAPIHooker;

    public static MoreFish getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        this.locale = new Locale(this);

        this.fishManager = new FishManager(this);
        this.contestManager = new ContestManager(this);

        // For 1.9+
        if (getConfig().getBoolean("general.use-boss-bar") && Material.getMaterial("SHIELD") != null) {
            this.bossBarManager = new BossBarManager(this);
        }

        getCommand("morefish").setExecutor(new GeneralCommands(this));

        manager = getServer().getPluginManager();
        manager.registerEvents(new FishingListener(this), this);
        manager.registerEvents(new PlayerListener(this), this);

        if (manager.getPlugin("Vault") != null && manager.getPlugin("Vault").isEnabled()) {
            vaultHooker = new VaultHooker(this);

            if (vaultHooker.setupEconomy()) {
                getLogger().info("Found Vault for economy support.");
            } else {
                vaultHooker = null;
            }
        }

        if (manager.getPlugin("Citizens") != null && manager.getPlugin("Citizens").isEnabled()) {
            citizensHooker = new CitizensHooker();
            citizensHooker.registerTrait();
            getLogger().info("Found Citizens for Fish Shop Trait.");
        }

        if (manager.getPlugin("PlaceholderAPI") != null && manager.getPlugin("PlaceholderAPI").isEnabled()) {
            placeholderAPIHooker = new PlaceholderAPIHooker(this);
            getLogger().info("Found PlaceholderAPI for placeholders support.");
        }

        loadFishShop();
        scheduleAutoRunning();

        getLogger().info("Plugin has been enabled!");
    }
    public void loadFishShop() {
        if (this.fishShopGUI != null) {
            return;
        }

        if (hasEconomy() && getConfig().getBoolean("fish-shop.enable")) {
            this.fishShopGUI = new FishShopGUI(this);
            manager.registerEvents(new SignListener(this), this);
            manager.registerEvents(fishShopGUI, this);
        }
    }

    public void scheduleAutoRunning() {
        if (taskId != -1) {
            getServer().getScheduler().cancelTask(taskId);
        }

        if (getConfig().getBoolean("auto-running.enable")) {
            final int required = getConfig().getInt("auto-running.required-players");
            final long timer = getConfig().getLong("auto-running.timer");
            final List<String> startTime = getConfig().getStringList("auto-running.start-time");
            final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");

            taskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
                @Override
                public void run() {
                    String now = dateFormat.format(new Date());

                    if (startTime.contains(now) && getServer().getOnlinePlayers().size() >= required) {
                        getServer().dispatchCommand(getServer().getConsoleSender(), "morefish start " + timer);
                    }
                }
            }, 0L, 1200L);
        }
    }

    @Override
    public void onDisable() {
        if (getConfig().getBoolean("general.save-records")) {
            contestManager.saveRecords();
        }

        if (getCitizensHooker() != null) {
            getCitizensHooker().deregisterTrait();
        }

        getLogger().info("Plugin has been disabled!");
    }

    public Locale getLocale() {
        return locale;
    }

    public FishManager getFishManager() {
        return fishManager;
    }

    public ContestManager getContestManager() {
        return contestManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public boolean hasBossBar() {
        return (getBossBarManager() != null);
    }

    public String getOrdinal(int number) {
        switch (number) {
            case 1:
                return "1st";
            case 2:
                return "2nd";
            case 3:
                return "3rd";
            default:
                if (number > 20) {
                    return (number / 10) + getOrdinal(number % 10);
                } else {
                    return number + "th";
                }
        }
    }

    public String getTimeString(long sec) {
        StringBuilder builder = new StringBuilder();

        int minutes = (int) (sec / 60);
        int second = (int) (sec - minutes * 60);

        if (minutes > 0) {
            builder.append(minutes);
            builder.append(getLocale().getString("time-format-minutes"));
            builder.append(" ");
        }

        builder.append(second);
        builder.append(getLocale().getString("time-format-seconds"));

        return builder.toString();
    }


    public FishShopGUI getFishShopGUI() {
        return fishShopGUI;
    }

    public boolean hasEconomy() {
        return (vaultHooker != null);
    }

    public VaultHooker getVaultHooker() {
        return vaultHooker;
    }

    public CitizensHooker getCitizensHooker() {
        return citizensHooker;
    }

    public PlaceholderAPIHooker getPlaceholderAPIHooker() {
        return placeholderAPIHooker;
    }

}
