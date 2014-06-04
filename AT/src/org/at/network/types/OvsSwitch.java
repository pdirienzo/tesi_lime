package org.at.network.types;

public class OvsSwitch {
	
	public static enum Type {
		ROOT(0),RELAY(1),LEAF(2),NULL(3);
		
		private int value;
		
		Type(int value){
			this.value = value;
		}
		
		public int getValue(){
			return value;
		}
		
		public String toString(){
			String str = null;
			
			switch(value){
			case 0:
				str = "ROOT";
				break;
			}
			
			return str;
		}
	}
	
	public String ip;
	public String dpid;
	public Type type;
	
	public OvsSwitch(String dpid, String ip){
		this(dpid,ip,Type.NULL);
	}
	
	public OvsSwitch(String dpid,String ip, Type type){
		this.ip = ip;
		this.dpid = dpid;
		this.type = type;
	}
	
	public String toString(){
		return "ovs "+dpid+" /"+ip;
	}
	
	public boolean equals(Object o){
		OvsSwitch s = (OvsSwitch)o;
		return (s.ip.equals(this.ip)) && (s.dpid.equals(this.dpid));
	}
	
	@Override
	public int hashCode() {
		return (this.ip+this.dpid).hashCode();
	}
	
	public static void main(String[] args){
		System.out.println(OvsSwitch.Type.ROOT.name());
	}
}
