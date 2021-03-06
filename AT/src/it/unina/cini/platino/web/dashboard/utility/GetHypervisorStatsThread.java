package it.unina.cini.platino.web.dashboard.utility;

import it.unina.cini.platino.db.Hypervisor;
import it.unina.cini.platino.libvirt.NetHypervisorConnection;
import it.unina.cini.platino.monitoring.LibvirtGuestStatus;
import it.unina.cini.platino.monitoring.LibvirtNodeStatus;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

/**
 * A thread whose task is to retrieve an hypervisor's stats by making use of 
 * GuestStatus and NodeStatus classes. It is implemented as a Callable so to return
 * these values.
 * 
 * 
 * <p> 
 * Copyright (C) 2014 University of Naples. All Rights Reserved.
 * <p>
 * This program is distributed under GPL Version 2.0, WITHOUT ANY WARRANTY
 * 
 * @author <a href="mailto:p.dirienzo@studenti.unina.it">p.dirienzo@studenti.unina.it</a>, 
 * <a href="mailto:enr.demaio@studenti.unina.it">enr.demaio@studenti.unina.it</a>
 * @version 1.0
 */
public class GetHypervisorStatsThread implements Callable<JSONObject>{
	private NetHypervisorConnection c;
	
	public NetHypervisorConnection getHypervisorConnection(){
		return c;
	}
	
	public GetHypervisorStatsThread(NetHypervisorConnection c){
		this.c = c;
	}
	
	@Override
	public JSONObject call() {
		Hypervisor h = c.getHypervisor();
		JSONObject hypervisorJ = new JSONObject()
		.put("id", h.getId())
		.put("hostname",h.toString());
		
		try{
			hypervisorJ.put("ip", c.getIpAddress());
		}catch(IOException | LibvirtException ex){
			hypervisorJ.put("ip", "undefined");
		}
		
		try {
			hypervisorJ.put("status", Hypervisor.STATUS_ONLINE);
			hypervisorJ.put("cpuUsage", String.format(Locale.US,"%.2f", 
					(new LibvirtNodeStatus(c)).getOverallCPUUsage(500)));
			JSONArray machines = new JSONArray();
			for(Domain d : c.getAllDomains()){
				JSONObject vm = new JSONObject();
				vm.put("name", d.getName());
				int active = d.isActive();
				
				if(active == 1) {
					vm.put("status", "running");
					vm.put("cpuUsage", String.format(Locale.US,"%.2f", 
							(new LibvirtGuestStatus(d)).getOverallCPUUsage(500)));
				}
				else if(active == 0)
					vm.put("status", "stopped");
				else
					vm.put("status", "unknown");
				
				machines.put(vm);
			}
			
			hypervisorJ.put("machines", machines);
			
		} catch (LibvirtException e) {
			//if hypervisor is not online or there is any generic error
			//(usually if there is an error it is just cuz the hypervisor
			//is offline)
			e.printStackTrace();
			System.out.println(e.getMessage());
			hypervisorJ.put("status", Hypervisor.STATUS_OFFLINE);
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return hypervisorJ;
	}
}
