package com.eb;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

public class EbTerrainMod implements ModInitializer {
    public static final String MOD_ID = "ebterrain";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final Item EB_SNOWBALL = new Item(new FabricItemSettings());
    public static EntityType<EbProjectile> EB_PROJECTILE;

    @Override
    public void onInitialize() {
        // 修复：1.20+ 正确注册方式 Registry.register
        EB_PROJECTILE = FabricEntityTypeBuilder.<EbProjectile>create(SpawnGroup.MISC, EbProjectile::new)
                .dimensions(EntityDimensions.fixed(0.25f,0.25f))
                .build();

        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "eb_snowball"), EB_SNOWBALL);
        Registry.register(Registries.ENTITY_TYPE, new Identifier(MOD_ID, "eb_projectile"), EB_PROJECTILE);

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if(stack.getItem() == EB_SNOWBALL && !world.isClient()){
                EbProjectile proj = new EbProjectile(EB_PROJECTILE, world);
                proj.setPos(player.getX(), player.getY()+1.5, player.getZ());
                proj.setVelocity(player, player.getPitch(), player.getYaw(), 0, 1.2f, 1);
                world.spawnEntity(proj);
                if(!player.isCreative()) stack.decrement(1);
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(EB_SNOWBALL));

        // 修复 AFTER_RESPAWN 参数签名
        ServerPlayerEvents.AFTER_RESPAWN.register((newPlayer, oldPlayer, alive) -> {
            newPlayer.getInventory().insertStack(new ItemStack(EB_SNOWBALL));
        });
    }

    public static float perlinNoise(float x, float z, long seed) {
        Random r = new Random(seed);
        int xi = (int)Math.floor(x);
        int zi = (int)Math.floor(z);
        float xf = x - xi;
        float zf = z - zi;
        float v00 = r.nextFloat()*2-1;
        float v01 = r.nextFloat()*2-1;
        float v10 = r.nextFloat()*2-1;
        float v11 = r.nextFloat()*2-1;
        float tx = fade(xf);
        float tz = fade(zf);
        float i1 = lerp(v00, v10, tx);
        float i2 = lerp(v01, v11, tx);
        return lerp(i1, i2, tz);
    }
    private static float fade(float t){return t*t*t*(t*(t*6-15)+10);}
    private static float lerp(float a,float b,float t){return a+t*(b-a);}

    public static void generateTerrain(World world, BlockPos center) {
        long seed = world.getRandom().nextLong();
        int radius = 32;
        for(int dx = -radius; dx <= radius; dx++){
            for(int dz = -radius; dz <= radius; dz++){
                float noise = perlinNoise(dx*0.5f, dz*0.5f, seed)*2;
                int height = (int)(noise*4);
                for(int dy = -10; dy <= 15; dy++){
                    BlockPos p = center.add(dx, dy, dz);
                    if(dy <= height){
                        if(dy == height){
                            if(noise < -0.3) world.setBlockState(p, Blocks.WATER.getDefaultState(),3);
                            else world.setBlockState(p, Blocks.GRASS_BLOCK.getDefaultState(),3);
                        }else if(dy > height -3){
                            world.setBlockState(p, Blocks.DIRT.getDefaultState(),3);
                        }else{
                            world.setBlockState(p, Blocks.STONE.getDefaultState(),3);
                        }
                    }else{
                        world.setBlockState(p, Blocks.AIR.getDefaultState(),3);
                    }
                }
                if(world.getRandom().nextFloat() < 0.04){
                    BlockPos treePos = center.add(dx, height+1, dz);
                    world.setBlockState(treePos, Blocks.OAK_LOG.getDefaultState(),3);
                    world.setBlockState(treePos.up(), Blocks.OAK_LOG.getDefaultState(),3);
                    world.setBlockState(treePos.up(2), Blocks.OAK_LEAVES.getDefaultState(),3);
                    world.setBlockState(treePos.up(2).north(), Blocks.OAK_LEAVES.getDefaultState(),3);
                    world.setBlockState(treePos.up(2).south(), Blocks.OAK_LEAVES.getDefaultState(),3);
                    world.setBlockState(treePos.up(2).east(), Blocks.OAK_LEAVES.getDefaultState(),3);
                    world.setBlockState(treePos.up(2).west(), Blocks.OAK_LEAVES.getDefaultState(),3);
                }
            }
        }
    }

    public static class EbProjectile extends ProjectileEntity {
        public EbProjectile(EntityType<? extends ProjectileEntity> type, World world) {
            super(type, world);
        }

        // 修复：使用正确方块碰撞回调
        @Override
        protected void onBlockHit(BlockHitResult hitResult) {
            super.onBlockHit(hitResult);
            World w = this.getWorld(); // 修复：不用私有字段world，使用getWorld()
            if(!w.isClient()){
                BlockPos hitPos = hitResult.getBlockPos();
                generateTerrain(w, hitPos);
            }
            this.discard();
        }

        @Override
        protected void initDataTracker() {}
    }
}
