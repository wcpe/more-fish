package me.elsiff.morefish.manager;

import me.elsiff.morefish.CaughtFish;
import me.elsiff.morefish.MoreFish;
import me.elsiff.morefish.util.StringActionUtil;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ContestManager {
    private final MoreFish plugin;
    private final RecordComparator comparator = new RecordComparator();
    private final List<Record> recordList = new ArrayList<>();
    private final File fileRewards;
    private final FileConfiguration configRewards;
    private File fileRecords;
    private FileConfiguration configRecords;
    private boolean hasStarted = false;
    private TimerTask task = null;

    public ContestManager(MoreFish plugin) {
        this.plugin = plugin;

        if (plugin.getConfig().getBoolean("general.auto-start")) {
            hasStarted = true;
        }

        fileRewards = new File(plugin.getDataFolder(), "rewards.yml");

        createFile(fileRewards);
        configRewards = YamlConfiguration.loadConfiguration(fileRewards);

        if (plugin.getConfig().getBoolean("general.save-records")) {
            fileRecords = new File(plugin.getDataFolder(), "records.yml");
            createFile(fileRecords);
            configRecords = YamlConfiguration.loadConfiguration(fileRecords);

            loadRecords();
        }
    }

    private void createFile(File file) {
        if (!file.exists()) {
            try {
                boolean created = file.createNewFile();

                if (!created) {
                    plugin.getLogger().warning("Failed to create " + file.getName() + "!");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void saveRewards() {
        try {
            configRewards.save(fileRewards);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadRecords() {
        recordList.clear();

        for (String path : configRecords.getKeys(false)) {
            UUID id = UUID.fromString(configRecords.getString(path + ".player"));
            String fishName = configRecords.getString(path + ".fish-name");
            double length = configRecords.getDouble(path + ".length");

            recordList.add(new Record(id, fishName, length));
        }

        recordList.sort(comparator);
    }

    public void saveRecords() {
        for (String path : configRecords.getKeys(false)) {
            configRecords.set(path, null);
        }

        for (int i = 0; i < recordList.size(); i++) {
            Record record = recordList.get(i);
            configRecords.set(i + ".player", record.getPlayer().getUniqueId().toString());
            configRecords.set(i + ".fish-name", record.getFishName());
            configRecords.set(i + ".length", record.getLength());
        }

        try {
            configRecords.save(fileRecords);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean hasStarted() {
        return hasStarted;
    }

    public boolean hasTimer() {
        return (task != null);
    }

    public void start() {
        hasStarted = true;
    }

    public void startWithTimer(long sec) {
        task = new TimerTask(sec);
        task.runTaskTimer(plugin, 20, 20);

        if (plugin.hasBossBar()) {
            plugin.getBossBarManager().createTimerBar(sec);
        }

        start();
    }

    public void stop() {
        if (task != null) {
            if (plugin.hasBossBar()) {
                plugin.getBossBarManager().removeTimerBar();
            }

            task.cancel();
            task = null;
        }

        giveRewards();

        if (!plugin.getConfig().getBoolean("general.save-records")) {
            recordList.clear();
        }

        hasStarted = false;
    }

    private void giveRewards() {
        ConfigurationSection cfs = plugin.getConfig().getConfigurationSection("rank-reward");
        for (String key : cfs.getKeys(false)) {
            int rank = Integer.parseInt(key);
            Record record = getRecord(rank);
            if (record == null) {
                continue;
            }
            OfflinePlayer player = record.getPlayer();
            sendReward(player, cfs.getStringList(key + ".string-action"));
        }

    }


    private void sendReward(OfflinePlayer oPlayer, List<String> stringAction) {
        if (!oPlayer.isOnline()) {
            plugin.getLogger().info(oPlayer.getName() + "'s reward of fishing contest has not been sent as the player is offline now.");
            return;
        }

        Player player = oPlayer.getPlayer();

        StringActionUtil.executionCommands(stringAction, false, player);
    }

    private void sendCashPrize(OfflinePlayer player, double amount) {
        if (!plugin.getVaultHooker().getEconomy().hasAccount(player)) {
            plugin.getLogger().info(player.getName() + "'s reward of fishing contest has not been sent as having no economy account.");
            return;
        } else {
            plugin.getVaultHooker().getEconomy().depositPlayer(player, amount);
        }

        if (player.isOnline()) {
            int number = getNumber(player);
            String msg = plugin.getLocale().getString("reward-cash-prize");

            msg = msg.replaceAll("%player%", player.getName())
                    .replaceAll("%amount%", Double.toString(amount))
                    .replaceAll("%ordinal%", plugin.getOrdinal(number))
                    .replaceAll("%number%", Integer.toString(number));

            player.getPlayer().sendMessage(msg);
        }
    }

    private String getItemName(ItemStack item) {
        return ((item.hasItemMeta() && item.getItemMeta().hasDisplayName()) ?
                item.getItemMeta().getDisplayName() : item.getType().name().toLowerCase().replaceAll("_", " "));
    }


    public boolean isNew1st(CaughtFish fish) {
        Record record = getRecord(1);

        return (record == null || record.getLength() < fish.getLength());
    }

    public void addRecord(OfflinePlayer player, CaughtFish fish) {
        ListIterator<Record> it = recordList.listIterator();
        while (it.hasNext()) {
            Record record = it.next();

            if (record.getPlayer().equals(player)) {
                if (record.getLength() < fish.getLength()) {
                    it.remove();
                    break;
                } else {
                    return;
                }
            }
        }

        recordList.add(new Record(player.getUniqueId(), fish));

        Collections.sort(recordList, comparator);
    }

    public Record getRecord(int number) {
        return ((recordList.size() >= number) ? recordList.get(number - 1) : null);
    }

    public int getRecordAmount() {
        return recordList.size();
    }

    public boolean hasRecord(OfflinePlayer player) {
        for (Record record : recordList) {
            if (record.getPlayer().equals(player)) {
                return true;
            }
        }

        return false;
    }

    public double getRecordLength(OfflinePlayer player) {
        for (Record record : recordList) {
            if (record.getPlayer().equals(player)) {
                return record.getLength();
            }
        }

        return 0.0D;
    }

    public int getNumber(OfflinePlayer player) {
        for (int i = 0; i < recordList.size(); i++) {
            if (recordList.get(i).getPlayer().getUniqueId().equals(player.getUniqueId())) {
                return (i + 1);
            }
        }

        return 0;
    }

    public void clearRecords() {
        recordList.clear();
    }

    private class RecordComparator implements Comparator<Record> {

        @Override
        public int compare(Record arg0, Record arg1) {
            if (arg0.getLength() < arg1.getLength()) {
                return 1;
            } else if ((arg0.getLength() > arg1.getLength())) {
                return -1;
            }
            return 0;
        }
    }

    public class Record {
        private final UUID id;
        private final String fishName;
        private final double length;

        public Record(UUID id, CaughtFish fish) {
            this(id, fish.getName(), fish.getLength());
        }

        public Record(UUID id, String fishName, double length) {
            this.id = id;
            this.fishName = fishName;
            this.length = length;
        }

        public OfflinePlayer getPlayer() {
            return plugin.getServer().getOfflinePlayer(id);
        }

        public String getFishName() {
            return fishName;
        }

        public double getLength() {
            return length;
        }
    }

    private class TimerTask extends BukkitRunnable {
        private final long timer;
        private long passed = 0;

        public TimerTask(long sec) {
            this.timer = sec;
        }

        public void run() {
            passed++;

            if (plugin.hasBossBar()) {
                plugin.getBossBarManager().updateTimerBar(passed, timer);
            }

            if (passed >= timer) {
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "morefish stop");
                this.cancel();
            }
        }
    }
}
