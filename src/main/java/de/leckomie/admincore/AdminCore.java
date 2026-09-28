package de.leckomie.admincore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AdminCore extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Boolean> vanishPlayers = new HashMap<>();
    private final Map<UUID, Boolean> flyPlayers = new HashMap<>();
    private final Map<UUID, String> playerRanks = new HashMap<>();
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final Map<UUID, Long> mutedPlayers = new HashMap<>();
    private final Set<UUID> staffChat = new HashSet<>();
    private final Map<UUID, Inventory> invseeMenus = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        Arrays.asList(
                "gmc", "gms", "gmsp", "gma",
                "fly", "godmode",
                "v", "vanish",
                "tp", "tphere", "tpall",
                "rank",
                "freeze", "unfreeze",
                "invsee",
                "kick", "ban", "unban",
                "mute", "unmute",
                "heal", "feed",
                "give", "clearinv",
                "speed", "broadcast",
                "staffchat"
        ).forEach(name -> this.getCommand(name).setExecutor(this));

        // Scoreboard teams initialisieren
        setupRankTeams();

        // Bereits online Spieler aufsetzen
        for (Player p : Bukkit.getOnlinePlayers()) {
            updatePlayerRankDisplay(p);
            applyVanishState(p);
        }

        // Dauerhafte Überprüfung für Mute-Timeouts
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (UUID uuid : new ArrayList<>(mutedPlayers.keySet())) {
                    long until = mutedPlayers.get(uuid);
                    if (now >= until) {
                        mutedPlayers.remove(uuid);
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            p.sendMessage(ChatColor.GREEN + "Du bist wieder entmutet.");
                        }
                    }
                }
            }
        }.runTaskTimer(this, 20L, 20L);

        getLogger().info("AdminCore aktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler können diese Befehle nutzen.");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase(Locale.ROOT);

        if (cmd.equals("gmc")) {
            if (!player.hasPermission("admincore.use")) return noPerm(player);
            player.setGameMode(GameMode.CREATIVE);
            player.sendMessage(ChatColor.GREEN + "Spielmodus gesetzt: Creative");
            return true;
        }

        if (cmd.equals("gms")) {
            if (!player.hasPermission("admincore.use")) return noPerm(player);
            player.setGameMode(GameMode.SURVIVAL);
            player.sendMessage(ChatColor.GREEN + "Spielmodus gesetzt: Survival");
            return true;
        }

        if (cmd.equals("gmsp")) {
            if (!player.hasPermission("admincore.use")) return noPerm(player);
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(ChatColor.GREEN + "Spielmodus gesetzt: Spectator");
            return true;
        }

        if (cmd.equals("gma")) {
            if (!player.hasPermission("admincore.use")) return noPerm(player);
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(ChatColor.GREEN + "Spielmodus gesetzt: Adventure");
            return true;
        }

        if (cmd.equals("fly") || cmd.equals("godmode")) {
            if (!player.hasPermission("admincore.fly")) return noPerm(player);
            if (args.length > 0) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                toggleFly(target, player);
                return true;
            }
            toggleFly(player, player);
            return true;
        }

        if (cmd.equals("v") || cmd.equals("vanish")) {
            if (!player.hasPermission("admincore.vanish")) return noPerm(player);
            toggleVanish(player);
            return true;
        }

        if (cmd.equals("tp")) {
            if (!player.hasPermission("admincore.tp")) return noPerm(player);
            if (args.length == 1) {
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                player.teleport(target.getLocation());
                player.sendMessage(ChatColor.GREEN + "Zu " + target.getName() + " teleportiert.");
                return true;
            }

            if (args.length == 2) {
                Player first = Bukkit.getPlayerExact(args[0]);
                Player second = Bukkit.getPlayerExact(args[1]);
                if (first == null || second == null) {
                    player.sendMessage(ChatColor.RED + "Mindestens ein Spieler wurde nicht gefunden.");
                    return true;
                }
                first.teleport(second.getLocation());
                first.sendMessage(ChatColor.GRAY + "Du wurdest zu " + second.getName() + " teleportiert.");
                player.sendMessage(ChatColor.GREEN + first.getName() + " wurde zu " + second.getName() + " teleportiert.");
                return true;
            }

            if (args.length == 3) {
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    double z = Double.parseDouble(args[2]);
                    player.teleport(new org.bukkit.Location(player.getWorld(), x, y, z));
                    player.sendMessage(ChatColor.GREEN + "Zu Koordinaten teleportiert: " + x + ", " + y + ", " + z);
                    return true;
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Usage: /tp <Spieler> oder /tp <x> <y> <z>");
                    return true;
                }
            }

            player.sendMessage(ChatColor.RED + "Usage: /tp <Spieler> oder /tp <x> <y> <z>");
            return true;
        }

        if (cmd.equals("tphere")) {
            if (!player.hasPermission("admincore.tp")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /tphere <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            target.teleport(player.getLocation());
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde zu dir teleportiert.");
            return true;
        }

        if (cmd.equals("tpall")) {
            if (!player.hasPermission("admincore.tp.admin")) return noPerm(player);
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player)) {
                    online.teleport(player.getLocation());
                }
            }
            player.sendMessage(ChatColor.GREEN + "Alle Spieler wurden zu dir teleportiert.");
            return true;
        }

        if (cmd.equals("rank")) {
            if (!player.hasPermission("admincore.rank")) return noPerm(player);
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /rank give <Spieler> <Rang> | /rank remove <Spieler> <Rang> | /rank list <Spieler>");
                return true;
            }

            String action = args[0].toLowerCase(Locale.ROOT);
            if (action.equals("give")) {
                if (args.length != 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /rank give <Spieler> <Rang>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                String rank = args[2];
                playerRanks.put(target.getUniqueId(), rank);
                updatePlayerRankDisplay(target);
                player.sendMessage(ChatColor.GREEN + "Rang " + rank + " wurde " + target.getName() + " gegeben.");
                return true;
            }

            if (action.equals("remove")) {
                if (args.length != 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /rank remove <Spieler> <Rang>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                String rank = args[2];
                String current = playerRanks.get(target.getUniqueId());
                if (current != null && current.equalsIgnoreCase(rank)) {
                    playerRanks.remove(target.getUniqueId());
                    updatePlayerRankDisplay(target);
                    player.sendMessage(ChatColor.GREEN + "Rang entfernt.");
                } else {
                    player.sendMessage(ChatColor.RED + "Dieser Spieler hat diesen Rang nicht.");
                }
                return true;
            }

            if (action.equals("list")) {
                if (args.length != 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /rank list <Spieler>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                    return true;
                }
                String rank = playerRanks.get(target.getUniqueId());
                player.sendMessage(ChatColor.YELLOW + target.getName() + " hat Rang: " + (rank == null ? "Kein Rang" : rank));
                return true;
            }

            player.sendMessage(ChatColor.RED + "Unbekannte Aktion. Nutze give/list/remove.");
            return true;
        }

        if (cmd.equals("freeze")) {
            if (!player.hasPermission("admincore.freeze")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /freeze <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            frozenPlayers.add(target.getUniqueId());
            target.sendMessage(ChatColor.RED + "Du wurdest eingefroren.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde eingefroren.");
            return true;
        }

        if (cmd.equals("unfreeze")) {
            if (!player.hasPermission("admincore.freeze")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /unfreeze <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            frozenPlayers.remove(target.getUniqueId());
            target.sendMessage(ChatColor.GREEN + "Du wurdest entfroren.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde entfroren.");
            return true;
        }

        if (cmd.equals("invsee")) {
            if (!player.hasPermission("admincore.invsee")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /invsee <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            Inventory inv = Bukkit.createInventory(null, 54, "Inventar von " + target.getName());
            inv.setContents(target.getInventory().getContents());
            player.openInventory(inv);
            invseeMenus.put(player.getUniqueId(), inv);
            return true;
        }

        if (cmd.equals("kick")) {
            if (!player.hasPermission("admincore.kick")) return noPerm(player);
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /kick <Spieler> [Grund]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            String msg = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Du wurdest gekickt.";
            target.kickPlayer(ChatColor.RED + msg);
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde gekickt.");
            return true;
        }

        if (cmd.equals("ban")) {
            if (!player.hasPermission("admincore.ban")) return noPerm(player);
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /ban <Spieler> [Grund]");
                return true;
            }
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Du wurdest gebannt.";
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target.getName(), reason, null, player.getName());
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                online.kickPlayer(ChatColor.RED + "Gebannt: " + reason);
            }
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde gebannt.");
            return true;
        }

        if (cmd.equals("unban")) {
            if (!player.hasPermission("admincore.ban")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /unban <Spieler>");
                return true;
            }
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(args[0]);
            player.sendMessage(ChatColor.GREEN + args[0] + " wurde entbannt.");
            return true;
        }

        if (cmd.equals("mute")) {
            if (!player.hasPermission("admincore.mute")) return noPerm(player);
            if (args.length < 1) {
                player.sendMessage(ChatColor.RED + "Usage: /mute <Spieler> [Dauer in Minuten]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            long minutes = 60L;
            if (args.length >= 2) {
                try {
                    minutes = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Dauer muss eine Zahl sein.");
                    return true;
                }
            }
            long until = System.currentTimeMillis() + (minutes * 60L * 1000L);
            mutedPlayers.put(target.getUniqueId(), until);
            target.sendMessage(ChatColor.RED + "Du bist für " + minutes + " Minuten gemutet.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde gemutet.");
            return true;
        }

        if (cmd.equals("unmute")) {
            if (!player.hasPermission("admincore.mute")) return noPerm(player);
            if (args.length != 1) {
                player.sendMessage(ChatColor.RED + "Usage: /unmute <Spieler>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            mutedPlayers.remove(target.getUniqueId());
            target.sendMessage(ChatColor.GREEN + "Du bist nicht mehr gemutet.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde entmutet.");
            return true;
        }

        if (cmd.equals("heal")) {
            if (!player.hasPermission("admincore.heal")) return noPerm(player);
            if (args.length == 0) {
                player.setHealth(player.getMaxHealth());
                player.setFoodLevel(20);
                player.sendMessage(ChatColor.GREEN + "Du wurdest geheilt.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            target.setHealth(target.getMaxHealth());
            target.setFoodLevel(20);
            target.sendMessage(ChatColor.GREEN + "Du wurdest geheilt.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde geheilt.");
            return true;
        }

        if (cmd.equals("feed")) {
            if (!player.hasPermission("admincore.feed")) return noPerm(player);
            if (args.length == 0) {
                player.setFoodLevel(20);
                player.sendMessage(ChatColor.GREEN + "Du wurdest gesättigt.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            target.setFoodLevel(20);
            target.sendMessage(ChatColor.GREEN + "Du wurdest gesättigt.");
            player.sendMessage(ChatColor.GREEN + target.getName() + " wurde gesättigt.");
            return true;
        }

        if (cmd.equals("give")) {
            if (!player.hasPermission("admincore.give")) return noPerm(player);
            if (args.length < 2) {
                player.sendMessage(ChatColor.RED + "Usage: /give <Spieler> <Item> [Menge]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            Material material;
            try {
                material = Material.valueOf(args[1].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage(ChatColor.RED + "Unbekanntes Item.");
                return true;
            }
            int amount = 1;
            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "Menge muss eine Zahl sein.");
                    return true;
                }
            }
            target.getInventory().addItem(new ItemStack(material, amount));
            player.sendMessage(ChatColor.GREEN + amount + "x " + material.name() + " an " + target.getName() + " gegeben.");
            return true;
        }

        if (cmd.equals("clearinv")) {
            if (!player.hasPermission("admincore.clearinv")) return noPerm(player);
            if (args.length == 0) {
                player.getInventory().clear();
                player.sendMessage(ChatColor.GREEN + "Dein Inventar wurde geleert.");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            target.getInventory().clear();
            target.sendMessage(ChatColor.RED + "Dein Inventar wurde von einem Admin geleert.");
            player.sendMessage(ChatColor.GREEN + "Inventar von " + target.getName() + " geleert.");
            return true;
        }

        if (cmd.equals("speed")) {
            if (!player.hasPermission("admincore.speed")) return noPerm(player);
            if (args.length != 2) {
                player.sendMessage(ChatColor.RED + "Usage: /speed <Spieler> <1-10>");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                player.sendMessage(ChatColor.RED + "Spieler nicht gefunden.");
                return true;
            }
            try {
                float speed = Float.parseFloat(args[1]);
                if (speed < 1 || speed > 10) {
                    player.sendMessage(ChatColor.RED + "Geschwindigkeit muss zwischen 1 und 10 liegen.");
                    return true;
                }
                target.setWalkSpeed(speed / 10f);
                player.sendMessage(ChatColor.GREEN + "Geschwindigkeit für " + target.getName() + " gesetzt auf " + speed + ".");
            } catch (NumberFormatException e) {
                player.sendMessage(ChatColor.RED + "Wert muss eine Zahl sein.");
            }
            return true;
        }

        if (cmd.equals("broadcast")) {
            if (!player.hasPermission("admincore.broadcast")) return noPerm(player);
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /broadcast <Nachricht>");
                return true;
            }
            String text = ChatColor.translateAlternateColorCodes('&', String.join(" ", args));
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "[Broadcast] " + ChatColor.RESET + text);
            return true;
        }

        if (cmd.equals("staffchat")) {
            if (!player.hasPermission("admincore.staffchat")) return noPerm(player);
            if (args.length == 0) {
                player.sendMessage(ChatColor.RED + "Usage: /staffchat <Nachricht>");
                return true;
            }
            String message = String.join(" ", args);
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("admincore.staffchat") || p.isOp()) {
                    p.sendMessage(ChatColor.AQUA + "[Staff] " + player.getName() + ": " + message);
                }
            }
            return true;
        }

        return false;
    }

    private boolean noPerm(Player player) {
        player.sendMessage(ChatColor.RED + "Du hast keine Rechte dafür.");
        return true;
    }

    private void toggleFly(Player target, Player executor) {
        boolean state = flyPlayers.getOrDefault(target.getUniqueId(), false);
        state = !state;
        flyPlayers.put(target.getUniqueId(), state);

        if (state) {
            target.setAllowFlight(true);
            target.setFlying(true);
            target.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 255, false, false));
            target.setInvulnerable(true);
            target.sendMessage(ChatColor.GREEN + "Flight / GodMode aktiviert.");
            executor.sendMessage(ChatColor.GREEN + target.getName() + " wurde in Flight/GodMode gesetzt.");
        } else {
            target.setAllowFlight(false);
            target.setFlying(false);
            target.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            target.setInvulnerable(false);
            target.sendMessage(ChatColor.RED + "Flight / GodMode deaktiviert.");
            executor.sendMessage(ChatColor.RED + target.getName() + " wurde aus Flight/GodMode entfernt.");
        }
    }

    private void toggleVanish(Player player) {
        boolean vanish = vanishPlayers.getOrDefault(player.getUniqueId(), false);
        vanish = !vanish;
        vanishPlayers.put(player.getUniqueId(), vanish);
        applyVanishState(player);
        player.sendMessage(vanish ? ChatColor.GREEN + "Vanish aktiviert." : ChatColor.RED + "Vanish deaktiviert.");
    }

    private void applyVanishState(Player player) {
        boolean vanish = vanishPlayers.getOrDefault(player.getUniqueId(), false);
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online == player) continue;
            if (vanish) {
                online.hidePlayer(this, player);
            } else {
                online.showPlayer(this, player);
            }
        }
    }

    private void setupRankTeams() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getMainScoreboard();

        for (String name : Arrays.asList("owner", "admin", "mod", "helper", "vip", "default")) {
            Team team = board.getTeam(name);
            if (team == null) {
                team = board.registerNewTeam(name);
            }
            team.setPrefix(ChatColor.GRAY.toString());
            team.setSuffix(ChatColor.RESET.toString());
        }
    }

    private void updatePlayerRankDisplay(Player player) {
        String rank = playerRanks.get(player.getUniqueId());
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) return;
        Scoreboard board = manager.getMainScoreboard();

        for (String teamName : Arrays.asList("owner", "admin", "mod", "helper", "vip", "default")) {
            Team team = board.getTeam(teamName);
            if (team != null) team.removeEntry(player.getName());
        }

        if (rank == null) {
            player.setPlayerListName(player.getName());
            player.setDisplayName(player.getName());
            return;
        }

        String prefix = switch (rank.toLowerCase(Locale.ROOT)) {
            case "owner" -> ChatColor.DARK_RED + "[Owner] " + ChatColor.RESET;
            case "admin" -> ChatColor.RED + "[Admin] " + ChatColor.RESET;
            case "mod" -> ChatColor.GOLD + "[Mod] " + ChatColor.RESET;
            case "helper" -> ChatColor.AQUA + "[Helper] " + ChatColor.RESET;
            case "vip" -> ChatColor.LIGHT_PURPLE + "[VIP] " + ChatColor.RESET;
            default -> ChatColor.GRAY + "[" + rank + "] " + ChatColor.RESET;
        };

        String teamKey = rank.toLowerCase(Locale.ROOT);
        Team team = board.getTeam(teamKey);
        if (team == null) {
            team = board.registerNewTeam(teamKey);
        }

        team.setPrefix(prefix);
        team.addEntry(player.getName());

        player.setPlayerListName(prefix + player.getName());
        player.setDisplayName(prefix + player.getName());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updatePlayerRankDisplay(player);
        applyVanishState(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        vanishPlayers.remove(event.getPlayer().getUniqueId());
        flyPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getEntity() instanceof Player target) {
            if (flyPlayers.getOrDefault(target.getUniqueId(), false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChat(PlayerChatEvent event) {
        Player player = event.getPlayer();
        if (mutedPlayers.containsKey(player.getUniqueId())) {
            long until = mutedPlayers.get(player.getUniqueId());
            if (System.currentTimeMillis() < until) {
                event.setCancelled(true);
                long remainingSeconds = (until - System.currentTimeMillis()) / 1000;
                player.sendMessage(ChatColor.RED + "Du bist gemutet. Verbleibend: " + remainingSeconds + "s");
                return;
            }
            mutedPlayers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (frozenPlayers.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().startsWith("Inventar von ")) {
            event.setCancelled(true);
        }
    }
}
