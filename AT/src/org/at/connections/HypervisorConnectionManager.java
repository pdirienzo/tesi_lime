package org.at.connections;

import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.at.db.Database;
import org.at.db.DatabaseListener;
import org.at.db.Hypervisor;
import org.at.libvirt.HypervisorConnection;
import org.libvirt.LibvirtException;

public class HypervisorConnectionManager implements DatabaseListener{
	
	private static final int CONNECTION_TIMEOUT = 500;
	public static final String HYPERVISOR_CONNECTION_MANAGER = "hmanager";
	private static final String XML_NETWORK_FILEPATH = "xml_definitions/vpm_network.xml";
	
	private int retryTimout;
	private Timer timer; //this timer is used by the polling thread
	
	private boolean active;
	private synchronized boolean isActive(){
		return active;
	}
	private synchronized void setActive(boolean v){
		active = v;
	}
	
	
	private Vector<HypervisorConnection> activeConnections;
	private Vector<Hypervisor> offlineConnections;
	
	private Database d;
	
	// listener methods----------------------------------------------------------->
	@Override
	public void hypervisorInserted(Hypervisor h) {
		addHypervisor(h);
		
	}
	
	@Override
	public void hypervisorDeleted(Hypervisor h) {
		removeHypervisor(h);
	}
	
	//<-----------------------------------------------------------------------------
	
	/**
	 * Creates a connection manager with a specified timeout in order to
	 * check if any hypervisor has returned on. 
	 * 
	 * @param retryTimeout 0 for no retry
	 * @param dbPath path to the sqlite db
	 * @throws IOException 
	 */
	public HypervisorConnectionManager(int retryTimeout,String dbPath) {
		this.retryTimout = retryTimeout;
		activeConnections = new Vector<HypervisorConnection>();
		offlineConnections = new Vector<Hypervisor>();
		d = new Database(dbPath);
		timer = new Timer();
	}
	
	void start() throws IOException{
		d.connect();
		
		for(Hypervisor h : d.getAllHypervisors()){
			addHypervisor(h);
			/*try {
				HypervisorConnection conn = HypervisorConnection.getConnectionWithTimeout(h, false, CONNECTION_TIMEOUT);
				conn.createNetworkFromFile(XML_NETWORK_FILEPATH);
				activeConnections.add(conn);
			} catch (IOException e) {
				System.err.println("Hypervisor "+h+" was offline, adding it to offline list");
				offlineConnections.add(h);
			} catch(LibvirtException e1){
				e1.printStackTrace();
			}*/
		}	
		d.close();
		
		if(retryTimout != 0){ //a zero value means user wants to recover connections
			setActive(true);
			timer.schedule(new ConnectionCheckerThread(),0);
		}
	}
	
	synchronized void stop() throws IOException{
		
		if(retryTimout != 0){//polling thread was active
			setActive(false);
			timer.cancel();
		}
		
		//closing every active connection
		for(int i=0;i<activeConnections.size();i++)
			removeHypervisor(activeConnections.remove(i).getHypervisor());
			
	}
	
	public synchronized void addHypervisor(Hypervisor h){
		try {
			HypervisorConnection conn = HypervisorConnection.getConnectionWithTimeout(h, false, CONNECTION_TIMEOUT); 
			try{
				conn.createNetworkFromFile(XML_NETWORK_FILEPATH);
			}catch(LibvirtException ex){
				//this just catches if a network already exists.
				//normally the network should be destroyed at app shutdown, but if some errors occur this could not happen.
			}
			activeConnections.add(conn);
		} catch (IOException e) {
			System.err.println("Hypervisor "+h+" was offline, adding it to offline list");
			offlineConnections.add(h);
		} catch (LibvirtException e1){
			e1.printStackTrace();
		}
	}
	
	public synchronized void removeHypervisor(Hypervisor h){
		//we check if it is among the offlines
		if(offlineConnections.contains(h)){
			offlineConnections.remove(h); //easy!
			
		}else{ //we have to check if it is among active connections
			int i = 0;
			boolean result = false;
			while((!result) && (i<activeConnections.size())){
				if(activeConnections.get(i).getHypervisor().equals(h)){//found
					try {
						HypervisorConnection conn = activeConnections.get(i);
						conn.networkShutdown();
						conn.close();
						activeConnections.removeElementAt(i); //and then remove the element itself
						result = true;
					} catch (LibvirtException e) {
						e.printStackTrace();
					}
				}else
					i++;
			}	
		}
	}
	
	/*
	public static void main(String[] args) throws IOException{
		//testing some stuff
		
		HypervisorConnectionManager c = new HypervisorConnectionManager(0);
		c.start();
		Hypervisor h = new Hypervisor("pasquale", "pasquale-VPCEB1A4E", 16514);
		for(HypervisorConnection hh : c.getActiveConnections())
			System.out.println(hh.getHypervisor());
		
		c.removeHypervisor(h);
		for(HypervisorConnection hh : c.getActiveConnections())
			System.out.println(hh.getHypervisor());
		
		System.out.println(c.getOfflineHypervisors().contains(h) +""+c.getActiveConnections().size());
		c.stop();
	}*/
	
	/**
	 * Creates a connection manager with a specified timeout in order to
	 * check if any hypervisor has returned on. Default db path is used
	 * 
	 * @param retryTimeout 0 for no retry
	 * @throws IOException 
	 */
	public HypervisorConnectionManager(int retryTimeout) throws IOException{
		this(retryTimeout,Database.DEFAULT_DBPATH);
	}
	
	public synchronized List<HypervisorConnection> getActiveConnections(){
		return this.activeConnections;
	}
	
	public synchronized List<Hypervisor> getOfflineHypervisors(){
		return this.offlineConnections;
	}
	
	public synchronized HypervisorConnection getActiveConnection(Hypervisor h){
		int i = 0;
		HypervisorConnection conn = null;
		
		
		while((conn == null) && (i<activeConnections.size())){
			if(activeConnections.get(i).getHypervisor().equals(h)){//found
				conn = activeConnections.get(i);
			}else
				i++;
		}
		
		return conn;
	}
	
	public synchronized HypervisorConnection getActiveConnection(String hypervisorId){
		int i = 0;
		HypervisorConnection conn = null;
		
		while((conn == null) && (i<activeConnections.size())){
			if(activeConnections.get(i).getHypervisor().getId().equals(hypervisorId)){//found
				conn = activeConnections.get(i);
			}else
				i++;
		}
		
		return conn;
	}
	
	/**
	 * Marks as inactive a connection, if retry timeout is !=0 the system will periodically check it to see
	 * if it returns online
	 * 
	 * @param c
	 */
	public synchronized void setInactive(HypervisorConnection c){
		activeConnections.removeElement(c);
		offlineConnections.addElement(c.getHypervisor());
	}
	
	/**
	 * This class periodically pools offline connections in order to see if an hypervisor was active
	 * @author pasquale
	 *
	 */
	private class ConnectionCheckerThread extends TimerTask{

		@Override
		public void run() {
			for(Hypervisor h : offlineConnections){
				try {
					HypervisorConnection c = HypervisorConnection.getConnectionWithTimeout(h, false, CONNECTION_TIMEOUT);
					activeConnections.add(c);
					offlineConnections.removeElement(h);
				} catch (IOException | LibvirtException e) {
					//System.err.println(h+" is still offline");
				}
			}
		
			if(retryTimout!= 0 && isActive()){
				timer.schedule(new ConnectionCheckerThread(), retryTimout);
			}
		}
		
	}
}
