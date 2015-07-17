package org.printcraft.printplot;

//import org.apache.http.client.ClientProtocolException;
//import org.apache.http.client.HttpClient;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CommandBlock;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.databases.ProtectionDatabaseException;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.CuboidClipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;


import com.sk89q.worldedit.data.DataException;
import com.sk89q.worldedit.masks.CombinedMask;
import com.sk89q.worldedit.masks.Mask;
import com.sk89q.worldedit.masks.RegionMask;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.schematic.SchematicFormat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


 
class UnclaimTask extends BukkitRunnable {
 
    private final PrintPlot plugin;
 
    public UnclaimTask(PrintPlot plugin) {
        this.plugin = plugin;
    }
 
    public void run() {
        // What you want to schedule goes here
    	plugin.checkAllClaims();
    }
}


public class PrintPlot extends JavaPlugin implements Listener {
	
	private static final byte [] BUTTON_ORIENTATION_LOOKUP = {4, 2, 3, 1};
	private static final byte [] SIGN_ORIENTATION_LOOKUP = {2, 4, 3, 5};
	public static StringFlag UNCLAIM_TIME_FLAG = new StringFlag("unclaim-time");
	public static IntegerFlag ORIENTATION_FLAG = new IntegerFlag("orientation");
	private static final int CONTROLS_OFFSET = 5;

    @Override
    public void onEnable(){
    	this.saveDefaultConfig();
    	getServer().getPluginManager().registerEvents(this, this);
    	WGCustomFlagsPlugin wgCustomFlagsPlugin = getWGCustomFlags();
    	wgCustomFlagsPlugin.addCustomFlag(UNCLAIM_TIME_FLAG);
    	wgCustomFlagsPlugin.addCustomFlag(ORIENTATION_FLAG);
    	int check = this.getConfig().getInt("claimcheck");
    	updateAllOwnershipDisplays();
    	getServer().getScheduler().scheduleSyncRepeatingTask(this, new UnclaimTask(this), 20*check, 20*check);
    	
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        setMask(player);
    }
    
    @EventHandler
    public void onLogin(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        setMask(player);
    }
    
    private WGCustomFlagsPlugin getWGCustomFlags()
    {
      Plugin plugin = getServer().getPluginManager().getPlugin("WGCustomFlags");
      if (plugin == null || !(plugin instanceof WGCustomFlagsPlugin))
      {
        return null;
      }
      return (WGCustomFlagsPlugin) plugin;
    }
 
    @Override
    public void onDisable() {
    	
    }
    

    private WorldGuardPlugin getWorldGuard(){
    	
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");
     
        // WorldGuard may not be loaded
        if (plugin == null || !(plugin instanceof WorldGuardPlugin)) {
            return null; // Maybe you want throw an exception instead
        }
     
        return (WorldGuardPlugin) plugin;
    }
    
    private WorldEditPlugin getWorldEdit(){
    	
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldEdit");
     
        // WorldGuard may not be loaded
        if (plugin == null || !(plugin instanceof WorldEditPlugin)) {
            return null; // Maybe you want throw an exception instead
        }
     
        return (WorldEditPlugin) plugin;
    }
    
    public void updateAllOwnershipDisplays(){
    	
    	List<World> worlds = getServer().getWorlds();
    	WorldGuardPlugin worldGuard = getWorldGuard();
    	
		for (World world : worlds)
		{
			RegionManager regionManager = worldGuard.getRegionManager(world);
			Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
			
			for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
			{
				ProtectedRegion region = entry.getValue();
				if(region.hasMembersOrOwners()){
					String ownername = (String) region.getOwners().getPlayers().toArray()[0];
					setAreaOwner(world, region, ownername);
				}
				else{
					setAreaOwner(world, region, "");
				}
			}
		}
    }
    
