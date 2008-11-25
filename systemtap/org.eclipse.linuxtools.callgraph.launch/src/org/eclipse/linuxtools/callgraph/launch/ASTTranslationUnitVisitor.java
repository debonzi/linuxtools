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
package org.eclipse.linuxtools.callgraph.launch;

import java.util.ArrayList;

import org.eclipse.cdt.core.dom.ast.ASTVisitor;
import org.eclipse.cdt.core.dom.ast.IASTDeclaration;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;


//TODO: Remove unused class?
public class ASTTranslationUnitVisitor extends ASTVisitor{
	
	public ASTTranslationUnitVisitor () {
		  super();
		  shouldVisitStatements = true;
		  shouldVisitDeclarations = true;
		  funcs = new ArrayList<String>(); 
	}

	
	private String fileName;
	
	private ArrayList<String> funcs;
	 public int visit(IASTDeclaration s) {
		 if (!s.getParent().getContainingFilename().contains(fileName))
			 return PROCESS_SKIP;
		 if (s instanceof IASTFunctionDefinition) {
			 String name =((IASTFunctionDefinition) s).getDeclarator().getName().toString();
			 if (!name.substring(0, 2).equals("__")) { //$NON-NLS-1$
				 funcs.add(name);
			 }
		 }
		 return PROCESS_CONTINUE;
	 }

	 
	 public ArrayList<String> getFunctions() {
		 return funcs;
	 }
	 
	 public void setFileName(String val) {
		 fileName = val;
	 }
}