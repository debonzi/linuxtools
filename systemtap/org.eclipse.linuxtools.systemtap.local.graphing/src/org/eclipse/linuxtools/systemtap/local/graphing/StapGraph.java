/*******************************************************************************
 * Copyright (c) 2009 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Red Hat - initial API and implementation
 *******************************************************************************/
package org.eclipse.linuxtools.systemtap.local.graphing;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.Map.Entry;

import org.eclipse.cdt.core.model.ICProject;
import org.eclipse.draw2d.Animation;
import org.eclipse.draw2d.Label;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.linuxtools.systemtap.local.core.MP;
import org.eclipse.linuxtools.systemtap.local.graphing.graphlisteners.StapGraphKeyListener;
import org.eclipse.linuxtools.systemtap.local.graphing.graphlisteners.StapGraphMouseListener;
import org.eclipse.linuxtools.systemtap.local.graphing.graphlisteners.StapGraphMouseWheelListener;
import org.eclipse.linuxtools.systemtap.local.graphing.treeviewer.StapTreeContentProvider;
import org.eclipse.linuxtools.systemtap.local.graphing.treeviewer.StapTreeDoubleClickListener;
import org.eclipse.linuxtools.systemtap.local.graphing.treeviewer.StapTreeLabelProvider;
import org.eclipse.linuxtools.systemtap.local.graphing.treeviewer.StapTreeListener;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.zest.core.widgets.Graph;
import org.eclipse.zest.core.widgets.GraphNode;
import org.eclipse.zest.layouts.LayoutStyles;
import org.eclipse.zest.layouts.algorithms.GridLayoutAlgorithm;


public class StapGraph extends Graph {

	public static final String CONSTANT_TOP_NODE_NAME = Messages.getString("StapGraph.0"); //$NON-NLS-1$
	public static final int CONSTANT_HORIZONTAL_SPACING = 50; 
	public static final int CONSTANT_DRAWMODE_BOX = 0;
	public static final int CONSTANT_DRAWMODE_RADIAL = 1;
	public static final int CONSTANT_DRAWMODE_TREE = 2;
	public static final int CONSTANT_DRAWMODE_AGGREGATE = 3;
	public static final int CONSTANT_ANIMATION_SLOW = 1;
	public static final int CONSTANT_ANIMATION_FASTER = 2;
	public static final int CONSTANT_ANIMATION_FASTEST = 3;
	public static final int CONSTANT_MAX_NUMBER_OF_SIBLINGS = 3;
	public static final int CONSTANT_MAX_NUMBER_OF_RADIAL_SIBLINGS = 15;
	public static final int CONSTANT_VERTICAL_INCREMENT = 50;
	public static final int CONSTANT_HORIZONTAL_SPACING_FOR_BOX = 150;
	public static final Color CONSTANT_HAS_PARENT = new Color(Display.getCurrent(), 240, 200,
			200);
	public static final Color CONSTANT_HAS_CHILDREN = new Color(Display.getCurrent(), 200,
			250, 200);
	public static final Color CONSTANT_MARKED = new Color(Display.getCurrent(), 210, 112, 214);
	private int ANIMATION_TIME = 500;
	//Draw level management
	private int topLevelToDraw;
	private int bottomLevelToDraw;
	private int topLevelOnScreen;
	private static int levelBuffer = 30;
	private static int maxNodes = 150;


	private int lowestLevelOfNodesAdded;
	public HashMap<Integer, List<Integer>> levels; 			//Level number, list of node ids
	

	//Node management
	private int idOfLastNode;	
	private HashMap<Integer, StapNode> nodeMap; 				// HashMap of current nodes
	private HashMap<Integer, StapData> nodeDataMap; 			// HashMap of all data
	
	public List<GraphNode> aggregateNodes;
	public HashMap<String, Long> aggregateTime;
	public HashMap<String, Integer> aggregateCount;
	private HashMap <Integer, Integer> collapsedLevelSize;
	public List<Integer> markedNodes;
	public List<Integer> markedCollapsedNodes;
	
	//Modes
	private boolean collapse_mode;
	private int draw_mode;
	private int animation_mode;

	//Time
	private long totalTime;

	//The current center/top of the nodes list
	private int rootVisibleNodeNumber;
	
	//Buttons
	private HashMap<Integer, StapButton> buttons; 			//NodeID of each button
	
	//Special cases
	private boolean killInvalidFunctions;					//Toggle hiding of invalid functions					
	
	//Tree viewer
	private static TreeViewer treeViewer;
	private Composite treeComp;
	private static IDoubleClickListener treeDoubleListener;
	public HashMap<Integer, Integer> currentPositionInLevel;
	//(level, next horizontal position to place a node)
	
	//For cycling through marked nodes
	private int nextMarkedNode;

	//Zooming factor
	public double scale;
	
	private int counter; 		//All purpose counting variable

	
	private ArrayList<Integer> callOrderList;
	private int lastFunctionCalled;
	private Canvas thumbCanvas;
	
	
	private ICProject project;
	
	public StapGraph(Composite parent, int style, Composite treeComp, Canvas tCanvas) {
		super(parent, style);

		//-------------Initialize variables
		thumbCanvas = tCanvas;
		nodeMap = new HashMap<Integer, StapNode>();
		levels = new HashMap<Integer, List<Integer>>();
		nodeDataMap = new HashMap<Integer, StapData>();
		buttons = new HashMap<Integer,StapButton>();
		aggregateTime = new HashMap<String, Long>();
		aggregateCount = new HashMap<String, Integer>();
		currentPositionInLevel = new HashMap<Integer, Integer>();
		collapsedLevelSize = new HashMap<Integer, Integer>();
		markedNodes = new ArrayList<Integer>();
		markedCollapsedNodes = new ArrayList<Integer>();
		animation_mode = 1;
		idOfLastNode = 0;
		rootVisibleNodeNumber=0;
		totalTime = 0;
		collapse_mode = false;
		killInvalidFunctions = true;
		nextMarkedNode = -1;
		scale = 1;
		
		this.treeComp = treeComp;
		if (treeViewer == null || treeViewer.getControl().isDisposed()) {
			//Only create once
			treeViewer = new TreeViewer(this.treeComp);
			StapTreeListener stl = new StapTreeListener(treeViewer.getTree().getHorizontalBar());
			treeViewer.addTreeListener(stl);
		}
				
		//-------------Add listeners
		this.addMouseListener(new StapGraphMouseListener(this));		
		this.addKeyListener(new StapGraphKeyListener(this));
		this.addMouseWheelListener(new StapGraphMouseWheelListener(this));
	}

	
	
