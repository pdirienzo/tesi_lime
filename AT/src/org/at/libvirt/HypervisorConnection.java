package org.at.libvirt;

import java.util.ArrayList;
import java.util.List;

import org.at.db.Hypervisor;
import org.libvirt.Connect;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

public class HypervisorConnection extends Connect{
	
	public HypervisorConnection(Hypervisor h, String method, boolean readOnly) throws LibvirtException{
		super(method+"://"+h.getName()+"@"+h.getHostAddress()+
				"/system?no_tty=1", readOnly);
	}
	
	public HypervisorConnection(Hypervisor h) throws LibvirtException{
		this(h,"qemu+ssh");
	}
	
	public HypervisorConnection(Hypervisor h,boolean readOnly) throws LibvirtException{
		this(h,"qemu+ssh",readOnly);
	}
	
	public HypervisorConnection(Hypervisor h, String method) throws LibvirtException{
		this(h,method,false);
	}
	
	public List<Domain> getAllDomains() throws LibvirtException{
		List<Domain> domains = new ArrayList<Domain>();
		
		//getting running domains...
		int[] ids = super.listDomains();
		for(int i = 0;i<ids.length;i++)
			domains.add(super.domainLookupByID(ids[i]));
		
		//getting shutted down domains
		String[] names = super.listDefinedDomains();
		for(int i=0; i<names.length;i++)
			domains.add(super.domainLookupByName(names[i]));
			
		return domains;
	}
	
	/**
	 * Migreates a domain belonging to this hypervisor to selected hypervisor
	 * 
	 * @param domainName
	 * @param destConn
	 * @throws LibvirtException 
	 */
	public boolean migrate(String domainName,Hypervisor destination) throws LibvirtException{
		HypervisorConnection destConn = new HypervisorConnection(destination);
		Domain domain = super.domainLookupByName(domainName);
		Domain newDomain = null;
		newDomain = domain.migrate(destConn, 1, null, null, 0);
		destConn.close();
		return (newDomain != null);
	}
	
}
