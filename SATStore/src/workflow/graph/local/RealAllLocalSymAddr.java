package workflow.graph.local;

import formula.VariableContext;
import formula.simple.ClauseList;
import graph.PossiblyDenseGraph;
import group.LiteralGroup;
import group.LiteralPermutation;
import group.PairSchreierVector;
import group.SchreierVector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import task.symmetry.ExperimentalSmallerIsomorph;
import task.symmetry.RealSymFinder;
import task.symmetry.local.LocalSymClauses;
import util.IntPair;
import util.lit.DirectedLitGraph;
import util.lit.LitSorter;
import util.lit.LitsMap;
import util.lit.LocalInterpComp;
import workflow.graph.ReportableEdgeAddr;

public class RealAllLocalSymAddr extends ReportableEdgeAddr {
	private volatile int iters;
	private int numComp;
	private int numVars;
	private int numModels;
	private volatile long propTime = 0;

	private static LocalInterpComp interMeasuer = new LocalInterpComp();

	private LatticePart root;
	private ArrayList<List<LatticePart>> latticeLevels;
	private LitsMap<LatticePart> lattice;

	private boolean checkFirstInLocalOrbit = false;
	private boolean checkLitGraph = true;
	private boolean checkFullGlobal = false;
	private boolean checkFullLocalPath = true;
	private LocalSymClauses raClauses; //Random access clauses
	private ExperimentalSmallerIsomorph iso = new ExperimentalSmallerIsomorph();
	
	private LiteralPermutation varID;
	private LiteralPermutation modID;
	
	private boolean checkInterrupt = false;

	public RealAllLocalSymAddr() {}


	public RealAllLocalSymAddr(boolean checkFirstInLocalOrbit,
			boolean checkLitGraph, boolean checkFullGlobal,
			boolean checkFullLocalPath) {
		super();
		this.checkFirstInLocalOrbit = checkFirstInLocalOrbit;
		this.checkLitGraph = checkLitGraph;
		this.checkFullGlobal = checkFullGlobal;
		this.checkFullLocalPath = checkFullLocalPath;
	}


	@Override
	public void addEdges(PossiblyDenseGraph<int[]> g, ClauseList orig) {
		propTime = 0;
		List<int[]> representatives = orig.getClauses();
		iters = 1; //At least global
		numComp = 1;
		numModels = orig.size();
		
		ClauseList globalModels = new ClauseList(new VariableContext());
		

		globalModels.addAll(representatives);
		
		raClauses = new LocalSymClauses(globalModels);

		numVars = globalModels.getContext().size();
		
		varID = new LiteralPermutation(numVars);
		modID = new LiteralPermutation(numModels);

		latticeLevels = new ArrayList<List<LatticePart>>(numVars+1);

		for(int k = 0; k < numVars+1; k++) {
			latticeLevels.add(new ArrayList<LatticePart>());//new LinkedList<LatticePart>());
		}

		LocalSymClauses clauses = new LocalSymClauses(globalModels);

		RealSymFinder globalFinder = new RealSymFinder(globalModels);
		LiteralGroup globalSyms = globalFinder.getSymGroup();
		
		LiteralGroup modelGlobSyms = clauses.getModelGroup(globalSyms);
		
		DirectedLitGraph lit = new DirectedLitGraph(globalModels.getContext().size());
		lit.push(new PairSchreierVector(globalSyms,modelGlobSyms));
		//		System.out.println("G:"+globalSyms);
		//		System.out.println(modelGlobSyms);

		int[] canonical = clauses.getCanonicalInter(new int[]{});

		LatticePart globLat = new LatticePart(canonical,modelGlobSyms,globalSyms);

		latticeLevels.get(0).add(globLat);
		root = globLat;

		addEdges(g,clauses,modelGlobSyms);//,globLat);

		LinkedList<LocalInfo> info = new  LinkedList<LocalInfo>();
		info.add(new LocalInfo(globalFinder,globLat));


		generateNext(g,clauses,lit,info,new int[]{},canonical);


		long propStart = System.currentTimeMillis();
		
		List<IntPair> edges = getAllEdges();
		for(IntPair p : edges) {
			g.setEdgeWeight(p.getI1()-1,p.getI2()-1,0);
		}
		
		long propEnd = System.currentTimeMillis();
		propTime = propEnd-propStart;

		//		System.out.println(iters);
		//		System.out.println(numComp);
		//		System.out.print("1B: ");
		//		for(int k = 1; k < g.getNumNodes(); k++) {
		//			if(g.areAdjacent(0,k)) System.out.print((k+1) + " ");
		//		}
		//		
		//		System.out.println();

		//		latticeLevels.clear();
	}




