package me.elsiff.morefish.listener;

import me.elsiff.morefish.CaughtFish;
import me.elsiff.morefish.MoreFish;
import me.elsiff.morefish.event.PlayerCatchCustomFishEvent;
import me.elsiff.morefish.manager.ContestManager;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;

public class FishingListener implements Listener {
    private final MoreFish plugin;
    private final ContestManager contest;

    public FishingListener(MoreFish plugin) {
        this.plugin = plugin;
        this.contest = plugin.getContestManager();
    }

    public boolean isClick(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        for (String s : plugin.getConfig().getStringList("shop-allow-click-id")) {
            String[] sSplit = s.split(":");
            if (sSplit.length == 2) {
                if (!"*".equals(sSplit[0]) && Integer.parseInt(sSplit[0]) != item.getTypeId()) {
                    continue;
                }
                if (!"*".equals(sSplit[1]) && Integer.parseInt(sSplit[1]) != item.getData().getData()) {
                    continue;
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void on(InventoryClickEvent event) {
        if (event.getInventory().getTitle().equals(plugin.getLocale().getString("shop-gui-title"))) {
            if (event.getHotbarButton() != -1) {
                event.setCancelled(true);
            }
            if (event.getClickedInventory() == event.getWhoClicked().getInventory()) {
                if (event.getCursor().getType() != Material.AIR) {
                    return;
                }
                if (isClick(event.getCurrentItem())) {
                    return;
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item) {
            if (!contest.hasStarted() && plugin.getConfig().getBoolean("general.no-fishing-unless-contest")) {
                event.setCancelled(true);

                String msg = plugin.getLocale().getString("no-fishing-allowed");
                event.getPlayer().sendMessage(msg);
                return;
            }

            if (!hasEnabled(event)) {
                return;
            }

            CaughtFish fish = plugin.getFishManager().generateRandomFish(event.getPlayer());

            if (fish == null) {
                event.getPlayer().sendMessage(plugin.getLocale().getString("fish-escape"));
                event.setCancelled(true);
                return;
            }

            PlayerCatchCustomFishEvent customEvent = new PlayerCatchCustomFishEvent(event.getPlayer(), fish, event);
            plugin.getServer().getPluginManager().callEvent(customEvent);

            if (customEvent.isCancelled()) {
                return;
            }


            String msgFish = getMessage("catch-fish", event.getPlayer(), fish);
            int ancFish = plugin.getConfig().getInt("messages.announce-catch");

            if (fish.getRarity().isNoBroadcast()) {
                ancFish = 0;
            }

            announceMessage(event.getPlayer(), msgFish, ancFish);


            if (fish.getRarity().hasFirework()) {
                launchFirework(event.getPlayer().getLocation().add(0, 1, 0));
            }


            if (!fish.getCommands().isEmpty()) {
                executeCommands(event.getPlayer(), fish);
            }


            if (contest.hasStarted()) {
                if (contest.isNew1st(fish)) {
                    String msgContest = getMessage("get-1st", event.getPlayer(), fish);
                    int ancContest = plugin.getConfig().getInt("messages.announce-new-1st");

                    announceMessage(event.getPlayer(), msgContest, ancContest);
                }

                contest.addRecord(event.getPlayer(), fish);
            }

            ItemStack itemStack = plugin.getFishManager().getItemStack(fish, event.getPlayer().getName());
            Item caught = (Item) event.getCaught();
            caught.setItemStack(itemStack);
        }
    }

    private boolean hasEnabled(PlayerFishEvent event) {
        boolean enabled = !plugin.getConfig().getStringList("general.contest-disabled-worlds")
                .contains(event.getPlayer().getWorld().getName());

        // Check if the world hasn't disabled

        // Check if the contest is ongoing
        if (plugin.getConfig().getBoolean("general.only-for-contest") &&
                !contest.hasStarted())
            enabled = false;

        // Check if the caught is fish
        if (plugin.getConfig().getBoolean("general.replace-only-fish") &&
                ((Item) event.getCaught()).getItemStack().getType() != Material.RAW_FISH)
            enabled = false;

        return enabled;
    }

    private String getMessage(String path, Player player, CaughtFish fish) {
        String message = plugin.getLocale().getString(path);

        message = message.replaceAll("%player%", player.getName())
                .replaceAll("%length%", fish.getLength() + "")
                .replaceAll("%rarity%", fish.getRarity().getDisplayName())
                .replaceAll("%rarity_color%", fish.getRarity().getColor() + "")
                .replaceAll("%fish%", fish.getName())
                .replaceAll("%fish_with_rarity%", (((fish.getRarity().isNoDisplay()) ? "" : fish.getRarity().getDisplayName() + " ")) + fish.getName());

        message = ChatColor.translateAlternateColorCodes('&', message);

        return message;
    }

    private void announceMessage(Player player, String message, int announce) {
        if (message.length() == 0)
            return;

        switch (announce) {
            case -1:
                plugin.getServer().broadcastMessage(message);
                break;
            case 0:
                player.sendMessage(message);
                break;
            default:
                Location loc = player.getLocation();

                for (Player other : player.getWorld().getPlayers()) {
                    if (other.getLocation().distance(loc) <= announce) {
                        other.sendMessage(message);
                    }
                }
        }
    }

    private void executeCommands(Player player, CaughtFish fish) {
        for (String command : fish.getCommands()) {
            String str = command.replaceAll("@p", player.getName())
                    .replaceAll("%fish%", fish.getName())
                    .replaceAll("%length%", fish.getLength() + "");

            str = ChatColor.translateAlternateColorCodes('&', str);

            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), str);
        }
    }

    private void launchFirework(Location loc) {
        Firework firework = loc.getWorld().spawn(loc, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        FireworkEffect effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.AQUA)
                .withFade(Color.BLUE)
                .withTrail()
                .withFlicker()
                .build();
        meta.addEffect(effect);
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }
}
