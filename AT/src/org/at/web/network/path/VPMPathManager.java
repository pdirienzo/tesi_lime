package org.at.web.network.path;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.at.db.Controller;
import org.at.db.Database;
import org.at.floodlight.FloodlightController;
import org.at.network.NetworkConverter;
import org.at.network.types.LinkConnection;
import org.at.network.types.OvsSwitch;
import org.at.network.types.Port;
import org.at.network.types.VPMGraph;
import org.at.network.types.VPMGraphHolder;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.alg.DijkstraShortestPath;
import org.json.JSONObject;

import com.mxgraph.io.mxCodec;
import com.mxgraph.util.mxXmlUtils;
import com.mxgraph.view.mxGraph;

/**
 * Servlet implementation class VPMPathManager
 */
@WebServlet("/VPMPathManager")
public class VPMPathManager extends HttpServlet {
	private static final long serialVersionUID = 1L;


	private boolean test = false;

	private FloodlightController getController() throws IOException{
		Database d = new Database();
		d.connect();
		Controller c = d.getController();
		d.close();

		if(c==null)
			throw new IOException("couldn't contact controller");

		FloodlightController controller = new FloodlightController(c);

		return controller;
	}

	//TODO temporary
	private String computePathName(String dpidSrc,String dpidDst){
		StringBuilder sb = new StringBuilder();

		String[] subs = dpidSrc.split(":");
		sb.append(subs[subs.length-2]);
		sb.append(subs[subs.length-1]);

		subs = dpidDst.split(":");
		sb.append(subs[subs.length-2]);
		sb.append(subs[subs.length-1]);

		return sb.toString();
	}

	//TODO stupid workaround
	private OvsSwitch findOriginal(VPMGraph<OvsSwitch, LinkConnection> graph, OvsSwitch ovs){
		OvsSwitch found = null;

		Iterator<OvsSwitch> ves = graph.vertexSet().iterator();
		while((found == null) && ves.hasNext()){
			OvsSwitch temp = ves.next();
			if(temp.equals(ovs))
				found = temp;
		}

		return found;
	}

