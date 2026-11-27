package com.empires;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.*;

public class EmpireManager {
	/** اللاعبون الذين فعّلوا أمر "اتبعني" لجيشهم (غير محفوظ بين الجلسات). */
	public static final Set<UUID> FOLLOWING = new HashSet<>();
	private static final Map<Integer, Long> MISSING = new HashMap<>();
	private static final Block[] WOOLS = {
		Blocks.WHITE_WOOL, Blocks.ORANGE_WOOL, Blocks.MAGENTA_WOOL, Blocks.LIGHT_BLUE_WOOL,
		Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.PINK_WOOL, Blocks.GRAY_WOOL,
		Blocks.LIGHT_GRAY_WOOL, Blocks.CYAN_WOOL, Blocks.PURPLE_WOOL, Blocks.BLUE_WOOL,
		Blocks.BROWN_WOOL, Blocks.GREEN_WOOL, Blocks.RED_WOOL, Blocks.BLACK_WOOL
	};

	public static final int INITIAL_SOLDIERS = 6;
	public static final int RECRUIT_COST = 8; // سبائك حديد

	public static MutableText nameOf(Empire e) {
		return Text.literal(e.name()).setStyle(Style.EMPTY.withColor(e.color()));
	}

	// ---------------- بدء السيرفر ----------------

	public static void onStart(MinecraftServer server) {
		EmpireState st = EmpireState.get(server);
		if (st.layout.length != Empire.COUNT * 3) {
			// عالم جديد (أو عالم قديم بدون توزيع): نولّد مواقع برّية عشوائية
			st.layout = generateLayout(server.getOverworld());
			st.players.clear();
			st.built.clear();
			st.capY.clear();
			st.allies.clear();
			st.wars.clear();
			st.initialized = false;
			st.markDirty();
		}
		Empire.applyLayout(st.layout);
		if (!st.initialized) {
			Random r = new Random(server.getOverworld().getSeed());
			for (int i = 0; i < Empire.COUNT; i++) {
				int a = r.nextInt(Empire.COUNT);
				if (a != i) st.allies.add(EmpireState.key(i, a));
				for (int k = 0; k < 2; k++) {
					int b = r.nextInt(Empire.COUNT);
					if (b != i && !st.allies.contains(EmpireState.key(i, b))) st.wars.add(EmpireState.key(i, b));
				}
			}
			st.initialized = true;
			st.markDirty();
		}
		Scoreboard sb = server.getScoreboard();
		for (Empire e : Empire.ALL) {
			String n = "empire_" + e.id();
			Team t = sb.getTeam(n);
			if (t == null) t = sb.addTeam(n);
			t.setColor(Formatting.byColorIndex(e.id() % 15 + 1));
			t.setFriendlyFireAllowed(false);
			t.setDisplayName(Text.literal(e.name()));
		}
	}

	// ---------------- توزيع الإمبراطوريات ----------------

	private static final int MIN_DISTANCE = 800;   // أقل مسافة بين عاصمتين
	private static final int MIN_SIZE = 500;       // أصغر حجم
	private static final int MAX_SIZE = 3000;      // أكبر حجم

	/** يختار مواقع عشوائية على اليابسة بدون توليد الأرض، بمسافة 800+ بلوك بين العواصم. */
	private static int[] generateLayout(ServerWorld w) {
		Random r = new Random(w.getSeed() ^ 0x5DEECE66DL);
		ChunkGenerator gen = w.getChunkManager().getChunkGenerator();
		NoiseConfig nc = w.getChunkManager().getNoiseConfig();
		int[] l = new int[Empire.COUNT * 3];
		int placed = 0, attempts = 0;
		double range = 6000;
		while (placed < Empire.COUNT && attempts < 200000) {
			attempts++;
			if (attempts % 4000 == 0) range *= 1.25; // نوسّع المنطقة إذا صعب إيجاد يابسة
			int x = (((int) ((r.nextDouble() * 2 - 1) * range)) >> 4 << 4) + 8;
			int z = (((int) ((r.nextDouble() * 2 - 1) * range)) >> 4 << 4) + 8;
			boolean ok = true;
			for (int i = 0; i < placed; i++) {
				long dx = x - l[i * 3], dz = z - l[i * 3 + 1];
				if (dx * dx + dz * dz < (long) MIN_DISTANCE * MIN_DISTANCE) { ok = false; break; }
			}
			if (!ok || !isGoodLand(w, gen, nc, x, z)) continue;
			l[placed * 3] = x;
			l[placed * 3 + 1] = z;
			l[placed * 3 + 2] = MIN_SIZE + r.nextInt(MAX_SIZE - MIN_SIZE + 1);
			placed++;
		}
		while (placed < Empire.COUNT) { // احتياط نادر جداً
			l[placed * 3] = placed * 1000 + 8;
			l[placed * 3 + 1] = 8;
			l[placed * 3 + 2] = MIN_SIZE + r.nextInt(MAX_SIZE - MIN_SIZE + 1);
			placed++;
		}
		return l;
	}

