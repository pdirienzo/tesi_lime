package net.floodlightcontroller.vpm;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.vpm.json.VPMEventForwarding;

import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VPMForwarding implements IFloodlightModule,IOFMessageListener,IOFSwitchListener {

	private IFloodlightProviderService floodlightProvider;
	private IStaticFlowEntryPusherService staticFlowPusher;
	private IDeviceService deviceService;
	private Logger log;
	public static final int FORWARDING_APP_ID=20;
	private IRoutingService router;
	private VPMNetworkTopologyListener netListener = null;
	//private static final String CALLBACK_URI = "http://192.168.1.180:8080/VPM/VPMEventListener";

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(ITopologyService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException {
		
		this.floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		this.netListener = new VPMNetworkTopologyListener(this.floodlightProvider);
		this.staticFlowPusher = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		this.deviceService = context.getServiceImpl(IDeviceService.class);
		this.router = context.getServiceImpl(IRoutingService.class);
		log=LoggerFactory.getLogger(VPMForwarding.class);

	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		ILinkDiscoveryService ilds=context.getServiceImpl(ILinkDiscoveryService.class);
		ilds.addListener(this.netListener);
		this.floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		this.floodlightProvider.addOFSwitchListener(this);
		this.floodlightProvider.addOFSwitchListener(this.netListener);
	}

	private boolean isDhcpRequest(short src,short dst){
		return (src == UDP.DHCP_CLIENT_PORT && dst == UDP.DHCP_SERVER_PORT);
	}


	private boolean isDhcpResponse(short src,short dst){
		//log.info("SRC: "+src+" DST: "+dst);
		return (src== UDP.DHCP_SERVER_PORT && dst== UDP.DHCP_CLIENT_PORT);
	}

	public boolean isArpRequest(OFMatch match){
		//System.out.println("macth: "+match);
		return ((match.getDataLayerType()==Ethernet.TYPE_ARP) && 
				(HexString.toHexString(match.getDataLayerDestination()).equals("ff:ff:ff:ff:ff:ff")));
	}

	public boolean isArpResponse(OFMatch match){
		//System.out.println("macth response: "+match);
		return ((match.getDataLayerType()==Ethernet.TYPE_ARP) && 
				!(HexString.toHexString(match.getDataLayerDestination()).equals("ff:ff:ff:ff:ff:ff")));
	}

	public boolean isIcmp(OFMatch match){
		return match.getNetworkProtocol()==IPv4.PROTOCOL_ICMP;
	}


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return VPMForwarding.class.getCanonicalName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		return false;
	}

	private IDevice findDevice(long macAddress){
		IDevice device=null;
		Iterator<? extends IDevice> g = deviceService.getAllDevices().iterator();

		while ( (device == null) && (g.hasNext())){
			IDevice dev = g.next();
			if (dev.getMACAddress()==macAddress){
				device=dev;
			}
		}
		return device;
	}

	@Override
	public synchronized net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		OFPacketIn pi = (OFPacketIn) msg;
		OFMatch match = new OFMatch();
		match.loadFromPacket(pi.getPacketData(), pi.getInPort());

		if(!sw.getPort(pi.getInPort()).getName().equals("patch1")){ 

			if(match.getNetworkProtocol() == IPv4.PROTOCOL_UDP){
				
				if (isDhcpRequest(match.getTransportSource(),match.getTransportDestination())){
					log.info(sw.getStringId()+": DHCP REQUEST ");
					List<OFAction> list = new ArrayList<OFAction>();
					for (ImmutablePort p : sw.getPorts()){
						if (!p.getName().equals("patch1") && (p.getPortNumber() != pi.getInPort())){
							//log.info(sw.getStringId()+" Outputting to Port: "+p.getName());
							OFActionOutput ofaction = new OFActionOutput((short) p.getPortNumber());
							list.add(ofaction);
						}
					}
					if (list.size()>0){
						OFMatch my_match= new OFMatch();
						my_match.setDataLayerDestination(match.getDataLayerDestination());
						my_match.setDataLayerSource(match.getDataLayerSource());
						my_match.setTransportSource(match.getTransportSource());
						my_match.setTransportDestination(match.getTransportDestination());
						my_match.setNetworkDestination(match.getNetworkDestination());
						my_match.setNetworkSource(match.getNetworkSource());
						
						OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
						.setMatch(my_match)
						.setActions(list);
						staticFlowPusher.addFlow("DHCP_REQUEST("+sw.getId()+")", fm, sw.getStringId());
					}
				}
				else if (isDhcpResponse(match.getTransportSource(),match.getTransportDestination())){
					log.info(sw.getStringId()+": DHCP RESPONSE");
					long macSrc = Ethernet.toLong(match.getDataLayerSource());
					long macDst = Ethernet.toLong(match.getDataLayerDestination());
					IDevice devSrc = findDevice(macSrc);
					IDevice devDst = findDevice(macDst);
					if (devSrc!=null && devDst!=null){
						SwitchPort srcId = devSrc.getAttachmentPoints()[0];
						SwitchPort dstId = devDst.getAttachmentPoints()[0];
						
						
						List<NodePortTuple> np = router.getRoute(srcId.getSwitchDPID(),(short) srcId.getPort(), dstId.getSwitchDPID(), 
								(short) dstId.getPort(), FORWARDING_APP_ID, true).getPath();
						short firstPort = match.getInputPort();
						for (int i=np.size()-1;i>=0;i=i-2){
							log.info("ID: "+HexString.toHexString(np.get(i).getNodeId())+" : "+np.get(i).getPortId()+" port: "
						+floodlightProvider.getSwitch(np.get(i).getNodeId()).getPort(np.get(i).getPortId()).getName());
							List<OFAction> list = new ArrayList<OFAction>();
							OFActionOutput ofaction = new OFActionOutput((short) np.get(i).getPortId());
							list.add(ofaction);
							OFMatch my_match= new OFMatch();
							my_match.setDataLayerDestination(match.getDataLayerDestination());
							my_match.setDataLayerSource(match.getDataLayerSource());
							my_match.setTransportSource(match.getTransportSource());
							my_match.setTransportDestination(match.getTransportDestination());
							my_match.setNetworkDestination(match.getNetworkDestination());
							my_match.setNetworkSource(match.getNetworkSource());
							if(i>1){
								my_match.setInputPort(np.get(i-1).getPortId());
							}
							else{
								my_match.setInputPort(firstPort);
							}
						
							OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
							fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
							//.setIdleTimeout((short)5)
							.setPriority((short)20)
							.setMatch(my_match)
							.setActions(list);
							
							//log.info("MATCH: "+fm);
							staticFlowPusher.addFlow("DHCP_RESPONSE("+
									HexString.toHexString(my_match.getDataLayerDestination()).replace(":", "")/*np.get(i).getNodeId()*/+")", fm, HexString.toHexString(np.get(i).getNodeId()));
						}
					}
				}

			}else if(isArpRequest(match)){
				//log.info("ARP REQUEST: "+HexString.toHexString(match.getDataLayerSource())+" to "
				//		+HexString.toHexString(match.getDataLayerDestination()));
				log.info(sw.getStringId()+": ARP REQUEST ");
				List<OFAction> list = new ArrayList<OFAction>();
				for (ImmutablePort p : sw.getPorts()){
					if (!p.getName().equals("patch1") && (p.getPortNumber() != pi.getInPort())){// && !p.getName().equals("br0"))){
						//log.info(sw.getStringId()+" Outputting to Port: "+p.getName());
						OFActionOutput ofaction = new OFActionOutput((short) p.getPortNumber());
						list.add(ofaction);
					}
				}
				if (list.size()>0){
					OFMatch my_match= new OFMatch();
					my_match.setDataLayerDestination(match.getDataLayerDestination());
					my_match.setDataLayerSource(match.getDataLayerSource());
					my_match.setTransportSource(match.getTransportSource());
					my_match.setTransportDestination(match.getTransportDestination());
					my_match.setNetworkDestination(match.getNetworkDestination());
					my_match.setNetworkSource(match.getNetworkSource());
					OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
					fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
					//.setIdleTimeout((short)5)
					.setMatch(my_match)
					.setActions(list);
					staticFlowPusher.addFlow("ARP_REQUEST("+sw.getId()+")", fm, sw.getStringId());
					//	messageDamper.write(sw, fm, cntx);
				}

			}
			else if(isArpResponse(match)){
				log.info(sw.getStringId()+": ARP RESPONSE ");
//				log.info("ARP RESPONSE: "+HexString.toHexString(match.getDataLayerSource())+" to "
//						+HexString.toHexString(match.getDataLayerDestination()));
				long macSrc = Ethernet.toLong(match.getDataLayerSource());
				long macDst = Ethernet.toLong(match.getDataLayerDestination());
				IDevice devSrc = findDevice(macSrc);
				IDevice devDst = findDevice(macDst);
				if (devSrc!=null && devDst!=null){
					SwitchPort srcId = devSrc.getAttachmentPoints()[0];
					SwitchPort dstId = devDst.getAttachmentPoints()[0];
					List<NodePortTuple> np = router.getRoute(srcId.getSwitchDPID(),(short) srcId.getPort(), dstId.getSwitchDPID(), 
							(short) dstId.getPort(), FORWARDING_APP_ID, true).getPath();
					short firstPort = match.getInputPort();
					for (int i=np.size()-1;i>=0;i=i-2){
						//log.info("ID: "+np.get(i).getNodeId()+" : "+np.get(i).getPortId());
						List<OFAction> list = new ArrayList<OFAction>();
						OFActionOutput ofaction = new OFActionOutput((short) np.get(i).getPortId());
						list.add(ofaction);
						OFMatch my_match= new OFMatch();
						my_match.setDataLayerDestination(match.getDataLayerDestination());
						my_match.setDataLayerSource(match.getDataLayerSource());
						my_match.setTransportSource(match.getTransportSource());
						my_match.setTransportDestination(match.getTransportDestination());
						my_match.setNetworkDestination(match.getNetworkDestination());
						my_match.setNetworkSource(match.getNetworkSource());
						if(i>1){
							my_match.setInputPort(np.get(i-1).getPortId());
						}
						else{
							my_match.setInputPort(firstPort);
						}
						OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
						//.setIdleTimeout((short)5)
						.setPriority((short)20)
						.setMatch(my_match)
						.setActions(list);
						
						staticFlowPusher.addFlow("ARP_RESPONSE("+HexString.toHexString(my_match.getDataLayerDestination()).replace(":", "")+")", fm, HexString.toHexString(np.get(i).getNodeId()));
					}
				}
			}
			else if(isIcmp(match)){
				log.info("ICMP RESPONSE/REQUEST: "+HexString.toHexString(match.getDataLayerSource())+" to "
						+HexString.toHexString(match.getDataLayerDestination()));
				long macSrc = Ethernet.toLong(match.getDataLayerSource());
				long macDst = Ethernet.toLong(match.getDataLayerDestination());
				IDevice devSrc = findDevice(macSrc);
				IDevice devDst = findDevice(macDst);
				if (devSrc!=null && devDst!=null){
					SwitchPort srcId = devSrc.getAttachmentPoints()[0];
					SwitchPort dstId = devDst.getAttachmentPoints()[0];
					List<NodePortTuple> np = router.getRoute(srcId.getSwitchDPID(),(short) srcId.getPort(), dstId.getSwitchDPID(), 
							(short) dstId.getPort(), FORWARDING_APP_ID, true).getPath();
					short firstPort = match.getInputPort();
					for (int i=np.size()-1;i>=0;i=i-2){
						//log.info("ID: "+np.get(i).getNodeId()+" : "+np.get(i).getPortId());
						List<OFAction> list = new ArrayList<OFAction>();
						OFActionOutput ofaction = new OFActionOutput((short) np.get(i).getPortId());
						list.add(ofaction);
						OFMatch my_match= new OFMatch();
						my_match.setDataLayerDestination(match.getDataLayerDestination());
						my_match.setDataLayerSource(match.getDataLayerSource());
						my_match.setTransportSource(match.getTransportSource());
						my_match.setTransportDestination(match.getTransportDestination());
						my_match.setNetworkDestination(match.getNetworkDestination());
						my_match.setNetworkSource(match.getNetworkSource());
						if(i>1){
							my_match.setInputPort(np.get(i-1).getPortId());
						}
						else{
							my_match.setInputPort(firstPort);
						}
						OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
						fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
						//.setIdleTimeout((short)5)
						.setPriority((short)20)
						.setMatch(my_match)
						.setActions(list);
						//log.info("MATCH: "+fm);
						staticFlowPusher.addFlow("ICMP("+np.get(i).getNodeId()+my_match.getNetworkDestination()+")", fm, HexString.toHexString(np.get(i).getNodeId()));
					}
				}
			}
		}
		return net.floodlightcontroller.core.IListener.Command.CONTINUE;
	}

	@Override
	public void switchAdded(long switchId) {

		OFMatch match = new OFMatch();
		match.setInputPort(floodlightProvider.getSwitch(switchId).getPort("patch1").getPortNumber());
		OFFlowMod fm = (OFFlowMod) floodlightProvider.getOFMessageFactory().getMessage(OFType.FLOW_MOD);
		fm.setCookie(AppCookie.makeCookie(FORWARDING_APP_ID, 0))
		.setPriority((short)25)
		.setMatch(match);
		staticFlowPusher.addFlow("BLOCK"+switchId, fm, HexString.toHexString(switchId));
	}

	@Override
	public void switchRemoved(long switchId) {
		// TODO Auto-generated method stub

	}

	@Override
	public void switchActivated(long switchId) {
		// TODO Auto-generated method stub

	}
	
	
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) {
		this.staticFlowPusher.deleteFlow("ARP_REQUEST("+switchId+")");
		this.staticFlowPusher.deleteFlow("ARP_RESPONSE("+switchId+")");
		this.staticFlowPusher.deleteFlow("DHCP_REQUEST("+switchId+")");
		this.staticFlowPusher.deleteFlow("DHCP_RESPONSE("+switchId+")");
		System.out.println("CALLED SWITCH PORT CHANGED!");
		VPMEventForwarding vpmev = null;
		try {

			if (type==PortChangeType.DELETE){
				vpmev = new VPMEventForwarding("VM",HexString.toHexString(switchId),
						port.getName()+"/"+port.getPortNumber(),"REMOVE");

			}
			else if(type==PortChangeType.UP){
				vpmev = new VPMEventForwarding("VM",HexString.toHexString(switchId),
						port.getName()+"/"+port.getPortNumber(),"ADD");

			}
			if(vpmev != null){				
				VPMNotificationService.notifyEventForwarding(vpmev);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void switchChanged(long switchId) {
		// TODO Auto-generated method stub

	}
}