	private void generateNext(PossiblyDenseGraph<int[]> g, LocalSymClauses clauses, DirectedLitGraph litGraph,
			LinkedList<LocalInfo> info, int[] prevFilter, int[] prevCanon) {
		if(checkInterrupt) {
			if(Thread.interrupted()) {
				throw new RuntimeException();
			}
		}

		Set<Integer> validLits = clauses.curUsefulLits();//clauses.curValidLits();//

		int largestInFilter = prevFilter.length == 0 ? 0 : prevFilter[prevFilter.length-1];

		LitsMap<Object> seenChildren = new LitsMap<Object>(numVars);

		for(int next : validLits) {
			int[] nextFilter = new int[prevFilter.length+1];
			System.arraycopy(prevFilter,0,nextFilter,0,prevFilter.length);
			nextFilter[nextFilter.length-1] = next;

			LitSorter.inPlaceSort(nextFilter);
			
			clauses.post();
			clauses.addCondition(next);

			int curNumModels = clauses.curValidModels();
			
			if(curNumModels <= 1) {
				clauses.pop();
				continue; //Irrelevant
			}
			
			int[] nextCanon = clauses.getCanonicalInter(nextFilter);
			


//			if(seenChildren.contains(nextCanon)) {
//				clauses.pop();
//				continue;								//A CONTINUE
//			} else {
//				seenChildren.put(nextCanon,null);
//			}

			TreeSet<Integer> allInNext = new TreeSet<Integer>();
			for(int i : nextCanon) {
				allInNext.add(i);
			}
			
			TreeSet<Integer> allInPrev = new TreeSet<Integer>();
			for(int i : prevCanon) {
				allInPrev.add(i);
			}


			boolean lexLoserPath = false;
			for(int i : allInNext) {
				if(!allInPrev.contains(i) && Math.abs(i) < Math.abs(next)) {
					lexLoserPath = true;
					break;
				} 
			}
			
			Shortcut sc = null;
			if((prevFilter.length > 0 && Math.abs(prevFilter[prevFilter.length-1]) > Math.abs(next)) 
					||
					lexLoserPath
					) {
				//If nextCanon adds items below 'next', we know we've already seen it somewhere.
				sc = getShortcut(nextCanon,varID,modID);
			} else {
				//Once we get here, we know that next is not constant on the previous interpretation 
				SchreierVector vec = new SchreierVector(info.getLast().getSyms());
				LiteralPermutation smallerPerm = null;
				LiteralPermutation assocModelPerm = null;

				if(checkFirstInLocalOrbit) {
					int rep = vec.getRep(next);

					if(rep != next) {
						//LiteralPermutation smallerOther = litGraph.validatePerm(nextFilter,raClauses);
					
						smallerPerm = vec.getPerm(next,rep);
						
						if(smallerPerm != null) {
							raClauses.setFilter(prevFilter);
							assocModelPerm = raClauses.getModelPart(smallerPerm);
						}
					}
				}
				
				if(smallerPerm == null && checkLitGraph) {
	
					
					//We use nextFilter because 'next' is the item we want to change, it can
					//only go lower.
					//TODO: set it up so that the litGraph also calculates the associated model permutation
					smallerPerm = litGraph.validatePerm(nextFilter,raClauses);
					
					if(smallerPerm != null) {
						assocModelPerm = litGraph.getValidateModelPerm();
					}
				}

				if(smallerPerm == null && checkFullGlobal) {
					LiteralGroup globalGroup = info.getFirst().getSyms();
					LocalInfo globInfo = info.getFirst();
//					smallerPerm = globInfo.getSymUtil().getPermForSmallerIfPossible(nextFilter,globalGroup);
//					smallerPerm = iso.getSmallerSubsetIfPossible(nextFilter,globalGroup);
					smallerPerm = iso.getSmallerSubsetIfPossible(nextCanon,globalGroup);
					
					if(smallerPerm != null) {
						raClauses.setFilter(globInfo.getFilter());
						assocModelPerm = raClauses.getModelPart(smallerPerm);
					}
				}

				if(smallerPerm == null && checkFullLocalPath) {
					Iterator<LocalInfo> iter = info.descendingIterator();
//					for(LocalInfo li : info) {
					while(iter.hasNext()) {
						LocalInfo li = iter.next();
						LiteralGroup group = li.getSyms();
//						smallerPerm =  li.getSymUtil().getPermForSmallerIfPossible(nextFilter,group);
//						smallerPerm = iso.getSmallerSubsetIfPossible(nextFilter,group);
						raClauses.setFilter(li.getFilter());
						
						smallerPerm = iso.getSmallerSubsetIfPossible(nextCanon,group,raClauses.curUsefulLits(),li.lp.modelGroup);
						if(smallerPerm != null) {
							
							assocModelPerm = raClauses.getModelPart(smallerPerm);
							break;
						}
					}
				}
				
				if(smallerPerm != null) {
					sc = getShortcut(smallerPerm.applySort(nextCanon),smallerPerm.inverse(), assocModelPerm.inverse());
				}
			}

			if(sc != null) {
				info.getLast().lp.addChild(next,sc);
			}
			
			if(sc == null) {
				findSyms(g,nextFilter, nextCanon, clauses,litGraph,info);
			}
			
			

			clauses.pop();
		}
	}




