package com.kamesuta.physxmc

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.ArmorStand
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import physx.common.PxVec3
import physx.physics.PxRigidDynamic

class PhysxMc : JavaPlugin(), Listener {
    lateinit var physicsWorld: PhysicsWorld

    override fun onEnable() {
        // Plugin startup logic
        PhysxLoader.loadPhysxOnAppClassloader()
        Physx.init()

        // TODO: ワールド取得
        physicsWorld = PhysicsWorld(server.worlds[0])

        server.pluginManager.registerEvents(this, this)
        server.scheduler.runTaskTimer(this, this::tick, 0, 0)

        val armorStand = physicsWorld.level.spawn(Location(physicsWorld.level, 0.0, 10.0, 0.0), ArmorStand::class.java)
        //armorStand.isInvulnerable = true
        //armorStand.isMarker = true
        armorStand.isVisible = false
        armorStand.setItem(EquipmentSlot.HEAD, ItemStack(Material.TNT))
        armorStand.setGravity(false)
        physicsWorld.addRigidBody(
            BoxRigidBody(
                PhysicsEntity.ArmorStandEntity(armorStand),
                1f,
                1f,
                1f,
                0f,
                0f,
                0f,
                true
            )
        )
    }

    override fun onDisable() {
        // Plugin shutdown logic
        physicsWorld.destroy()
        Physx.release()
    }

    var lastNanoTime = System.nanoTime()

    private fun tick() {
        val nanoTime = System.nanoTime()
        physicsWorld.update((nanoTime - lastNanoTime) / 1_000_000_000.0)
        lastNanoTime = nanoTime
    }

    @EventHandler
    fun onDamage(event: EntityDamageByEntityEvent) {
        val damager = event.damager.location
        val armorStand = event.entity as? ArmorStand
            ?: return
        val rigidBody = physicsWorld.findEntity(armorStand)
            ?: return
        val rigidDynamic = rigidBody.actor as? PxRigidDynamic
            ?: return
        // キャンセル
        event.isCancelled = true
        // 力を加える
        val force = damager.direction.clone().multiply(100)
        val pxForce = PxVec3(force.x.toFloat(), force.y.toFloat(), force.z.toFloat())
        rigidDynamic.addForce(pxForce)
        pxForce.destroy()
    }
}