	private static int floorY(ServerWorld w, ChunkGenerator gen, NoiseConfig nc, int x, int z) {
		return gen.getHeight(x, z, Heightmap.Type.OCEAN_FLOOR_WG, w, nc);
	}

	/** يابسة مرتفعة عن البحر (63)، شبه مستوية، وليست جزيرة صغيرة. */
	private static boolean isGoodLand(ServerWorld w, ChunkGenerator gen, NoiseConfig nc, int x, int z) {
		if (floorY(w, gen, nc, x, z) < 66) return false;
		int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
		int[][] pts = {{0, 0}, {-7, -7}, {7, -7}, {-7, 7}, {7, 7}};
		for (int[] p : pts) {
			int h = floorY(w, gen, nc, x + p[0], z + p[1]);
			min = Math.min(min, h);
			max = Math.max(max, h);
		}
		if (min < 66 || max > 140 || max - min > 10) return false;
		int[][] far = {{40, 0}, {-40, 0}, {0, 40}, {0, -40}};
		for (int[] p : far) if (floorY(w, gen, nc, x + p[0], z + p[1]) < 64) return false;
		return true;
	}

	// ---------------- دخول لاعب ----------------

	public static void onJoin(ServerPlayerEntity p, MinecraftServer server) {
		EmpireState st = EmpireState.get(server);
		ServerWorld w = server.getOverworld();
		boolean first = false;
		Integer pe = st.players.get(p.getUuid());
		if (pe == null) {
			pe = w.random.nextInt(Empire.COUNT);
			st.players.put(p.getUuid(), pe);
			st.markDirty();
			first = true;
		}
		Empire e = Empire.get(pe);
		Team team = server.getScoreboard().getTeam("empire_" + pe);
		if (team != null) server.getScoreboard().addScoreHolderToTeam(p.getNameForScoreboard(), team);
		if (!first) return;

		// نتأكد أن لإمبراطوريتك عدواً واحداً على الأقل
		if (st.enemiesOf(pe).isEmpty()) {
			int b;
			do { b = w.random.nextInt(Empire.COUNT); } while (b == pe || st.areAllies(pe, b));
			st.declareWar(pe, b);
		}

		ensure(w, st, e, true);
		int y = st.capY.get(pe);
		p.teleport(w, e.cx() + 0.5, y, e.cz() + 3.5, 180F, 0F);
		p.setSpawnPoint(w.getRegistryKey(), new BlockPos(e.cx(), y, e.cz() + 3), 0F, true, false);

		p.giveItemStack(new ItemStack(Items.STONE_SWORD));
		p.giveItemStack(new ItemStack(Items.BREAD, 16));
		p.giveItemStack(new ItemStack(Items.IRON_INGOT, 16));

		p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 90, 20));
		p.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("أنت قائد جيوشها. اكتب /empire للتفاصيل")));
		p.networkHandler.sendPacket(new TitleS2CPacket(nameOf(e)));
		p.sendMessage(Text.literal("وصلت إلى عاصمة ").append(nameOf(e)).append(Text.literal(". كلّم الوزير والرئيس بالضغط يمين عليهم.")), false);
	}

	// ---------------- الحلقة الرئيسية ----------------

	public static void tick(MinecraftServer server) {
		int t = server.getTicks();
		if (t % 20 == 0) hud(server);
		if (t % 100 == 50) garrisons(server);
		if (t % 3600 == 1800) raids(server);
		if (t % 6000 == 3000) diplomacy(server);
	}

	/** الواجهة: شريط معلومات فوق الشريط السريع (Action Bar). */
	private static void hud(MinecraftServer server) {
		EmpireState st = EmpireState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			Integer pe = st.players.get(p.getUuid());
			if (pe == null) continue;
			Empire e = Empire.get(pe);
			int n = p.getWorld().getEntitiesByClass(SoldierEntity.class,
					p.getBoundingBox().expand(64), s -> s.getEmpire() == pe && s.getRole() == 0).size();
			MutableText t = Text.empty().append(nameOf(e)).append(Text.literal(
					"  |  الحلفاء: " + st.alliesOf(pe).size()
					+ "  |  الحروب: " + st.enemiesOf(pe).size()
					+ "  |  جنودك: " + n
					+ "  |  الجيش: " + (FOLLOWING.contains(p.getUuid()) ? "يتبعك" : "يحرس")
			).formatted(Formatting.WHITE));
			Empire terr = Empire.territoryAt(p.getX(), p.getZ());
			t.append(Text.literal("  |  الأراضي: ").formatted(Formatting.WHITE));
			t.append(terr == null ? Text.literal("محايدة").formatted(Formatting.GRAY) : nameOf(terr));
			p.sendMessage(t, true);
		}
	}

	/** العواصم المحمّلة: نبنيها إن لم تكن مبنية ونملأها بالرئيس والوزير والحرس. */
	private static void garrisons(MinecraftServer server) {
		ServerWorld w = server.getOverworld();
		EmpireState st = EmpireState.get(server);
		for (Empire e : Empire.ALL) {
			if (!w.getChunkManager().isChunkLoaded(e.cx() >> 4, e.cz() >> 4)) continue;
			ensure(w, st, e, false);
		}
	}

	public static void ensure(ServerWorld w, EmpireState st, Empire e, boolean immediate) {
		if (!st.built.contains(e.id())) {
			int y = Math.max(63, w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, e.cx(), e.cz()));
			buildCapital(w, e, y);
			st.built.add(e.id());
			st.capY.put(e.id(), y);
			st.markDirty();
			immediate = true;
		}
		int y = st.capY.get(e.id());
		BlockPos c = new BlockPos(e.cx(), y, e.cz());
		List<SoldierEntity> list = w.getEntitiesByClass(SoldierEntity.class,
				new Box(c).expand(24, 12, 24), s -> s.getEmpire() == e.id() && !s.isRaider());
		boolean hasPres = false, hasMin = false;
		int soldiers = 0;
		for (SoldierEntity s : list) {
			if (s.getRole() == 2) hasPres = true;
			else if (s.getRole() == 1) hasMin = true;
			else soldiers++;
		}
		if (!hasPres && waited(w, e.id() * 10 + 2, immediate)) spawn(w, e, 2, e.cx(), y, e.cz() - 2, false, c);
		if (!hasMin && waited(w, e.id() * 10 + 1, immediate)) spawn(w, e, 1, e.cx() + 2, y, e.cz() - 2, false, c);
		int want = 4 + e.size() / 300; // الإمبراطورية الأكبر عندها حرس أكثر
		int toSpawn = Math.min(want - soldiers, immediate ? want : 1);
		for (int i = 0; i < toSpawn; i++) {
			spawn(w, e, 0, e.cx() + w.random.nextInt(11) - 5, y, e.cz() + 1 + w.random.nextInt(5), false, c);
		}
	}

	/** بعد موت الرئيس/الوزير ننتظر نصف دقيقة قبل تعيين بديل. */
	private static boolean waited(ServerWorld w, int key, boolean immediate) {
		if (immediate) { MISSING.remove(key); return true; }
		long now = w.getTime();
		Long since = MISSING.get(key);
		if (since == null) { MISSING.put(key, now); return false; }
		if (now - since >= 600) { MISSING.remove(key); return true; }
		return false;
	}

	public static SoldierEntity spawn(ServerWorld w, Empire e, int role, double x, double y, double z,
									  boolean raider, BlockPos home) {
		SoldierEntity s = ModEntities.SOLDIER.create(w);
		if (s == null) return null;
		s.refreshPositionAndAngles(x + 0.5, y, z + 0.5, w.random.nextFloat() * 360F, 0F);
		s.initEmpire(e, role, raider, home);
		w.spawnEntity(s);
		return s;
	}

	// ---------------- الغارات ----------------

	private static void raids(MinecraftServer server) {
		ServerWorld w = server.getOverworld();
		EmpireState st = EmpireState.get(server);
		for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
			Integer pe = st.players.get(p.getUuid());
			if (pe == null || p.getWorld() != w) continue;
			Empire mine = Empire.get(pe);
			double dx = p.getX() - mine.cx(), dz = p.getZ() - mine.cz();
			double rad = Math.max(250, mine.radius());
			if (dx * dx + dz * dz > rad * rad) continue; // الغارات فقط داخل أراضي إمبراطوريتك
			List<Integer> enemies = st.enemiesOf(pe);
			if (enemies.isEmpty()) continue;
			Empire en = Empire.get(enemies.get(w.random.nextInt(enemies.size())));
			int count = 4 + w.random.nextInt(4);
			double ang = w.random.nextDouble() * Math.PI * 2;
			int spawned = 0;
			for (int i = 0; i < count; i++) {
				int x = (int) (p.getX() + Math.cos(ang) * 40 + w.random.nextInt(7) - 3);
				int z = (int) (p.getZ() + Math.sin(ang) * 40 + w.random.nextInt(7) - 3);
				if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue;
				int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				if (!w.getFluidState(new BlockPos(x, y - 1, z)).isEmpty()) continue;
				spawn(w, en, 0, x, y, z, true, new BlockPos(en.cx(), 64, en.cz()));
				spawned++;
			}
			if (spawned > 0) {
				p.sendMessage(Text.literal("غارة! جنود ").append(nameOf(en)).append(Text.literal(" يهاجمون عاصمتك!")), false);
			}
		}
	}

	// ---------------- دبلوماسية الذكاء الاصطناعي ----------------

	private static void diplomacy(MinecraftServer server) {
		ServerWorld w = server.getOverworld();
		EmpireState st = EmpireState.get(server);
		int a = w.random.nextInt(Empire.COUNT), b = w.random.nextInt(Empire.COUNT);
		if (a == b) return;
		Empire ea = Empire.get(a), eb = Empire.get(b);
		MutableText msg;
		if (st.atWar(a, b)) {
			st.makePeace(a, b);
			msg = nameOf(ea).append(Text.literal(" وقّعت هدنة مع ")).append(nameOf(eb));
		} else if (w.random.nextBoolean() && !st.areAllies(a, b)) {
			st.makeAlly(a, b);
			msg = nameOf(ea).append(Text.literal(" تحالفت مع ")).append(nameOf(eb));
		} else if (!st.areAllies(a, b)) {
			st.declareWar(a, b);
			msg = nameOf(ea).append(Text.literal(" أعلنت الحرب على ")).append(nameOf(eb));
		} else return;
		server.getPlayerManager().broadcast(Text.literal("[أخبار العالم] ").formatted(Formatting.GOLD).append(msg), false);
	}

	// ---------------- التفاعل مع الرئيس والوزير ----------------

	public static void onInteract(SoldierEntity npc, ServerPlayerEntity p) {
		EmpireState st = EmpireState.get(p.getServer());
		Integer pe = st.players.get(p.getUuid());
		int ne = npc.getEmpire();
		Empire e = Empire.get(ne);
		if (npc.getRole() == 1) {
			p.sendMessage(Text.literal("[وزير] ").append(nameOf(e)).append(Text.literal(":")), false);
			p.sendMessage(Text.literal("الحلفاء: ").append(listNames(st.alliesOf(ne))), false);
			p.sendMessage(Text.literal("الأعداء: ").append(listNames(st.enemiesOf(ne))), false);
			return;
		}
		// الرئيس
		if (pe == null) return;
		if (pe == ne) {
			p.sendMessage(Text.literal("[الرئيس] أهلاً بقائد جيوشنا. دافع عن إمبراطوريتك!"), false);
		} else if (st.atWar(pe, ne)) {
			p.sendMessage(Text.literal("[الرئيس] نحن في حالة حرب معكم! اطلب السلام بـ /empire peace " + (ne + 1)), false);
		} else if (st.areAllies(pe, ne)) {
			p.sendMessage(Text.literal("[الرئيس] نحن حلفاء، وسنقف معكم."), false);
		} else {
			st.makeAlly(pe, ne);
			p.getServer().getPlayerManager().broadcast(
					nameOf(Empire.get(pe)).append(Text.literal(" تحالفت مع ")).append(nameOf(e)), false);
		}
	}

	public static MutableText listNames(List<Integer> ids) {
		if (ids.isEmpty()) return Text.literal("لا أحد");
		MutableText t = Text.empty();
		for (int i = 0; i < ids.size(); i++) {
			if (i > 0) t.append(Text.literal("، "));
			t.append(nameOf(Empire.get(ids.get(i))));
		}
		return t;
	}

	/** خصم سبائك حديد من اللاعب. يرجع false إذا لا يكفي. */
	public static boolean takeIron(ServerPlayerEntity p, int amount) {
		PlayerInventory inv = p.getInventory();
		int have = 0;
		for (int i = 0; i < inv.size(); i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(Items.IRON_INGOT)) have += s.getCount();
		}
		if (have < amount) return false;
		int need = amount;
		for (int i = 0; i < inv.size() && need > 0; i++) {
			ItemStack s = inv.getStack(i);
			if (s.isOf(Items.IRON_INGOT)) {
				int t = Math.min(need, s.getCount());
				s.decrement(t);
				need -= t;
			}
		}
		return true;
	}

	// ---------------- بناء العاصمة (قلعة 15×15 داخل تشانك واحد) ----------------

	private static void set(ServerWorld w, int x, int y, int z, BlockState s) {
		w.setBlockState(new BlockPos(x, y, z), s, Block.NOTIFY_LISTENERS);
	}

	private static void buildCapital(ServerWorld w, Empire e, int y) {
		int cx = e.cx(), cz = e.cz();
		BlockState brick = Blocks.STONE_BRICKS.getDefaultState();
		BlockState air = Blocks.AIR.getDefaultState();
		for (int dx = -7; dx <= 7; dx++) {
			for (int dz = -7; dz <= 7; dz++) {
				int x = cx + dx, z = cz + dz;
				// أساس يمتد للأسفل حتى يلمس أرضاً صلبة
				for (int yy = y - 1; yy >= y - 60; yy--) {
					BlockState b = w.getBlockState(new BlockPos(x, yy, z));
					if (yy < y - 2 && !(b.isAir() || !b.getFluidState().isEmpty())) break;
					set(w, x, yy, z, brick);
				}
				// تنظيف الفراغ فوق الأرضية
				for (int dy = 0; dy <= 9; dy++) set(w, x, y + dy, z, air);
				// أرضية مميزة في الوسط
				if (Math.abs(dx) <= 2 && Math.abs(dz) <= 2) set(w, x, y - 1, z, Blocks.POLISHED_ANDESITE.getDefaultState());
				// الأسوار
				boolean edge = Math.max(Math.abs(dx), Math.abs(dz)) == 7;
				boolean gate = dz == 7 && Math.abs(dx) <= 1;
				if (edge && !gate) {
					for (int dy = 0; dy <= 3; dy++) set(w, x, y + dy, z, brick);
					if (((dx + dz) & 1) == 0) set(w, x, y + 4, z, brick);
				}
				// الأبراج
				if (Math.abs(dx) >= 5 && Math.abs(dz) >= 5) {
					for (int dy = 0; dy <= 6; dy++) set(w, x, y + dy, z, brick);
					if (((dx + dz) & 1) == 0) set(w, x, y + 7, z, brick);
				}
			}
		}
		set(w, cx, y - 1, cz, Blocks.GOLD_BLOCK.getDefaultState());
		// علم بلون الإمبراطورية
		BlockState wool = WOOLS[e.id() % 16].getDefaultState();
		for (int dy = 0; dy <= 7; dy++) set(w, cx, y + dy, cz - 5, Blocks.OAK_FENCE.getDefaultState());
		for (int fx = 1; fx <= 3; fx++) for (int fy = 5; fy <= 7; fy++) set(w, cx + fx, y + fy, cz - 5, wool);
	}
}
