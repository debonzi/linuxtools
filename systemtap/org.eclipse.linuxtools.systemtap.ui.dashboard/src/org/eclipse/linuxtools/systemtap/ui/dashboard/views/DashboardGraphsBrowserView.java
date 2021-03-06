/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - Jeff Briggs, Henry Hughes, Ryan Morse, Anithra P J
 *******************************************************************************/

package org.eclipse.linuxtools.systemtap.ui.dashboard.views;

import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;

import org.eclipse.linuxtools.systemtap.ui.graphingapi.nonui.filters.IDataSetFilter;
import org.eclipse.linuxtools.systemtap.ui.logging.LogManager;
import org.eclipse.linuxtools.systemtap.ui.structures.TreeNode;
import org.eclipse.linuxtools.systemtap.ui.dashboard.actions.ActivateGraphAction;
import org.eclipse.linuxtools.systemtap.ui.dashboard.internal.DashboardPlugin;
import org.eclipse.linuxtools.systemtap.ui.dashboard.structures.ActiveModuleData;
import org.eclipse.linuxtools.systemtap.ui.dashboard.structures.ActiveModuleTreeNode;
import org.eclipse.linuxtools.systemtap.ui.dashboard.structures.DashboardGraphData;
import org.eclipse.linuxtools.systemtap.ui.dashboard.structures.GraphTreeNode;


/**
 * This is a basic browser view for the dashboard perspective.  It contains a list
 * of all of the available dashboard modules.
 * @author Ryan Morse
 */
public class DashboardGraphsBrowserView extends GraphsView {
	public DashboardGraphsBrowserView() {
		super();
		LogManager.logInfo("Initializing", this);
	}
	
	/**
	 * This method sends requests to get all of the modules that are
	 * avialable on the system.  Once then are found, it will
	 * set the viewer's content to the tree of modules that were found.
	 */
	/*protected void generateGraphsTree() {
		TreeNode graphs = DashboardGraphsLocator.getGraphs();
		
		if(null != graphs)
			viewer.setInput(graphs);
		else
			viewer.setInput(new TreeNode("", false));
	}*/
	
	protected void generateGraphsTree() {
		viewer.setInput(new TreeNode("root", "", false));
	}
	
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		viewer.setLabelProvider(new ViewLabelProvider());
	}
	
	/**
	 * This method builds the actions for the items inside this view.  It adds a
	 * double click listener to each of the Items so they will be run if they
	 * are actual modules.  It also sets up the layout for popup menu when users
	 * right click on a module element.
	 */
	protected void makeActions() {
		//Gets items from plugin.xml
		MenuManager manager = new MenuManager("modulePopup");
		Control control = this.viewer.getControl();
		manager.add(new Separator("file.ext"));
		manager.add(new Separator("build.ext"));
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		Menu menu = manager.createContextMenu(control);
		control.setMenu(menu);
		getSite().registerContextMenu(manager, viewer);
		super.makeActions();
	}
	
	@SuppressWarnings("unchecked")
	public void add(String display, ActiveModuleData data) {
		TreeNode root = (TreeNode)viewer.getInput();
		ActiveModuleTreeNode child = new ActiveModuleTreeNode(data, display, true);
		GraphTreeNode graphNode;
		root.add(child);
		DashboardGraphData graphData;
		for(int i=0; i<data.module.graphs.length; i++) {
			graphData = new DashboardGraphData();
			graphData.data = data.data;
			graphData.filters = new IDataSetFilter[data.module.filters[i].size()];
			data.module.filters[i].toArray(graphData.filters);
			graphData.graph = data.module.graphs[i];
			graphData.moduleName = data.module.category;
			graphData.adapter = null;
			ActivateGraphAction act = new ActivateGraphAction();
			graphNode = new GraphTreeNode(graphData, data.module.graphs[i].title, true);
			act.run(graphNode);
			child.add(graphNode);
			
		}
		root.sortLevel();
		viewer.refresh();
		
	}
	/**
	 * This method removes all internal references. Nothing should be called/referenced after
	 * this method is run.
	 */
	public void dispose() {
		LogManager.logInfo("disposing", this);
		super.dispose();
		viewer=null;
	}
	/**
	 * This class provides functionality for determining what image to 
	 * display for each item in the tree.
	 */
	private class ViewLabelProvider extends LabelProvider {
		public String getText(Object obj) {
			return obj.toString();
		}

		public Image getImage(Object obj) {
			TreeNode treeObj = (TreeNode)obj;
			Image img = PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
			
			if(treeObj.getChildCount() > 0)
				img = DashboardPlugin.getImageDescriptor("icons/misc/module_obj.gif").createImage();
			else if(treeObj instanceof GraphTreeNode){
				if(null == ((DashboardGraphData)((GraphTreeNode)treeObj).getData()).adapter)
					img = DashboardPlugin.getImageDescriptor("icons/misc/graph_dis.gif").createImage();
				else
					img = DashboardPlugin.getImageDescriptor("icons/misc/graph_act.gif").createImage();
			}

			return img;
		}
	}	
	public static final String ID = "org.eclipse.linuxtools.systemtap.ui.dashboard.views.DashboardGraphsBrowserView";
	
}