	/**
	 * @throws IOException 
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		JSONObject jsResp = new JSONObject();

		String srcDpid = request.getParameter("src_dpid");
		String targetdpid = request.getParameter("dst_dpid");
		String pathName = computePathName(srcDpid, targetdpid);

		VPMPathHolder pathHolder = (VPMPathHolder)getServletContext().getAttribute(VPMPathHolder.VPM_PATHS);
		mxGraph pathToClient = pathHolder.get(pathName).path;
		String externalAddr = pathHolder.get(pathName).externalAddress;

		if(pathToClient != null){
			jsResp.put("status", "ok_path");
			mxCodec codec = new mxCodec();	
			String xmlString =  mxXmlUtils.getXml(codec.encode(
					(pathToClient).getModel()));
			jsResp.put("path", xmlString);
			jsResp.put("external", externalAddr);
		}else
			jsResp.put("status", "ok_no_path");

		out.println(jsResp.toString());
		out.close();
	}

	private void deleteRelayFlow(OvsSwitchInfo info, int port, FloodlightController controller) throws IOException{
		info.removeOutputPort(port);
		info.decrementCounter();

		JSONObject data = new JSONObject()
		.put("name", "VPM_RTP_"+info.sw.dpid.hashCode())
		.put("switch", info.sw.dpid)
		.put("cookie", (new Random()).nextInt())
		.put("priority", "100")
		.put("dst-ip", "10.0.0.255")
		.put("ether-type", "0x0800")
		.put("active", "true")
		.put("actions", info.getCurrentOutputActionString());

		try{
			if(info.getCounter() > 0){

				List<Port> vnets = controller.getVnetPorts(info.sw);
				if(vnets.size() > 0){ // in this case we have multiple rules for each vnet
					for(Port vnet : vnets){
						data.put("name", "VPM_RTP_VNET"+vnet.number+"_"+info.sw.dpid.hashCode());
						data.put("ingress-port", vnet.number);
						controller.addStaticFlow(data);
					}
				}else
					controller.addStaticFlow(data);
			}else{ //we delete all rules

				List<Port> vnets = controller.getVnetPorts(info.sw);
				if(vnets.size() > 0){ // in this case we have multiple rules for each vnet
					for(Port vnet : vnets){
						controller.deleteFlow(info.sw.dpid,"VPM_RTP_VNET"+vnet.number+"_"+info.sw.dpid.hashCode());
					}
				}
				controller.deleteFlow(info.sw.dpid,"VPM_RTP_"+info.sw.dpid.hashCode());

			}

		}catch(IOException ex){
			info.incrementCounter();
			throw ex;
		}
	}

	private void deleteRootFlow(OvsSwitchInfo info, int port, FloodlightController controller) throws IOException{
		info.removeOutputPort(port);
		info.decrementCounter();

		JSONObject data = new JSONObject()
		.put("name", "VPM_RTP_"+info.sw.dpid.hashCode())
		.put("switch", info.sw.dpid)
		.put("cookie", (new Random()).nextInt())
		.put("priority", "100")
		.put("dst-ip", "10.0.0.255")
		.put("ether-type", "0x0800")
		.put("active", "true")
		.put("actions", info.getCurrentOutputActionString());

		try{
			if(info.getCounter() > 0){
				controller.addStaticFlow(data);
			}else
				controller.deleteFlow(info.sw.dpid,"VPM_RTP_"+info.sw.dpid.hashCode());
		}catch(IOException ex){
			info.incrementCounter();
			throw ex;
		}
	}

	private void deleteLeafFlow(OvsSwitchInfo info, FloodlightController controller) throws IOException{
		controller.deleteFlow(info.sw.dpid, "VPM_RTP_P_"+info.sw.dpid.hashCode() );
		List<Port> vnets = controller.getVnetPorts(info.sw);
		if(vnets.size() > 0){
			for(Port vnet : vnets){
				controller.deleteFlow(info.sw.dpid,"VPM_RTP_PVNET"+vnet.number+"_"+info.sw.dpid.hashCode());
			}
		}

	}

	private void deleteFlows(VPMGraph<OvsSwitch,LinkConnection> jpath) throws IOException{
		for(OvsSwitch sw: jpath.vertexSet())
			System.out.println("YOLO :"+ sw);
		/*
		FloodlightController controller = getController();
		VPMSwitchInfoHolder vsHolder = (VPMSwitchInfoHolder)getServletContext().
				getAttribute(VPMSwitchInfoHolder.SWITCH_INFO_HOLDER);

		Iterator<LinkConnection> edges = jpath.edgeSet().iterator();
		LinkConnection cl = null;

		while(edges.hasNext()){
			cl = edges.next();
			OvsSwitchInfo infoSrc = vsHolder.get(cl.getSource().dpid);
			OvsSwitchInfo infoTarget = vsHolder.get(cl.getTarget().dpid);

			if(infoSrc.sw.type == OvsSwitch.Type.LEAF ){
				deleteLeafFlow(infoSrc, controller);
			}else if(infoSrc.sw.type == OvsSwitch.Type.RELAY){
				deleteRelayFlow(infoSrc, cl.getSrcPort().number, controller);
			}else
				deleteRootFlow(infoSrc, cl.getSrcPort().number, controller);

			if(infoTarget.sw.type == OvsSwitch.Type.LEAF ){
				deleteLeafFlow(infoTarget, controller);
			}else if(infoTarget.sw.type == OvsSwitch.Type.RELAY){
				deleteRelayFlow(infoTarget, cl.getTargetPort().number, controller);
			}else
				deleteRootFlow(infoTarget, cl.getSrcPort().number, controller);
		}*/

	}

	
	private void addFlows(GraphPath<OvsSwitch,LinkConnection> jpath, String externalBcast) throws IOException{

		VPMSwitchInfoHolder vsHolder = (VPMSwitchInfoHolder)getServletContext().
				getAttribute(VPMSwitchInfoHolder.SWITCH_INFO_HOLDER);


		FloodlightController controller = getController();
		List<OvsSwitch> nodes = Graphs.getPathVertexList(jpath);

		for(int i=0;i<nodes.size()-1;i++){
			System.out.println("YOLO "+nodes.get(i));
			OvsSwitchInfo infos = vsHolder.get(nodes.get(i).dpid);
			if(infos == null){
				infos = new OvsSwitchInfo(nodes.get(i));
				vsHolder.put(nodes.get(i).dpid, infos);
			}

			try{
				OvsSwitch ovsDst = nodes.get(i+1);
				String path = computePathName(infos.sw.dpid, ovsDst.dpid);
				//this is the gre tunnel
				infos.addOutputPort(controller.getPortNumber(infos.sw, "gre"+path));
				infos.incrementCounter(); //on this switch we have a new route passing by

				JSONObject data = new JSONObject()
				.put("name", "VPM_RTP_"+infos.sw.dpid.hashCode())
				.put("switch", infos.sw.dpid)
				.put("cookie", (new Random()).nextInt())
				.put("priority", "100")
				.put("dst-ip", "10.0.0.255")
				.put("ether-type", "0x0800")
				.put("active", "true");


				if(i>0){//middle nodes, just if this is the first flow

					//we search for any virtual machine attached to this openvswitch
					List<Port> vnets = controller.getVnetPorts(infos.sw);

					if(vnets.size() > 0){ //if there is a vm we send the traffic to it
						if(infos.getCounter()==1){
							StringBuilder actionBuilder = new StringBuilder();
							for(Port vnet : vnets)
								actionBuilder.append("output="+vnet.number+",");
							actionBuilder.deleteCharAt(actionBuilder.length()-1);//removing comma
							data.put("actions", actionBuilder.toString());
							controller.addStaticFlow(data);
						}
						//now for traffic from vm
						data.put("actions", infos.getCurrentOutputActionString());
						for(Port vnet : vnets){
							data.put("name", "VPM_RTP_VNET"+vnet.number+"_"+infos.sw.dpid.hashCode());
							data.put("ingress-port", vnet.number);
							controller.addStaticFlow(data);
						}

					}else{ //if no vms we just make the traffic flow to next openvswitch
						data.put("actions", infos.getCurrentOutputActionString());
						controller.addStaticFlow(data);
					}
					//} //else any flow has already been installed

				}else{ //root node just outputs to gre tunnels
					data.put("actions", infos.getCurrentOutputActionString());
					controller.addStaticFlow(data);
				}
				//now if there is some vm on this switch it will receive the bcast traffic too
				//there is no need to add a specific rule for vm's response traffic as the previous
				//is good (we don't specify the ingress port so it is ok as vm response will also
				//match that flow rule)

			}catch(IOException ex){
				//this way if any exception occurs we make sure to decrement the counter so to have
				//a consistent data structure
				infos.decrementCounter();
				throw ex;
			}

		}

		OvsSwitchInfo infos = vsHolder.get( nodes.get(nodes.size()-1).dpid);
		if(infos == null){
			infos = new OvsSwitchInfo(nodes.get(nodes.size()-1));
			vsHolder.put(infos.sw.dpid, infos);
		}

		infos.incrementCounter();

		//the final flow to output it on the physical network
		JSONObject data = new JSONObject()
		.put("name", "VPM_RTP_P"+infos.sw.dpid.hashCode())
		.put("switch", infos.sw.dpid)
		.put("cookie", (new Random()).nextInt())
		.put("priority", "100")
		.put("dst-ip", "10.0.0.255")
		//.put("ingress-port", "3")
		.put("ether-type", "0x0800")
		.put("active", "true");

		List<Port> vnets = controller.getVnetPorts(infos.sw);
		if(vnets.size() > 0){ //if there is a vm we send the traffic to it
			if(infos.getCounter()==1){
				StringBuilder actionBuilder = new StringBuilder();
				for(Port vnet : vnets)
					actionBuilder.append("output="+vnet.number+",");
				actionBuilder.deleteCharAt(actionBuilder.length()-1);//removing comma
				data.put("actions", actionBuilder.toString());
				controller.addStaticFlow(data);
			}
			//now traffic from vms
			for(Port vnet : vnets){
				data.put("name", "VPM_RTP_PVNET"+vnet.number+"_"+infos.sw.dpid.hashCode());
				data.put("ingress-port", vnet.number);
				if(test)
					data.put("actions", "set-dst-ip="+infos.sw.ip+",output="+controller.getPortNumber(infos.sw, "patch1")); //TODO testing only
				else
					data.put("actions", "set-dst-ip="+externalBcast+",output="+controller.getPortNumber(infos.sw, "patch1"));

				controller.addStaticFlow(data);
			}

		}else{
			data.put("name", "VPM_RTP_P_"+infos.sw.dpid.hashCode());
			if(test)
				data.put("actions", "set-dst-ip="+infos.sw.ip+",output="+controller.getPortNumber(infos.sw, "patch1")); //TODO testing only
			else
				data.put("actions", "set-dst-ip="+externalBcast+",output="+controller.getPortNumber(infos.sw, "patch1"));
			controller.addStaticFlow(data);
		}
	}
	 



	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
		PrintWriter out = response.getWriter();
		JSONObject jsResp = new JSONObject();

		JSONObject jsReq = new JSONObject(request.getParameter("path"));
		String srcDpid = jsReq.getString("src_dpid");
		String targetdpid = jsReq.getString("dst_dpid");
		String pathName = computePathName(srcDpid, targetdpid);

		String op = jsReq.getString("op");

		try{

			VPMPathHolder pathHolder = (VPMPathHolder)getServletContext().getAttribute(VPMPathHolder.VPM_PATHS);
			mxGraph pathToClient = pathHolder.get(pathName).path;

			if(op.equals("add")){
				test = jsReq.getBoolean("test");
				if(pathToClient == null){ //no path was saved
					VPMGraphHolder holder = (VPMGraphHolder)getServletContext().getAttribute(VPMGraphHolder.VPM_GRAPH_HOLDER);
					VPMGraph<OvsSwitch, LinkConnection> currentGraph = holder.getGraph();

					if(currentGraph == null)
						throw new IOException("You need to setup a tree before asking for a path");


					DijkstraShortestPath<OvsSwitch, LinkConnection> shortest = new DijkstraShortestPath<OvsSwitch, LinkConnection>(currentGraph,
							findOriginal(currentGraph, new OvsSwitch(srcDpid,jsReq.getString("src_ip"))), 
							findOriginal(currentGraph, new OvsSwitch(targetdpid, jsReq.getString("dst_ip"))));
					
					addFlows(shortest.getPath(), jsReq.getString("broadcast"));

					pathToClient = NetworkConverter.jpathToMx(shortest.getPath());
					pathHolder.put(pathName, new VPMPathInfo(pathToClient,jsReq.getString("broadcast")));
				}

				mxCodec codec = new mxCodec();	
				String xmlString =  mxXmlUtils.getXml(codec.encode(
						(pathToClient).getModel()));
				jsResp.put("path", xmlString);

			}else if(op.equals("del")){
				if(pathToClient != null){ //if there is no path the request makes no sense
					deleteFlows(NetworkConverter.mxToJgraphT(pathToClient, false));
					pathHolder.remove(pathName);
				}else
					throw new IOException("You are trying to delete a non existent path");
			}

			jsResp.put("status", "ok");

		}catch(IOException ex){
			ex.printStackTrace();
			System.err.println(ex.getMessage());
			jsResp.put("status","error");
			jsResp.put("details", ex.getMessage());
		}finally{
			out.println(jsResp.toString());
			out.close();
		}
	}

}