    public boolean tp(World world, Player player, String[] args){
    	
		Player player2;
		
    	//if a playername is given
    	if(args.length == 1){
    		String name = args[0];
    		player2 = (getServer().getPlayer(name));
            if (player2 == null) {
                player.sendMessage(name + " is not online!");
                return false;
             }
    	}
    	else{
    		return false;
    	}
    	
    	Location tploc = player2.getLocation();
    	player.teleport(tploc);

		return true;
    }
    
    
    public void loadFromSchematic(World world, ProtectedRegion region){
 
    	Vector min = region.getMinimumPoint();
    	Vector max = region.getMaximumPoint();
    	Vector size = max.subtract(min).add(1, 1, 1);

		SchematicFormat sf  = SchematicFormat.MCEDIT;
		File schematic_dir = new File("plugins/printplot/saves");
		if (!schematic_dir.exists()) {
			schematic_dir.mkdirs();
		}

		String localFilename = "plugins/printplot/saves/" + region.getId() + ".schematic";
		File schfile = new File(localFilename);
		
		if (!schfile.exists()) {
			return;
		}

		BukkitWorld localworld = new BukkitWorld(world);
		EditSession editSession = new EditSession(localworld, size.getBlockX() * size.getBlockY() * size.getBlockZ());
		CuboidClipboard clipboard = null;
		try {
			clipboard = sf.load(schfile);
		} catch (DataException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			clipboard.place(editSession, min, false);
		} catch (MaxChangedBlocksException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
    }
    
    public void saveToSchematic(World world, ProtectedRegion region){
    	
    	Vector min = region.getMinimumPoint();
    	Vector max = region.getMaximumPoint();
    	Vector size = max.subtract(min).add(1, 1, 1);
    	
		BukkitWorld localworld = new BukkitWorld(world);
		EditSession editSession = new EditSession(localworld, size.getBlockX() * size.getBlockY() * size.getBlockZ());
		CuboidClipboard clipboard = new CuboidClipboard(size, min);
		clipboard.copy(editSession);

		SchematicFormat sf  = SchematicFormat.MCEDIT;
		File schematic_dir = new File("plugins/printplot/saves");
		if (!schematic_dir.exists()) {
			schematic_dir.mkdirs();
		}
		
		File file = null;
		try {
			file = new File("plugins/printplot/saves/" + region.getId() + ".schematic");
			try {
				sf.save(clipboard, file);
			} catch (DataException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} catch (IOException e1) {
			// TODO Auto-generated catch blockw
			e1.printStackTrace();
		} 

    }
    
    public boolean saveall(World world, RegionManager regionManager, Player player){
		getLogger().info("printbot saving all plots");
		
	    if(player.hasPermission("printplot.saveall")) {
	    	
	    	Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
    		
    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
    		{
        		try {
        			Integer.valueOf(entry.getKey());
        			saveToSchematic(world, entry.getValue());
        			
				} catch (NumberFormatException e) {
				}
        		catch (Exception e) {
					e.printStackTrace();
				}
    		}
	    }
	    else{
	    	player.sendMessage("You don't have permission to save plots");
	    	
	    }
	    return true;
    }
    
    
    public boolean loadall(World world, RegionManager regionManager, Player player){
		getLogger().info("printbot loading all plots");
		
	    if(player.hasPermission("printplot.saveall")) {
	    	
	    	Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
    		
    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
    		{
        		try {
        			Integer.valueOf(entry.getKey());
        			loadFromSchematic(world, entry.getValue());
        			
				} catch (NumberFormatException e) {
				}
        		catch (Exception e) {
					e.printStackTrace();
				}
    		}
	    }
	    else{
	    	player.sendMessage("You don't have permission to load plots");
	    	
	    }
	    return true;
    }
    
    
    public boolean home(World world, RegionManager regionManager, Player player){
		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
		
		ProtectedRegion region = null;
		
		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
		{
			if (entry.getValue().isOwner(player.getName())){
				region = entry.getValue();
			}
		}
		
		if(region != null){
			
			BlockVector max = region.getMaximumPoint();
			BlockVector min = region.getMinimumPoint();
			
			int x = (max.getBlockX() + min.getBlockX())/2;
			int z = (max.getBlockZ() + min.getBlockZ())/2;
			int y = max.getBlockY() + 5;
			
			player.setFlying(true);
			Location tplocation = new Location(world, x, y, z);
			player.teleport(tplocation);
		}
		else{
			player.sendMessage("You dont have a claim at the moment");
		}
	
		return true;
    }
    
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	
    	Player player;
    	
    	if (sender instanceof Player){
    		player = (Player) sender;
    	}
    	else{
    		
    		player = (getServer().getPlayer(args[1]));
            if (player == null) {
                sender.sendMessage(args[1] + " is not online!");
                return false;
             }
    	}
    	
		Location loc = player.getLocation();
		World world = loc.getWorld();
		WorldGuardPlugin worldGuard = getWorldGuard();
		RegionManager regionManager = worldGuard.getRegionManager(world);
		String playerName = player.getName();
		
		//send player home
    	if(cmd.getName().equalsIgnoreCase("home")){ 
    		return this.home(world, regionManager, player);
    	}
    	
		//send player to other player
    	if(cmd.getName().equalsIgnoreCase("tp")){ 
    		return this.tp(world, player, args);
    	}
    	
		//send player to spawn
    	if(cmd.getName().equalsIgnoreCase("spawn")){ 
    		
        	Location tploc = world.getSpawnLocation();
        	player.teleport(tploc);
 
			return true;
    	}
    	
		//save all plots
    	if(cmd.getName().equalsIgnoreCase("printplot_saveall")){ 
    		
    		return this.saveall(world, regionManager, player);
    	}
    	
		//load all plots
    	if(cmd.getName().equalsIgnoreCase("printplot_loadall")){ 
    		
    		return this.loadall(world, regionManager, player);
    	}
    	

    	//claim a plot
    	if(cmd.getName().equalsIgnoreCase("printplot_claim")){ 
    		String area = args[0];
    		ProtectedRegion region  = regionManager.getRegion(area);
    		
    		getLogger().info("printplot claiming");
    		
    	    if(player.hasPermission("printplot.claim")) {
    	    	
    	    	int claimPeriod = this.getConfig().getInt("claim");

    	    	if(!region.hasMembersOrOwners()){
    	    		
    	    		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
    	    		
    	    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
    	    		{
    	    			if (entry.getValue().isOwner(playerName)){
    	    				player.sendMessage("You can't claim this plot. You have a plot already");
    	    				return true;
    	    			}
    	    		}
    	    		
        			player.sendMessage("You have claimed this plot for 1 week");
            		region.getOwners().addPlayer(playerName);
            		setMask(player);
            		try {
						region.setFlag(DefaultFlag.BUILD, DefaultFlag.BUILD.parseInput(worldGuard, sender, "none"));
					} catch (InvalidFlagFormat e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
            		setUnclaimFlag(region, (long)claimPeriod);

            		getLogger().info("about to set owner");
            		setAreaOwner(world, region, playerName);
            		try {
						regionManager.save();
					} catch (ProtectionDatabaseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
            		return true;
    	    	}
    	    	else{

    	    		if(region.isOwner(playerName)){
    	    			setUnclaimFlag(region, (long)claimPeriod);
                		try {
    						regionManager.save();
    					} catch (ProtectionDatabaseException e) {
    						// TODO Auto-generated catch block
    						e.printStackTrace();
    					}
    	    			player.sendMessage("You have renewed the claim on this plot for one week from now");
    	    			return true;
    	    		}
    	    		else{
    	    	    	player.sendMessage("You can't renew the claim on this plot it isn't yours");
    	    	    	return true;
    	    		}
    	    	}
    	    }
    	    else{
    	    	player.sendMessage("You dont have permission to claim this plot");
    	    	return true;
    	    }
    	}
    	
    	//unclaim a plot
    	if(cmd.getName().equalsIgnoreCase("printplot_unclaim")){ 
    		String area = args[0];
    		ProtectedRegion region  = regionManager.getRegion(area);
    		
    		
    	    if(player.hasPermission("printplot.unclaim")) {

    	    	if(!region.hasMembersOrOwners()){
        			player.sendMessage("You cant unclaim this plot. It doesn't have an owner");
            		return true;
    	    	}
    	    	else{
    	    		if(region.isOwner(playerName) || player.hasPermission("printplot.unclaimall")){
    	    			unclaimRegion(world, region, sender);
    	    			setMask(player);
    	    			player.sendMessage("You have unclaimed this plot");
    	    			return true;
    	    		}
    	    		else{
    	    	    	player.sendMessage("You can't unclaim this plot it isn't yours");
    	    	    	return true;
    	    		}
    	    	}
    	    }
    	    else{
    	    	player.sendMessage("You dont have permission to unclaim this plot");
    	    	return true;
    	    }
    	}

    	//create all plots
    	if(cmd.getName().equalsIgnoreCase("printplot_create")){ 
			
    		getLogger().info("printbot creating all plots");
    		
    	    if(player.hasPermission("printplot.create")) {
    	    	
    	    	Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
	    		
	    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
	    		{
            		try {
            			Integer.valueOf(entry.getKey());
            			createPlot(world, entry.getValue());
            			
					} catch (NumberFormatException e) {
					}
            		catch (Exception e) {
						e.printStackTrace();
					}
	    		}
    	    }
    	    else{
    	    	player.sendMessage("You don't have permission to create plots");
    	    	return true;
    	    }
    	}
    	
    	if(cmd.getName().equalsIgnoreCase("printplot_add")){ 
    		
    		if(player.hasPermission("printplot.add")){
        		String addedPlayerName = args[0];
        		
        		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
        		
        		ProtectedRegion region = null;
        		
        		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
        		{
        			if (entry.getValue().isOwner(playerName)){
        				region = entry.getValue();
        			}
        		}
        		
        		if(region == null){
        	    	player.sendMessage("You don't own a region to add them to");
        	    	return true;	
        		}

        		if(!region.isMember(addedPlayerName)){
        			region.getMembers().addPlayer(addedPlayerName);
        	    	player.sendMessage(addedPlayerName + " has been added to your plot");
        	    	
            		try {
						regionManager.save();
					} catch (ProtectionDatabaseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

        	    	return true;	
        		}
        		else{
        	    	player.sendMessage(addedPlayerName + " is already a member");
        	    	return true;
        		}
    		}
    		else{
    	    	player.sendMessage("You don't have permission to add players to plots");
    	    	return true;
    		}
    	}
    	
    	if(cmd.getName().equalsIgnoreCase("printplot_clear")){ // If the player typed /basic then do the following...
    		getLogger().info("printplot_clear has been invoked!");
    		
    		String area = args[0];
    		ProtectedRegion region  = regionManager.getRegion(area);
    	
    		boolean can_build = true;
    		
    		if(region.hasMembersOrOwners()){
    			can_build = region.isMember(playerName);
    		}
    		else{
    			can_build = true;
    		}
			
    	    if((can_build  && player.hasPermission("printplot.clear")) || player.hasPermission("printplot.clearall")) {

    			player.sendMessage("Clearing print area");		
	        	BlockVector min = region.getMinimumPoint();
	        	BlockVector max = region.getMaximumPoint();

	    		for (int x = min.getBlockX(); x <= max.getBlockX(); x++){
	   			     for (int y = min.getBlockY(); y <= max.getBlockY(); y++){
	   			        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
	   			        	Block b = world.getBlockAt(x, y, z);
	   			        	b.setType(Material.AIR);
	   			        }
	   			    }
	   			}	
        		return true;
    	    }
    	    else{
    	    	player.sendMessage("You dont have permission to clear");
    	    	return true;
    	    }

    	} //If this has happened the function will return true. 
    	
    	if(cmd.getName().equalsIgnoreCase("printplot_remove")){ 
    		
    		if(player.hasPermission("printplot.add")){
    		
	    		String removedPlayerName = args[0];
	    		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
	    		ProtectedRegion region = null;
	    		
	    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
	    		{
	    			if (entry.getValue().isOwner(playerName)){
	    				region = entry.getValue();
	    			}
	    		}
	    		
	    		if(region == null){
	    	    	player.sendMessage("You don't own a region to remove them from");
	    	    	return true;	
	    		}
	
	    		if(!region.isMember(removedPlayerName)){
	    	    	player.sendMessage(removedPlayerName + " isn't a member of your plot");
	    	    	return true;	
	    		}
	    		else{
	    			region.getMembers().removePlayer(removedPlayerName);
	    			
            		try {
						regionManager.save();
					} catch (ProtectionDatabaseException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    			
	    	    	player.sendMessage(removedPlayerName + " has been removed from your plot");
	    	    	return true;
	    		}
    		}
    		else{
    	    	player.sendMessage("You don't have permission to remove players from plots");
    	    	return true;
    		}
    	}
    	return false; 
    }
    
    
    public void unclaimRegion(World world, ProtectedRegion region, CommandSender sender){
		region.setMembers(new DefaultDomain());
		region.setOwners(new DefaultDomain());

		setAreaOwner(world, region, "");
		WorldGuardPlugin worldGuard = getWorldGuard();
		RegionManager regionManager = worldGuard.getRegionManager(world);
		
		try {
			region.setFlag(DefaultFlag.BUILD, DefaultFlag.BUILD.parseInput(worldGuard, sender, "allow"));
		} catch (InvalidFlagFormat e1) {
			e1.printStackTrace();
		}

		try {
			regionManager.save();
		} catch (ProtectionDatabaseException e) {
			e.printStackTrace();
		}	
    }
    
    
    public void checkAllClaims(){
    	
    	
    	List<World> worlds = getServer().getWorlds();
    	WorldGuardPlugin worldGuard = getWorldGuard();
    	CommandSender sender = getServer().getConsoleSender();
    	
		for (World world : worlds)
		{
			RegionManager regionManager = worldGuard.getRegionManager(world);
			Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
			
			for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
			{
				ProtectedRegion region = entry.getValue();
				if(region.hasMembersOrOwners()){
					if(checkUnclaimFlag(region)){
						unclaimRegion(world, region, sender);
					}
				}
			}
    		try {
				regionManager.save();
			} catch (ProtectionDatabaseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
    }
    
    
    public void createPlot(World world, ProtectedRegion region){
    	
    	int orientation = 0;

		try {
			orientation = region.getFlag(ORIENTATION_FLAG);		
		} catch (NullPointerException e) {
			region.setFlag(ORIENTATION_FLAG, 0);	
		}
    	
    	BlockVector min = region.getMinimumPoint();
    	BlockVector max = region.getMaximumPoint();
    	Vector bloc = BlockVector.getMidpoint(min, max);
    	Location loc = new Location(world, bloc.getBlockX(), min.getBlockY(), bloc.getBlockZ());
    	Vector dims = max.subtract(min);
    	
    	double cx = 0.0;
    	double cz = 0.0;
    	
    	if(orientation == 0){
    		cz = (dims.getBlockZ()/2) + CONTROLS_OFFSET;
    	}
    	if(orientation == 1){
    		cx = (dims.getBlockX()/2) + CONTROLS_OFFSET;
    	}
    	if(orientation == 2){
    		cz = -((dims.getBlockZ()/2) + CONTROLS_OFFSET);
    	}
    	if(orientation == 3){
    		cx = -((dims.getBlockX()/2) + CONTROLS_OFFSET);
    	} 	
    	
    	Location control_origin = loc.clone().add(cx, 0.0, cz);
    	
    	int minx = min.getBlockX();
    	int miny = min.getBlockY();
    	int minz = min.getBlockZ();
    	int maxx = max.getBlockX();
    	int maxy = max.getBlockY();
    	int maxz = max.getBlockZ();
    	
		for (int x = minx - 1; x <= maxx + 1; x++){
	        for (int z = minz - 1; z <= maxz + 1; z++) {
		        Block b = world.getBlockAt(x, miny - 1, z);
		        b.setType(Material.GRASS);
	        }
		}
    	
		for (int x = minx - 1; x <= maxx + 1; x++){
		     for (int y = miny - 1; y <= maxy + 1; y++){
		        for (int z = minz - 1; z <= maxz + 1; z++) {
		        	int truth = 0;
		        	if(x == minx - 1 || x == maxx + 1)truth++;
		        	if(y == miny - 1 || y == maxy + 1)truth++;
		        	if(z == minz - 1 || z == maxz + 1)truth++;
		        	if(truth > 1){
			        	Block b = world.getBlockAt(x, y, z);
			        	if(y == miny - 1){
			        		b.setType(Material.GLOWSTONE);
			        	}
			        	else{
			        		b.setType(Material.AIR);
			        	}
		        	}
		        }
		    }
		}	

		/*
		for (int x = minx - 1; x <= maxx + 1; x++){
        	Block b = world.getBlockAt(x, miny - 1, bloc.getBlockZ());
        	b.setTypeId(Material.GLOWSTONE.getId());
		}	
		
		for (int z = minz - 1; z <= maxz + 1; z++){
        	Block b = world.getBlockAt(bloc.getBlockX(), miny - 1, z);
        	b.setTypeId(Material.GLOWSTONE.getId());
		}
		*/
		
	
    	Block b = world.getBlockAt(bloc.getBlockX(), miny - 1, bloc.getBlockZ());
    	b.setType(Material.LAPIS_BLOCK);
    	
    	b = world.getBlockAt(bloc.getBlockX()-2, miny - 1, bloc.getBlockZ());
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX()-1, miny - 1, bloc.getBlockZ());
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX()+1, miny - 1, bloc.getBlockZ());
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX()+2, miny - 1, bloc.getBlockZ());
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX(), miny - 1, bloc.getBlockZ() - 2);
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX(), miny - 1, bloc.getBlockZ() - 1);
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX(), miny - 1, bloc.getBlockZ() + 1);
    	b.setType(Material.GLOWSTONE);
    	b = world.getBlockAt(bloc.getBlockX(), miny - 1, bloc.getBlockZ() + 2);
    	b.setType(Material.GLOWSTONE);
    	
		String plotId = region.getId();
		
		setBlockAndData(world, control_origin, new Location(world, -3, 0, 0), Material.AIR, orientation, (byte)2);
		
		setBlockAndData(world, control_origin, new Location(world, -2, 0, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, -1, 0, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 0, 0, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 1, 0, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 2, 0, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 3, 0, 0), Material.AIR, orientation, (byte)2);

		setBlockAndData(world, control_origin, new Location(world, -3, 1, 0), Material.AIR, orientation, (byte)2);
		setBlockAndData(world, control_origin, new Location(world, -2, 1, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, -1, 1, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 0, 1, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 1, 1, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 2, 1, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		setBlockAndData(world, control_origin, new Location(world, 3, 1, 0), Material.AIR, orientation, (byte)2);
		
		setBlockAndData(world, control_origin, new Location(world, 0, 0, -1), Material.AIR, orientation, (byte)2);
		setBlockAndData(world, control_origin, new Location(world, 0, 1, -1), Material.AIR, orientation, (byte)2);
		
	
		setBlockAndData(world, control_origin, new Location(world, 0, 2, 0), Material.QUARTZ_BLOCK, orientation, (byte)0);
		
		
		setButton(world, control_origin, new Location(world, -2, 1, -1), Material.STONE_BUTTON, orientation);
		setButton(world, control_origin, new Location(world, -1, 1, -1), Material.STONE_BUTTON, orientation);
	
		setButton(world, control_origin, new Location(world, 1, 1, -1), Material.STONE_BUTTON, orientation);
		setButton(world, control_origin, new Location(world, 2, 1, -1), Material.STONE_BUTTON, orientation);
		
		setCommand(world, control_origin, new Location(world, 2, 2, 0), orientation, "print " + plotId + " @p");
		setCommand(world, control_origin, new Location(world, 1, 2, 0), orientation, "printplot_clear " + plotId + " @p");
		
		setCommand(world, control_origin, new Location(world, -1, 2, 0), orientation, "printplot_claim " + plotId + " @p");
		setCommand(world, control_origin, new Location(world, -2, 2, 0), orientation, "printplot_unclaim " + plotId + " @p");
		
		
		String[] text = {"", "PRINT", "", ""};
		setSign(world, control_origin, new Location(world, 2, 0, -1), text, orientation);

		String[] text2 = {"", "CLEAR", "", ""};
		setSign(world, control_origin, new Location(world, 1, 0, -1), text2, orientation);
    	
		String[] text3 = {"", "CLAIM", "24 HOURS", ""};
		setSign(world, control_origin, new Location(world, -1, 0, -1), text3, orientation);
		
		String[] text4 = {"", "UNCLAIM", "", ""};
		setSign(world, control_origin, new Location(world, -2, 0, -1), text4, orientation);
		
		String[] text5 = {"Plot " + region.getId(), "", "", ""};
		setSign(world, control_origin, new Location(world, 0, 2, -1), text5, orientation);
    }
    
    public class FixedCombinedMask extends CombinedMask {
    	private final List<Mask> masks = new ArrayList<Mask>();
    	
    	
    	public void add(Mask mask) {
            masks.add(mask);
        }
   
        @Override
        public boolean matches(EditSession editSession, Vector pos) {
            for (Mask mask : masks) {
                if (mask.matches(editSession, pos)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    public void setMask(Player player){
    	
    	WorldEditPlugin worldEditPlugin = getWorldEdit();
    	WorldGuardPlugin worldGuard = getWorldGuard();
    	
    	FixedCombinedMask cb = new FixedCombinedMask();
		Location loc = player.getLocation();
		World world = loc.getWorld();

		RegionManager regionManager = worldGuard.getRegionManager(world);
		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
		
		for (Map.Entry<String, ProtectedRegion> entry : allRegions.entrySet())
		{
			ProtectedRegion pr = entry.getValue();
			if (pr.isMember(player.getName()) || entry.getKey().equals("default")){
				Region region = (Region) new CuboidRegion((Vector)pr.getMinimumPoint(), (Vector)pr.getMaximumPoint());
				cb.add(new RegionMask(region));
			}
		}
		
		LocalSession session = worldEditPlugin.getSession(player);
    	session.setMask(cb);
    }
    
    
    public void setAreaOwner(World world, ProtectedRegion region, String name){
    	
    	//System.out.printf("setAreaOwner %s", name);
    	
    	int orientation;

		try {
			orientation = region.getFlag(ORIENTATION_FLAG);		
		} catch (NullPointerException e) {
			System.out.printf("no flag");
			return;
		}
    	
    	BlockVector min = region.getMinimumPoint();
    	BlockVector max = region.getMaximumPoint();
    	Vector bloc = BlockVector.getMidpoint(min, max);
    	Location loc = new Location(world, bloc.getBlockX(), min.getBlockY(), bloc.getBlockZ());
    	Vector dims = max.subtract(min);
    	
    	double x = 0.0;
    	double z = 0.0;
    	
    	if(orientation == 0){
    		z = (dims.getBlockZ()/2) + CONTROLS_OFFSET;
    	}
    	if(orientation == 1){
    		x = (dims.getBlockX()/2) + CONTROLS_OFFSET;
    	}
    	if(orientation == 2){
    		z = -((dims.getBlockZ()/2) + CONTROLS_OFFSET);
    	}
    	if(orientation == 3){
    		x = -((dims.getBlockX()/2) + CONTROLS_OFFSET);
    	} 	
    	
    	Location control_origin = loc.clone().add(x, 0.0, z);
    	
    	byte materialdata = 5;
    	Material material = Material.WOOL;
    	
    	if(name == ""){
    		materialdata = 5;
    		material = Material.GRASS;
    		String[] text = {"Plot " + region.getId(), "", "", ""};
    		setSign(world, control_origin, new Location(world, 0, 2, -1), text, orientation);
    	}
    	else{
    		materialdata = 14;
    		material = Material.IRON_BLOCK;
    		int maxLength = (name.length() < 15)?name.length():15;
    		String[] text = {"Plot " + region.getId(), "", "Owner", name.substring(0, maxLength)};
    		setSign(world, control_origin, new Location(world, 0, 2, -1), text, orientation);
    		
    	}
    	
    	int xx;
    	int zz;
    	int width = dims.getBlockX() + 6;
    	int depth = dims.getBlockZ() + 6;
    
        for (xx = 0; xx <= width; xx++){
            for (zz = 0; zz <= depth; zz++) {
            	if(xx == 0 || zz == 0 || xx == width || zz == depth){
                	Block b = world.getBlockAt((int)loc.getX() + (xx-(width/2)), (int)loc.getY() - 1, (int)loc.getZ() + (zz-(depth/2)));
                	b.setType(material);
                	b.setData(materialdata);
            	}
            }
        }
    }
    
    public void setUnclaimFlag(ProtectedRegion region, long seconds){
    	
    	long secondsSinceEpoch = System.currentTimeMillis() / 1000l;
    	long unclaimTime = secondsSinceEpoch + seconds;
    	region.setFlag(UNCLAIM_TIME_FLAG, String.valueOf(unclaimTime));
    }
    
   
    
    
    public boolean checkUnclaimFlag(ProtectedRegion region){
    	long secondsSinceEpoch = System.currentTimeMillis() / 1000l;
    	String ucflag = region.getFlag(UNCLAIM_TIME_FLAG);
    	if(ucflag == null){
    		return true;
    	}
    	else{
	    	long unclaimTime = Long.valueOf(ucflag);
	    	return secondsSinceEpoch > unclaimTime;
    	}
    }
    
    public Location turn_round_origin(World world, Location loc, int quarter_turns){
    	
    	double x = loc.getX();
    	double y = loc.getY();
    	double z = loc.getZ();
    	
    	if(quarter_turns == 0){
    		return new Location(world, x, y, z);
    	}
    	if(quarter_turns == 1){
    		return new Location(world, z, y, -x);
    	}
    	if(quarter_turns == 2){
    		return new Location(world, -x, y, -z);
    	}
    	else{
    		return new Location(world, -z, y, x);
    	}
    }
    
    public void setBlockAndData(World world, Location origin, Location block, Material mat, int turns, byte data){
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setType(mat);
    	b.setData(data);
    	
    }
    
    
    public void setBlock(World world, Location origin, Location block, Material mat, int turns){
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setType(mat);
    }
    
    public void setCommand(World world, Location origin, Location block, int turns, String command){
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setType(Material.COMMAND);
    	CommandBlock c = (CommandBlock)b.getState();
    	c.setCommand(command);
    	c.update();
    }
    
    
    public void setButton(World world, Location origin, Location block, Material mat, int turns){
    	byte direction = BUTTON_ORIENTATION_LOOKUP[turns];
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setType(Material.STONE_BUTTON);
    	b.setData(direction);
    	
    	
    }
    
    
    public void setSign(World world, Location origin, Location block, String[] text, int turns){
    	
    	byte direction = SIGN_ORIENTATION_LOOKUP[turns];
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setType(Material.WALL_SIGN);
    	b.setData(direction);
    	Sign s = (Sign)b.getState();
    	
    	s.setLine(0, text[0]);
    	s.setLine(1, text[1]);
    	s.setLine(2, text[2]);
    	s.setLine(3, text[3]);
    	s.update();
    }
}
