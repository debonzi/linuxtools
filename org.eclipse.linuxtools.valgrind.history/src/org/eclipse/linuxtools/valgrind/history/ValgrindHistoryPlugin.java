/*******************************************************************************
 * Copyright (c) 2008 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Elliott Baron <ebaron@redhat.com> - initial API and implementation
 *******************************************************************************/
package org.eclipse.linuxtools.valgrind.history;

import java.util.HashMap;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class ValgrindHistoryPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.eclipse.linuxtools.valgrind.history"; //$NON-NLS-1$

	// Extension point constants
	public static final String TOOL_EXT_ID = "statePersisters"; //$NON-NLS-1$
		
	protected static final String EXT_ELEMENT = "persister"; //$NON-NLS-1$
	protected static final String EXT_ATTR_TOOL = "tool"; //$NON-NLS-1$
	protected static final String EXT_ATTR_CLASS = "class"; //$NON-NLS-1$
	
	// The shared instance
	private static ValgrindHistoryPlugin plugin;
	
	protected HashMap<String, IConfigurationElement> toolMap;
	
	/**
	 * The constructor
	 */
	public ValgrindHistoryPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static ValgrindHistoryPlugin getDefault() {
		return plugin;
	}
	
	public IValgrindPersistable getPersistable(String id) throws CoreException {
		IValgrindPersistable persistable = null;
		IConfigurationElement config = getToolMap().get(id);
		if (config != null) {
			Object obj = config.createExecutableExtension(EXT_ATTR_CLASS);
			if (obj instanceof IValgrindPersistable) {
				persistable = (IValgrindPersistable) obj;
			}
			if (persistable == null) {
				throw new CoreException(new Status(IStatus.ERROR, PLUGIN_ID, Messages.getString("ValgrindHistoryPlugin.Cannot_retrieve_persistable"))); //$NON-NLS-1$
			}
		}		
		return persistable;
	}
	
	protected void initializeToolMap() {
		toolMap = new HashMap<String, IConfigurationElement>();
		IExtensionPoint extPoint = Platform.getExtensionRegistry().getExtensionPoint(PLUGIN_ID, TOOL_EXT_ID);
		IConfigurationElement[] configs = extPoint.getConfigurationElements();
		for (IConfigurationElement config : configs) {
			if (config.getName().equals(EXT_ELEMENT)) {
				String id = config.getAttribute(EXT_ATTR_TOOL);
				if (id != null && config.getAttribute(EXT_ATTR_CLASS) != null) {
					toolMap.put(id, config);
				}
			}
		}
	}

	protected HashMap<String, IConfigurationElement> getToolMap() {
		if (toolMap == null) {
			initializeToolMap();
		}
		return toolMap;
	}

}