	/**
	 * Initialize the treeviewer with data from the graph
	 */
	public void initializeTree() {
		if (treeViewer.getContentProvider() == null) {
			StapTreeContentProvider scp = new StapTreeContentProvider();
			treeViewer.setContentProvider(scp);
		} 
		
		((StapTreeContentProvider) treeViewer.getContentProvider()).setGraph(this);
		
		if (treeViewer.getLabelProvider() != null)
			treeViewer.getLabelProvider().dispose();
		StapTreeLabelProvider prov = new StapTreeLabelProvider();
		treeViewer.setLabelProvider(prov);

		if (treeDoubleListener != null) {
			treeViewer.removeDoubleClickListener(treeDoubleListener);
		}
		treeDoubleListener = new StapTreeDoubleClickListener(treeViewer, this);
		treeViewer.addDoubleClickListener(treeDoubleListener);
		
		treeViewer.setInput(getData(getTopNode()));
		treeViewer.refresh();
	}
	

	/**
	 * Create a new StapData object with the given parameters
	 * 
	 * @param style
	 * @param id
	 * @param txt
	 * @param time
	 * @param called
	 * @param caller
	 * @return
	 */
	public int loadData(int style, int id, String txt, long time, int called,
			int caller, boolean isMarked, String message) {
		//-------------Invalid function catching
		// Catches some random C/C++ directive functions
		if (id < 10 && killInvalidFunctions) {
			if (txt.contains(")")) { //$NON-NLS-1$
				return -1;
			} else if (txt.contains(".")) { //$NON-NLS-1$
				return -1;
			} else if (txt.contains("\"")) { //$NON-NLS-1$
				return -1;
			}
		} 
		
		//-------------Add node to appropriate map/list
		StapData n = new StapData(this, style, txt, time, called, id, caller, isMarked, message);
		if (isMarked)
			markedNodes.add(id);
		nodeDataMap.put(id, n);

		// Make no assumptions about the order that data is input
		if (id > idOfLastNode)
			idOfLastNode = id;
		return id;
	}
	
	/*
	 * Fully functional draw functions
	 * 
	 * -Radial
	 * -Tree
	 */

	/**
	 * Draws a 2-node-layer circle
	 * Draws all nodes in  place.
	 * @param centerNode
	 */
	public void drawRadial(int centerNode) {
		int radius = Math.min(this.getBounds().width,
				this.getBounds().height)
				/ 2 - CONSTANT_VERTICAL_INCREMENT;

		rootVisibleNodeNumber = centerNode;
		if (nodeMap.get(centerNode) == null) {
			nodeMap.put(centerNode, getNodeData(centerNode).makeNode(this));
		}

		// Draw node in center
		StapNode n = nodeMap.get(centerNode);
		int x = this.getBounds().width / 2 - n.getSize().width/2;
		int y = this.getBounds().height / 2 - n.getSize().height;
		n.setLocation(x, y);
		
		if (getData(centerNode).isMarked())
			nodeMap.get(centerNode).setBackgroundColor(CONSTANT_MARKED);
		radialHelper(centerNode, x, y, radius, 0);
	}

	/**
	 * Helps animation of radial draw. Can be replaced by a draw and moveAll.
	 * 
	 * @param centerNode
	 */
	public void preDrawRadial(int centerNode) {
		rootVisibleNodeNumber = centerNode;
		
		if (nodeMap.get(centerNode) == null) {
			nodeMap.put(centerNode, getNodeData(centerNode).makeNode(this));
			StapNode n = nodeMap.get(centerNode);
			n.setLocation(this.getBounds().width / 2, this.getShell()
					.getSize().y / 2);
		}

		//Pass coordinates of the node to radialHelper
		StapNode n = nodeMap.get(centerNode);
		int x = n.getLocation().x;
		int y = n.getLocation().y;
		radialHelper(centerNode, x, y, 0, 0);
	}

	/**
	 * Completes radial-mode draws 
	 * 
	 * @param id
	 * @param x
	 * @param y
	 * @param radius
	 * @param startFromChild
	 */
	public void radialHelper(int id, int x, int y, int radius, int startFromChild) {		
		//-------------Draw parent node
		// Draw caller node right beside this one, in a different color
		int callerID = nodeDataMap.get(id).caller;
		if (callerID != -1) {
			if (getNode(callerID) == null) {
				nodeMap.put(callerID, getNodeData(callerID).makeNode(this));
			}
			getNode(callerID).setBackgroundColor(CONSTANT_HAS_PARENT);
			getNode(callerID).setLocation(x + radius / 5, y - radius / 5);
			if (getNode(id).connection == null) {
				getNode(id).makeConnection(SWT.NONE, getNode(callerID), getData(id).called);
			}
			
			if (getData(callerID).isMarked())
				nodeMap.get(callerID).setBackgroundColor(CONSTANT_MARKED);
		}
		

		//-------------Draw children nodes
		List<Integer> nodeList;
		if (!collapse_mode) {
			nodeList = nodeDataMap.get(id).callees;
		}
		else {
			nodeList = nodeDataMap.get(id).collapsedCallees;
		}

		int numberOfNodes;
		
		if (nodeList.size() >= CONSTANT_MAX_NUMBER_OF_RADIAL_SIBLINGS ) {
			numberOfNodes = CONSTANT_MAX_NUMBER_OF_RADIAL_SIBLINGS;
		}
		else
			numberOfNodes = nodeList.size();
			
		
		double angle;
		if (numberOfNodes > 5)
			angle = 2 * Math.PI / numberOfNodes;
		else
			angle = 2 * Math.PI / CONSTANT_MAX_NUMBER_OF_RADIAL_SIBLINGS;
		
		int i = 0;

		for (i = 0; i < numberOfNodes; i++) {
			

			int subID = nodeList.get(i);
			int yOffset = 0;
			int xOffset = 0;
			if (nodeMap.get(subID) == null) {
				nodeMap.put(subID, getNodeData(subID).makeNode(this));
			}
			
			StapNode subN = nodeMap.get(subID);
			if (radius != 0) {
				yOffset = (int) (radius * Math.cos(angle * i));
				xOffset = (int) (radius * Math.sin(angle * i) + StapNode.getNodeSize()*Math.sin(angle*i)*3);
			}


			if (hasChildren(subID))
				subN.setBackgroundColor(CONSTANT_HAS_CHILDREN);
			subN.setLocation(x + xOffset, y + yOffset);
			if (subN.connection == null) {
				subN.makeConnection(SWT.NONE, nodeMap.get(id), nodeDataMap
						.get(subID).called);
			}
			
			if (getData(subID).isMarked())
				subN.setBackgroundColor(CONSTANT_MARKED);
		}
	}
	
	
	/**
	 * THE AGGREGATE VIEW FROM VIEW -> AGGREGATE VIEW
	 */
	public void drawAggregateView(){
		
		//TEMPORARY STORAGE OF THE ENTRIES
		//IMPLEMENTS A COMPARATOR TO STORE BY ORDER OF THE VALUE
		TreeSet<Entry<String, Long>> sortedValues = new TreeSet<Entry<String, Long>>(StapGraph.VALUE_ORDER);
		sortedValues.addAll(aggregateTime.entrySet());
		
		
		if (aggregateNodes == null){
			aggregateNodes = new ArrayList<GraphNode>();
		}else{
			aggregateNodes.clear();
		}
		
		//-------------Format numbers
		float percentage_time;
		final int colorLevels = 15;
		final int colorLevelDifference = 12;
		int primary;
		int secondary;
		
		NumberFormat num = NumberFormat.getInstance(Locale.CANADA);
		num.setMinimumFractionDigits(2);
		num.setMaximumFractionDigits(2);

		
		
		//-------------Draw nodes
		for (Entry<String, Long> ent : sortedValues) {
			if (!ent.getKey().equals("init")) { //$NON-NLS-1$

				GraphNode n = new GraphNode(this.getGraphModel(),SWT.NONE);
				aggregateNodes.add(n);
				
				percentage_time = ((float) ent.getValue() / this
						.getTotalTime() * 100);
				n.setText(ent.getKey() + "\n"  //$NON-NLS-1$
						+ num.format((float)percentage_time) + "%" + "\n" //$NON-NLS-1$ //$NON-NLS-2$
						+ aggregateCount.get(ent.getKey()) + "\n") ; //$NON-NLS-1$
				
				
				primary = (int)(percentage_time / 100 * colorLevels * colorLevelDifference);
				secondary = (colorLevels * colorLevelDifference) - (int)(percentage_time / 100 * colorLevels * colorLevelDifference);
				
				primary = Math.max(0, primary);
				secondary = Math.max(0, secondary);
				
				primary = Math.min(primary, 255);
				secondary = Math.min(secondary, 255);
				
				
				Color c = new Color(this.getDisplay(),primary,0,secondary);
				n.setBackgroundColor(c);
				n.setHighlightColor(c);
				n.setForegroundColor(new Color(this.getDisplay(),255,255,255));
				n.setTooltip(new Label(
						Messages.getString("StapGraph.2")+ ent.getKey() + "\n" //$NON-NLS-1$ //$NON-NLS-2$
						+ Messages.getString("StapGraph.3") + num.format((float)percentage_time) + "%" + "\n" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						+ Messages.getString("StapGraph.1") + aggregateCount.get(ent.getKey())		 //$NON-NLS-1$
				));
				n.setBorderWidth(2);
			}
		}
		
		//Set layout to gridlayout
		this.setLayoutAlgorithm(new GridLayoutAlgorithm(LayoutStyles.NONE), true);
	}



