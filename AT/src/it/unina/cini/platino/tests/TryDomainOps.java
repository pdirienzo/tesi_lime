package it.unina.cini.platino.tests;

import it.unina.cini.platino.db.Hypervisor;
import it.unina.cini.platino.libvirt.HypervisorConnection;
import it.unina.cini.platino.libvirt.NetHypervisorConnection;

import java.io.File;
import java.io.IOException;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;
import org.libvirt.Domain;
import org.libvirt.LibvirtException;

public class TryDomainOps {
	private static final String XML_NETWORK_FILEPATH = "xml_definitions/network_template.xml";
	private static final String XML_VM_FILEPATH = "xml_definitions/test_vm.xml";
	private static final String NETWORK_NAME = "ovs-network";
	private static final String BRIDGE_NAME = "br0";
	
	private static String getNetworkDescription() throws IOException{
		SAXBuilder builder = new SAXBuilder();
		File xmlFile = new File(XML_NETWORK_FILEPATH);
 
		Document doc = null;
		try{
			doc = (Document) builder.build(xmlFile);
		}catch(JDOMException ex){
			throw new IOException(ex.getMessage());
		}
		
		Element rootNode = doc.getRootElement();
		rootNode.getChild("name").setText(NETWORK_NAME);
		rootNode.getChild("bridge").setAttribute("name",BRIDGE_NAME);
		
		return new XMLOutputter().outputString(doc);
	}

	public static void main(String[] args) throws IOException, LibvirtException, JDOMException {
		/*NetHypervisorConnection hc = NetHypervisorConnection.getConnectionWithTimeout(
				new Hypervisor("pasquale", "platino1",
						16514), NETWORK_NAME,getNetworkDescription(), 3000);*/
		
		HypervisorConnection hc = HypervisorConnection.getConnectionWithTimeout(new Hypervisor("pasquale", "pasquale-VPCEB1A4E", 16514),
				true, 3000);
		
		System.out.println(hc.getCapabilities());
		/*Domain stream = hc.domainLookupByName("stream1");
		
		for(String s : hc.listInterfaces())
			System.out.println(s);
		*/
		System.out.println(hc.getIpAddress());
		//System.out.println(hc.getNetwork().getName()+ hc.interfaceLookupByName("eth0").getMACString());
		//hc.bootDomain("test");
		//hc.migrate("test", new Hypervisor("pasquale", "pasquale-PC", 16514));
		hc.close();
		
		
		/*Network net = hc.networkLookupByName(NETWORK_NAME);
		Domain linx2 = hc.domainLookupByName("test");
		System.out.println(linx2.getXMLDesc(2));
		
		SAXBuilder builder = new SAXBuilder();
		
		Document doc = builder.build(new ByteArrayInputStream(linx2.getXMLDesc(2).getBytes()));
		Element networkNode = doc.getRootElement().getChild("devices").getChild("interface");
		networkNode.setAttribute("type","network");
		networkNode.getChild("source").setAttribute("network",net.getName());
		
		linx2.free();
		
		hc.domainCreateXML(new XMLOutputter().outputString(doc), 0);*/
		
		//hc.domainCreateXML(linx2.getXMLDesc(2), 0);
		//linx2.free();

	}

}