	private void findSyms(PossiblyDenseGraph<int[]> g,int[] filter, int[] canonFilter, LocalSymClauses clauses,
			DirectedLitGraph litGraph, LinkedList<LocalInfo> info) {
		iters++;

//		System.out.println(Arrays.toString(filter));
//		System.out.println(Arrays.toString(canonFilter));

		ClauseList cl = clauses.getCurList(false);
		int numModels = cl.getClauses().size();

		if(numModels > 1) {
			numComp++;

			RealSymFinder finder = new RealSymFinder(cl);
			finder.addKnownSubgroup(info.getLast().getSyms().getStabSubGroup(filter[filter.length-1]).reduce());
			LiteralGroup syms = finder.getSymGroup().reduce();


			//			System.out.println(syms);
			LiteralGroup modelGroup  = null;
			modelGroup = clauses.getModelGroup(syms);

			LatticePart latP = new LatticePart(canonFilter,modelGroup,syms);

			
			latticeLevels.get(canonFilter.length).add(latP);

//			System.out.println(Arrays.toString(canonFilter));
//			System.out.println(syms);
//			System.out.println();
//			System.out.println(modelGroup.reduce());

			litGraph.push(new PairSchreierVector(syms,modelGroup));
			
			info.getLast().lp.addChild(filter[filter.length-1],new Shortcut(syms.getId(),modelGroup.getId(),latP));

			info.addLast(new LocalInfo(finder,latP));
			
			

			if(numModels > 2) {
				generateNext(g,clauses,litGraph,info,filter,canonFilter);
			}

			//Save some memory
			LocalInfo pastInfo = info.pollLast(); 
			pastInfo.lp.varGroup = null;
			//No longer need var and model syms to match up
			pastInfo.lp.modelGroup = pastInfo.lp.modelGroup.reduce();

			litGraph.pop();
			
			

		} else if(numModels == 1 || numModels == 0) {
			iters--;
		}
	}

	private Shortcut getShortcut(int[] nextCanon,
			LiteralPermutation literalPermutation,
			LiteralPermutation modelPermutation) {
		
		LatticePart cur = root;
		int[] lookingFor = nextCanon;
		int index = 0;
		index = calibrateIndex(cur, lookingFor, index);
		
		LinkedList<LatticePart> debug = new LinkedList<LatticePart>();
		
		int numSym = 0;
		
		while(!Arrays.equals(lookingFor,cur.filter)) {
			debug.add(cur);
			int nextInt = lookingFor[index];
			Shortcut sc = cur.children.get(nextInt);
			if(sc.litPerm.isId()) {
				cur = sc.part;
				
				index = calibrateIndex(cur, lookingFor, index);
				
			} else {
				numSym++;
				cur = root;
				index = 0;
				lookingFor = sc.litPerm.inverse().applySort(lookingFor);
				index = calibrateIndex(cur, lookingFor, index);
				
				literalPermutation = sc.litPerm.compose(literalPermutation);
				modelPermutation = sc.modPer.compose(modelPermutation);
			}
		}
		
		if(modelPermutation.size() == 0) throw new RuntimeException();
		
		return new Shortcut(literalPermutation,modelPermutation,cur);
		
	}


	private int calibrateIndex(LatticePart cur, int[] lookingFor, int index) {
		for(int k = index; k < cur.filter.length; k++) {
			if(lookingFor[k] == cur.filter[k]) {
				index++;
			} else {
				break;
			}
		}
		return index;
	}

