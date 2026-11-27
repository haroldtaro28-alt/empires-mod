package com.empires;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/** جندي / وزير / رئيس. الدور: 0 جندي، 1 وزير، 2 رئيس. */
public class SoldierEntity extends PathAwareEntity {
	public static final TrackedData<Integer> ROLE =
			DataTracker.registerData(SoldierEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private int empire = 0;
	private boolean raider = false;
	private int homeX, homeY, homeZ;

	public SoldierEntity(EntityType<? extends PathAwareEntity> type, World world) {
		super(type, world);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(ROLE, 0);
	}

	public static DefaultAttributeContainer.Builder createAttrs() {
		return PathAwareEntity.createMobAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 24.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
				.add(EntityAttributes.GENERIC_ARMOR, 4.0);
	}

	public int getEmpire() { return empire; }
	public int getRole() { return dataTracker.get(ROLE); }
	public boolean isRaider() { return raider; }

	/** تجهيز الكيان: الإمبراطورية، الدور، الدروع الملونة بلون الإمبراطورية. */
	public void initEmpire(Empire emp, int role, boolean raider, BlockPos home) {
		this.empire = emp.id();
		this.raider = raider;
		this.homeX = home.getX();
		this.homeY = home.getY();
		this.homeZ = home.getZ();
		dataTracker.set(ROLE, role);

		double hp = role == 2 ? 60 : role == 1 ? 40 : 24;
		getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH).setBaseValue(hp);
		setHealth((float) hp);

		int c = emp.color();
		equipStack(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, c));
		equipStack(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, c));
		equipStack(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, c));
		if (role == 2) {
			equipStack(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_SWORD));
		} else if (role == 1) {
			equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, c));
			equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOOK));
		} else {
			equipStack(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, c));
			equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
		for (EquipmentSlot s : EquipmentSlot.values()) setEquipmentDropChance(s, 0.0F);

		if (role > 0) {
			String title = role == 2 ? "رئيس " : "وزير ";
			setCustomName(Text.literal(title + emp.name()).setStyle(Style.EMPTY.withColor(c)));
			setCustomNameVisible(true);
		}
		if (!raider) setPersistent();
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(rgb, false));
		return s;
	}

	@Override
	protected void initGoals() {
		goalSelector.add(1, new SwimGoal(this));
		goalSelector.add(2, new MeleeAttackGoal(this, 1.15, true));
		goalSelector.add(3, new FollowPlayerGoal(this));
		goalSelector.add(4, new ReturnHomeGoal(this));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 8.0F));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new FindEnemyGoal(this));
	}

	/** هل الكيان عدو لهذا الجندي؟ (وحوش، أو جنود/لاعبين من إمبراطورية في حرب معنا) */
	public boolean isEnemy(LivingEntity e) {
		if (e == this || !e.isAlive()) return false;
		if (e instanceof Monster) return true;
		if (!(getWorld() instanceof ServerWorld sw)) return false;
		EmpireState st = EmpireState.get(sw.getServer());
		if (e instanceof SoldierEntity s) return s.empire != empire && st.atWar(empire, s.empire);
		if (e instanceof ServerPlayerEntity p) {
			if (p.isCreative() || p.isSpectator()) return false;
			Integer pe = st.players.get(p.getUuid());
			return pe != null && pe != empire && st.atWar(empire, pe);
		}
		return false;
	}

	@Override
	public void tick() {
		super.tick();
		if (!getWorld().isClient && raider && age > 12000 && getTarget() == null) discard();
	}

	@Override
	protected ActionResult interactMob(PlayerEntity player, Hand hand) {
		if (getRole() == 0) return super.interactMob(player, hand);
		if (hand == Hand.MAIN_HAND && !getWorld().isClient && player instanceof ServerPlayerEntity sp) {
			EmpireManager.onInteract(this, sp);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (!getWorld().isClient && getRole() > 0 && getWorld().getServer() != null) {
			Empire e = Empire.get(empire);
			String title = getRole() == 2 ? "الرئيس" : "الوزير";
			getWorld().getServer().getPlayerManager().broadcast(
					Text.literal("قُتل " + title + " في " + e.name() + "!").setStyle(Style.EMPTY.withColor(e.color())), false);
		}
	}

	@Override public boolean canImmediatelyDespawn(double d) { return false; }
	@Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.ENTITY_PLAYER_HURT; }
	@Override protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_PLAYER_DEATH; }

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		nbt.putInt("Empire", empire);
		nbt.putInt("Role", getRole());
		nbt.putBoolean("Raider", raider);
		nbt.putInt("HomeX", homeX);
		nbt.putInt("HomeY", homeY);
		nbt.putInt("HomeZ", homeZ);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		empire = nbt.getInt("Empire");
		raider = nbt.getBoolean("Raider");
		homeX = nbt.getInt("HomeX");
		homeY = nbt.getInt("HomeY");
		homeZ = nbt.getInt("HomeZ");
		dataTracker.set(ROLE, nbt.getInt("Role"));
	}

	// ---------------- أهداف الذكاء الاصطناعي ----------------

	/** يبحث عن أقرب عدو (الغزاة يبحثون في مدى أوسع). */
	static class FindEnemyGoal extends Goal {
		private final SoldierEntity me;
		private int cooldown = 0;

		FindEnemyGoal(SoldierEntity me) {
			this.me = me;
			setControls(EnumSet.of(Control.TARGET));
		}

		private LivingEntity find() {
			double r = me.raider ? 64 : 24;
			Box box = me.getBoundingBox().expand(r, 8, r);
			List<LivingEntity> list = me.getWorld().getEntitiesByClass(LivingEntity.class, box, me::isEnemy);
			LivingEntity best = null;
			double bd = Double.MAX_VALUE;
			for (LivingEntity e : list) {
				double d = me.squaredDistanceTo(e);
				if (d < bd) { bd = d; best = e; }
			}
			return best;
		}

		@Override
		public boolean canStart() {
			if (me.getTarget() != null && me.getTarget().isAlive()) return false;
			if (cooldown-- > 0) return false;
			cooldown = 10 + me.random.nextInt(10);
			return find() != null;
		}

		@Override
		public void start() {
			me.setTarget(find());
		}
	}

	/** الجندي يتبع اللاعب من نفس الإمبراطورية إذا فعّل /empire follow */
	static class FollowPlayerGoal extends Goal {
		private final SoldierEntity me;
		private ServerPlayerEntity target;
		private int recalc = 0;

		FollowPlayerGoal(SoldierEntity me) {
			this.me = me;
			setControls(EnumSet.of(Control.MOVE));
		}

		@Override
		public boolean canStart() {
			if (me.getRole() != 0 || me.raider) return false;
			if (!(me.getWorld() instanceof ServerWorld sw)) return false;
			EmpireState st = EmpireState.get(sw.getServer());
			ServerPlayerEntity best = null;
			double bd = 48 * 48;
			for (ServerPlayerEntity p : sw.getPlayers()) {
				if (!EmpireManager.FOLLOWING.contains(p.getUuid())) continue;
				Integer pe = st.players.get(p.getUuid());
				if (pe == null || pe != me.empire) continue;
				double d = me.squaredDistanceTo(p);
				if (d < bd) { bd = d; best = p; }
			}
			if (best == null || bd < 6 * 6) return false;
			target = best;
			return true;
		}

		@Override
		public boolean shouldContinue() {
			return target != null && target.isAlive()
					&& EmpireManager.FOLLOWING.contains(target.getUuid())
					&& me.squaredDistanceTo(target) > 3 * 3 && me.squaredDistanceTo(target) < 64 * 64;
		}

		@Override
		public void tick() {
			if (recalc-- <= 0) {
				recalc = 10;
				me.getNavigation().startMovingTo(target, 1.2);
			}
		}

		@Override
		public void stop() {
			target = null;
			me.getNavigation().stop();
		}
	}

	/** الجنود الحراس يرجعون للعاصمة إذا ابتعدوا. */
	static class ReturnHomeGoal extends Goal {
		private final SoldierEntity me;

		ReturnHomeGoal(SoldierEntity me) {
			this.me = me;
			setControls(EnumSet.of(Control.MOVE));
		}

		private double distSq() {
			double dx = me.getX() - (me.homeX + 0.5), dz = me.getZ() - (me.homeZ + 0.5);
			return dx * dx + dz * dz;
		}

		@Override
		public boolean canStart() {
			return !me.raider && me.getTarget() == null && distSq() > 28 * 28;
		}

		@Override
		public boolean shouldContinue() {
			return !me.getNavigation().isIdle() && distSq() > 8 * 8 && me.getTarget() == null;
		}

		@Override
		public void start() {
			me.getNavigation().startMovingTo(me.homeX + 0.5, me.homeY, me.homeZ + 0.5, 1.0);
		}
	}
}
