package org.printcraft.printbot;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
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
	
	public HttpClient httpclient;

    @Override
    public void onEnable(){
    	PoolingClientConnectionManager cm = new PoolingClientConnectionManager();
    	this.httpclient = new DefaultHttpClient(cm);
    }
 
    @Override
    public void onDisable() {
    	this.httpclient.getConnectionManager().shutdown();
    }
    
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
    	if(cmd.getName().equalsIgnoreCase("pb_print")){ 
    		
    		getLogger().info("printbot printing");
    		
			Player player = (Player) sender;
    		
    	    if(player.hasPermission("printbot.print")) {
    	    	
    			Location loc = player.getLocation();
    			
    			World world = loc.getWorld();
    			player.sendMessage("Your model is being processed - please wait");
        			 
        		int loc_x = Integer.parseInt(args[0]);   
        		int loc_y = Integer.parseInt(args[1]);
        		int loc_z = Integer.parseInt(args[2]);
        		int size = Integer.parseInt(args[3]);
        		String area = args[4];
        		
        		PostThread thread = new PostThread(this.httpclient, world, player, loc_x, loc_y, loc_z, size, area) ;
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
    	if(cmd.getName().equalsIgnoreCase("pb_clear")){ // If the player typed /basic then do the following...
    		getLogger().info("pb_clear has been invoked!");
    		
			Player player = (Player) sender;
			
			
    	    if(player.hasPermission("printbot.clear")) {
    	    	
    			Location loc = player.getLocation();
				
    			World world = loc.getWorld();
    			player.sendMessage("Clearing print area");
        			 
        		int loc_x = Integer.parseInt(args[0]);   
        		int loc_y = Integer.parseInt(args[1]);
        		int loc_z = Integer.parseInt(args[2]);
        		int size = Integer.parseInt(args[3]);		
        		
    			for (int y = 0; y <= size*2; y++){
    			     for (int x = 0; x <= size * 2; x++){
    			        for (int z = 0; z <= size * 2; z++) {
    			        	Block b = world.getBlockAt(x + loc_x - size, y + loc_y, z + loc_z - size);
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
    	
            // If this hasn't happened the a value of false will be returned.
    	return false; 
    }

    /**
     * A thread that performs Post.
     */
    static class PostThread extends Thread {

        private final HttpClient httpclient;
        private final Player player;
        private final int loc_x;
        private final int loc_y;
        private final int loc_z;
        private final int size;
        private final World world;
        private final String area;

        public PostThread(HttpClient httpClient, World world, Player player, int loc_x, int loc_y, int loc_z, int size, String area) {
            this.httpclient = httpClient;
            this.loc_x = loc_x;
            this.loc_y = loc_y;
            this.loc_z = loc_z;
            this.size = size;
            this.player = player;
            this.world = world;
            this.area = area;
        }

  
        @Override
        public void run() {
        	
        	List<String> emails = new ArrayList<String>();
    		
    		try {
    
	    		int[][][][] blocks = new int[size*2 + 1][size*2 + 1][size*2 + 1][2];
	    		
    			for (int y = 0; y <= size*2; y++){
    			     for (int x = 0; x <= size * 2; x++){
    			        for (int z = 0; z <= size * 2; z++) {
    			        	Block b = world.getBlockAt(x + loc_x - size, y + loc_y, z + loc_z - size);
    			        	int id = b.getTypeId();
    			        	blocks[y][x][z][0] = id;
    			        	blocks[y][x][z][1] = b.getData();
    			        	
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
    				String filepath = String.format("/Users/paul/Dropbox/glowinthedark/printcraft/file_store/%s/%s", containerName, model_key);
    				
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
