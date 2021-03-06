package com.dre.dungeonsxl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.milkbowl.vault.permission.Permission;
import net.minecraft.server.Packet103SetSlot;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.dre.dungeonsxl.commands.DCommandRoot;
import com.dre.dungeonsxl.game.GameCheckpoint;
import com.dre.dungeonsxl.game.GameMessage;
import com.dre.dungeonsxl.game.GameWorld;
import com.dre.dungeonsxl.game.MobSpawner;
import com.dre.dungeonsxl.listener.BlockListener;
import com.dre.dungeonsxl.listener.CommandListener;
import com.dre.dungeonsxl.listener.EntityListener;
import com.dre.dungeonsxl.listener.PlayerListener;

public class DungeonsXL extends JavaPlugin{
	public static DungeonsXL p;
	
	//Listener
	private static Listener entitylistener;
	private static Listener playerlistener;
	private static Listener blocklistener;
	
	//Main Config Reader
	public ConfigReader mainConfig;
	
	//Tutorial
	public String tutorialDungeon;
	public String tutorialStartGroup;
	public String tutorialEndGroup;
	
	//Chatspyer
	public CopyOnWriteArrayList<Player> chatSpyer=new CopyOnWriteArrayList<Player>();
	
	
	@Override
	public void onEnable(){
		p=this;
		
		//Commands
		getCommand("dungeonsxl").setExecutor(new CommandListener());
		
		//MSG
		this.log(this.getDescription().getName()+" enabled!");
		
		//Init Classes
		new DCommandRoot();
		
		//InitFolders
		this.initFolders();
		
		//Setup Permissions
		this.setupPermissions();
		
		//Listener
		entitylistener=new EntityListener();
		playerlistener=new PlayerListener();
		blocklistener=new BlockListener();
		
		Bukkit.getServer().getPluginManager().registerEvents(entitylistener,this);
		Bukkit.getServer().getPluginManager().registerEvents(playerlistener,this);
		Bukkit.getServer().getPluginManager().registerEvents(blocklistener,this);
		
		//Load All
		this.loadAll();
		
		//Load Config
		mainConfig=new ConfigReader(new File(p.getDataFolder(), "config.yml"));
		
		//Load MobTypes
		DMobType.load(new File(p.getDataFolder(), "mobs.yml"));
		
		// -------------------------------------------- //
		// Update Sheduler
		// -------------------------------------------- //
		
		p.getServer().getScheduler().scheduleSyncRepeatingTask(p, new Runnable() {
			public void run() {
				for(GameWorld gworld:GameWorld.gworlds){
					if(gworld.world.getPlayers().isEmpty()){
						if(DPlayer.get(gworld.world).isEmpty()){
							gworld.delete();
						}
					}
				}
				for(EditWorld eworld:EditWorld.eworlds){
					if(eworld.world.getPlayers().isEmpty()){
						
						eworld.delete();
					}
				}
		    }
		}, 0L, 1200L);
		
		p.getServer().getScheduler().scheduleSyncRepeatingTask(p, new Runnable() {
		    public void run() {
		        MobSpawner.updateAll();
		        GameWorld.update();
		        GameMessage.updateAll();
		        GameCheckpoint.update();
		        DPlayer.update(true);
		        
		        //Tutorial Mode
		        for(Player player:p.getServer().getOnlinePlayers()){
		        	if(DPlayer.get(player)==null){
			    		if(p.tutorialDungeon!=null && p.tutorialStartGroup!=null && p.tutorialEndGroup!=null){
			    			for(String group:p.permission.getPlayerGroups(player)){
			    				if(p.tutorialStartGroup.equalsIgnoreCase(group)){
			    					DGroup dgroup=new DGroup(player, p.tutorialDungeon);
			    					if(dgroup.gworld==null){
			    						dgroup.gworld=GameWorld.load(DGroup.get(player).dungeonname);
			    						dgroup.gworld.isTutorial=true;
			    					}
			    					if(dgroup.gworld!=null){
			    						if(dgroup.gworld.locLobby==null){
			    							new DPlayer(player,dgroup.gworld.world,dgroup.gworld.world.getSpawnLocation(), false);
			    						}else{
			    							new DPlayer(player,dgroup.gworld.world,dgroup.gworld.locLobby, false);
			    						}
			    					}else{
			    						p.msg(player,ChatColor.RED+"Tutorial Dungeon existiert nicht!");
			    					}
			    				}
			    			}
			    		}
		        	}
		        }
		    }
		}, 0L, 20L);
		
		p.getServer().getScheduler().scheduleSyncRepeatingTask(p, new Runnable() {
		    public void run() {
		        DPlayer.update(false);
		    }
		}, 0L, 2L);
	}
	
	
	@Override
	public void onDisable(){
		//Save
		this.saveAll();
		
		//MSG
		this.log(this.getDescription().getName()+" disabled!");
		
		//DPlayer leaves World
		for(DPlayer dplayer:DPlayer.players){
			dplayer.leave();
		}
		
		//Delete all Data
		DPortal.portals.clear();
		DGSign.dgsigns.clear();
		LeaveSign.lsigns.clear();
		
		//Delete Worlds
		GameWorld.deleteAll();
		EditWorld.deleteAll();
	}
	
	
	//Init.
	public void initFolders(){
		//Check Folder
		File folder = new File(this.getDataFolder()+"");
		if(!folder.exists()){
			folder.mkdir();
		}
		
		folder = new File(this.getDataFolder()+File.separator+"dungeons");
		if(!folder.exists()){
			folder.mkdir();
		}
	}
	
	
	//Permissions
	public Permission permission = null;

