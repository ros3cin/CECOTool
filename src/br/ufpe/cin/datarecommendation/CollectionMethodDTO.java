package br.ufpe.cin.datarecommendation;

import java.util.ArrayList;

import br.ufpe.cin.dataanalysis.CollectionMethod;
import br.ufpe.cin.dataanalysis.Complexity;
import br.ufpe.cin.dataanalysis.LoopBlockInfo;

public class CollectionMethodDTO extends CollectionMethod{
	
	private String concreteType;	
	private String method;
	private Integer ocurrencies;
	private String loopInfo;
	
	public CollectionMethodDTO(String concreteType, String fieldName, String method, Integer ocurrencies, String loopInfo, String classContainingField, boolean isFieldLocal) {
		super();
		this.concreteType = concreteType;
		this.method = method;
		this.ocurrencies = ocurrencies;
		this.loopInfo = loopInfo;
		setFieldName(fieldName);
		setFieldLocal(isFieldLocal);
		setClasse(classContainingField);
	}
	
	@Override
	public String getConcreteType() {
		return concreteType;
	}
	
	@Override
	public int getOcorrencias() {
		return ocurrencies;
	}
	
	@Override
	public String getNome() {
		return method;
	}
	
	@Override
	public ArrayList<LoopBlockInfo> getOuterLoops() {
		
		ArrayList<LoopBlockInfo> loops = new ArrayList<LoopBlockInfo>();
		
		String[] depths = loopInfo.split("\\)");
		
		for (int i = 1; i < depths.length; i++) {
			LoopBlockInfo info = new LoopBlockInfo();
			
			String[] complexity = depths[i].split(";");
			
			if(complexity[1].equals("ON")){
				info.setComplexity(Complexity.ON);
			}else if (complexity[1].equals("OLOGN")){
				info.setComplexity(Complexity.OLOGN);
			}
			loops.add(info);
		}
		
		return loops;		
	}
	
	public int getOuterLoopsDepth(){
		return loopInfo.split("�").length;
	}
	
	
	public void setConcreteType(String concreteType) {
		this.concreteType = concreteType;
	}
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}	
	
	public void setOcurrencies(Integer ocurrencies) {
		this.ocurrencies = ocurrencies;
	}
	public String getLoopInfo() {
		return loopInfo;
	}
	public void setLoopInfo(String loopInfo) {
		this.loopInfo = loopInfo;
	}
}
