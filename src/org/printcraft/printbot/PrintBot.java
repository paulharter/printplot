package org.printcraft.printbot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.Plugin;

import com.mewin.WGCustomFlags.WGCustomFlagsPlugin;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.IntegerFlag;
import com.sk89q.worldedit.blocks.SignBlock;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.Vector;


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.apache.commons.lang.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
//import org.apache.http.conn.HttpHostConnectException;
import org.apache.http.entity.mime.MultipartEntity;

import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

//import org.apache.commons.httpclient.HttpException;
import com.rackspacecloud.client.cloudfiles.*;


public class PrintBot extends JavaPlugin {
	
	
	private static final byte [] BUTTON_ORIENTATION_LOOKUP = {4, 2, 3, 1};
	private static final byte [] SIGN_ORIENTATION_LOOKUP = {2, 4, 3, 5};
	public static StringFlag UNCLAIM_TIME_FLAG = new StringFlag("unclaim-time");
	public static IntegerFlag ORIENTATION_FLAG = new IntegerFlag("orientation");
	
	private static final int CONTROLS_OFFSET = 5;
	public HttpClient httpclient;

    @Override
    public void onEnable(){
    	PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
    	this.httpclient = new DefaultHttpClient(cm);
    	WGCustomFlagsPlugin wgCustomFlagsPlugin = getWGCustomFlags();
    	wgCustomFlagsPlugin.addCustomFlag(UNCLAIM_TIME_FLAG);
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
    	this.httpclient.getConnectionManager().shutdown();
    }
    
    
    private WorldGuardPlugin getWorldGuard(){
    	
        Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");
     
        // WorldGuard may not be loaded
        if (plugin == null || !(plugin instanceof WorldGuardPlugin)) {
            return null; // Maybe you want throw an exception instead
        }
     
        return (WorldGuardPlugin) plugin;
    }
    
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	
		Player player = (Player) sender;
		Location loc = player.getLocation();
		World world = loc.getWorld();
		WorldGuardPlugin worldGuard = getWorldGuard();
		RegionManager regionManager = worldGuard.getRegionManager(world);
		String area = args[0];
		ProtectedRegion region  = regionManager.getRegion(area);
		String playerName = player.getName();
		
		boolean can_build = true;
		