	/**
	 * Draws a tree starting with node id, putting node id at location x,y
	 * @param id
	 * @param x
	 * @param y
	 */
	public void drawTree(int id, int x, int y) {
		
		//-------------Create node id
		// Create and set
		if (nodeMap.get(id) == null) {
			nodeMap.put(id, getData(id).makeNode(this));
		}
		StapNode n = getNode(id);
		n.setLocation(x,y);
		n.setSize(n.getSize().width/scale, n.getSize().height/scale);

		//This is the lowest level of nodes to draw, and it still has kids
		if (getLevelOfNode(id) == bottomLevelToDraw &&
				getData(id).callees.size() > 0)
			n.setBackgroundColor(CONSTANT_HAS_CHILDREN);
		
		if (getData(id).isMarked())
			n.setBackgroundColor(CONSTANT_MARKED);
		
		
		
		
		//-------------Get appropriate list of children
		List<Integer> callees = null;
		int usefulSize = 0;
		
		// Determine which list of callees to use
		if (!collapse_mode)
			callees = getData(id).callees;
		else
			callees = getData(id).collapsedCallees;
		if (callees == null)
			return;
		
		int cLevel = getLevelOfNode(id) + 1;
		
		if (!collapse_mode) {
			if (levels.get(cLevel) != null) {
				usefulSize = levels.get(cLevel).size() - collapsedLevelSize.get(cLevel);
			}
		}
		else {
		if (collapsedLevelSize.get(cLevel) != null)
			usefulSize = collapsedLevelSize.get(cLevel);
		}
		//-------------Draw all children
		for (int i = 0; i < callees.size(); i++) {
			//Find the number of nodes on this level for spacing purposes
			int childID = callees.get(i);
			int childLevel = getLevelOfNode(childID);

			
			//Initialise the offset to roughly centre the nodes 
			if (currentPositionInLevel.get(getLevelOfNode(childID)) == null) {
				int tmp = (int) (CONSTANT_HORIZONTAL_SPACING*(usefulSize-1) * -1/scale);
				currentPositionInLevel.put(childLevel, getNode(rootVisibleNodeNumber)
						.getLocation().x + tmp);
			}
			
			//Recursive iteration
			if (childLevel <= bottomLevelToDraw && 
					childLevel <= lowestLevelOfNodesAdded) {
				drawTree(callees.get(i), currentPositionInLevel.get(childLevel), 
						y + (int)(CONSTANT_VERTICAL_INCREMENT/scale));
								
				//Do not scale newSize or nodes will no longer be adjacent
				int newSize = currentPositionInLevel.get(getLevelOfNode(childID)) 
								+ getNode(childID).getSize().width;
				
				//Leave a small blank space between nodes for aesthetic purposes
				if (i == callees.size() - 1)
					newSize += CONSTANT_HORIZONTAL_SPACING/3;
				currentPositionInLevel.put(getLevelOfNode(childID), newSize);
			}
			
		}
	}
	
	
	
	/**
	 * Moves all nodes to the point x,y
	 * @param x
	 * @param y
	 */
	public void moveAllNodesTo(int x, int y) {
		for (int i: nodeMap.keySet()) {
			nodeMap.get(i).setLocation(x,y);
		}
	}

	/*
	 * Partially functional draw functions
	 * 
	 * -Box (drawFromBottomToTop)
	 * 	Breaks when switching modes??
	 */


