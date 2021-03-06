package it.unina.cini.platino.network.types;

import it.unina.cini.platino.floodlight.FloodlightPort;

import org.jgrapht.WeightedGraph;
import org.jgrapht.graph.ListenableUndirectedWeightedGraph;

/**
 * A class representing a VPM graph, made of OvsSwitch class as nodes and LinkConnection
 * as links. It extends a JGraphT ListenableUndirectedWeightedGraph class so to be used
 * to perform algorithms.
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
public class VPMGraph<V extends OvsSwitch, E extends LinkConnection> extends 
ListenableUndirectedWeightedGraph<V, E>{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public VPMGraph(Class<? extends E> edgeClass) {
		super(edgeClass);
	}

	public VPMGraph(WeightedGraph<V, E> graph){
		super(graph);
	}

	public LinkConnection addLinkConnection(V sourceVertex, FloodlightPort sourcePort, 
			V targetVertex, FloodlightPort targetPort){
		
		LinkConnection l = (LinkConnection)super.addEdge(sourceVertex, targetVertex);
		l.setSourceP(sourcePort);
		l.setTargetP(targetPort);
		l.isTree = false;
		
		return l;
	}

	public LinkConnection addLinkConnection(V sourceVertex, FloodlightPort sourcePort, 
			V targetVertex, FloodlightPort targetPort, boolean isTree){

		LinkConnection l = this.addLinkConnection(sourceVertex, sourcePort, targetVertex, targetPort);
		l.isTree = isTree;

		return l;
	}


	/**
	 * @deprecated use addLinkConnection instead
	 */
	@Deprecated 
	@Override
	public E addEdge(V sourceVertex, V targetVertex) {
		return super.addEdge(sourceVertex, targetVertex);
	}

	/**
	 * @deprecated use addLinkConnection instead
	 */
	@Deprecated
	@Override
	public boolean addEdge(V sourceVertex, V targetVertex, E e) {
		return super.addEdge(sourceVertex, targetVertex, e);
	}

}