		if(region.hasMembersOrOwners()){
			can_build = region.isMember(playerName);
		}
		else{
			can_build = true;
		}
		
    	
    	if(cmd.getName().equalsIgnoreCase("printbot_print")){ 
    			
    		getLogger().info("printbot printing");
    		
    	    if(player.hasPermission("printbot.print") && can_build) {

    			player.sendMessage("Your model is being processed - please wait");
        		
        		PostThread thread = new PostThread(this.httpclient, world, player, region) ;
                thread.start();
                try {
    				thread.join();
    			} catch (InterruptedException e) {
    				// TODO Auto-generated catch block
    				e.printStackTrace();
    			}
        		return true;
    	        
    	    }
    	    else{
    	    	player.sendMessage("You dont have permission to print");
    	    	return true;
    	    }

    	} //If this has happened the function will return true. 
    	if(cmd.getName().equalsIgnoreCase("printbot_clear")){ // If the player typed /basic then do the following...
    		getLogger().info("printbot_clear has been invoked!");
			
    	    if(player.hasPermission("printbot.clear") && can_build) {

    			player.sendMessage("Clearing print area");		
	        	BlockVector min = region.getMinimumPoint();
	        	BlockVector max = region.getMaximumPoint();

	    		for (int x = min.getBlockX(); x <= max.getBlockX(); x++){
	   			     for (int y = min.getBlockY(); y <= max.getBlockY(); y++){
	   			        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
	   			        	Block b = world.getBlockAt(x, y, z);
    			        	b.setTypeId(0);
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
    	
    	//claim a plot
    	if(cmd.getName().equalsIgnoreCase("printplot_claim")){ 
			
    		getLogger().info("printbot claiming");
    		
    	    if(player.hasPermission("printbot.claim")) {

    	    	if(!region.hasMembersOrOwners()){
    	    		
    	    		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
    	    		
    	    		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
    	    		{
    	    			if (entry.getValue().isOwner(playerName)){
    	    				player.sendMessage("You can't claim this plot. You have a plot already");
    	    				return true;
    	    			}
    	    		}
    	    		
        			player.sendMessage("You have claimed this plot for 24 hours");
            		region.getOwners().addPlayer(playerName);
            		setUnclaimFlag(region, 60*60*24);
            		setAreaOwner(world, region, playerName);
            		return true;
    	    	}
    	    	else{

    	    		if(region.isOwner(playerName)){
    	    			setUnclaimFlag(region, 60*60*24);
    	    			player.sendMessage("You have renewed the claim on this plot until 24 hours from now");
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
			
    		getLogger().info("printbot unclaiming");
    		
    	    if(player.hasPermission("printbot.unclaim")) {

    	    	if(!region.hasMembersOrOwners()){
        			player.sendMessage("You cant unclaim this plot. It doesn't have an owner");
            		return true;
    	    	}
    	    	else{
    	    		if(region.isOwner(playerName)){
    	    			region.getOwners().removePlayer(playerName);
    	    			DefaultDomain members = new DefaultDomain();
    	    			region.setMembers(members);
    	    			setAreaOwner(world, region, "");
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
    	//create a plot
    	if(cmd.getName().equalsIgnoreCase("printplot_create")){ 
			
    		getLogger().info("printbot creating a plot");
    		
    	    if(player.hasPermission("printplot.create")) {
    	    	createPlot(world, region);
    	    	
    	    }
    	    else{
    	    	player.sendMessage("You dont have permission to create this plot");
    	    	return true;
    	    }

    	}
    	
    	
    	
    	return false; 
    }
    
    
    public void unclaimRegion(World world, ProtectedRegion region){

		region.setMembers(new DefaultDomain());
		region.setMembers(new DefaultDomain());
		setAreaOwner(world, region, "");
    }
    
    
    public void checkAllClaims(RegionManager regionManager){
    	
		Map <String,ProtectedRegion> allRegions = regionManager.getRegions();
		
		for (Map.Entry<String,ProtectedRegion> entry : allRegions.entrySet())
		{
			ProtectedRegion region = entry.getValue();
			if(region.hasMembersOrOwners()){
				if(checkUnclaimFlag(region)){
					
				}
				
			}
		}

    }
    
    
    public void createPlot(World world, ProtectedRegion region){
    	
    	int orientation = region.getFlag(ORIENTATION_FLAG);		
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
		     for (int y = miny - 1; y <= maxy + 1; y++){
		        for (int z = minz - 1; z <= maxz + 1; z++) {
		        	int truth = 0;
		        	if(x == minx - 1 || x == maxx + 1)truth++;
		        	if(y == miny - 1 || y == maxy + 1)truth++;
		        	if(z == minz - 1 || z == maxz + 1)truth++;
		        	if(truth > 1){
			        	Block b = world.getBlockAt(x, y, z);
			        	b.setTypeId(Material.GLOWSTONE.getId());
		        	}
		        }
		    }
		}	
		
		setBlock(world, control_origin, new Location(world, -3, 0, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, -2, 0, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, -1, 0, 0), 24, orientation);
		
		setBlock(world, control_origin, new Location(world, 0, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 1, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 2, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 3, 0, 0), 24, orientation);
		
		setBlock(world, control_origin, new Location(world, -3, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, -2, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, -1, 1, 0), 24, orientation);
		
		setBlock(world, control_origin, new Location(world, 3, 1, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 0, 0, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 1, 0, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 2, 0, 0), 24, orientation);
		
		setBlock(world, control_origin, new Location(world, -1, 2, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 0, 2, 0), 24, orientation);
		setBlock(world, control_origin, new Location(world, 1, 2, 0), 24, orientation);
		
		
		setButton(world, control_origin, new Location(world, -2, 1, -1), Material.STONE_BUTTON.getId(), orientation);
		setButton(world, control_origin, new Location(world, 0, 1, -1), Material.STONE_BUTTON.getId(), orientation);
		setButton(world, control_origin, new Location(world, 2, 1, -1), Material.STONE_BUTTON.getId(), orientation);
		
		String[] text = {"", "PRINT", "", ""};
		setSign(world, control_origin, new Location(world, 2, 0, -1), text, orientation);

		String[] text2 = {"", "CLEAR", "", ""};
		setSign(world, control_origin, new Location(world, 0, 0, -1), text2, orientation);
    	
		String[] text3 = {"", "CLAIM", "24 HOURS", ""};
		setSign(world, control_origin, new Location(world, -2, 0, -1), text3, orientation);
		
		String[] text4 = {"", "Plot " + region.getId(), "", ""};
		setSign(world, control_origin, new Location(world, 0, 2, -1), text4, orientation);
    }
    
    
    public void setAreaOwner(World world, ProtectedRegion region, String name){
    	
    	int orientation = region.getFlag(ORIENTATION_FLAG);		
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
    	
    	byte woolcolour = 5;
    	
    	if(name == ""){
    		woolcolour = 5;
    		String[] text = {"", "CLAIM", "24 HOURS", ""};
    		setSign(world, control_origin, new Location(world, -2, 0, -1), text, orientation);
    		setBlock(world, control_origin, new Location(world, -3, 0, -1), Material.AIR.getId(), orientation);
    		setBlock(world, control_origin, new Location(world, -3, 1, -1), Material.AIR.getId(), orientation);
    		String[] text4 = {"", "Plot " + region.getId(), "", ""};
    		setSign(world, control_origin, new Location(world, 0, 2, -1), text4, orientation);
    	}
    	else{
    		woolcolour = 14;
    		String[] text = {"RENEW", "CLAIM", "24 HOURS", ""};
    		setSign(world, control_origin, new Location(world, -2, 0, -1), text, orientation);
    		String[] text2 = {"", "UNCLAIM", "", ""};
    		setSign(world, control_origin, new Location(world, -3, 0, -1), text2, orientation);
    		String[] text4 = {"", "Plot " + region.getId(), "Owner", name};
    		setSign(world, control_origin, new Location(world, 0, 2, -1), text4, orientation);
    		setButton(world, control_origin, new Location(world, -3, 1, -1), Material.STONE_BUTTON.getId(), orientation);
    	}
    	
    	int xx;
    	int zz;
    	int width = dims.getBlockX() + 6;
    	int depth = dims.getBlockZ() + 6;
    
        for (xx = 0; xx <= width; xx++){
            for (zz = 0; zz <= depth; zz++) {
            	if(xx == 0 || zz == 0 || xx == width || zz == depth){
                	Block b = world.getBlockAt((int)loc.getX() + (xx-(width/2)), (int)loc.getY() - 1, (int)loc.getZ() + (zz-(depth/2)));
                	b.setTypeIdAndData(Material.WOOL.getId(), woolcolour, false);
            	}
            }
        }
    }
    
    public void setUnclaimFlag(ProtectedRegion region, long seconds){
    	Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    	long secondsSinceEpoch = calendar.getTimeInMillis() / 1000L;
    	long unclaimTime = secondsSinceEpoch + seconds;
    	region.setFlag(UNCLAIM_TIME_FLAG, String.valueOf(unclaimTime));
    }
    
    
    public boolean checkUnclaimFlag(ProtectedRegion region){
    	Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    	long secondsSinceEpoch = calendar.getTimeInMillis() / 1000L;
    	long unclaimTime = Long.valueOf(region.getFlag(UNCLAIM_TIME_FLAG));
    	return secondsSinceEpoch > unclaimTime;
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
    
    
    public void setBlock(World world, Location origin, Location block, int type, int turns){
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setTypeId(type);
    }
    
    
    public void setButton(World world, Location origin, Location block, int type, int turns){
    	byte direction = BUTTON_ORIENTATION_LOOKUP[turns];
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setTypeIdAndData(Material.STONE_BUTTON.getId(), direction, false);
    }
    
    
    public void setSign(World world, Location origin, Location block, String[] text, int turns){
    	
    	byte direction = SIGN_ORIENTATION_LOOKUP[turns];
    	Location rotated = turn_round_origin(world, block, turns);
    	Block b = world.getBlockAt(origin.clone().add(rotated));
    	b.setTypeIdAndData(Material.WALL_SIGN.getId(), direction, false);
    	Sign s = (Sign)b.getState();
    	
    	s.setLine(0, text[0]);
    	s.setLine(1, text[1]);
    	s.setLine(2, text[2]);
    	s.setLine(3, text[3]);
    	s.update();
    }
    


    /**
     * A thread that performs Post.
     */
    static class PostThread extends Thread {

        private final HttpClient httpclient;
        private final Player player;
        private final World world;
        private final ProtectedRegion region;

        public PostThread(HttpClient httpClient, World world, Player player, ProtectedRegion region) {
            this.httpclient = httpClient;
            this.region = region;
            this.player = player;
            this.world = world;
        }

  
        @Override
        public void run() {
        	
        	List<String> emails = new ArrayList<String>();
        	BlockVector min = region.getMinimumPoint();
        	BlockVector max = region.getMaximumPoint();
        	Vector dims = max.subtract(min);
    		
    		try {
    
	    		int[][][][] blocks = new int[dims.getBlockX() + 1][dims.getBlockY() + 1][dims.getBlockZ() + 1][2];
	    		
    			for (int x = min.getBlockX(); x <= max.getBlockX(); x++){
    			     for (int y = min.getBlockY(); y <= max.getBlockY(); y++){
    			        for (int z = min.getBlockZ(); z <= max.getBlockZ(); z++) {
    			        	Block b = world.getBlockAt(x, y, z);
    			        	int id = b.getTypeId();
    			        	blocks[x][y][z][0] = id;
    			        	blocks[x][y][z][1] = b.getData();
    			        	
    			            if (id == 63) {
    			            	Sign sign = (Sign)b.getState();
    			            	String email = StringUtils.join(sign.getLines(), "");
    			            	if (isValidEmail(email)){
    			            		emails.add(email); 
    			            	}  
    			            }
    			        }
    			    }
    			}
    			
    			if(emails.isEmpty()){
    				player.sendMessage("You have to add a sign with your email on it inside the building area");
    			}
    			else{
    				
    				String url = "";
    				String value = System.getenv("PRINTBOT_MODE");
    				boolean local = false;
    				
    				
    				if (value == null){
    					url = "http://glowinthedark-printcraft.appspot.com/printjob";
    				}
    				else{
	    	            if (value.equals("development")) {
	    	                url = "http://localhost:8085/printjob";
	    	                local = true;
	    	            } else {
	    	            	 url = "http://glowinthedark-printcraft.appspot.com/printjob";
	    	            }
    				}

        			String emailsString = StringUtils.join(emails, ",");
        			String modelStr = Arrays.deepToString(blocks);
        			UUID uuid = player.getUniqueId();
        			
        			String player_address = player.getAddress().getAddress().toString();
        			String player_address2 = player_address.replace("/", "");
        			String player_address3 = player_address2.replace(":", "");
        			String area = region.getId();
        			
        			Date date = new Date();
    			    Calendar cal = Calendar.getInstance();
    			    cal.setTime(date);
    			    int year = cal.get(Calendar.YEAR);
    			    int month = cal.get(Calendar.MONTH);
        			
        			String containername = String.format("printcraft_%d_%d", year, month + 1);
        			
        			String model_key = null;
        			
        			try {
        				model_key = storeData(containername, modelStr, local);
        			} catch (UnsupportedEncodingException e) {
        				player.sendMessage("Something went wrong in storeData");
        				e.printStackTrace();
        			}
        			
        			if( model_key != null){
	        			
	        			String short_url = postToApp(containername, model_key, emailsString, player.getName(), uuid.toString(), url, area, player_address3);	
	        			player.sendMessage("Your model will be emailed to " + emailsString);
	        			if(short_url != null){
		    				player.sendMessage("Or you can click on the link below");
		    				player.sendMessage(short_url);
	                    }
        			}
        			else{
        				player.sendMessage("Something went wrong: model_key is null");
        			}
    			}

			} catch (Exception e) {
				// TODO Auto-generated catch block
				// HttpHostConnectException needs catching
				player.sendMessage("Something went wrong in printbot :(");
				e.printStackTrace();
			}
        }
        
        public String getKey(String modelStr) throws NoSuchAlgorithmException{
        	byte[] bytesOfMessage;
        	byte[] thedigest = null;
			try {
				bytesOfMessage = modelStr.getBytes("UTF-8");
				MessageDigest md = MessageDigest.getInstance("MD5");
	        	thedigest = md.digest(bytesOfMessage);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	
	        return new String(thedigest);
        }
        
        
        public String storeData(String containerName, String modelStr, boolean local) throws Exception, IOException, FilesException
        {

        	FilesClient client = new FilesClient("paulharter", "f20d2b879b210e6bf65e72f841b36cf6", "https://lon.auth.api.rackspacecloud.com/v1.1/auth", "", 5000);
    		
    		boolean success = client.login();

    		if (success)
    		{	byte[] bb = modelStr.getBytes();
    			String model_key = FilesClient.md5Sum(bb);
    			if(local){
    				String filepath = String.format("/Users/paul/Dropbox/glowinthedark/printcraft/glowinthedark-printcraft/local_file_store/%s/%s", containerName, model_key);
    				
    				try{
    					  FileWriter fstream = new FileWriter(filepath);
    					  BufferedWriter out = new BufferedWriter(fstream);
    					  out.write(modelStr);
    					  //Close the output stream
    					  out.close();
				  }catch (Exception e){//Catch exception if any
				  System.err.println("Error: " + e.getMessage());
				  }

    			}
    			else{//save to file
        			if(!client.containerExists(containerName)){
        				client.createContainer(containerName);
        				client.cdnEnableContainer(containerName);
        			}
        			
        			try {
        				FilesObjectMetaData md = client.getObjectMetaData(containerName, model_key);
        			} catch (FilesNotFoundException e) {
            			Map<String, String> metadata = Collections.<String, String>emptyMap();
            			client.storeObject(containerName, bb, "application/json", model_key, metadata);
        			}	
    			}
 
    			return model_key;
    		}	
    		else{
    			return null;
    		}
    		
        }
        
        
        public boolean isValidEmail(String email){
	        Pattern p = Pattern.compile(".+@.+\\.[a-z]+");
	        Matcher m = p.matcher(email);
	        boolean matchFound = m.matches();
	        return matchFound;
        }
        
        
        public String postToApp(String container, String model_key, String email, String playername, String player_id, String address, String areaname, String client_ip) throws Exception {

            HttpPost httppost = new HttpPost(address);
            HttpContext context = new BasicHttpContext();

            StringBody clientipBody = new StringBody(client_ip);
            StringBody areaBody = new StringBody(areaname);
            StringBody playerBody = new StringBody(playername);
            StringBody playeridBody = new StringBody(player_id);
            StringBody emailBody = new StringBody(email);
            StringBody containerBody = new StringBody(container);
            StringBody modelBody = new StringBody(model_key);
            
            MultipartEntity reqEntity = new MultipartEntity();
            
            reqEntity.addPart("area", areaBody);
            reqEntity.addPart("emails", emailBody);
            reqEntity.addPart("playername", playerBody);
            reqEntity.addPart("playerid", playeridBody);
            reqEntity.addPart("model", modelBody);
            reqEntity.addPart("container", containerBody);
            reqEntity.addPart("clientip", clientipBody);

            httppost.setEntity(reqEntity);

            System.out.println("executing request " + httppost.getRequestLine());
            HttpResponse response = httpclient.execute(httppost, context);
            HttpEntity resEntity = response.getEntity();
            Header location = response.getFirstHeader("Location");
            String short_url = null;
            if(location != null){
            	short_url = location.getValue();
            }
            EntityUtils.consume(resEntity);
            return short_url;
 
        }

    } 

}