	/**
	 * Draws a tree roughly starting from node id
	 */
	public void drawBox(int id, int x, int y) {
		setLevelLimits(id);
		int MaxLevelPixelWidth = 1;
		int currPixelWidth = 1;
		
		// FIND THE LEVEL THAT WILL BE THE WIDEST
		// WILL BE A USEFUL VALUE LATER ON
		int count;
		
		
		for (int i = topLevelToDraw; i <= bottomLevelToDraw; i++) {
			count = 0;
			levels.get(i).add(0, count);
			int size = levels.get(i).size();
			for (int j = 1; j < size; j++){
				int val = levels.get(i).get(j);
				if (collapse_mode && nodeDataMap.get(val).isPartOfCollapsedNode()) {
					continue;
				}
				if (!collapse_mode && nodeDataMap.get(val).isCollapsed)
					continue;
				
				currPixelWidth += nodeDataMap.get(val).name.length() * 10 + StapGraph.CONSTANT_HORIZONTAL_SPACING_FOR_BOX;
				if (MaxLevelPixelWidth < currPixelWidth) {
					MaxLevelPixelWidth = currPixelWidth;
				}
				count++;
				levels.get(i).remove(0);
				levels.get(i).add(0, count);
			}
			currPixelWidth = 1;
		}

		MaxLevelPixelWidth = (int)(MaxLevelPixelWidth/scale);
		counter = 0;
		if (id == getFirstUsefulNode())
			nodeMap.get(id).setLocation(150 + (MaxLevelPixelWidth/2),y);
		
		drawFromBottomToTop(bottomLevelToDraw, y
				+ ((bottomLevelToDraw  - topLevelToDraw ) * 3 * (int)(CONSTANT_VERTICAL_INCREMENT/scale)),
				MaxLevelPixelWidth);
		
		if (id == getFirstUsefulNode())
			nodeMap.get(id).setLocation(150 + (MaxLevelPixelWidth/2),y);
	}
	
	public void drawFromBottomToTop(int level, int height,
			int MaxLevelPixelWidth) {
		
		// FINISHED DRAWING THE ROOT IN THE LAST RECURSIVE CALL
		if (level == 0 || level < topLevelToDraw ) {
			return;
		}
		
		// FIND ALL THE CHILDREN AT LEVEL 'level'
		int total = levels.get(level).remove(0);
		int count = 1;
		
		//CREATE THE NODES
		for (int i = 0; i < levels.get(level).size(); i ++) {
			int id = levels.get(level).get(i);
			
			if (collapse_mode && nodeDataMap.get(id).isPartOfCollapsedNode()) {
				continue;
			}
			if (!collapse_mode && nodeDataMap.get(id).isCollapsed)
				continue;
			
			if (nodeMap.get(id) == null) {
				nodeMap.put(id, getNodeData(id).makeNode(this));
			}
			
			StapNode n = nodeMap.get(id);
			
			n.setVisible(true);
			n.setSize(n.getSize().width/scale, n.getSize().height/scale);
			//Placement algorithm
			if (getAnimationMode() == CONSTANT_ANIMATION_SLOW){	
				
				if (counter <= ANIMATION_TIME*3)
					Animation.markBegin();
				n.setLocation(150 + (nodeMap.get(getRootVisibleNodeNumber()).getLocation().x),nodeMap.get(getRootVisibleNodeNumber()).getLocation().y);
				n.setLocation(150 + (MaxLevelPixelWidth / (total + 1) * count),height);

				if (counter <= ANIMATION_TIME*3) {
					Animation.run(ANIMATION_TIME/nodeMap.size());
					counter+=ANIMATION_TIME/nodeMap.size();
				}
					
			}else{
				n.setLocation(150 + (MaxLevelPixelWidth / (total + 1) * count),height);				
			}
			
			//IF WE CANNOT DISPLAY ALL NODES COLOUR NODES ON BOTTOM THAT STILL HAVE CHILDREN
			if (level == bottomLevelToDraw && nodeDataMap.get(id).callees.size() != 0){
				n.setBackgroundColor(CONSTANT_HAS_CHILDREN);
			}
			
			
			
			if (getData(n.id).isMarked())
				n.setBackgroundColor(CONSTANT_MARKED);
			
			
			// FIND ALL THE NODES THAT THIS NODE CALLS AND MAKE CONNECTIONS
			List<Integer> setOfCallees = null;
			if (collapse_mode)
				setOfCallees = nodeDataMap.get(id).collapsedCallees;
			else 
				setOfCallees = nodeDataMap.get(id).callees;
			
			for (int val : setOfCallees) {
				if (nodeMap.get(val) != null)
					nodeMap.get(val).makeConnection(SWT.NONE, n, 
						nodeDataMap.get(val).called);
			}
			
			count++;
		}
		// DRAW THE NEXT LEVEL UP
		drawFromBottomToTop(level - 1, height - (3 * (int)(CONSTANT_VERTICAL_INCREMENT/scale)),
				MaxLevelPixelWidth);
	}

	
	
	/*
	 * Level/node management
	 */

	/**
	 * Delete all nodes except for the node with the specified nodeID
	 * 
	 * @param exception
	 *            - id of node NOT to delete (use -1 for 'no exceptions')
	 */
	public void deleteAll(int exception) {		
		//-------------Delete aggregate nodes
		if (aggregateNodes != null){			
			for (GraphNode n : aggregateNodes){
				n.dispose();
			}
			aggregateNodes.clear();
		}
		
		//-------------Save exception node's location
		int x = -1;
		int y = -1;
		if (exception != -1 && nodeMap.get(exception) != null) {
			x =	nodeMap.get(exception).getLocation().x;
			y = nodeMap.get(exception).getLocation().y;
		}

		//-------------Delete all nodes
		for (int i : nodeMap.keySet()) {
			StapNode node = nodeMap.get(i);
			if (node == null)
				continue;
			
			for (int j: node.buttons) {
				buttons.get(j).dispose();
				buttons.remove(j);
			}
			
			node.unhighlight();
			node.dispose();
		}
		nodeMap.clear();

		//-------------Recreate exception
		if (x != -1 && y != -1) {
			StapNode n =getData(exception).makeNode(this);
			n.setLocation(x,y);
			n.highlight();
			nodeMap.put(exception, n);
		}
	}

	/**
	 * Delete a number of levels from the top of the graph
	 * 
	 * @param numberOfLevelsToDelete
	 */
	private void deleteLevelsFromTop(int numberOfLevelsToDelete) {

		if (numberOfLevelsToDelete <= 0)
			return;

		for (int i = 0; i < numberOfLevelsToDelete; i++) {
			List<Integer> level = levels.get(topLevelToDraw);
			for (int j = 0; j < level.size(); j++) {
				if (nodeMap.get(level.get(j)) != null)
					nodeMap.remove(level.get(j)).dispose();
			}
			topLevelToDraw++;
		}
	}

	/**
	 * Delete a number of levels from the bottom of the graph
	 * 
	 * @param numberOfLevelsToDelete
	 */
	private void deleteLevelsFromBottom(int numberOfLevelsToDelete) {

		if (numberOfLevelsToDelete <= 0)
			return;

		for (int i = 0; i < numberOfLevelsToDelete; i++) {
			List<Integer> level = levels.get(getBottomLevelToDraw());

			for (int j = 0; j < level.size(); j++) {
				if (nodeMap.get(level.get(j)) != null)
					nodeMap.remove(level.get(j)).dispose();
			}
			bottomLevelToDraw--;
		}
	}

