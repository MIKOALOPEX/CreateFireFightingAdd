package com.mikoalopex.createfirefightingadd.content.items.firefighter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;

public record FirefighterHandbookSnapshot(
	boolean serverRecordsEnabled,
	boolean registered,
	boolean completed,
	@Nullable UUID owner,
	String ownerName,
	int extinguished,
	boolean teamCaptain,
	List<PlayerEntry> registeredPlayers,
	List<PlayerEntry> teamMembers
) {
	public static final int GOAL = 1000;
	public static final FirefighterHandbookSnapshot EMPTY =
		new FirefighterHandbookSnapshot(true, false, false, null, "", 0, false, List.of(), List.of());

	public FirefighterHandbookSnapshot {
		ownerName = ownerName == null ? "" : ownerName;
		extinguished = Math.max(0, extinguished);
		registeredPlayers = List.copyOf(registeredPlayers);
		teamMembers = List.copyOf(teamMembers);
	}

	public boolean hasMedal() {
		return completed || extinguished >= GOAL;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(serverRecordsEnabled);
		buf.writeBoolean(registered);
		buf.writeBoolean(completed);
		buf.writeBoolean(owner != null);
		if (owner != null)
			buf.writeUUID(owner);
		buf.writeUtf(ownerName);
		buf.writeVarInt(extinguished);
		buf.writeBoolean(teamCaptain);
		writePlayers(buf, registeredPlayers);
		writePlayers(buf, teamMembers);
	}

	public static FirefighterHandbookSnapshot read(RegistryFriendlyByteBuf buf) {
		boolean enabled = buf.readBoolean();
		boolean registered = buf.readBoolean();
		boolean completed = buf.readBoolean();
		UUID owner = buf.readBoolean() ? buf.readUUID() : null;
		String ownerName = buf.readUtf();
		int count = buf.readVarInt();
		boolean captain = buf.readBoolean();
		List<PlayerEntry> players = readPlayers(buf);
		List<PlayerEntry> team = readPlayers(buf);
		return new FirefighterHandbookSnapshot(enabled, registered, completed, owner, ownerName, count, captain, players, team);
	}

	private static void writePlayers(RegistryFriendlyByteBuf buf, List<PlayerEntry> players) {
		buf.writeVarInt(players.size());
		for (PlayerEntry player : players) {
			buf.writeUUID(player.id());
			buf.writeUtf(player.name());
		}
	}

	private static List<PlayerEntry> readPlayers(RegistryFriendlyByteBuf buf) {
		int size = buf.readVarInt();
		List<PlayerEntry> players = new ArrayList<>(size);
		for (int i = 0; i < size; i++)
			players.add(new PlayerEntry(buf.readUUID(), buf.readUtf()));
		return players;
	}

	public record PlayerEntry(UUID id, String name) {
		public PlayerEntry {
			name = name == null ? "" : name;
		}
	}
}