    private Boolean setupPermissions()
    {
        RegisteredServiceProvider<Permission> permissionProvider = getServer().getServicesManager().getRegistration(net.milkbowl.vault.permission.Permission.class);
        if (permissionProvider != null) {
            permission = permissionProvider.getProvider();
        }
        return (permission != null);
    }
    
    public Boolean GroupEnabled(String group){
    	
    	for(String agroup:permission.getGroups()){
    		if(agroup.equalsIgnoreCase(group)){
    			return true;
    		}
    	}
    	
    	return false;
    }
	
    
    //Save and Load
	public void saveAll(){
		File file = new File(this.getDataFolder(), "data.yml");
		FileConfiguration configFile = new YamlConfiguration();
		
		DPortal.save(configFile);
		DGSign.save(configFile);
		LeaveSign.save(configFile);
		
		try {
			configFile.save(file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void loadAll(){
		File file = new File(this.getDataFolder(), "data.yml");
		FileConfiguration configFile = YamlConfiguration.loadConfiguration(file);
		
		DPortal.load(configFile);
		DGSign.load(configFile);
		LeaveSign.load(configFile);
	}
    
    
	//File Control
	public boolean removeDirectory(File directory) {
		if (directory.isDirectory()) {
			for (File f : directory.listFiles()) {
				if (!removeDirectory(f)) return false;
			}
		}
		return directory.delete();
	}
	
	public void copyFile(File in, File out) throws IOException { 
        FileChannel inChannel = new FileInputStream(in).getChannel(); 
        FileChannel outChannel = new FileOutputStream(out).getChannel(); 
        try { 
            inChannel.transferTo(0, inChannel.size(), outChannel); 
        } catch (IOException e) { 
            throw e; 
        } finally { 
            if (inChannel != null) 
                inChannel.close(); 
            if (outChannel != null) 
                outChannel.close(); 
        } 
    } 
	
	public String[] excludedFiles={"config.yml"};
	
	public void copyDirectory(File sourceLocation, File targetLocation) {
		 
        if (sourceLocation.isDirectory()) {
 
            if (!targetLocation.exists()) {
                targetLocation.mkdir();
            }
 
            String[] children = sourceLocation.list();
            for (int i = 0; i < children.length; i++) {
            	boolean isOk=true;
            	for (String excluded:excludedFiles){
            		if(children[i].contains(excluded)){
            			isOk=false;
            			break;
            		}
            	}
            	if(isOk){
            		copyDirectory(new File(sourceLocation, children[i]), new File(
                            targetLocation, children[i]));
            	}
                
            }
        } else {
 
            try {
 
                if (!targetLocation.getParentFile().exists()) {
 
                    createDirectory(targetLocation.getParentFile().getAbsolutePath());
                    targetLocation.createNewFile();
 
                } else if (!targetLocation.exists()) {
 
                    targetLocation.createNewFile();
                }
 
                InputStream in = new FileInputStream(sourceLocation);
                OutputStream out = new FileOutputStream(targetLocation);
 
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
 
            } catch (Exception e) {
 
                if (e.getMessage().contains("Zugriff")
                        || e.getMessage().contains("Access"))
                    DungeonsXL.p.log("Error: " + e.getMessage() + " // Access denied");
                else
                	DungeonsXL.p.log("Error: " + e.getMessage());
            }
        }
    }
 
    public void createDirectory(String s) {
 
        if (!new File(s).getParentFile().exists()) {
 
            createDirectory(new File(s).getParent());
        }
 
        new File(s).mkdir();
    }
	
	public void deletenotusingfiles(File directory){
		File[] files=directory.listFiles();
		for(File file:files){
			if(file.getName().equalsIgnoreCase("uid.dat")){
				file.delete();
			}
		}
	}
    
	//Misc
	public void updateInventory(Player p) {
        CraftPlayer c = (CraftPlayer) p;
        for (int i = 0;i < 36;i++) {
            int nativeindex = i;
            if (i < 9) nativeindex = i + 36;
            ItemStack olditem =  c.getInventory().getItem(i);
            net.minecraft.server.ItemStack item = null;
            if (olditem != null && olditem.getType() != Material.AIR) {
                item = new net.minecraft.server.ItemStack(0, 0, 0);
                item.id = olditem.getTypeId();
                item.count = olditem.getAmount();
                item.b = olditem.getDurability();
            }
            if(olditem != null){
	            if(olditem.getEnchantments().size()==0) {
	                Packet103SetSlot pack = new Packet103SetSlot(0, nativeindex, item);
	                c.getHandle().netServerHandler.sendPacket(pack);
	            }
            }
        }
    }
	
	//Msg
	
	public void msg(Player player,String msg){
		player.sendMessage(ChatColor.DARK_GREEN+"[DXL] "+ChatColor.WHITE+msg);
	}
	
	public void msg(Player player,String msg, boolean zusatz){
		if(zusatz){
			player.sendMessage(ChatColor.DARK_GREEN+"[DXL]"+ChatColor.WHITE+msg);
		}else{
			player.sendMessage(ChatColor.WHITE+msg);
		}
		
	}
	
	//Misc.
	
	public EntityType getEntitiyType(String name){
		for(EntityType type:EntityType.values()){
			if(name.equalsIgnoreCase(type.getName())){
				return type;
			}
		}
		
		return null;
	}
	
    // -------------------------------------------- //
 	// LOGGING
 	// -------------------------------------------- //
 	public void log(Object msg)
 	{
 		log(Level.INFO, msg);
 	}

 	public void log(Level level, Object msg)
 	{
 		Logger.getLogger("Minecraft").log(level, "["+this.getDescription().getFullName()+"] "+msg);
 	}
	
}