	/**
	 * Sets top level limit to the level of id, bottom level limit to top level
	 * limit + CONSTANT_LEVEL_BUFFER.
	 * Deletes extraneous levels, changes topLevelToDraw, bottomLevelToDraw
	 * 
	 * @param id - node to recenter with
	 */
	public void setLevelLimits(int id) {
		
		
		
		int new_topLevelToDraw = getLevelOfNode(id);
		changeLevelLimits(new_topLevelToDraw);
		
		
		int new_bottomLevelToDraw = new_topLevelToDraw + levelBuffer;
		if (new_bottomLevelToDraw > lowestLevelOfNodesAdded)
			new_bottomLevelToDraw = lowestLevelOfNodesAdded;

		deleteLevelsFromTop(new_topLevelToDraw - topLevelToDraw);
		deleteLevelsFromBottom(getBottomLevelToDraw() - new_bottomLevelToDraw);

		topLevelToDraw = new_topLevelToDraw;
		bottomLevelToDraw = new_bottomLevelToDraw;
	}
	
	public boolean changeLevelLimits(int lvl) {
		int numberOfNodes = 0;
		List<Integer> list;
		
		int maxLevel = min(lvl + levelBuffer, lowestLevelOfNodesAdded);
		
		for (int level = lvl; level < maxLevel; level++) {
			for (int id : levels.get(level)) {
				if (isCollapseMode())
					list = getData(id).collapsedCallees;
				else
					list = getData(id).callees;
				
				numberOfNodes += list.size();
				
				if (numberOfNodes > maxNodes) {
					levelBuffer = max(0,level-1);
					return true;
				}
			}
		}
		
		return false;

		
	}
	
	/**
	 * Convenience method to redraw everything.
	 */
	public void draw() {
		draw(getFirstUsefulNode());
	}
	
	/**
	 * Convenience method to draw with current draw parameters. Equivalent to
	 * draw(graph.draw_mode, graph.animation_mode, id)
	 * @param id
	 */
	public void draw(int id) {
		draw(draw_mode, animation_mode, id);
	}
	
	/**
	 * Convenience method to draw with current draw parameters. Equivalent to
	 * draw(graph.draw_mode, animation, id)
	 * @param id
	 */
	public void draw(int animation, int id) {
		draw(draw_mode, animation, id);
	}
	

	/**
	 * Draws with the given modes.
	 * @param drawMode
	 * @param animationMode
	 * @param id
	 */
	public void draw(int drawMode, int animationMode, int id) {
		setDrawMode(drawMode);
		setAnimationMode(animationMode);
		this.clearSelection();
		
		this.setRedraw(false);
		if (draw_mode == CONSTANT_DRAWMODE_RADIAL) {
			//Remove thumbnail
			GridData gd = (GridData) thumbCanvas.getLayoutData();
			gd.exclude = true;
			thumbCanvas.setLayoutData(gd);
			thumbCanvas.setVisible(false);
			CallgraphView.layout();
			
			
			//Add treeComp
			gd = (GridData) treeComp.getLayoutData();
			gd.exclude = false;
			treeComp.setLayoutData(gd);
			treeComp.setVisible(true);
			treeViewer.collapseToLevel(getData(id), 1);
			treeViewer.expandToLevel(getData(id), 1);
			
			
		} else if (draw_mode == CONSTANT_DRAWMODE_AGGREGATE){
			//Remove treeComp
			GridData gd = (GridData) treeComp.getLayoutData();
			gd.exclude = true;
			treeComp.setLayoutData(gd);
			treeComp.setVisible(false);
			
			CallgraphView.layout();
			//Remove thumbnail
			gd = (GridData) thumbCanvas.getLayoutData();
			gd.exclude = true;
			thumbCanvas.setLayoutData(gd);
			thumbCanvas.setVisible(false);					
		}
		else{
			//Remove treeComp
			GridData gd = (GridData) treeComp.getLayoutData();
			gd.exclude = true;
			treeComp.setLayoutData(gd);
			treeComp.setVisible(false);

			CallgraphView.layout();

			//Add thumbnail
			gd = (GridData) thumbCanvas.getLayoutData();
			gd.exclude = true;
			thumbCanvas.setLayoutData(gd);
			thumbCanvas.setVisible(true);
			thumbCanvas.setBackground(this.getBackground());
			
			
		}
			//Remove treeComp
		this.setRedraw(true);

		
		//-------------Draw tree
		if (draw_mode == CONSTANT_DRAWMODE_TREE) {
			
			if (animation_mode == CONSTANT_ANIMATION_SLOW) {
				if (nodeMap.get(id) == null)
					nodeMap.put(id, getData(id).makeNode(this));
				int tempX = nodeMap.get(id).getLocation().x;
				int tempY = nodeMap.get(id).getLocation().y;
				Animation.markBegin();
				moveAllNodesTo(tempX, tempY);
				Animation.run(ANIMATION_TIME);
				
				deleteAll(id);
				setLevelLimits(id);
				rootVisibleNodeNumber = id;
				drawTree(id, this.getBounds().width / 2, 20);
				currentPositionInLevel.clear();

				this.update();
				Animation.markBegin();
				drawTree(id, this.getBounds().width / 2, 20);

				Animation.run(ANIMATION_TIME);
				getNode(id).unhighlight();
				currentPositionInLevel.clear();
			} else {
				deleteAll(id);
				setLevelLimits(id);
				rootVisibleNodeNumber = id;
				drawTree(id, this.getBounds().width / 2, 20);
				getNode(id).unhighlight();
				currentPositionInLevel.clear();
			}
		}
		
		
		//-------------Draw radial
		else if (draw_mode == CONSTANT_DRAWMODE_RADIAL) {
			
			if (animation_mode == CONSTANT_ANIMATION_SLOW) {
				rootVisibleNodeNumber = id;
				deleteAll(id);

				preDrawRadial(id);
				this.redraw();
				this.getLightweightSystem().getUpdateManager()
						.performUpdate();
	
				Animation.markBegin();
				nodeMap.get(id).setLocation(this.getBounds().width / 2,
						this.getBounds().height / 2);
				drawRadial(id); 
				Animation.run(ANIMATION_TIME);
				CallgraphView.maximizeOrRefresh(false);
			}
	
			else {	
				deleteAll(id);
				drawRadial(id);
			}
		}
		
		//-------------Draw box
		else if (draw_mode == CONSTANT_DRAWMODE_BOX) {
			rootVisibleNodeNumber = id;
			if (animation_mode == CONSTANT_ANIMATION_SLOW) {
				if (nodeMap.get(id) == null)
					nodeMap.put(id, getData(id).makeNode(this));
				
				Animation.markBegin();
				moveAllNodesTo(nodeMap.get(id).getLocation().x, nodeMap.get(id).getLocation().y);
				Animation.run(ANIMATION_TIME);
				
				deleteAll(id);
				
				drawBox(id, 0, 0);
				
			} else {
				if (nodeMap.get(id) == null)
					nodeMap.put(id, getData(id).makeNode(this));
				deleteAll(id);
				drawBox(id, 0, 0);

			}
		}
		
		
		//-------------Draw aggregate
		else if (draw_mode == CONSTANT_DRAWMODE_AGGREGATE) {
			rootVisibleNodeNumber = getFirstUsefulNode();
			deleteAll(-1);
			drawAggregateView();
		}
		
		//THIS CAUSED A NULL POINTER GOING INTO AGGREGATE VIEW
		if (getNode(id) != null)
			getNode(id).unhighlight();
		clearSelection();
		
	}
	
	
	
