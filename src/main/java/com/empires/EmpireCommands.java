package com.empires;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class EmpireCommands {
	public static void register(CommandDispatcher<ServerCommandSource> d) {
		d.register(CommandManager.literal("empire")
				.executes(EmpireCommands::info)
				.then(CommandManager.literal("list").executes(EmpireCommands::list))
				.then(CommandManager.literal("tp").executes(EmpireCommands::tp))
				.then(CommandManager.literal("recruit").executes(EmpireCommands::recruit))
				.then(CommandManager.literal("follow").executes(c -> follow(c, true)))
				.then(CommandManager.literal("stay").executes(c -> follow(c, false)))
				.then(CommandManager.literal("ally").then(CommandManager.argument("id", IntegerArgumentType.integer(1, Empire.COUNT))
						.executes(c -> diplo(c, 0))))
				.then(CommandManager.literal("war").then(CommandManager.argument("id", IntegerArgumentType.integer(1, Empire.COUNT))
						.executes(c -> diplo(c, 1))))
				.then(CommandManager.literal("peace").then(CommandManager.argument("id", IntegerArgumentType.integer(1, Empire.COUNT))
						.executes(c -> diplo(c, 2)))));
	}

	private static void say(CommandContext<ServerCommandSource> c, Text t) {
		c.getSource().sendFeedback(() -> t, false);
	}

	private static int info(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		EmpireState st = EmpireState.get(c.getSource().getServer());
		Integer pe = st.players.get(p.getUuid());
		if (pe == null) { say(c, Text.literal("لا تنتمي لأي إمبراطورية.")); return 0; }
		Empire e = Empire.get(pe);
		say(c, Text.literal("إمبراطوريتك: (" + (pe + 1) + ") ").append(EmpireManager.nameOf(e)));
		say(c, Text.literal("العاصمة: " + e.cx() + ", " + e.cz() + "  |  الحجم: " + e.size() + " بلوك"));
		say(c, Text.literal("الحلفاء: ").append(EmpireManager.listNames(st.alliesOf(pe))));
		say(c, Text.literal("الأعداء: ").append(EmpireManager.listNames(st.enemiesOf(pe))));
		say(c, Text.literal("الأوامر: /empire list | tp | recruit | follow | stay | ally <رقم> | war <رقم> | peace <رقم>"));
		return 1;
	}

	private static int list(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		EmpireState st = EmpireState.get(c.getSource().getServer());
		Integer pe = st.players.get(p.getUuid());
		for (Empire e : Empire.ALL) {
			String rel = "";
			if (pe != null) rel = st.areAllies(pe, e.id()) ? "  [حليف]" : st.atWar(pe, e.id()) ? "  [عدو]" : "";
			say(c, Text.literal((e.id() + 1) + ". ").append(EmpireManager.nameOf(e)).append(Text.literal(rel + "  [" + e.cx() + ", " + e.cz() + "]  حجم " + e.size())));
		}
		return 1;
	}

	private static int tp(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		EmpireState st = EmpireState.get(c.getSource().getServer());
		Integer pe = st.players.get(p.getUuid());
		if (pe == null || !st.capY.containsKey(pe)) { say(c, Text.literal("عاصمتك غير جاهزة.")); return 0; }
		Empire e = Empire.get(pe);
		ServerWorld w = c.getSource().getServer().getOverworld();
		p.teleport(w, e.cx() + 0.5, st.capY.get(pe), e.cz() + 3.5, 180F, 0F);
		return 1;
	}

	private static int recruit(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		EmpireState st = EmpireState.get(c.getSource().getServer());
		Integer pe = st.players.get(p.getUuid());
		if (pe == null) return 0;
		if (!EmpireManager.takeIron(p, EmpireManager.RECRUIT_COST)) {
			say(c, Text.literal("تحتاج " + EmpireManager.RECRUIT_COST + " سبائك حديد لتجنيد 4 جنود."));
			return 0;
		}
		ServerWorld w = (ServerWorld) p.getWorld();
		Empire e = Empire.get(pe);
		BlockPos home = new BlockPos(e.cx(), st.capY.getOrDefault(pe, 64), e.cz());
		for (int i = 0; i < 4; i++) {
			EmpireManager.spawn(w, e, 0, p.getX() + w.random.nextInt(5) - 2, p.getY(), p.getZ() + w.random.nextInt(5) - 2, true, home);
		}
		say(c, Text.literal("تم تجنيد 4 جنود. استخدم /empire follow ليتبعوك."));
		return 1;
	}

	private static int follow(CommandContext<ServerCommandSource> c, boolean on) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		if (on) EmpireManager.FOLLOWING.add(p.getUuid()); else EmpireManager.FOLLOWING.remove(p.getUuid());
		say(c, Text.literal(on ? "جيشك سيتبعك." : "جيشك سيحرس العاصمة."));
		return 1;
	}

	private static int diplo(CommandContext<ServerCommandSource> c, int mode) throws CommandSyntaxException {
		ServerPlayerEntity p = c.getSource().getPlayerOrThrow();
		EmpireState st = EmpireState.get(c.getSource().getServer());
		Integer pe = st.players.get(p.getUuid());
		int other = IntegerArgumentType.getInteger(c, "id") - 1;
		if (pe == null || pe == other) { say(c, Text.literal("اختيار غير صالح.")); return 0; }
		Empire a = Empire.get(pe), b = Empire.get(other);
		Text msg;
		if (mode == 0) { st.makeAlly(pe, other); msg = EmpireManager.nameOf(a).append(Text.literal(" تحالفت مع ")).append(EmpireManager.nameOf(b)); }
		else if (mode == 1) { st.declareWar(pe, other); msg = EmpireManager.nameOf(a).append(Text.literal(" أعلنت الحرب على ")).append(EmpireManager.nameOf(b)); }
		else { st.makePeace(pe, other); msg = EmpireManager.nameOf(a).append(Text.literal(" وقّعت السلام مع ")).append(EmpireManager.nameOf(b)); }
		c.getSource().getServer().getPlayerManager().broadcast(msg, false);
		return 1;
	}
}
