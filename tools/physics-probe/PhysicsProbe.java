import org.bukkit.*;
import org.bukkit.block.data.Rail;
import org.bukkit.block.data.Powerable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.cubexmc.metro.train.MinecartPhysicsCompatibility;
import org.cubexmc.metro.train.TrainPhysicsController;
import org.cubexmc.metro.util.MetroConstants;
public class PhysicsProbe extends JavaPlugin implements Listener {
    private final TrainPhysicsController physics = new TrainPhysicsController();
    Minecart moving, held, braking;
    double maxDelta, dockedX = Double.NaN;
    int tick;
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 400);
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            World w = Bukkit.getWorlds().get(0);
            getLogger().info("FEATURE_DETECTED="+MinecartPhysicsCompatibility.usesExperimentalMovement(w));
            for(int cx=-1;cx<=26;cx++) w.setChunkForceLoaded(cx,0,true);
            for (int x=0;x<400;x++) for(int z:new int[]{0,4,8}) {
                w.getBlockAt(x,63,z).setType(Material.REDSTONE_BLOCK,false);
                org.bukkit.block.data.BlockData r=Bukkit.createBlockData(Material.POWERED_RAIL);
                ((Rail)r).setShape(Rail.Shape.EAST_WEST); ((Powerable)r).setPowered(true);
                w.getBlockAt(x,64,z).setBlockData(r,false);
            }
            w.getBlockAt(-1,64,4).setType(Material.STONE,false);
            moving=cart(w,0);held=cart(w,4);braking=cart(w,8);
            moving.setMaxSpeed(3);moving.setVelocity(new Vector(3,0,0));
            held.setMaxSpeed(0);held.setVelocity(new Vector());
            braking.setMaxSpeed(8);braking.setVelocity(new Vector(8,0,0));
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                if(++tick==100) {
                    getLogger().info("RESULT maxBlocksPerTick="+maxDelta+" heldX="+held.getLocation().getX()+
                        " heldPassengers="+held.getPassengers().size()+" dockedX="+dockedX+
                        " brakingX="+braking.getLocation().getX()+" brakingPassengers="+braking.getPassengers().size());
                    moving.getPassengers().forEach(Entity::remove);held.getPassengers().forEach(Entity::remove);
                    braking.getPassengers().forEach(Entity::remove);
                    moving.remove();held.remove();braking.remove();Bukkit.shutdown();
                }
            },1,1);
        },20);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onMove(VehicleMoveEvent e) {
        if(e.getVehicle()==moving) maxDelta=Math.max(maxDelta,e.getTo().getX()-e.getFrom().getX());
        if(e.getVehicle()==braking && braking.getMaxSpeed()>0) {
            double distance=braking.getLocation().distance(new Location(braking.getWorld(),100.5,64.0625,8.5));
            if(distance<0.8) {
                dockedX=braking.getLocation().getX();braking.setMaxSpeed(0);braking.setVelocity(new Vector());
            } else physics.applyExperimentalApproachBraking(braking,distance);
        }
    }
    private Minecart cart(World w,int z) {
        Minecart c=w.spawn(new Location(w,.5,64.1,z+.5),Minecart.class);
        Pig p=w.spawn(new Location(w,.5,65,z+.5),Pig.class);p.setAI(false);c.addPassenger(p);
        c.getPersistentDataContainer().set(MetroConstants.getMinecartKey(),PersistentDataType.BYTE,(byte)1);
        return c;
    }
}