	@SuppressWarnings("unchecked")
	/**
	 * Unhighlights all selected nodes and sets selection to null
	 */
	public void clearSelection() {
		List<GraphNode> list = this.getSelection();
		
		for (GraphNode n : list) {
			if (n != null) n.unhighlight();
		}
		this.setSelection(null);
		
	}

	/*
	 * THE FOLLOWING METHODS ARE NOT WELL TESTED
	 * 
	 * Some are not thoroughly tested, and some just plain don't work. Use at your peril!
	 */

	/**
	 * Shift the given node to the given location, moving all children nodes
	 * accordingly
	 * 
	 * 
	 * @param id
	 * @param x
	 * @param y
	 */
	public void moveRecursive(int id, int xTarget, int yTarget) {
		if (nodeMap.get(id) != null) {
			int x = nodeMap.get(id).getLocation().x;
			int y = nodeMap.get(id).getLocation().y;
			nodeMap.get(id).setLocation(x + xTarget, y + yTarget);
		}
		//If a node is null, then its children must be null
		else
			return;

		List<Integer> list = null;
		
		if (collapse_mode)
			list = nodeDataMap.get(id).collapsedCallees;
		else
			list = nodeDataMap.get(id).callees;
		for (int i = 0; i < list.size(); i++) {
			moveRecursive(list.get(i), xTarget, yTarget);
		}
	}
	
	
	/**
	 * Moves all nodes by the given amount. Adds xDiff, yDiff to the current x,y coordinates.
	 * 
	 * Currently unused.
	 */
	public void moveAllNodesBy(int xDiff, int yDiff) {
		for (int id : nodeMap.keySet()) {
			if (nodeMap.get(id) == null) continue;
			
			int x = nodeMap.get(id).getLocation().x;
			int y = nodeMap.get(id).getLocation().y;
			getNode(id).setLocation(x + xDiff, y + yDiff);
		}
	}
	


	/**
	 * Recursively collapses all children of node id, and puts them in the
	 * collapsedCallees list of id.
	 * 
	 * At the end of this run, each collapsed node will have a list of other
	 * collapsed nodes AND a list of non-collapsed nodes. So will node #id.
	 * 
	 * Uncollapsed nodes, however, will not have a list of collapsed nodes attached. 
	 * 
	 * @param ID of node to start from (use getFirstUsefulNode() to collapse everything
	 * @return True if successful
	 */
	public boolean recursivelyCollapseAllChildrenOfNode(int id) {
		//-------------Initialize
		//If all nodes have been collapsed, don't do anything
		setCollapseMode(true);

		if (nodeDataMap.get(id).hasCollapsedChildren
				|| nodeDataMap.get(id).callees.size() == 0)
			return true;
		nodeDataMap.get(id).hasCollapsedChildren = true;

		
		
		// Name, id
		HashMap<String, Integer> newNodeMap = new HashMap<String, Integer>();
		// id of 'collapsed' node, id of its uncollapsed twin
		HashMap<Integer, Integer> collapsedNodesWithOnlyOneNodeInThem = new HashMap<Integer, Integer>();
		int size = nodeDataMap.get(id).callees.size();
		
		
		
		//-------------Iterate
		for (int i = 0; i < size; i++) {

			int childID = nodeDataMap.get(id).callees.get(i);
			int childLevel = getLevelOfNode(childID);
			if (collapsedLevelSize.get(childLevel) == null)
				collapsedLevelSize.put(childLevel, 0);
			String nodeName = nodeDataMap.get(childID).name;

			/*
			 * Aggregate data for the given node
			 */
			if (newNodeMap.get(nodeName) != null) {
				int aggregateID = newNodeMap.get(nodeName);

				if (collapsedNodesWithOnlyOneNodeInThem.get(aggregateID) != null) {
					
					//-------------Aggregate nodes - second node to be found
					// We still think this is an only child - create a new
					// data node and aggregate
					this.loadData(SWT.NONE, aggregateID, nodeName, nodeDataMap
							.get(childID).time, nodeDataMap.get(childID).called,
							id, nodeDataMap.get(childID).isMarked(), ""); //$NON-NLS-1$
					
					if (getData(aggregateID).isMarked()) {
						markedCollapsedNodes.add(aggregateID);
						markedNodes.remove((Integer) aggregateID);
					}
					
					nodeDataMap.get(id).callees.remove((Integer) aggregateID);
					nodeDataMap.get(id).collapsedCallees.add(aggregateID);

					nodeDataMap.get(aggregateID).collapsedCaller = id;

					// Aggregate
					int otherChildID = collapsedNodesWithOnlyOneNodeInThem
							.get(aggregateID);
					aggregateData(nodeDataMap.get(aggregateID), nodeDataMap
							.get(otherChildID));
					collapsedNodesWithOnlyOneNodeInThem.remove(aggregateID);
					nodeDataMap.get(aggregateID).callees.addAll(nodeDataMap
							.get(otherChildID).callees);

					nodeDataMap.get(otherChildID).setPartOfCollapsedNode(true);

				} else 
					//-------------Aggregate - third and additional nodes
					aggregateData(nodeDataMap.get(aggregateID), nodeDataMap
							.get(childID));

				
				//-------------Complete aggregation
				nodeDataMap.get(aggregateID).callees
						.addAll(nodeDataMap.get(childID).callees);
				nodeDataMap.get(aggregateID).isCollapsed = true;

				if (nodeMap.get(childID) != null) {
					nodeMap.get(childID).setLocation(
							nodeMap.get(id).getLocation().x
									- nodeMap.get(id).getSize().width,
							nodeMap.get(id).getLocation().y);
				}

				nodeDataMap.get(childID).setPartOfCollapsedNode(true);
			} else {
				//-------------First child with this name
				
				idOfLastNode++;
				newNodeMap.put(nodeName, idOfLastNode);
				collapsedNodesWithOnlyOneNodeInThem.put(idOfLastNode, childID);
				if (nodeMap.get(childID) != null) {
					nodeMap.get(childID).setLocation(
							nodeMap.get(id).getLocation().x,
							nodeMap.get(id).getLocation().y);
				}

				int tmp = collapsedLevelSize.get(childLevel) + 1;
				collapsedLevelSize.put(childLevel, tmp);
			}
		}

		//-------------Handle nodes that only appeared once
		for (int i : collapsedNodesWithOnlyOneNodeInThem.keySet()) {
			int childID =collapsedNodesWithOnlyOneNodeInThem.get(i); 
			nodeDataMap.get(childID).onlyChildWithThisName = true;
			nodeDataMap.get(id).collapsedCallees.add(childID);
			newNodeMap.remove(nodeDataMap.get(childID).name);
			nodeDataMap.get(childID).collapsedCaller = id;
			
			if (getData(childID).isMarked())
				markedCollapsedNodes.add(childID);
		}


		
		//-------------Finish iterations
		for (int i : nodeDataMap.get(id).collapsedCallees) {
			recursivelyCollapseAllChildrenOfNode(i);
		}

		nodeDataMap.get(id).sortByTime();

		collapsedNodesWithOnlyOneNodeInThem.clear();
		newNodeMap.clear();

		nodeDataMap.get(id).hasCollapsedChildren = true;
		return true;
	}

