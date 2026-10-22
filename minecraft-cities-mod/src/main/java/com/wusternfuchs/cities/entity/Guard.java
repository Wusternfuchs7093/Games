package com.wusternfuchs.cities.entity;

import com.wusternfuchs.cities.settlement.ReputationRank;
import com.wusternfuchs.cities.settlement.Settlement;
import com.wusternfuchs.cities.settlement.SettlementData;
import com.wusternfuchs.cities.settlement.SettlementTier;
import com.wusternfuchs.cities.war.WarManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

/** A town guard: patrols the settlement, fights monsters and raiders, and attacks outlaws on sight. */
public class Guard extends PathfinderMob {
    private static final int HEAL_INTERVAL = 40;

    @Nullable
    private UUID settlementId;
    private BlockPos post = BlockPos.ZERO;
    private int patrolRadius = 32;
    /** Soldiers are sent to fight another town; they go home (vanish) when the battle ends. */
    private boolean soldier;

    public Guard(EntityType<? extends Guard> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            setDropChance(slot, 0.0f);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 3.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector.addGoal(2, new MoveTowardsRestrictionGoal(this, 0.8));
        goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6));
        goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        targetSelector.addGoal(1, new HurtByTargetGoal(this, Guard.class, IronGolem.class, AbstractVillager.class)
                .setAlertOthers(Guard.class));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Guard.class, 5, false, false, this::isEnemyGuard));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isOutlaw));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, false, false,
                target -> target instanceof Enemy && !(target instanceof Creeper)));
    }

    /** Assigns the guard to a settlement and equips them as well as a town of {@code gear} tier can. */
    public void enlist(Settlement settlement, BlockPos post, int patrolRadius, SettlementTier gear) {
        this.settlementId = settlement.id();
        this.post = post.immutable();
        this.patrolRadius = patrolRadius;
        restrictTo(this.post, this.patrolRadius);
        equip(gear);
    }

    /** Sends the guard to war: they march on {@code target} and fight whoever defends it. */
    public void enlistSoldier(Settlement settlement, BlockPos target, SettlementTier gear) {
        enlist(settlement, target, 12, gear);
        this.soldier = true;
        setCustomName(Component.translatable("cities.war.soldier", settlement.name()));
    }

    public boolean isSoldier() {
        return soldier;
    }

    /** Guards of the same town never fight each other, and do not rally against each other either. */
    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity instanceof Guard other && settlementId != null && Objects.equals(other.settlementId, settlementId)) {
            return true;
        }
        return super.isAlliedTo(entity);
    }

    private boolean isEnemyGuard(LivingEntity entity) {
        if (!(entity instanceof Guard other) || settlementId == null || other.settlementId == null
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        return WarManager.atWar(SettlementData.get(serverLevel), settlementId, other.settlementId);
    }

    public void equip(SettlementTier tier) {
        int level = tier.ordinal();
        boolean iron = level >= SettlementTier.SMALL_TOWN.ordinal();
        boolean chain = level >= SettlementTier.TOWNSHIP.ordinal();
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(iron ? Items.IRON_SWORD : Items.STONE_SWORD));
        if (level >= SettlementTier.METROPOLIS.ordinal()) {
            setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        }
        armor(EquipmentSlot.HEAD, iron ? Items.IRON_HELMET : chain ? Items.CHAINMAIL_HELMET : Items.LEATHER_HELMET);
        armor(EquipmentSlot.CHEST, iron ? Items.IRON_CHESTPLATE : chain ? Items.CHAINMAIL_CHESTPLATE : Items.LEATHER_CHESTPLATE);
        armor(EquipmentSlot.LEGS, iron ? Items.IRON_LEGGINGS : chain ? Items.CHAINMAIL_LEGGINGS : Items.LEATHER_LEGGINGS);
        armor(EquipmentSlot.FEET, iron ? Items.IRON_BOOTS : chain ? Items.CHAINMAIL_BOOTS : Items.LEATHER_BOOTS);
    }

    private void armor(EquipmentSlot slot, Item item) {
        setItemSlot(slot, new ItemStack(item));
    }

    @Nullable
    public UUID settlementId() {
        return settlementId;
    }

    /** Players the town considers enemies are attacked on sight. */
    private boolean isOutlaw(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()
                || !(level() instanceof ServerLevel serverLevel) || settlementId == null) {
            return false;
        }
        Settlement settlement = SettlementData.get(serverLevel).byId(settlementId);
        return settlement != null && ReputationRank.of(settlement.reputation(player.getUUID())) == ReputationRank.ENEMY;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % HEAL_INTERVAL == 0 && getTarget() == null
                && getHealth() < getMaxHealth()) {
            heal(1.0f);
        }
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (settlementId != null) {
            tag.putUUID("Settlement", settlementId);
        }
        tag.put("Post", NbtUtils.writeBlockPos(post));
        tag.putInt("PatrolRadius", patrolRadius);
        tag.putBoolean("Soldier", soldier);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        settlementId = tag.hasUUID("Settlement") ? tag.getUUID("Settlement") : null;
        post = NbtUtils.readBlockPos(tag, "Post").orElse(blockPosition());
        patrolRadius = tag.contains("PatrolRadius") ? tag.getInt("PatrolRadius") : 32;
        soldier = tag.getBoolean("Soldier");
        restrictTo(post, patrolRadius);
    }
}
