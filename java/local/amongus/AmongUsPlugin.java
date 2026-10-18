package local.amongus;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AmongUsPlugin extends JavaPlugin implements Listener {

    private boolean running = false;
    private final Map<UUID, Role> roles = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("AmongUs загружен.");
    }

    // ---------------- Команды ----------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            msg(sender, "Команды: /amongus start | stop | setlobby | setspawn | status | role", NamedTextColor.YELLOW);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "setlobby" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Эту команду можно использовать только в игре.", NamedTextColor.RED);
                    return true;
                }
                getConfig().set("lobby", p.getLocation());
                saveConfig();
                msg(sender, "Точка лобби сохранена.", NamedTextColor.GREEN);
            }
            case "setspawn" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Эту команду можно использовать только в игре.", NamedTextColor.RED);
                    return true;
                }
                getConfig().set("spawn", p.getLocation());
                saveConfig();
                msg(sender, "Точка старта игры сохранена.", NamedTextColor.GREEN);
            }
            case "start" -> startGame(sender);
            case "stop" -> {
                if (!running) {
                    msg(sender, "Игра сейчас не идёт.", NamedTextColor.RED);
                    return true;
                }
                endGame(Component.text("Игра остановлена администратором.", NamedTextColor.YELLOW));
            }
            case "status" -> {
                if (!running) {
                    msg(sender, "Игра не идёт. Игроков на сервере: " + Bukkit.getOnlinePlayers().size(), NamedTextColor.YELLOW);
                } else {
                    long imp = roles.values().stream().filter(r -> r == Role.IMPOSTOR).count();
                    long crew = roles.size() - imp;
                    msg(sender, "Игра идёт. Экипаж: " + crew + ", предателей: " + imp, NamedTextColor.YELLOW);
                }
            }
            case "role" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Эту команду можно использовать только в игре.", NamedTextColor.RED);
                    return true;
                }
                Role r = roles.get(p.getUniqueId());
                if (!running || r == null) {
                    msg(sender, "Ты сейчас не участвуешь в игре.", NamedTextColor.GRAY);
                } else {
                    msg(sender, "Твоя роль: " + r.title(), r.color());
                }
            }
            default -> msg(sender, "Неизвестная команда. Напиши /amongus", NamedTextColor.RED);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> all = List.of("start", "stop", "setlobby", "setspawn", "status", "role");
            List<String> out = new ArrayList<>();
            for (String s : all) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        return Collections.emptyList();
    }

    // ---------------- Логика игры ----------------

    private void startGame(CommandSender sender) {
        if (running) {
            msg(sender, "Игра уже идёт. Останови её командой /amongus stop.", NamedTextColor.RED);
            return;
        }

        Location spawn = getConfig().getLocation("spawn");
        if (spawn == null) {
            msg(sender, "Сначала задай точку старта: встань на место и напиши /amongus setspawn", NamedTextColor.RED);
            return;
        }

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int min = getConfig().getInt("min-players", 4);
        if (players.size() < min) {
            msg(sender, "Нужно минимум " + min + " игроков, сейчас " + players.size() + ".", NamedTextColor.RED);
            return;
        }

        int impostorCount = impostorCountFor(players.size());
        Collections.shuffle(players);

        roles.clear();
        for (int i = 0; i < players.size(); i++) {
            roles.put(players.get(i).getUniqueId(), i < impostorCount ? Role.IMPOSTOR : Role.CREWMATE);
        }
        running = true;

        List<String> impostorNames = new ArrayList<>();
        for (Player p : players) {
            if (roles.get(p.getUniqueId()) == Role.IMPOSTOR) impostorNames.add(p.getName());
        }

        for (Player p : players) {
            Role role = roles.get(p.getUniqueId());
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.teleport(spawn);

            p.showTitle(Title.title(
                    Component.text(role.title(), role.color()),
                    Component.text(role == Role.IMPOSTOR
                            ? "Устраняй экипаж незаметно"
                            : "Выполняй задания и найди предателей", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofMillis(600))));

            p.sendMessage(Component.text("Твоя роль: " + role.title(), role.color()));
            if (role == Role.IMPOSTOR) {
                p.sendMessage(Component.text("Предатели: " + String.join(", ", impostorNames), NamedTextColor.RED));
            }
        }

        Bukkit.broadcast(Component.text("Игра началась! Игроков: " + players.size()
                + ", предателей: " + impostorCount, NamedTextColor.GOLD));
    }

    private int impostorCountFor(int players) {
        String setting = getConfig().getString("impostors", "auto");
        if (setting != null && !setting.equalsIgnoreCase("auto")) {
            try {
                int n = Integer.parseInt(setting.trim());
                return Math.max(1, Math.min(n, Math.max(1, (players - 1) / 2)));
            } catch (NumberFormatException ignored) {
                getLogger().warning("Неверное значение impostors в config.yml, использую auto.");
            }
        }
        if (players <= 7) return 1;
        if (players <= 12) return 2;
        return 3;
    }

    private void endGame(Component reason) {
        Location lobby = getConfig().getLocation("lobby");
        Bukkit.broadcast(reason);

        // Открываем роли всем
        for (Player p : Bukkit.getOnlinePlayers()) {
            Role r = roles.get(p.getUniqueId());
            if (r != null) {
                Bukkit.broadcast(Component.text(p.getName() + " — " + r.title(), r.color()));
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (roles.containsKey(p.getUniqueId())) {
                p.setGameMode(GameMode.ADVENTURE);
                if (lobby != null) p.teleport(lobby);
            }
        }

        roles.clear();
        running = false;
    }

    private void checkWin() {
        if (!running) return;
        long imp = roles.values().stream().filter(r -> r == Role.IMPOSTOR).count();
        long crew = roles.size() - imp;

        if (imp == 0) {
            endGame(Component.text("Победа экипажа! Все предатели ушли.", NamedTextColor.AQUA));
        } else if (crew == 0 || imp >= crew) {
            endGame(Component.text("Победа предателей!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (!running) return;
        if (roles.remove(event.getPlayer().getUniqueId()) != null) {
            checkWin();
        }
    }

    // ---------------- Вспомогательное ----------------

    private void msg(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text(text, color));
    }
}
