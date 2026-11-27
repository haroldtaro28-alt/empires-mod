package com.empires;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.*;

/** حالة العالم المحفوظة: انتماء اللاعبين، التحالفات، الحروب، العواصم المبنية. */
public class EmpireState extends PersistentState {
	public final Map<UUID, Integer> players = new HashMap<>();
	public final Set<Integer> allies = new HashSet<>();
	public final Set<Integer> wars = new HashSet<>();
	public final Set<Integer> built = new HashSet<>();
	public final Map<Integer, Integer> capY = new HashMap<>();
	public boolean initialized = false;

	public static final PersistentState.Type<EmpireState> TYPE =
			new PersistentState.Type<>(EmpireState::new, EmpireState::fromNbt, null);

	public static EmpireState get(MinecraftServer server) {
		return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, "empires_data");
	}

	public static int key(int a, int b) {
		return Math.min(a, b) * 100 + Math.max(a, b);
	}

	public boolean areAllies(int a, int b) { return a != b && allies.contains(key(a, b)); }
	public boolean atWar(int a, int b) { return a != b && wars.contains(key(a, b)); }

	public List<Integer> alliesOf(int a) {
		List<Integer> l = new ArrayList<>();
		for (int i = 0; i < Empire.COUNT; i++) if (areAllies(a, i)) l.add(i);
		return l;
	}

	public List<Integer> enemiesOf(int a) {
		List<Integer> l = new ArrayList<>();
		for (int i = 0; i < Empire.COUNT; i++) if (atWar(a, i)) l.add(i);
		return l;
	}

	public void makeAlly(int a, int b) {
		wars.remove(key(a, b));
		allies.add(key(a, b));
		markDirty();
	}

	public void makePeace(int a, int b) {
		wars.remove(key(a, b));
		markDirty();
	}

	/** إعلان حرب: حلفاء كل طرف ينضمون ضد الطرف الآخر. */
	public void declareWar(int a, int b) {
		allies.remove(key(a, b));
		wars.add(key(a, b));
		for (int x = 0; x < Empire.COUNT; x++) {
			if (x == a || x == b) continue;
			if (areAllies(x, b) && !areAllies(x, a)) wars.add(key(a, x));
			if (areAllies(x, a) && !areAllies(x, b)) wars.add(key(b, x));
		}
		markDirty();
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		NbtList pl = new NbtList();
		for (Map.Entry<UUID, Integer> en : players.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putUuid("u", en.getKey());
			c.putInt("e", en.getValue());
			pl.add(c);
		}
		nbt.put("players", pl);
		nbt.putIntArray("allies", allies.stream().mapToInt(Integer::intValue).toArray());
		nbt.putIntArray("wars", wars.stream().mapToInt(Integer::intValue).toArray());
		nbt.putIntArray("built", built.stream().mapToInt(Integer::intValue).toArray());
		NbtList cy = new NbtList();
		for (Map.Entry<Integer, Integer> en : capY.entrySet()) {
			NbtCompound c = new NbtCompound();
			c.putInt("e", en.getKey());
			c.putInt("y", en.getValue());
			cy.add(c);
		}
		nbt.put("capY", cy);
		nbt.putBoolean("init", initialized);
		return nbt;
	}

	public static EmpireState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
		EmpireState s = new EmpireState();
		NbtList pl = nbt.getList("players", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < pl.size(); i++) {
			NbtCompound c = pl.getCompound(i);
			s.players.put(c.getUuid("u"), c.getInt("e"));
		}
		for (int v : nbt.getIntArray("allies")) s.allies.add(v);
		for (int v : nbt.getIntArray("wars")) s.wars.add(v);
		for (int v : nbt.getIntArray("built")) s.built.add(v);
		NbtList cy = nbt.getList("capY", NbtElement.COMPOUND_TYPE);
		for (int i = 0; i < cy.size(); i++) {
			NbtCompound c = cy.getCompound(i);
			s.capY.put(c.getInt("e"), c.getInt("y"));
		}
		s.initialized = nbt.getBoolean("init");
		return s;
	}
}
