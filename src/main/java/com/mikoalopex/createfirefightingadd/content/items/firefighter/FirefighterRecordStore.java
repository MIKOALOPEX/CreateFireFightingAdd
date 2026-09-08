package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mikoalopex.createfirefightingadd.Config;
import com.mikoalopex.createfirefightingadd.CreateFireFightingAdd;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

public final class FirefighterRecordStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int SOURCE_BINDING_TICKS = 20 * 300;
	private static final long INVITE_COOLDOWN_MS = 30_000L;
	private static final long REJECT_COOLDOWN_MS = 120_000L;
	private static Store loaded;

	private FirefighterRecordStore() {
	}

	public static PlayerRecord register(ServerPlayer player) {
		Store store = store(player.server);
		Record record = store.players.computeIfAbsent(player.getUUID(), id -> new Record());
		record.name = player.getGameProfile().getName();
		record.registered = true;
		if (record.teamId == null)
			record.teamId = UUID.randomUUID();
		Team team = store.teams.computeIfAbsent(record.teamId, id -> new Team());
		team.members.add(player.getUUID());
		if (team.leader == null)
			team.leader = player.getUUID();
		store.save();
		return recordView(player.getUUID(), record);
	}

	public static Optional<PlayerRecord> record(MinecraftServer server, UUID playerId) {
		Record record = store(server).players.get(playerId);
		return record == null || !record.registered ? Optional.empty() : Optional.of(recordView(playerId, record));
	}

	public static FirefighterHandbookSnapshot snapshot(ServerPlayer viewer, @org.jetbrains.annotations.Nullable UUID owner) {
		return snapshot(viewer, owner, 0, true);
	}

	public static FirefighterHandbookSnapshot snapshot(ServerPlayer viewer, @org.jetbrains.annotations.Nullable UUID owner,
			int localCount, boolean useServerCount) {
		Store store = store(viewer.server);
		Record record = owner == null ? null : store.players.get(owner);
		boolean registered = record != null && record.registered;
		int count = registered && useServerCount ? Math.max(0, record.extinguished) : Math.max(0, localCount);
		String name = registered ? record.name : "";
		List<FirefighterHandbookSnapshot.PlayerEntry> players = store.registeredPlayers(owner);
		List<FirefighterHandbookSnapshot.PlayerEntry> team = registered ? store.teamMembers(record.teamId) : List.of();
		store.saveIfDirty();
		return new FirefighterHandbookSnapshot(Config.firefighterExtinguishRecordsEnabled, registered,
			count >= FirefighterHandbookSnapshot.GOAL, owner, name, count, registered && store.isTeamLeader(record.teamId, owner),
			players, team);
	}

	public static int count(MinecraftServer server, UUID playerId) {
		Record record = store(server).players.get(playerId);
		return record == null ? 0 : Math.max(0, record.extinguished);
	}

	public static void recordExtinguish(Level level, Collection<UUID> owners, int amount) {
		if (!Config.firefighterExtinguishRecordsEnabled || !(level instanceof ServerLevel serverLevel)
			|| owners.isEmpty() || amount <= 0)
			return;
		Store store = store(serverLevel.getServer());
		boolean changed = false;
		for (UUID owner : new HashSet<>(owners)) {
			Record record = store.players.get(owner);
			if (record == null || !record.registered)
				continue;
			record.extinguished = Math.max(0, record.extinguished + amount);
			changed = true;
		}
		if (changed)
			store.saveLater();
	}

	public static void recordExtinguish(Level level, UUID owner, int amount) {
		recordExtinguish(level, List.of(owner), amount);
	}

	public static void bindSource(Level level, BlockPos pos, UUID owner) {
		if (!Config.firefighterExtinguishRecordsEnabled || !(level instanceof ServerLevel serverLevel))
			return;
		Store store = store(serverLevel.getServer());
		SourceBinding binding = store.sourceBindings.computeIfAbsent(SourceKey.of(level, pos), key -> new SourceBinding());
		binding.owners.add(owner);
		binding.expiresAtGameTime = Math.max(binding.expiresAtGameTime, level.getGameTime() + SOURCE_BINDING_TICKS);
	}

	public static List<UUID> activeOwners(Level level, BlockPos pos) {
		if (!Config.firefighterExtinguishRecordsEnabled || !(level instanceof ServerLevel serverLevel))
			return List.of();
		Store store = store(serverLevel.getServer());
		SourceKey key = SourceKey.of(level, pos);
		SourceBinding binding = store.sourceBindings.get(key);
		if (binding == null)
			return List.of();
		if (binding.expiresAtGameTime < level.getGameTime()) {
			store.sourceBindings.remove(key);
			return List.of();
		}
		return List.copyOf(binding.owners);
	}

	public static boolean invite(ServerPlayer inviter, UUID target) {
		Store store = store(inviter.server);
		if (inviter.getUUID().equals(target))
			return false;
		long now = System.currentTimeMillis();
		InviteKey key = new InviteKey(inviter.getUUID(), target);
		Long blockedUntil = store.inviteCooldowns.get(key);
		if (blockedUntil != null && blockedUntil > now) {
			inviter.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.invite_wait")
				.withStyle(ChatFormatting.RED), true);
			return false;
		}
		if (!store.isRegistered(inviter.getUUID()) || !store.isRegistered(target))
			return false;
		ServerPlayer targetPlayer = inviter.server.getPlayerList().getPlayer(target);
		if (targetPlayer == null) {
			inviter.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.player_offline")
				.withStyle(ChatFormatting.RED), true);
			return false;
		}
		store.pendingInvites.put(target, inviter.getUUID());
		store.inviteCooldowns.put(key, now + INVITE_COOLDOWN_MS);
		FirefighterInvitePacket.send(targetPlayer, inviter.getUUID(), inviter.getGameProfile().getName());
		inviter.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.invite_sent"), true);
		return true;
	}

	public static void respondInvite(ServerPlayer target, UUID inviterId, boolean accepted) {
		Store store = store(target.server);
		if (!inviterId.equals(store.pendingInvites.remove(target.getUUID())))
			return;
		ServerPlayer inviter = target.server.getPlayerList().getPlayer(inviterId);
		if (!accepted) {
			store.inviteCooldowns.put(new InviteKey(inviterId, target.getUUID()),
				System.currentTimeMillis() + REJECT_COOLDOWN_MS);
			if (inviter != null)
				inviter.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.invite_rejected",
					target.getGameProfile().getName()), false);
			return;
		}
		store.joinTeam(inviterId, target.getUUID());
		store.save();
		if (inviter != null)
			inviter.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.invite_accepted",
				target.getGameProfile().getName()), false);
		target.displayClientMessage(Component.translatable("createfirefightingadd.firefighter_handbook.team_joined"), false);
	}

	public static boolean kickMember(ServerPlayer captain, UUID targetId) {
		Store store = store(captain.server);
		boolean changed = store.kickMember(captain.getUUID(), targetId);
		if (changed)
			store.save();
		return changed;
	}

	public static boolean hasTeamPairNear(ServerLevel level, BlockPos center) {
		Store store = store(level.getServer());
		AABB area = new AABB(center).inflate(4.0);
		Map<UUID, Integer> nearbyTeams = new HashMap<>();
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
			Record record = store.players.get(player.getUUID());
			if (record == null || !record.registered || record.teamId == null)
				continue;
			int count = nearbyTeams.merge(record.teamId, 1, Integer::sum);
			if (count >= 2)
				return true;
		}
		return false;
	}

	private static PlayerRecord recordView(UUID id, Record record) {
		return new PlayerRecord(id, record.name, Math.max(0, record.extinguished));
	}

	private static Store store(MinecraftServer server) {
		Path path = recordsPath(server);
		if (loaded == null || !loaded.path.equals(path))
			loaded = Store.load(path);
		return loaded;
	}

	private static Path recordsPath(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT)
			.resolve(CreateFireFightingAdd.MODID)
			.resolve("firefighter_records.json");
	}

	public record PlayerRecord(UUID id, String name, int extinguished) {
	}

	private record SourceKey(String dimension, long pos) {
		static SourceKey of(Level level, BlockPos pos) {
			return new SourceKey(level.dimension().location().toString(), pos.asLong());
		}
	}

	private record InviteKey(UUID inviter, UUID target) {
	}

	private static final class SourceBinding {
		final Set<UUID> owners = new HashSet<>();
		long expiresAtGameTime;
	}

	private static final class Record {
		String name = "";
		boolean registered;
		int extinguished;
		UUID teamId;
	}

	private static final class Team {
		UUID leader;
		final Set<UUID> members = new HashSet<>();
	}

	private static final class Store {
		final Path path;
		final Map<UUID, Record> players = new HashMap<>();
		final Map<UUID, Team> teams = new HashMap<>();
		// These entries describe live interactions only; player and team records are persisted.
		final Map<SourceKey, SourceBinding> sourceBindings = new HashMap<>();
		final Map<UUID, UUID> pendingInvites = new HashMap<>();
		final Map<InviteKey, Long> inviteCooldowns = new HashMap<>();
		boolean dirty;
		long lastSaveMillis;
		long lastSaveWarningMillis;

		private Store(Path path) {
			this.path = path;
		}

		static Store load(Path path) {
			Store store = new Store(path);
			if (!Files.exists(path))
				return store;
			try (Reader reader = Files.newBufferedReader(path)) {
				JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
				JsonObject playersJson = root.has("players") ? root.getAsJsonObject("players") : new JsonObject();
				for (String key : playersJson.keySet()) {
					UUID id = UUID.fromString(key);
					JsonObject player = playersJson.getAsJsonObject(key);
					Record record = new Record();
					record.name = player.has("name") ? player.get("name").getAsString() : "";
					record.registered = player.has("registered") && player.get("registered").getAsBoolean();
					record.extinguished = player.has("extinguished") ? Math.max(0, player.get("extinguished").getAsInt()) : 0;
					if (player.has("teamId") && !player.get("teamId").getAsString().isBlank())
						record.teamId = UUID.fromString(player.get("teamId").getAsString());
					store.players.put(id, record);
				}
				JsonObject teamsJson = root.has("teams") ? root.getAsJsonObject("teams") : new JsonObject();
				for (String key : teamsJson.keySet()) {
					UUID id = UUID.fromString(key);
					Team team = new Team();
					JsonObject teamJson = teamsJson.getAsJsonObject(key);
					if (teamJson.has("leader") && !teamJson.get("leader").getAsString().isBlank())
						team.leader = UUID.fromString(teamJson.get("leader").getAsString());
					if (teamJson.has("members"))
						for (var member : teamJson.getAsJsonArray("members"))
							team.members.add(UUID.fromString(member.getAsString()));
					if (team.leader == null && !team.members.isEmpty())
						team.leader = team.members.iterator().next();
					store.teams.put(id, team);
				}
			} catch (IOException | RuntimeException e) {
				CreateFireFightingAdd.LOGGER.warn(
					"Could not read firefighter records from '{}'. Existing records will not be loaded: {}",
					path, e.toString());
				CreateFireFightingAdd.LOGGER.debug("Firefighter record read failure", e);
			}
			return store;
		}

		void save() {
			saveNow();
		}

		void saveLater() {
			dirty = true;
			if (System.currentTimeMillis() - lastSaveMillis >= 5000L)
				saveNow();
		}

		void saveIfDirty() {
			if (dirty)
				saveNow();
		}

		private void saveNow() {
			JsonObject root = new JsonObject();
			root.addProperty("version", 1);
			JsonObject playersJson = new JsonObject();
			for (Map.Entry<UUID, Record> entry : players.entrySet()) {
				Record record = entry.getValue();
				JsonObject player = new JsonObject();
				player.addProperty("name", record.name);
				player.addProperty("registered", record.registered);
				player.addProperty("extinguished", Math.max(0, record.extinguished));
				player.addProperty("teamId", record.teamId == null ? "" : record.teamId.toString());
				playersJson.add(entry.getKey().toString(), player);
			}
			root.add("players", playersJson);
			JsonObject teamsJson = new JsonObject();
			for (Map.Entry<UUID, Team> entry : teams.entrySet()) {
				JsonObject team = new JsonObject();
				team.addProperty("leader", entry.getValue().leader == null ? "" : entry.getValue().leader.toString());
				team.add("members", GSON.toJsonTree(entry.getValue().members.stream().map(UUID::toString).toList()));
				teamsJson.add(entry.getKey().toString(), team);
			}
			root.add("teams", teamsJson);
			try {
				Files.createDirectories(path.getParent());
				try (Writer writer = Files.newBufferedWriter(path)) {
					GSON.toJson(root, writer);
				}
				dirty = false;
				lastSaveMillis = System.currentTimeMillis();
			} catch (IOException e) {
				long now = System.currentTimeMillis();
				if (now - lastSaveWarningMillis >= 30_000L) {
					lastSaveWarningMillis = now;
					CreateFireFightingAdd.LOGGER.warn(
						"Could not write firefighter records to '{}'. Check server file permissions and free space: {}",
						path, e.toString());
				}
				CreateFireFightingAdd.LOGGER.debug("Firefighter record write failure", e);
			}
		}

		boolean isRegistered(UUID id) {
			Record record = players.get(id);
			return record != null && record.registered;
		}

		void joinTeam(UUID inviterId, UUID targetId) {
			Record inviter = players.get(inviterId);
			Record target = players.get(targetId);
			if (inviter == null || target == null || inviter.teamId == null)
				return;
			clearPendingInvitesFor(targetId);
			removeFromAllTeams(targetId);
			target.teamId = inviter.teamId;
			Team team = teams.computeIfAbsent(inviter.teamId, id -> new Team());
			team.members.add(targetId);
			if (team.leader == null)
				team.leader = inviterId;
		}

		boolean kickMember(UUID captainId, UUID targetId) {
			if (captainId.equals(targetId))
				return false;
			Record captain = players.get(captainId);
			Record target = players.get(targetId);
			if (captain == null || target == null || captain.teamId == null
				|| !isTeamLeader(captain.teamId, captainId) || !captain.teamId.equals(target.teamId))
				return false;
			removeFromAllTeams(targetId);
			target.teamId = UUID.randomUUID();
			Team newTeam = teams.computeIfAbsent(target.teamId, id -> new Team());
			newTeam.leader = targetId;
			newTeam.members.add(targetId);
			return true;
		}

		boolean isTeamLeader(@org.jetbrains.annotations.Nullable UUID teamId, @org.jetbrains.annotations.Nullable UUID playerId) {
			if (teamId == null || playerId == null)
				return false;
			Team team = teams.get(teamId);
			return team != null && playerId.equals(team.leader);
		}

		private void removeFromTeam(@org.jetbrains.annotations.Nullable UUID teamId, UUID playerId) {
			if (teamId == null)
				return;
			Team team = teams.get(teamId);
			if (team == null)
				return;
			team.members.remove(playerId);
			if (team.members.isEmpty()) {
				teams.remove(teamId);
			} else if (playerId.equals(team.leader)) {
				team.leader = team.members.iterator().next();
			}
		}

		private void removeFromAllTeams(UUID playerId) {
			for (UUID teamId : new ArrayList<>(teams.keySet()))
				removeFromTeam(teamId, playerId);
		}

		private void clearPendingInvitesFor(UUID playerId) {
			pendingInvites.entrySet().removeIf(entry -> playerId.equals(entry.getKey()) || playerId.equals(entry.getValue()));
		}

		List<FirefighterHandbookSnapshot.PlayerEntry> registeredPlayers(@org.jetbrains.annotations.Nullable UUID except) {
			List<FirefighterHandbookSnapshot.PlayerEntry> result = new ArrayList<>();
			for (Map.Entry<UUID, Record> entry : players.entrySet()) {
				if (entry.getKey().equals(except) || !entry.getValue().registered)
					continue;
				result.add(new FirefighterHandbookSnapshot.PlayerEntry(entry.getKey(), entry.getValue().name));
			}
			result.sort((a, b) -> a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT)));
			return result;
		}

		List<FirefighterHandbookSnapshot.PlayerEntry> teamMembers(@org.jetbrains.annotations.Nullable UUID teamId) {
			if (teamId == null || !teams.containsKey(teamId))
				return List.of();
			List<FirefighterHandbookSnapshot.PlayerEntry> result = new ArrayList<>();
			Team team = teams.get(teamId);
			for (UUID id : team.members) {
				Record record = players.get(id);
				if (record != null && record.registered)
					result.add(new FirefighterHandbookSnapshot.PlayerEntry(id, record.name));
			}
			result.sort((a, b) -> {
				if (a.id().equals(team.leader))
					return -1;
				if (b.id().equals(team.leader))
					return 1;
				return a.name().toLowerCase(Locale.ROOT).compareTo(b.name().toLowerCase(Locale.ROOT));
			});
			return result;
		}
	}
}