	private List<IntPair> getAllEdges() {
		for(int k = latticeLevels.size() - 1; k >= 0; k--) {
			for(LatticePart lp : latticeLevels.get(k)) {
				computeIso(lp);
			}

//			if(k != 0) {
//				latticeLevels.get(k).clear();
//			}
		}
		
		LinkedList<IntPair> allPairs = new LinkedList<IntPair>();
		
		allPairs.addAll(root.pairs);
		
		
		
		latticeLevels.clear();
	
		return allPairs;
	}

	private void computeIso(LatticePart lp) {
		SchreierVector modVec = new SchreierVector(lp.modelGroup);
		
		for(int k = 1; k <= modVec.getNumVars(); k++) {
			for(int i = k+1; i <= modVec.getNumVars(); i++) {
				if(modVec.sameOrbit(k,i)) {
					lp.pairs.add(new IntPair(k,i));
				}
			}
		}
		
		LinkedList<IntPair> toCompute = new LinkedList<IntPair>();
		
		for(Shortcut sc : lp.children.values()) {
			for(IntPair p : sc.part.pairs) {
				IntPair newPair = p.applySort(sc.modPer); 
				lp.pairs.add(newPair);
				toCompute.add(newPair);
			}
		}
		
		while(!toCompute.isEmpty()) {
			IntPair pair = toCompute.poll();

			for(LiteralPermutation p : lp.modelGroup.getGenerators()) {
				IntPair newP = pair.applySort(p);

				if(!lp.pairs.contains(newP)) {
					lp.pairs.add(newP);
					toCompute.push(newP);
				}
			}
		}
		
		//for memory reasons:
		lp.children = null;
		lp.modelGroup = null;
		lp.varGroup = null;
		
	}
	
	public long getPropogationTime() {
		return propTime;
	}


	private void addEdges(PossiblyDenseGraph<int[]> g, LocalSymClauses clauses, LiteralGroup modelGroup) {//, LatticePart p) {
		SchreierVector vec = new SchreierVector(modelGroup);

		//		for(int k = 0; k < g.getNumNodes(); k++) {
		//			for(int i = k+1; i < g.getNumNodes(); i++) {
		//				if(vec.sameOrbit(k+1,i+1)) {
		//					p.pairs.add(new IntPair(k+1,i+1));
		//					g.setEdgeWeight(k,i,0);
		//				}
		//			}
		//		}
	}


	//	private void setupLattice() {
	//		for(int k = 0; k < latticeLevels.size(); k++) {
	//			for(LatticePart p : latticeLevels.get(k)) {
	////								for(int i = k+1; i < latticeLevels.size(); i++) {
	//				int i = k+1;
	//				for(LatticePart p2 : latticeLevels.get(i)) {
	//					if(isSubset(p,p2)) {
	//						p2.addParent(p);
	//					}
	//				}
	////							}
	//			}
	//		}
	//	}



	//True is p is a subset of p2
	//	private boolean isSubset(LatticePart p, LatticePart p2) {
	//		int p2Ind = 0;
	//
	//		for(int k = 0; k < p.filter.length; k++) {
	//			int testLit = p.filter[k];
	//			int testVar = Math.abs(testLit);
	//			for(; p2Ind < p2.filter.length; p2Ind++) {
	//				int compareLit = p2.filter[p2Ind];
	//				int compareVar = Math.abs(compareLit);
	//
	//				if(testVar < compareVar) {
	//					return false;
	//				} else if(testVar == compareVar) {
	//					if(testLit == compareLit) {
	//						break;
	//					} else {
	//						return false;
	//					}
	//				}
	//
	//				if(p2Ind == p2.filter.length - 1) return false; //Did not find the kth literal
	//			}
	//		}
	//
	//		return true;
	//	}

