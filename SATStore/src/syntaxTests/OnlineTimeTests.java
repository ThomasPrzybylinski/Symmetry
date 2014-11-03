package syntaxTests;

import io.DimacsLoaderSaver;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.sat4j.core.VecInt;
import org.sat4j.specs.ISolver;

import task.formula.IdentityCNFCreator;
import task.formula.random.CNFCreator;
import task.symmetry.sparse.CNFSparseOnlineCNFDiversity;
import util.IntPair;
import util.IntegralDisjointSet;
import util.lit.LitSorter;
import formula.VariableContext;
import formula.simple.CNF;

public class OnlineTimeTests {

	static CNFCreator[] creators = new CNFCreator[]{
//		new IdentityCNFCreator("testcnf/uf250-1065/uf250-01.cnf"),
//		new IdentityCNFCreator("testcnf/Flat200-479/flat200-1.cnf"),
//		new IdentityCNFCreator("testcnf/logistics.a.cnf"),
	
//		new IdentityCNFCreator("testcnf/bmc-ibm-7.cnf","bmc-ibm-7.cnf",true), //bmcs don't work well with us
		
//		new QueensToSAT(8),
//		new IdentityCNFCreator("testcnf\\2bitmax_6.cnf"),
////		new IdentityCNFCreator("testcnf\\3blocks.cnf"),
//		new IdentityCNFCreator("testcnf\\4blocks.cnf"),
//		new IdentityCNFCreator("testcnf\\4blocksb.cnf"),
		new IdentityCNFCreator("testcnf\\uf250-1065\\uf250-01.cnf"),
//		new IdentityCNFCreator("testcnf\\uf250-1065\\uf250-02.cnf"),
//		new IdentityCNFCreator("testcnf\\uf250-1065\\uf250-03.cnf"),
//		new IdentityCNFCreator("testcnf\\uf250-1065\\uf250-04.cnf"),
//		new IdentityCNFCreator("testcnf\\uf250-1065\\uf250-05.cnf"),
		new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-1.cnf"),
//		new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-2.cnf"),
//		new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-3.cnf"),
//		new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-4.cnf"),
//		new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-5.cnf"),
//		new IdentityCNFCreator("testcnf\\sw100-1.cnf"), //currently p=2^-5
//		new IdentityCNFCreator("testcnf\\sw100-2.cnf"),
//		new IdentityCNFCreator("testcnf\\ais8.cnf"),
//		new IdentityCNFCreator("testcnf\\ais10.cnf"),
//		new IdentityCNFCreator("testcnf\\ais12.cnf"),
//		new IdentityCNFCreator("testcnf\\qg7-13.cnf"),
//		new IdentityCNFCreator("testcnf\\qg7-09.cnf"),
//		new IdentityCNFCreator("testcnf\\qg6-09.cnf"),
//		new IdentityCNFCreator("testcnf\\qg5-11.cnf"),
//		new IdentityCNFCreator("testcnf/bmc-ibm-2.cnf","bmc-ibm-2.cnf",true),
//		new IdentityCNFCreator("testcnf/bmc-ibm-1.cnf","bmc-ibm-1.cnf",true),
//		new IdentityCNFCreator("testcnf/bmc-ibm-10.cnf","bmc-ibm-10.cnf",true),


//
//		new IdentityCNFCreator("testcnf\\bw_large.c.cnf"),
//		new IdentityCNFCreator("testcnf\\logistics.a.cnf"),
//		new IdentityCNFCreator("testcnf\\logistics.b.cnf"),
//		new IdentityCNFCreator("testcnf\\logistics.c.cnf"),
//		new IdentityCNFCreator("testcnf\\logistics.d.cnf"),
//		
//		new IdentityCNFCreator("testcnf\\bw_large.d.cnf","bw_large.d.cnf",false),
//
//			new IdentityCNFCreator("testcnf\\ssa7552-038.cnf"), //To many syms?
//			new IdentityCNFCreator("testcnf\\ssa7552-158.cnf"),
//			new IdentityCNFCreator("testcnf\\ssa7552-159.cnf"),
//			new IdentityCNFCreator("testcnf\\ssa7552-160.cnf"),
//			new IdentityCNFCreator("testcnf/bmc-ibm-1.cnf","bmc-ibm-1.cnf",true),
//			new IdentityCNFCreator("testcnf\\g125.17.cnf"), //too slow
//			new IdentityCNFCreator("testcnf\\Flat200-479\\flat200-2.cnf"),
		
		////			new IdentityCNFCreator("testcnf\\bmc-ibm-2.cnf","bmc-ibm-2.cnf",true), //bmcs don't work well with us
		////			new IdentityCNFCreator("testcnf\\g125.17.cnf"), //too slow
		////			new IdentityCNFCreator("testcnf\\par16-5-c.cnf"), //One soln? Slow with sym breaking
		////			new IdentityCNFCreator("testcnf\\ii32d1.cnf"), //Unsat??????
		////			new IdentityCNFCreator("testcnf\\hanoi5.cnf"),
		////			new IdentityCNFCreator("testcnf\\ssa7552-038.cnf"), //To many syms?






	};


