package br.ufpe.cin.dataanalysis.pointeranalysis;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.ibm.wala.analysis.pointers.HeapGraph;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.analysis.typeInference.TypeInference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.HeapModel;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.LocalPointerKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.ReturnValueKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSALoadIndirectInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.TypeReference;

import br.ufpe.cin.dataanalysis.ComponentOfInterest;
import br.ufpe.cin.datarecommendation.CollectionsTypeResolver;
import br.ufpe.cin.datarecommendation.ICollectionsTypeResolver;

public class PointerAnalysisAnalyzer {
	
	public void extractPointsToAnalysisInformation(AnalysisScope scope, java.util.List<ComponentOfInterest> componentsOfInterest, IClassHierarchy cha) {
		Iterable<Entrypoint> entrypoints = Util.makeMainEntrypoints(scope,cha);
		try {
			

			java.util.List<Entrypoint> myEntrypoints = new ArrayList<Entrypoint>();
	
			for(IClass currClass : cha) {
				if(isApplicationClass(currClass)) {
					for(IMethod method : currClass.getDeclaredMethods()) {
						if(isMethodOfInterest(method, componentsOfInterest)) {
							myEntrypoints.add((new DefaultEntrypoint(method, cha)));
						}
					}
				}
			}
			
			AnalysisOptions options = new AnalysisOptions(scope,myEntrypoints);
			AnalysisCache analysisCache = new AnalysisCache();
			CallGraphBuilder cgBuilder = Util.makeZeroOneCFABuilder(options, analysisCache, cha, scope);
		
			CallGraph cg = cgBuilder.makeCallGraph(options,null);
			Map<CGNode,Boolean> isNodeVisited = new HashMap<CGNode,Boolean>();
			Map<IClass,Boolean> isClassComputed = new HashMap<IClass,Boolean>();
			Iterator<CGNode> allNodes = cg.iterator();
			
			Map<String, AnalyzedClass> pointerResult = new HashMap<String, AnalyzedClass>();

			while(allNodes.hasNext()) {
				CGNode node = allNodes.next();
				String declaringClassName = node.getMethod().getDeclaringClass().getName().toString();
				if(isClassOfInterest(declaringClassName, componentsOfInterest) && !declaringClassName.contains("exception")) {
					walkNodes(node,cg,isNodeVisited,isClassComputed,
							cgBuilder.getPointerAnalysis().getHeapModel(),
							cgBuilder.getPointerAnalysis().getHeapGraph(),
							componentsOfInterest,
							pointerResult);
				}
			}
			BufferedWriter out = new BufferedWriter(new FileWriter("pointerAnalysis.txt"),100000);
			
			for(AnalyzedClass analyzedClass : pointerResult.values()) {
				if(analyzedClass.hasAnyAlias()) {
					out.write("Class:"+analyzedClass.getClassName()+"\n");
					for(AnalyzedInstanceField instanceField : analyzedClass.getAnalyzedInstanceFields()) {
						if(instanceField.getAliases().size() > 0) {
							out.write("\t- Instance field:"+instanceField.getFieldName()+"\n");
							for(AnalyzedAlias alias : instanceField.getAliases()) {
								out.write("\t\t- Alias:"+alias.toString()+"\n");
							}
						}
					}
					for(AnalyzedStaticField instanceField : analyzedClass.getAnalyzedStaticFields()) {
						if(instanceField.getAliases().size() > 0) {
							out.write("\t- Static field:"+instanceField.getFieldName()+"\n");
							for(AnalyzedAlias alias : instanceField.getAliases()) {
								out.write("\t\t- Alias:"+alias.toString()+"\n");
							}
						}
					}
					for(AnalyzedMethod method : analyzedClass.getAnalyzedMethods()) {
						if(method.hasAnyAlias()) {
							out.write("\t- Method:"+method.getMethodName()+"\n");
							for(AnalyzedLocalVariable localVariable : method.getAnalyzedLocalVariables()) {
								if(localVariable.getAliases().size() > 0) {
									out.write("\t\t- Local variable:"+localVariable.getVariableName()+"\n");
									for(AnalyzedAlias alias : localVariable.getAliases()) {
										out.write("\t\t\t- Alias:"+alias.toString()+"\n");
									}
								}
							}
						}
					}
				}
			}
			
			out.close();
	
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (CallGraphBuilderCancelException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void walkNodes(CGNode node, 
			CallGraph cg, 
			Map<CGNode,Boolean> isNodeVisited,
			Map<IClass,Boolean> isClassComputed,
			HeapModel heapModel,
			HeapGraph heapGraph,
			java.util.List<ComponentOfInterest> componentsOfInterest,
			Map<String, AnalyzedClass> pointerResult) throws IOException {
		
		Iterator childs = cg.getSuccNodes(node);
		isNodeVisited.put(node, true);
		
		
		IClass declaringClass = node.getMethod().getDeclaringClass();
		String declaringClassName = declaringClass.getName().toString();
		
		
		ICollectionsTypeResolver nameResolver = new CollectionsTypeResolver();
		
		AnalyzedClass analyzedClass = null;
		if(!isClassComputed.getOrDefault(declaringClass, false)) {
			analyzedClass = new AnalyzedClass(declaringClassName);
			pointerResult.put(declaringClassName, analyzedClass);
			isClassComputed.put(declaringClass,true);
			Collection<IField> staticFields = declaringClass.getDeclaredStaticFields();
			Collection<IField> instanceFields = declaringClass.getDeclaredInstanceFields();
			
			Iterator<NewSiteReference> siteIterator = node.iterateNewSites();
			while(siteIterator.hasNext()) {
				NewSiteReference site = siteIterator.next();
				InstanceKey current = heapModel.getInstanceKeyForAllocation(node, site);
				for(IField instanceField : instanceFields) {
					String typeRef = instanceField.getFieldTypeReference().getName().toString();
					if(nameResolver.isList(typeRef) || nameResolver.isMap(typeRef) || nameResolver.isSet(typeRef)) {
						AnalyzedInstanceField analyzedInstanceField = new AnalyzedInstanceField(analyzedClass, instanceField.getName().toString());
						PointerKey pInstanceField = heapModel.getPointerKeyForInstanceField(current, instanceField);
						getVariablesPointingToTheSamePlace(heapGraph, pInstanceField, analyzedInstanceField, componentsOfInterest);
						analyzedClass.addAnalyzedInstanceField(analyzedInstanceField);
					}
				}
			}
			
			for(IField staticField : staticFields) {
				String typeRef = staticField.getFieldTypeReference().getName().toString();
				if(nameResolver.isList(typeRef) || nameResolver.isMap(typeRef) || nameResolver.isSet(typeRef)) {
					AnalyzedStaticField analyzedStaticField = new AnalyzedStaticField(analyzedClass, staticField.getName().toString());
					PointerKey pStaticField = heapModel.getPointerKeyForStaticField(staticField);
					getVariablesPointingToTheSamePlace(heapGraph, pStaticField, analyzedStaticField, componentsOfInterest);
					analyzedClass.addAnalyzedStaticField(analyzedStaticField);
				}
			}
		} else {
			analyzedClass = pointerResult.get(declaringClassName);
		}
		
		SSAInstruction[] instructions = node.getIR().getInstructions();
		TypeInference ti = TypeInference.make(node.getIR(), true);
		AnalyzedMethod analyzedMethod = new AnalyzedMethod(analyzedClass, node.getMethod().getName().toString());
		for(int i = 0; i < instructions.length; i++) {
			SSAInstruction instruction = instructions[i];
			PointerKey pLocalField = null;
			int vn = -1;
			if(instruction instanceof SSAGetInstruction) {
				SSAGetInstruction getInstruction = (SSAGetInstruction)instruction;
				vn = getInstruction.getDef(0);
				if(vn>-1)
					pLocalField = heapModel.getPointerKeyForLocal(node, getInstruction.getDef(0));
			} else if (instruction instanceof SSAInvokeInstruction) {
				SSAInvokeInstruction invokeInstruction = (SSAInvokeInstruction)instruction;
				vn = invokeInstruction.getReturnValue(0);
				if(vn>-1) 
					pLocalField = heapModel.getPointerKeyForLocal(node, vn);
			} else if (instruction instanceof SSAReturnInstruction) {
				SSAReturnInstruction returnInstruction = (SSAReturnInstruction)instruction;
				vn = returnInstruction.getResult();
				if(vn>-1)
					pLocalField = heapModel.getPointerKeyForLocal(node, vn);
			} else if (instruction instanceof SSAPutInstruction) {
				SSAPutInstruction putInstruction = (SSAPutInstruction)instruction;
				vn = putInstruction.getVal();
				if(vn>-1)
					pLocalField = heapModel.getPointerKeyForLocal(node, vn);
			} else if(instruction instanceof SSALoadIndirectInstruction) {
				SSALoadIndirectInstruction loadIndirectInstruction = (SSALoadIndirectInstruction)instruction;
				vn = loadIndirectInstruction.getDef();
				if(vn>-1)
					pLocalField = heapModel.getPointerKeyForLocal(node, vn);
			}
			if((pLocalField != null) && (vn > -1)) {
				String[] variablePossibleNames = node.getIR().getLocalNames(instruction.iindex, vn);
				if(variablePossibleNames != null) {
					TypeAbstraction typeAbstraction = ti.getType(vn);
					if(typeAbstraction != null) {
						TypeReference typeReference = ti.getType(vn).getTypeReference();
						if(typeReference != null) {
							String typeRef = typeReference.getName().toString();
							if(nameResolver.isList(typeRef) || nameResolver.isMap(typeRef) || nameResolver.isSet(typeRef)) {
								AnalyzedLocalVariable analyzedLocalVariable = new AnalyzedLocalVariable(analyzedMethod, variablePossibleNames[0]);
								getVariablesPointingToTheSamePlace(heapGraph, pLocalField, analyzedLocalVariable, componentsOfInterest);
								analyzedMethod.addLocalVariable(analyzedLocalVariable);
							}
						}
					}
				}
			}
		}
		
		analyzedClass.addAnalyzedMethod(analyzedMethod);
		
		while(childs.hasNext()) {
			CGNode next = (CGNode)childs.next();
			String nextChildDeclaringClassName = next.getMethod().getDeclaringClass().getName().toString();
			if((!isNodeVisited.getOrDefault(next, false)) && isClassOfInterest(nextChildDeclaringClassName, componentsOfInterest)
					&& !nextChildDeclaringClassName.contains("exception"))
				walkNodes(next,cg,isNodeVisited,isClassComputed,heapModel,heapGraph,componentsOfInterest,pointerResult);
		}
	}

	private void getVariablesPointingToTheSamePlace(HeapGraph heapGraph, 
			PointerKey pKey, 
			IContainAlias aliasContainer,
			List<ComponentOfInterest> componentsOfInterest) throws IOException {
		Iterator instanceKeys = heapGraph.getSuccNodes(pKey);
		while(instanceKeys.hasNext()) {
			InstanceKey instanceKey = (InstanceKey)instanceKeys.next();
			Iterator aliasesIterator = heapGraph.getPredNodes(instanceKey);
			while(aliasesIterator.hasNext()) {
				PointerKey alias = (PointerKey) aliasesIterator.next();
				if(!pKey.equals(alias)) {
					if(alias instanceof InstanceFieldKey) {
						InstanceFieldKey fieldKey = (InstanceFieldKey)alias;
						String declaringClassName = fieldKey.getField().getDeclaringClass().getName().toString();
						if(isClassOfInterest(declaringClassName, componentsOfInterest)) {
							AnalyzedAlias analyzedAlias = new AnalyzedAlias(
									fieldKey.getField().getDeclaringClass().getName().toString(), 
									null, 
									fieldKey.getField().getName().toString(), 
									PointerAnalysisVariableType.INSTANCE_FIELD
							);
							aliasContainer.addAlias(analyzedAlias);
						}
					} else if (alias instanceof StaticFieldKey) {
						StaticFieldKey fieldKey = (StaticFieldKey)alias;
						String declaringClassName = fieldKey.getField().getDeclaringClass().getName().toString();
						if(isClassOfInterest(declaringClassName, componentsOfInterest)) {
							AnalyzedAlias analyzedAlias = new AnalyzedAlias(
									fieldKey.getField().getDeclaringClass().getName().toString(), 
									null, 
									fieldKey.getField().getName().toString(), 
									PointerAnalysisVariableType.STATIC_FIELD
							);
							aliasContainer.addAlias(analyzedAlias);
						}
					} else if (alias instanceof LocalPointerKey) {
						LocalPointerKey localKey = (LocalPointerKey)alias;
						CGNode node = ((LocalPointerKey)alias).getNode();
						SSAInstruction[] instructions = node.getIR().getInstructions();
						boolean indexFound = false;
						for(SSAInstruction instruction : instructions) {
							if(instruction != null) {
								for(int i = 0; (i < instruction.getNumberOfUses()) && !indexFound; i++) {
									if(instruction.getUse(i)==((LocalPointerKey)alias).getValueNumber()) {
										String[] possibleVariableNames = node.getIR().getLocalNames(instruction.iindex, instruction.getUse(i));
										if( (possibleVariableNames!=null) && (possibleVariableNames.length > 0) && (possibleVariableNames[0] != null) ) {
											String declaringClassName = node.getMethod().getDeclaringClass().getName().toString();
											if(isClassOfInterest(declaringClassName, componentsOfInterest)) {
												AnalyzedAlias analyzedAlias = new AnalyzedAlias(
														node.getMethod().getDeclaringClass().getName().toString(), 
														node.getMethod().getName().toString(), 
														possibleVariableNames[0], 
														PointerAnalysisVariableType.LOCAL_VARIABLE);
												aliasContainer.addAlias(analyzedAlias);
											}
										}
										indexFound = true;
										break;
									}
								}
							}
						}
					} else if (alias instanceof ReturnValueKey) {
						ReturnValueKey valueKey = (ReturnValueKey)alias;
						CGNode node = valueKey.getNode();
						String declaringClassName = node.getMethod().getDeclaringClass().getName().toString();
						if(isClassOfInterest(declaringClassName, componentsOfInterest)) {
							AnalyzedAlias analyzedAlias = new AnalyzedAlias(
									node.getMethod().getDeclaringClass().getName().toString(),
									node.getMethod().getName().toString(),
									"method-returned-value",
									PointerAnalysisVariableType.LOCAL_VARIABLE);
							aliasContainer.addAlias(analyzedAlias);
						}
					}
				}
			}
		}
	}
	
	private boolean isApplicationClass(IClass clazz) {
		String classLoader = clazz.getClassLoader().toString();
		return classLoader.equals("Application");
	}
	private boolean isMethodOfInterest(IMethod method, java.util.List<ComponentOfInterest> componentsOfInterest) {
		boolean result=false;
		if(method!=null) {
			for(ComponentOfInterest component : componentsOfInterest) {
				if(component.checkIfMethodMeetsComponent(method)) {
					result=true;
					break;
				}
			}
		}
		return result;
	}
	private static boolean isClassOfInterest(String className, java.util.List<ComponentOfInterest> componentsOfInterest) {
		boolean result=false;
		if(className!=null) {
			for(ComponentOfInterest component : componentsOfInterest) {
				if(component.checkIfClassNameMeetsComponent(className)) {
					result=true;
					break;
				}
			}
		}
		return result;
	}
}