	//	private void addAllIsoEdges(PossiblyDenseGraph<int[]> g) {
	//		for(int k = latticeLevels.size() - 1; k >= 0; k--) {
	//			for(LatticePart lp : latticeLevels.get(k)) {
	//				computeIso(lp);
	//			}
	//			
	//			if(k != 0) {
	//				latticeLevels.get(k).clear();
	//			}
	//		}
	//
	//		LatticePart head = latticeLevels.get(0).get(0);
	//
	//		for(IntPair pairs : getPairs(head)) {
	//			g.setEdgeWeight(pairs.getI1()-1,pairs.getI2()-1,0);
	//		}
	//
	//	}
	//	
	//	private void computeIso(LatticePart lp) {
	//		TreeSet<IntPair> seen = getPairs(lp);
	//
	//		seen.addAll(lp.getPairs());
	//
	//		if(lp != latticeLevels.get(0).get(0)) {
	//			lp.getPairs().clear();
	//			lp.getComputePairs().clear(); //For memory purposes
	//		}
	//
	//		for(LatticePart p : lp.getParents()) {
	//			p.getComputePairs().addAll(seen);
	//		}
	//
	//	}
	//	
	//	private TreeSet<IntPair> getPairs(LatticePart lp) {
	//		Set<IntPair> compPairs = lp.getComputePairs();
	//
	//		LinkedList<IntPair> toCompute = new LinkedList<IntPair>();
	//		toCompute.addAll(compPairs);
	//
	//		TreeSet<IntPair> seen = new TreeSet<IntPair>();
	//
	//		seen.addAll(toCompute);
	//
	//		while(!toCompute.isEmpty()) {
	//			IntPair pair = toCompute.poll();
	//
	//			for(LiteralPermutation p : lp.getAutoGroup().getGenerators()) {
	//				IntPair newP = pair.applySort(p);
	//
	//				if(!seen.contains(newP)) {
	//					seen.add(newP);
	//					toCompute.push(newP);
	//				}
	//			}
	//		}
	//		return seen;
	//	}

	public int getIters() {
		return iters;
	}

	public void setIters(int iters) {
		this.iters = iters;
	}

	public boolean isCheckFirstInLocalOrbit() {
		return checkFirstInLocalOrbit;
	}

	public void setCheckFirstInLocalOrbit(boolean checkFirstInLocalOrbit) {
		this.checkFirstInLocalOrbit = checkFirstInLocalOrbit;
	}

	public boolean isCheckQuickLocal() {
		return checkLitGraph;
	}

	//Note: This is more powerful than checking firstInLocalOrbit
	public void setCheckQuickLocal(boolean checkQuickLocal) {
		this.checkLitGraph = checkQuickLocal;
	}

	public boolean isCheckFullGlobal() {
		return checkFullGlobal;
	}

	public void setCheckFullGlobal(boolean checkFullGlobal) {
		this.checkFullGlobal = checkFullGlobal;
	}

	public boolean isCheckFullLocalPath() {
		return checkFullLocalPath;
	}

	//Note:This is more powerful than checkFullGlobal
	public void setCheckFullLocalPath(boolean checkFullLocalPath) {
		this.checkFullLocalPath = checkFullLocalPath;
	}

	@Override
	public boolean isSimple() {
		return true;
	}

	@Override
	public String toString() {
		return "Sym(" +checkFirstInLocalOrbit +", " + checkLitGraph +", " + checkFullGlobal +", " + checkFullLocalPath+")";
	}

	


	public boolean isCheckInterrupt() {
		return checkInterrupt;
	}

	public void setCheckInterrupt(boolean checkInterrupt) {
		this.checkInterrupt = checkInterrupt;
		iso.setCheckInterrupt(checkInterrupt);
	}




	private class Shortcut {
		private LiteralPermutation litPerm;
		private LiteralPermutation modPer;
		private LatticePart part;
		
		public Shortcut(LiteralPermutation litPerm, LiteralPermutation modPer,
				LatticePart part) {
			super();
			this.litPerm = litPerm;
			this.modPer = modPer;
			this.part = part;
		}
		
		
	}


	private	class LatticePart {
		private int[] filter;
		private Map<Integer, Shortcut> children;
		private LiteralGroup modelGroup;
		private Set<IntPair> pairs;
		public LiteralGroup varGroup;
//		public LiteralGroup fullGroup;

		public LatticePart(int[] filter, LiteralGroup autoGroup, LiteralGroup varGroup) {
			super();
			this.filter = filter;
			this.modelGroup = autoGroup;
			this.varGroup = varGroup;
			children = new TreeMap<Integer, Shortcut>();
			pairs = new TreeSet<IntPair>();
		}

		public void addChild(int next, Shortcut parent) {
			this.children.put(next, parent);
		}

		public String toString() {
			return "Lat: " + Arrays.toString(filter);
		}
	}

	static class LocalInfo {

		private LatticePart lp;

		private RealSymFinder symUtil;

		public LocalInfo(RealSymFinder symUtil, LatticePart lp) {
			super();
			this.symUtil = symUtil;
			this.lp = lp;
		}

		public RealSymFinder getSymUtil() {
			return symUtil;
		}
		public void setSymUtil(RealSymFinder symUtil) {
			this.symUtil = symUtil;
		}
		public LiteralGroup getSyms() {
			return lp.varGroup;
		}

		public int[] getFilter() {
			return lp.filter;
		}

		public LatticePart getLp() {
			return lp;
		}



	}



}