	public static void main(String[] args) throws Exception {
		final int setSize = 1000;
		final long timeout = 6000000;

		//		private int numSets = 100;

		Random rand = new Random();
		System.out.println("\t Reg\t Globl\t GlobB\t Local\t Reg\t Glob \t GlobB \t Local");

		for(int k = 0; k < creators.length; k++) {
			long[] timeRes = new long[4];
			int[] sizeRes = new int[4];
			long[] solverTime = new long[4];
			VariableContext context = new VariableContext();
			CNFCreator creat = creators[k];

			CNF orig = creat.generateCNF(context);
			ISolver s = orig.getSolverForCNF();
//			orig = removeObvEqVars(orig);
//			orig = removeEqVars(orig,creat);
//			orig = null;

			long start = System.currentTimeMillis();
			while(s.isSatisfiable()) {
				s.addClause(new VecInt(getRejection(s.model())));
				sizeRes[0]++;
				if(sizeRes[0] == setSize || ((System.currentTimeMillis() - start) > timeout)) {
					break;
				}
			}
			timeRes[0] = 0;//
			solverTime[0] = (System.currentTimeMillis()-start)*1000000;
			s.reset();
			s = null;

			System.out.print(creat +"\t");

			//This one finds globally assymetric models
			CNFSparseOnlineCNFDiversity globMode = new CNFSparseOnlineCNFDiversity(orig);
			globMode.setMaxSize(setSize);
			globMode.setTimeOut(timeout);
			globMode.setUseLocalSymBreak(false);
			globMode.setUseGlobalSymBreak(false);
			globMode.setGlobMode(true);


			start = System.currentTimeMillis();
			List<int[]> ret;// = globMode.getDiverseSet();
//			timeRes[1] = globMode.getTotalSymTime();//System.currentTimeMillis() - start;
//			sizeRes[1] = ret.size();
//			solverTime[1] = globMode.getTotalSolverTime();
			globMode = null;


			CNFSparseOnlineCNFDiversity lowBreak = new CNFSparseOnlineCNFDiversity(orig);
			lowBreak.setMaxSize(setSize);
			lowBreak.setTimeOut(timeout);
//			lowBreak.setUseGlobalSymBreak(false);
//			lowBreak.setUseLocalSymBreak(false);
			lowBreak.setBreakFast(true);

			start = System.currentTimeMillis();
//			ret = lowBreak.getDiverseSet();
//			timeRes[2] = lowBreak.getTotalSymTime();//System.currentTimeMillis() - start;
//			sizeRes[2] = ret.size();
//			solverTime[2] = lowBreak.getTotalSolverTime();
			lowBreak = null;


			CNFSparseOnlineCNFDiversity all = new CNFSparseOnlineCNFDiversity(orig);
			all.setMaxSize(setSize);
			all.setTimeOut(timeout);

			start = System.currentTimeMillis();
			ret = all.getDiverseSet();
			timeRes[3] = all.getTotalSymTime();//System.currentTimeMillis() - start;
			sizeRes[3] = ret.size();
			solverTime[3] = all.getTotalSolverTime();
			all = null;

			for(int i = 0; i < timeRes.length; i++) {
				System.out.printf("%8d\t ",timeRes[i]);
			}

			for(int i = 0; i < sizeRes.length; i++) {
				System.out.printf("%8d\t ",sizeRes[i]);
			}
			
			for(int i = 0; i < solverTime.length; i++) {
				System.out.printf("%8d\t ",solverTime[i]/1000000);
			}
			System.out.println();

			creators[k] = null;
		}

	}

