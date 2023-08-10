package com.kamesuta.physxmc.wrapper;

import com.kamesuta.physxmc.PhysxSetting;
import com.kamesuta.physxmc.core.PhysxBox;
import com.kamesuta.physxmc.utils.ConversionUtility;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import physx.common.PxIDENTITYEnum;
import physx.common.PxQuat;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.geometry.PxBoxGeometry;
import physx.physics.PxForceModeEnum;
import physx.physics.PxPhysics;
import physx.physics.PxRigidBodyFlagEnum;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;

import static com.kamesuta.physxmc.core.Physx.defaultMaterial;

/**
 * Minecraft世界で表示可能なPhysxBox
 */
public class DisplayedPhysxBox extends PhysxBox {

    /**
     * 表示用のBlockDisplay
     */
    public BlockDisplay[] display;
    /**
     * スワップのフェーズ管理
     */
    public int swapPhase = 0;

    public DisplayedPhysxBox(PxPhysics physics, PxVec3 pos, PxQuat quat, Map<PxBoxGeometry, PxVec3> boxGeometries, BlockDisplay[] display) {
        super(physics, defaultMaterial, pos, quat, boxGeometries);

        this.display = display;
    }

    public void update() {
        trySwap();
    }

    /**
     * 物理の箱とBlockDisplayを同期する
     */
    private void trySwap() {
        PxQuat q = getPos().getQ();
        PxVec3 p = getPos().getP();
        Location pos = new Location(display[0].getWorld(), p.getX(), p.getY(), p.getZ());

        // スワップのフェーズ管理 (2ティックかけてスワップが完了する)
        if (swapPhase == 2) {
            swap(pos);
            swapPhase = 0;
        }
        if (swapPhase == 1) {
            preSwap(pos);
            swapPhase = 2;
        }
        // 位置が16マス以上離れていたら次のティックからスワップを開始する
        if (swapPhase == 0 && display[0].getLocation().toVector().distance(new Vector(p.getX(), p.getY(), p.getZ())) > 16) {
            swapPhase = 1;
        }

        for (BlockDisplay blockDisplay : display) {
            Location prev = blockDisplay.getLocation();

            Quaternionf boxQuat = new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW());
            Transformation transformation = blockDisplay.getTransformation();
            transformation.getLeftRotation().set(boxQuat);
            transformation.getTranslation().set(p.getX() - prev.getX(), p.getY() - prev.getY(), p.getZ() - prev.getZ());
            Matrix4f matrix = ConversionUtility.getTransformationMatrix(transformation);
            matrix.translate(-.5f, -.5f, -.5f);
            blockDisplay.setTransformationMatrix(matrix);
            // なめらかに補完する
            blockDisplay.setInterpolationDelay(0);
            blockDisplay.setInterpolationDuration(1);
            // blockDisplay.teleport(new Location(blockDisplay.getWorld(), p.getX(), p.getY(), p.getZ()));
        }
    }

    /**
     * スワップの1ティック前に呼ぶ
     *
     * @param pos 新しい位置
     */
    private void preSwap(Location pos) {
        display[0].setVisibleByDefault(true);
    }

    /**
     * TPの移動が見えないようにスワップする
     *
     * @param pos 新しい位置
     */
    private void swap(Location pos) {
        display[1].setVisibleByDefault(false);
        display[1].teleport(pos);

        BlockDisplay temp = display[1];
        display[1] = display[0];
        display[0] = temp;
    }

    /**
     * 箱をコンフィグで設定したパワーで投げる
     *
     * @param location 向き
     */
    public void throwBox(Location location) {
        double power = PhysxSetting.getThrowPower();
        Vector3f rot = location.getDirection().clone().multiply(power).toVector3f();
        PxVec3 force = new PxVec3(rot.x, rot.y, rot.z);
        addForce(force, PxForceModeEnum.eVELOCITY_CHANGE);
    }

    /**
     * 箱の周囲のチャンクを取得
     *
     * @return 箱があるチャンクとその8方にあるチャンク
     */
    public Collection<Chunk> getSurroundingChunks() {
        int[] offset = {-1, 0, 1};

        World world = display[0].getWorld();
        PxVec3 p = getPos().getP();
        Location pos = new Location(display[0].getWorld(), p.getX(), p.getY(), p.getZ());
        int baseX = pos.getChunk().getX();
        int baseZ = pos.getChunk().getZ();

        Collection<Chunk> chunksAround = new HashSet<>();
        for (int x : offset) {
            for (int z : offset) {
                Chunk chunk = world.getChunkAt(baseX + x, baseZ + z);
                chunksAround.add(chunk);
            }
        }
        return chunksAround;
    }

    /**
     * boxの持つ回転を取得する
     *
     * @return
     */
    public Quaternionf getQuat() {
        PxQuat q = getPos().getQ();
        return new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW());
    }

    /**
     * boxの座標と回転をminecraftのLocationの形式で取得する(rollは失われる)
     *
     * @return location
     */
    public Location getLocation() {
        PxVec3 vec3 = getPos().getP();
        PxQuat q = getPos().getQ();
        Quaternionf boxQuat = new Quaternionf(q.getX(), q.getY(), q.getZ(), q.getW());
        Vector3f dir = ConversionUtility.convertToEulerAngles(boxQuat);
        Vector dir2 = new Vector(dir.x, dir.y, dir.z);
        Location loc = new Location(display[0].getWorld(), vec3.getX(), vec3.getY(), vec3.getZ());
        loc.setDirection(dir2);
        return loc;
    }

    /**
     * boxが重力の影響を受けないようにするか変更する
     */
    public void makeKinematic(boolean flag) {
        getActor().setRigidBodyFlag(PxRigidBodyFlagEnum.eKINEMATIC, flag);
    }

    /**
     * 重力の影響を受けないboxを移動させる
     */
    public void moveKinematic(Location location) {
        Vector p = new Vector((float) location.x(), (float) location.y(), (float) location.z());

        // 相対的に回転させる
        Quaternionf quat = new Quaternionf();
        quat.rotateY((float) -Math.toRadians(location.getYaw()));
        quat.rotateX((float) Math.toRadians(location.getPitch()));

        moveKinematic(p, quat);
    }

    public void moveKinematic(Vector pos, Quaternionf rot) {
        PxVec3 p = new PxVec3((float) pos.getX(), (float) pos.getY(), (float) pos.getZ());

        PxQuat q = new PxQuat(rot.x, rot.y, rot.z, rot.w);
        PxTransform transform = new PxTransform(PxIDENTITYEnum.PxIdentity);
        transform.setP(p);
        transform.setQ(q);
        getActor().setKinematicTarget(transform);
        p.destroy();
        q.destroy();
        transform.destroy();
    }

    /**
     * 表示部分のDisplayがMinecraft側でkillされたかどうか
     * @return
     */
    public boolean isDisplayDead(){
        return display[0].isDead() || display[1].isDead();
    }
}
