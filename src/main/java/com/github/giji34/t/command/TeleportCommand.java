package com.github.giji34.t.command;

import com.github.giji34.t.Landmark;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class TeleportCommand {
    final Plugin owner;
    HashMap<UUID, HashMap<String, Landmark>> _knownLandmarks;

    public TeleportCommand(Plugin owner) {
        this.owner = owner;
    }

    public void init(File pluginDirectory) throws Exception {
        File json = new File(pluginDirectory, "buildings.tsv");
        if (json.exists()) {
            HashMap<UUID, HashMap<String, Landmark>> landmarks = new HashMap<>();
            BufferedReader br = new BufferedReader(new FileReader(json));
            String line;
            int lineN = 0;
            while ((line = br.readLine()) != null) {
                lineN++;
                if (line.startsWith("#")) {
                    continue;
                }
                String[] tokens = line.split("\t");
                if (tokens.length < 5) {
                    continue;
                }
                String name = tokens[0];
                String yomi = tokens[1];
                double x;
                double y;
                double z;
                UUID uid;
                try {
                    x = parseX(tokens[2], 0);
                    y = parseY(tokens[3], 0);
                    z = parseZ(tokens[4], 0);
                    uid = UUID.fromString(tokens[5]);
                } catch (Exception e) {
                    owner.getLogger().warning("line " + lineN + " parse error: \"" + line + "\"");
                    continue;
                }
                if (!landmarks.containsKey(uid)) {
                    landmarks.put(uid, new HashMap<>());
                }
                landmarks.get(uid).put(yomi, new Landmark(name, new Vector(x, y, z), uid));
            }
            _knownLandmarks = landmarks;
        } else {
            BufferedWriter bw = new BufferedWriter(new FileWriter(json));
            bw.write("#地点名\t地点名読み\tX\tY\tZ\tワールドUID");
            bw.newLine();
            bw.flush();
            bw.close();
        }
    }

    public boolean teleport(Player player, String[] args) {
        if (args.length != 3) {
            return false;
        }
        Location loc = player.getLocation().clone();
        try {
            loc.setX(parseX(args[0], loc.getX()));
            loc.setY(parseY(args[1], loc.getY()));
            loc.setZ(parseZ(args[2], loc.getZ()));
        } catch (Exception e) {
            return false;
        }

        player.teleport(loc);
        return true;
    }

    public boolean teleportToLandmark(Player player, String[] args) {
        if (args.length != 1) {
            return false;
        }
        Location loc = player.getLocation().clone();
        String name = args[0];
        Landmark landmark = findLandmark(player, name);
        if (landmark == null) {
            return true;
        }
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }
        Vector p = landmark.location;
        loc.setX(p.getX());
        loc.setY(p.getY());
        loc.setZ(p.getZ());
        player.teleport(loc);
        return true;
    }

    public boolean guide(Player player, String[] args) {
        if (args.length != 2) {
            return false;
        }
        String targetPlayerName = args[0];
        String landmarkName = args[1];
        Landmark landmark = findLandmark(player, landmarkName);
        if (landmark == null) {
            return true;
        }

        Player targetPlayer = null;
        World world = player.getWorld();
        if (!world.getUID().equals(landmark.worldUID)) {
            player.sendMessage(ChatColor.RED + "建物と違うディメンジョンに居るため案内できません");
            return true;
        }
        for (Player p : world.getPlayers()) {
            if (p.getName().equals(targetPlayerName)) {
                targetPlayer = p;
                break;
            }
        }
        if (targetPlayer == null) {
            player.sendMessage(ChatColor.RED + "案内対象のプレイヤーが見つかりません: \"" + targetPlayerName + "\"");
            return true;
        }
        if (targetPlayer.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage(ChatColor.RED + "自分自身を案内しようとしています。tpb コマンドを使って下さい");
            return true;
        }
        if (!targetPlayer.getWorld().getUID().equals(landmark.worldUID)) {
            player.sendMessage(ChatColor.RED + "案内対象プレイヤーが建物と違うディメンジョンに居るため案内できません");
            return true;
        }

        targetPlayer.sendMessage(ChatColor.GRAY + "\"" + landmark.name + "\" に移動します");
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != null) {
            player.setSpectatorTarget(null);
        }
        Location loc = targetPlayer.getLocation();
        loc.setX(landmark.location.getX());
        loc.setY(landmark.location.getY());
        loc.setZ(landmark.location.getZ());
        player.setGameMode(GameMode.SPECTATOR);
        targetPlayer.teleport(loc);
        player.teleport(loc);
        final Player p = targetPlayer;
        owner.getServer().getScheduler().runTaskLater(owner, () -> player.setSpectatorTarget(p), 1);
        return true;
    }

    @Nullable
    Landmark findLandmark(Player player, String landmarkName) {
        UUID uid = player.getWorld().getUID();
        HashMap<String, Landmark> knownLandmarks = ensureKnownLandmarks(uid);
        Landmark landmark = null;
        if (knownLandmarks.containsKey(landmarkName)) {
            landmark = knownLandmarks.get(landmarkName);
        } else {
            ArrayList<Landmark> candidate = pickupCandidates(player, landmarkName);
            HashSet<String> uniq = new HashSet<>();
            for (Landmark l : candidate) {
                uniq.add(l.name);
            }
            if (uniq.size() == 1) {
                landmark = candidate.get(0);
            } else {
                player.sendMessage(ChatColor.RED + "\"" + landmarkName + "\"に合致する建物が見つかりません");
            }
        }
        if (landmark == null) {
            player.sendMessage(ChatColor.RED + "\"" + landmarkName + "\"に合致する建物が見つかりません");
            return null;
        }
        if (landmark.worldUID.equals(uid)) {
            return landmark;
        } else {
            player.sendMessage(ChatColor.RED + "建物 \"" + landmarkName + "\" は現在居るディメンジョンには存在しません");
            return null;
        }
    }

    ArrayList<Landmark> pickupCandidates(Player player, String arg) {
        UUID uid = player.getWorld().getUID();
        HashMap<String, Landmark> landmarks = ensureKnownLandmarks(uid);
        ArrayList<Landmark> availableLandmarks = new ArrayList<>();
        landmarks.forEach((yomi, landmark) -> {
            if (!landmark.worldUID.equals(uid)) {
                return;
            }
            if (arg.length() == 0) {
                availableLandmarks.add(landmark);
            } else if (yomi.startsWith(arg)) {
                availableLandmarks.add(landmark);
            }
        });
        return availableLandmarks;
    }

    public synchronized HashMap<String, Landmark> ensureKnownLandmarks(UUID uuid) {
        if (_knownLandmarks.containsKey(uuid)) {
            return new HashMap<>(_knownLandmarks.get(uuid));
        } else {
            return new HashMap<>();
        }
    }

    private static double parseX(String s, double defaultValue) {
        return parseCoordinate(s, defaultValue, 0.5);
    }

    private static double parseY(String s, double defaultValue) {
        return parseCoordinate(s, defaultValue, 0);
    }

    private static double parseZ(String s, double defaultValue) {
        return parseCoordinate(s, defaultValue, 0.5);
    }

    private static double parseCoordinate(String s, double defaultValue, double offset) {
        if ("~".equals(s)) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(s);
            return v + offset;
        } catch (Exception ignored) {
        }
        return Double.parseDouble(s);
    }
}