	private static CNF removeObvEqVars(CNF orig) {
//		orig = orig.unitPropagate();
		IntegralDisjointSet equive = new IntegralDisjointSet(-orig.getContext().size(),orig.getContext().size());
		HashSet<IntPair> seenPairs = new HashSet<IntPair>();
		
		for(int[] i : orig.getClauses()) {
			if(i.length == 2) {
				seenPairs.add(new IntPair(i[0],i[1]));
				int[] nextPair = new int[]{-i[0],-i[1]};
				LitSorter.inPlaceSort(nextPair);
				if(seenPairs.contains(new IntPair(nextPair[0],nextPair[1]))) {
					equive.join(i[0],-i[1]);
					equive.join(-i[0],i[1]);
				}
			}
		}

		List<int[]> newCl = new ArrayList<int[]>(orig.size());
		CNF ret = new CNF(new VariableContext());
		for(int[] i : orig.getClauses()) {
			int[] toAdd = new int[i.length];
			for(int k = 0; k < i.length; k++) {
				toAdd[k] = equive.getRootOf(i[k]);
			}
			LitSorter.inPlaceSort(toAdd);
			int numRem = 0;
			boolean[] rem = new boolean[toAdd.length];
			for(int k = 0; k < toAdd.length; k++) {
				if(numRem == -1) break;
				if(rem[k]) continue;
				for(int j = k+1; j < toAdd.length; j++) {
					if(rem[j]) continue;
					if(toAdd[j] == toAdd[k]) {
						rem[j] = true;
						numRem++;
					} else if(toAdd[j] == -toAdd[k]) {
						numRem = -1;
						break;
					}
				}
			}
			
			if(numRem == -1) continue;
			
			if(numRem > 0) {
				int[] temp = new int[toAdd.length-numRem];
				int index = 0;
				for(int k = 0; k < toAdd.length; k++) {
					if(!rem[k]) {
						temp[index] = toAdd[k];
						index++;
					}
				}
				toAdd = temp;
			}
			
			newCl.add(toAdd);
		}
		
		ret.fastAddAll(newCl);
		ret.sort();
		ret = ret.unitPropagate();
		ret = ret.trySubsumption();
		ret = ret.squeezed();
		ret.sort();
		return ret;
	}
	
	private static CNF removeEqVars(CNF orig, CNFCreator creat) throws Exception {
		//orig = orig.unitPropagate();
	
		int[] varToVar = new int[orig.getContext().size()+1];
		
		for(int k = 0; k < varToVar.length; k++) {
			varToVar[k] = k;
		}
		int numEquiv = 0;
		for(int k = 1; k < orig.getContext().size(); k++) {
			if(varToVar[k] != k) continue;
			for(int j = k+1; j < orig.getContext().size(); j++) {
				if(varToVar[j] != j) continue;
				ISolver solve = orig.getSolverForCNF(true);
				solve.addClause(new VecInt(new int[]{k,j}));
				solve.addClause(new VecInt(new int[]{-k,-j}));
				
				if(!solve.isSatisfiable()) {
					solve.reset();
					varToVar[j] = k;
					numEquiv++;
//					System.out.println(numEquiv);
					continue;
				}
				solve.reset();
				
				solve = orig.getSolverForCNF(true);
				solve.addClause(new VecInt(new int[]{k,-j}));
				solve.addClause(new VecInt(new int[]{-k,j}));
				
				if(!solve.isSatisfiable()) {
					numEquiv++;
//					System.out.println(numEquiv);
					varToVar[j] = -k;
				}
				solve.reset();
			}
		}
		System.out.println(numEquiv);
		List<int[]> newCl = new ArrayList<int[]>(orig.size());
		CNF ret = new CNF(new VariableContext());
		for(int[] i : orig.getClauses()) {
			int[] toAdd = new int[i.length];
			for(int k = 0; k < i.length; k++) {
				int lit = i[k];
				int var = Math.abs(i[k]);
				toAdd[k] = varToVar[var]*(var/lit);
			}
			LitSorter.inPlaceSort(toAdd);
			int numRem = 0;
			boolean[] rem = new boolean[toAdd.length];
			for(int k = 0; k < toAdd.length; k++) {
				if(numRem == -1) break;
				if(rem[k]) continue;
				for(int j = k+1; j < toAdd.length; j++) {
					if(rem[j]) continue;
					if(toAdd[j] == toAdd[k]) {
						rem[j] = true;
						numRem++;
					} else if(toAdd[j] == -toAdd[k]) {
						numRem = -1;
						break;
					}
				}
			}
			
			if(numRem == -1) continue;
			
			if(numRem > 0) {
				int[] temp = new int[toAdd.length-numRem];
				int index = 0;
				for(int k = 0; k < toAdd.length; k++) {
					if(!rem[k]) {
						temp[index] = toAdd[k];
						index++;
					}
				}
				toAdd = temp;
			}
			
			newCl.add(toAdd);
		}
		
		ret.fastAddAll(newCl);
		ret.sort();
		ret = ret.unitPropagate();
		ret = ret.trySubsumption();
		ret = ret.squeezed();
		ret.sort();
		
		if(creat instanceof IdentityCNFCreator) {
			IdentityCNFCreator cr = (IdentityCNFCreator)creat;
			DimacsLoaderSaver.saveDimacs(new PrintWriter(cr.getPath()+".reduced.cnf"),ret,"Reduced version of " + cr.getPath());
		}
		
		return ret;
	}

	private static int[] getRejection(int[] firstModel) {
		int[] i = new int[firstModel.length];

		for(int k = 0; k < i.length; k++) {
			i[k] = -firstModel[k];
		}

		return i;
	}

}