	/**
	 * Add time, called values for the two given nodes, storing them inside
	 * victim. Also adds marked collapsed nodes to markedCollapsedNodes list
	 * @param target
	 * @param victim
	 */
	public void aggregateData(StapData target, StapData victim) {
		target.time += victim.time;
		target.called += victim.called;
		if (victim.isMarked() || target.isMarked()) {
			target.setMarked();
			markedCollapsedNodes.add(target.id);
		}
	}

	/*
	 * Convenience methods
	 */

	/**
	 * Prints the name of every node on the given level
	 * @param level
	 */
	public void printContents(int level) {
		if (levels.get(level) != null)
			return;
		MP.println("Contents of level " + level + ":\n"); //$NON-NLS-1$ //$NON-NLS-2$
		for (int i = 0; i < levels.get(level).size(); i++) {
			MP.println(nodeDataMap.get(levels.get(level).get(i)).name);
		}
		MP.println("---------------------------"); //$NON-NLS-1$
	}

	/**
	 * 
	 * @param id of node
	 * @return StapNode
	 */
	public StapNode getNode(int id) {
		return nodeMap.get(id);
	}

	/**
	 * 
	 * @param id of node
	 * @return StapData 
	 */
	public StapData getNodeData(int id) {
		return nodeDataMap.get(id);
	}

	/**
	 * Recommend using getFirstUsefulNode instead.
	 * @return First node in level 0
	 */
	public int getTopNode() {
		return levels.get(topLevelToDraw).get(0);
	}

	/**
	 * Recommend use of this function instead of getTopNode()
	 * @return First node that is not the dummy first node
	 */
	public int getFirstUsefulNode() {
		int id = 0;
		if (nodeDataMap.get(id).name == CONSTANT_TOP_NODE_NAME) {
			id++;
		}

		// Get first node that is not TOP_NODE_NAME
		while (nodeDataMap.get(id) == null) {
			id++;
		}

		return id;
	}

	/**
	 * 
	 * @return Top level to draw - the level should be defined by draw algorithms 
	 */
	public int getTopLevel() {
		return topLevelToDraw;
	}

	/**
	 * Returns the level of recursion associated with the given node.
	 * @param nodeID 
	 * @return
	 */
	public int getLevelOfNode(int nodeID) {
		return nodeDataMap.get(nodeID).levelOfRecursion;
	}

	/**
	 * Returns true if the given node has any children.
	 * @param nodeID
	 * @return
	 */
	public boolean hasChildren(int nodeID) {
		if (nodeDataMap.get(nodeID).callees.size() > 0)
			return true;
		return false;
	}

	/**
	 * Attempts to set dimensions (not used)
	 * @param width
	 * @param height
	 */
	public void setDimensions(int width, int height) {
		this.getBounds().width = width;
		this.getBounds().height = height;
	}
	
	/**
	 * Sets animation mode - all available modes are named 
	 * StapGraph.CONSTANT_ANIMATION_*
	 * @param mode
	 */
	public void setAnimationMode(int mode) {
		animation_mode = mode;
		if (mode == CONSTANT_ANIMATION_SLOW){
			CallgraphView.getAnimation_slow().setChecked(true);
			CallgraphView.getAnimation_fast().setChecked(false);
		}else if (mode == CONSTANT_ANIMATION_FASTEST){
			CallgraphView.getAnimation_slow().setChecked(false);
			CallgraphView.getAnimation_fast().setChecked(true);			
		}
	}
	
	public void setCollapseMode(boolean value) {
		collapse_mode = value;
		nextMarkedNode = -1;
		CallgraphView.getMode_collapsednodes().setChecked(value);
	}

	/**
	 * Gets id of root visible node
	 * @return rootVisibleNode - ID of centre node
	 */
	public int getRootVisibleNodeNumber() {
		return rootVisibleNodeNumber;
	}

	/**
	 * Sets id of root visible node
	 * @param id - ID of centre node
	 */
	public void setRootVisibleNodeNumber(int id) {
		this.rootVisibleNodeNumber = id;
	}

	/**
	 * Gets to the total time spent running tapped program
	 * @return Time in milliseconds
	 */
	public long getTotalTime() {
		return totalTime;
	}

	/**
	 * Sets total time spent running tapped program
	 * @param totalTime - Time in milliseconds
	 */
	public void setTotalTime(long val) {
		//Divide by 100 to save us the trouble of 
		//multiplying by 100 to get percentages
		this.totalTime = val;
	}

	/**
	 * 
	 * @return Number of the lowest level in which nodes exist
	 */
	public int getLowestLevelOfNodesAdded() {
		return lowestLevelOfNodesAdded;
	}

	/**
	 * Set lowest level to which nodes have been added
	 * 
	 * WARNING: Do not set this without adding nodes to that level first, or there
	 * may be null pointer exceptions.
	 * @param lowestLevelOfNodesAdded
	 */
	public void setLowestLevelOfNodesAdded(int lowestLevelOfNodesAdded) {
		this.lowestLevelOfNodesAdded = lowestLevelOfNodesAdded;
	}
	
	
	public int getDrawMode() {
		return draw_mode;
	}

	public void setDrawMode(int draw_mode) {
		this.draw_mode = draw_mode;
	}

	/**
	 * Resets the tree and graph to center on the first useful node.
	 * Does NOT change draw mode, animation mode or collapse mode.
	 */
	public void reset() {
		setSelection(null);
		
		draw(draw_mode, animation_mode, getFirstUsefulNode());
		if (! (draw_mode == StapGraph.CONSTANT_DRAWMODE_AGGREGATE)){
			getNode(getFirstUsefulNode()).unhighlight();			
		}
		if (treeViewer!=null) {
			treeViewer.collapseAll();
			treeViewer.expandToLevel(2);
		}
		scale = 1;
		nextMarkedNode = -1;
	}
	
	
	public boolean isCollapseMode() {
		return this.collapse_mode;
	}


	public void setBottomLevelToDraw(int bottomLevelToDraw) {
		this.bottomLevelToDraw = bottomLevelToDraw;
	}


	public int getBottomLevelToDraw() {
		return bottomLevelToDraw;
	}


	public void setTopLevelOnScreen(int topLevelOnScreen) {
		this.topLevelOnScreen = topLevelOnScreen;
	}


	public int getTopLevelOnScreen() {
		return topLevelOnScreen;
	}
	

	/**
	 * Returns a list of all nodes at the given level.
	 * 
	 * @param level
	 * @return List of ID's of all nodes at the given level.
	 */
	public List<Integer> getLevel(int level) {
		if (level < 0 || level > lowestLevelOfNodesAdded) {
			return null;
		}
		
		return levels.get(level); 
	}

	
	
	/**
	 * Returns the StapData object with id == val.
	 * @param val
	 * @return
	 */
	public StapData getData(int val) {
		if (val > -1)
			return nodeDataMap.get(val);
		else
			return null;
	}
	

	public void setTreeViewer(TreeViewer treeview) {
		StapGraph.treeViewer = treeview;
	}


	public TreeViewer getTreeViewer() {
		return treeViewer;
	}
	
	
	/**
	 * Returns the StapNode object for the parent of node id. May be null. 
	 * @param id
	 * @return
	 */
	public StapNode getParentNode(int id) {
		return nodeMap.get(nodeDataMap.get(id).caller);
	}
	
	
	/**
	 * Returns a StapData object for the parent of node id. May be null. 
	 * @param id
	 * @return
	 */
	public StapData getParentData(int id) {
		return nodeDataMap.get(nodeDataMap.get(id).caller);
	}
	
	/**
	 * Returns the id of the next node that was called. This is necessarily either a
	 * child of the current root node or of one of its ancestors' children.
	 * 
	 * @param id
	 * @return Id of next node that was called.
	 */
	public int getNextCalledNode(int id) {
		int returnID = -1;
		
		for (int count = callOrderList.indexOf((Integer)id) + 1;
			count < callOrderList.size(); count++) {
			if (getData(id) == null)
				continue;
			if (!getData(id).isCollapsed || getData(id).isOnlyChildWithThisName()) {
				returnID = callOrderList.get(count);
				return returnID;
			}
		}
		
		
		return returnID;
	}
	
	/**
	 * Returns the id of the previous node that was called.
	 * 
	 * @param id
	 * @return Id of previous node that was called.
	 */
	public int getPreviousCalledNode(int id) {
		int returnID = -1;
		
		for (int count = callOrderList.indexOf((Integer)id) - 1;
			count > -1; count--) {
			if (getData(id) == null)
				continue;
			if (!getData(id).isCollapsed || getData(id).isOnlyChildWithThisName()) {
				returnID = callOrderList.get(count);
				return returnID;
			}
		}
		
		
		return returnID;
	}
	
	

	/**
	 * Returns the id of the next marked node in current collapse mode. 
	 * Wraps back to the first marked node.
	 * 
	 * @return Node id of next marked node.
	 */
	public int getNextMarkedNode() {
		List<Integer> list = markedNodes;
		if (collapse_mode)
			list = markedCollapsedNodes;
		
		if (list.size() == 0)
			return -1;
		
		
		nextMarkedNode++;
		if (nextMarkedNode >= list.size())
			nextMarkedNode = 0;
		
		
		return list.get(nextMarkedNode);
	}
	
	
	/**
	 * Returns the id of the next marked node in current collapse mode. 
	 * Wraps back to the first marked node.
	 * 
	 * @return Node id of next marked node.
	 */
	public int getPreviousMarkedNode() {
		List<Integer> list = markedNodes;
		if (collapse_mode)
			list = markedCollapsedNodes;
		
		if (list.size() == 0)
			return -1;
		
		nextMarkedNode--;
		if (nextMarkedNode < 0)
			nextMarkedNode = list.size() - 1;
		
		
		return list.get(nextMarkedNode);
	}

	
	
	public static final Comparator<Entry<String, Long>> VALUE_ORDER = new Comparator<Entry<String, Long>>()
    {
        public int compare(Entry<String, Long> a, Entry<String, Long> b){
        	return ((Long)a.getValue()).compareTo(((Long)b.getValue()));
        }
    };

	
    /**
     * Increments the scrollbars by x, y
     * 
     * @param x
     * @param y
     */
    public void scrollBy(int x, int y) {
    	this.scrollTo(this.getHorizontalBar().getSelection() + x, 
    			this.getVerticalBar().getSelection() + y);
    }
    
    
    /**
     * Smoothly increments the scrollbars by x, y
     * 
     * @param x
     * @param y
     */
    public void scrollSmoothBy(int x, int y) {
    	this.scrollSmoothTo(this.getHorizontalBar().getSelection() + x, 
    			this.getVerticalBar().getSelection() + y);
    }
    
    /**
     * Retruns the number of StapData objects placed in the nodeDataMap.
     * @return
     */
    public int getDataMapSize() {
    	return nodeDataMap.size();
    }
    
	
	public int getAnimationMode() {
		return animation_mode;
	}


	public int getLevelBuffer() {
		return levelBuffer;
	}


	public void setLevelBuffer(int val) {
		levelBuffer = val;
	}
	
	public int min(int a, int b) {
		if (a < b) return a;
		return b;
	}

	public int max(int a, int b) {
		if (a > b) return a;
		return b;
	}

	public int getMaxNodes() {
		return maxNodes;
	}

	public void setMaxNodes(int val) {
		maxNodes = val;
	}



	public ArrayList<Integer> getCallOrderList() {
		return callOrderList;
	}



	public void setCallOrderList(ArrayList<Integer> callOrderList) {
		this.callOrderList = callOrderList;
	}



	public int getLastFunctionCalled() {
		return lastFunctionCalled;
	}



	public void setLastFunctionCalled(int lastFunctionCalled) {
		this.lastFunctionCalled = lastFunctionCalled;
	}



	public ICProject getProject() {
		return project;
	}



	public void setProject(ICProject myProject) {
		this.project = myProject;
	}
}