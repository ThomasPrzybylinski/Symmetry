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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.sat4j.minisat.core.IntQueue;

import task.symmetry.RealSymFinder;
import task.symmetry.SmallerIsomorphFinder;
import task.symmetry.local.LocalSymClauses;
import util.IntPair;
import util.lit.DirectedLitGraph;
import util.lit.LitSorter;
import workflow.graph.ReportableEdgeAddr;

public class DifferentAllLocalSymAddr extends ReportableEdgeAddr {
	private volatile int iters;
	private int numVars;
	private int numModels;
	private volatile long propTime = 0;

	private LatticePart root;
	
	private VariableContext context;
//	private ArrayList<List<LatticePart>> latticeLevels;

	private boolean checkFirstInLocalOrbit = false;
	private boolean checkLitGraph = true;
	private boolean checkFullGlobal = false;
	private boolean checkFullLocalPath = true;
	private LocalSymClauses raClauses; //Random access clauses
	private SmallerIsomorphFinder iso = new SmallerIsomorphFinder();

	private LiteralPermutation varID;
	private LiteralPermutation modID;

	private boolean checkInterrupt = false;
	
	private IntPair[][] cachedPairs;

	public DifferentAllLocalSymAddr() {}


	public DifferentAllLocalSymAddr(boolean checkFirstInLocalOrbit,
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
		this.context = orig.getContext();
		propTime = 0;
		List<int[]> representatives = orig.getClauses();
		iters = 1; //At least global
		numModels = orig.size();

		cachedPairs = new IntPair[numModels][numModels];
		
		for(int k = 0; k < cachedPairs.length; k++) {
			for(int i = 0; i < cachedPairs[k].length; i++) {
				cachedPairs[k][i] = new IntPair(k+1,i+1);
			}
		}
		
		
		ClauseList globalModels = new ClauseList(new VariableContext());


		globalModels.addAll(representatives);

		raClauses = new LocalSymClauses(globalModels);
		

		numVars = globalModels.getContext().size();

		varID = new LiteralPermutation(numVars);
		modID = new LiteralPermutation(numModels);

//		latticeLevels = new ArrayList<List<LatticePart>>(numVars+1);
//
//		for(int k = 0; k < numVars+1; k++) {
//			latticeLevels.add(new ArrayList<LatticePart>());//new LinkedList<LatticePart>());
//		}

		LocalSymClauses clauses = new LocalSymClauses(globalModels);

		RealSymFinder globalFinder = new RealSymFinder(globalModels);
		LiteralGroup globalSyms = globalFinder.getSymGroup();

		LiteralGroup modelGlobSyms = clauses.getModelGroup(globalSyms);

		DirectedLitGraph lit = new DirectedLitGraph(globalModels.getContext().size());
		lit.push(new PairSchreierVector(globalSyms,modelGlobSyms));
		//		System.out.println("G:"+globalSyms);
		//		System.out.println(modelGlobSyms);

		int[] canonical = clauses.getCanonicalInter(new int[]{});
		
//		System.out.println(Arrays.toString(new int[]{}));
//		System.out.println(Arrays.toString(canonical));
//		System.out.println(globalSyms.toString(context));

		LatticePart globLat = new LatticePart(canonical,modelGlobSyms,globalSyms);

//		latticeLevels.get(0).add(globLat);
		root = globLat;

		LinkedList<LocalInfo> info = new  LinkedList<LocalInfo>();
		info.add(new LocalInfo(globLat));


		generateNext(g,clauses,lit,info,new int[]{},canonical);


		long propStart = System.currentTimeMillis();

//		List<IntPair> edges = getAllEdges();
		computeIso(root);
		for(IntPair p : root.getPairs()) {
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
			boolean keepShortcut = true;
			if((prevFilter.length > 0 && Math.abs(prevFilter[prevFilter.length-1]) > Math.abs(next)) 
					||
					lexLoserPath
					) {
				//If nextCanon adds items below 'next', we know we've already seen it somewhere.
				//				sc = new Shortcut(null,null,null); //
				sc = getShortcut(nextCanon,varID,modID);

				//We will never traverse this shortcut when making new shortcuts
				//so it's ok to get the edges and discard it
				keepShortcut = false;

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
				if(keepShortcut) {
					info.getLast().lp.addChild(next,sc);
				}
				for(IntPair p : sc.part.getPairs()) {
					IntPair newPair = p.applySort(sc.modPer); 
					info.getLast().lp.getPairs().add(getCachedPair(newPair));
				}
			}

			if(sc == null) {
				findSyms(g,nextFilter, nextCanon, clauses,litGraph,info);
			}
			clauses.pop();
		}

		info.getLast().lp.varGroup=null;
	}




	private void findSyms(PossiblyDenseGraph<int[]> g,int[] filter, int[] canonFilter, LocalSymClauses clauses,
			DirectedLitGraph litGraph, LinkedList<LocalInfo> info) {
		iters++;

//				System.out.println(Arrays.toString(filter));
//				System.out.println(Arrays.toString(canonFilter));

		ClauseList cl = clauses.getCurList(false);
		int numModels = cl.getClauses().size();
		
		LocalInfo parentInfo = info.getLast();

		if(numModels > 1) {

			RealSymFinder finder = new RealSymFinder(cl);
			finder.addKnownSubgroup(parentInfo.getSyms().getStabSubGroup(filter[filter.length-1]).reduce());
			LiteralGroup syms = finder.getSymGroup();//.reduce();


//			System.out.println(syms.toString(context));
			LiteralGroup modelGroup  = null;
			modelGroup = clauses.getModelGroup(syms);

			LatticePart latP = new LatticePart(canonFilter,modelGroup,syms);


//			latticeLevels.get(canonFilter.length).add(latP);

			//			System.out.println(Arrays.toString(canonFilter));
			//			System.out.println(syms);
			//			System.out.println();
			//			System.out.println(modelGroup.reduce());

			litGraph.push(new PairSchreierVector(syms,modelGroup));

			parentInfo.lp.addChild(filter[filter.length-1],new Shortcut(syms.getId(),modelGroup.getId(),latP));

			info.addLast(new LocalInfo(latP));



			if(numModels > 2) {
				generateNext(g,clauses,litGraph,info,filter,canonFilter);
			}

			computeIso(info.getLast().lp);
//			latticeLevels.get(latP.filter.length).add(latP);

			info.pollLast();
			
			parentInfo.lp.getPairs().addAll(latP.getPairs());
			
			//Save some memory
//			pastInfo.lp.varGroup = null;
//			//No longer need var and model syms to match up
//			pastInfo.lp.modelGroup = pastInfo.lp.modelGroup.reduce();

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

		while(!Arrays.equals(lookingFor,cur.filter)) {
			debug.add(cur);
			int nextInt = lookingFor[index];
			Shortcut sc = cur.children.get(nextInt);
			if(sc.litPerm.isId()) {
				cur = sc.part;

				index = calibrateIndex(cur, lookingFor, index);

			} else {
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

//	private List<IntPair> getAllEdges() {
//		for(int k = latticeLevels.size() - 1; k >= 0; k--) {
//			for(LatticePart lp : latticeLevels.get(k)) {
//				computeIso(lp);
//			}
//
//			//			if(k != 0) {
//			//				latticeLevels.get(k).clear();
//			//			}
//		}
//
//		LinkedList<IntPair> allPairs = new LinkedList<IntPair>();
//
//		allPairs.addAll(root.pairs);
//
//
//
//		latticeLevels.clear();
//
//		return allPairs;
//	}

	private void computeIso(LatticePart lp) {
//		SchreierVector modVec = new SchreierVector(lp.modelGroup);

//		LinkedList<IntPair> toCompute = new LinkedList<IntPair>();
//		toCompute.addAll(lp.getPairs());
		
//		for(Shortcut sc : lp.children.values()) {
//			for(IntPair p : sc.part.getPairs()) {
//				IntPair newPair = p.applySort(sc.modPer); 
//				newPair = getCachedPair(newPair);
//				lp.getPairs().add(newPair);
//				toCompute.add(newPair);
//			}
//		}
		
		//Compute 
//		while(!toCompute.isEmpty()) {
//			IntPair pair = toCompute.poll();
//
//			for(LiteralPermutation p : lp.modelGroup.getGenerators()) {
//				IntPair newP = pair.applySort(p);
//				newP = getCachedPair(newP);
//				if(!lp.getPairs().contains(newP)) {
//					lp.getPairs().add(newP);
//					toCompute.push(newP);
//				}
//			}
//		}
		
		IntQueue toCompute = new IntQueue();
		toCompute.ensure(lp.modelGroup.size()+1);
		int[] orbits = new int[lp.modelGroup.size()+1];
		int[] localOrbit = new int[lp.modelGroup.size()+1];
		
		for(int k = 1; k < orbits.length; k++) {
			if(orbits[k] != 0) continue;
			
//			int rep = modVec.getRep(k);

			toCompute.insert(k);
			orbits[k] = k;
			localOrbit[0] = k;
			int localOrbitIndex = 1;
		
			//Compute orbit of k
			while(toCompute.size() > 0) {
				int i = toCompute.dequeue();
				for(LiteralPermutation perm : lp.modelGroup.getGenerators()) {
					int image = perm.imageOf(i);
					if(orbits[image] == 0) {
						orbits[image] = k;
						localOrbit[localOrbitIndex] = image;
						localOrbitIndex++;
						toCompute.insert(image);
					}
				}
			}
			
			//use the orbit to create edges
			Arrays.sort(localOrbit,0,Math.min(localOrbit.length-1,localOrbitIndex));
			for(int i = 0; i < localOrbitIndex; i++) {
				for(int j = i+1; j < localOrbitIndex; j++) {
					lp.getPairs().add(getCachedPair(localOrbit[i],localOrbit[j]));
				}
			}
			Arrays.fill(localOrbit,0,Math.min(localOrbit.length-1,localOrbitIndex),0);
			
//			for(int i = k+1; i <= modVec.getNumVars(); i++) {
//				if(modVec.sameOrbit(k,i)) {
////				if(modVec.getRep(i)==rep) {
//					lp.getPairs().add(getCachedPair(k,i));
//				}
//			}
		}
		
		lp.finishPairs();
		//for memory reasons:
//		lp.children = null;
		lp.modelGroup = null;
		lp.varGroup = null;

	}

	public long getPropogationTime() {
		return propTime;
	}

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
	
	private IntPair getCachedPair(int i1, int i2) {
		return cachedPairs[i1-1][i2-1];
	}
	
	private IntPair getCachedPair(IntPair pair) {
		return cachedPairs[pair.getI1()-1][pair.getI2()-1];
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
		private ArrayList<IntPair> pairsAr;
		public LiteralGroup varGroup;
		//		public LiteralGroup fullGroup;

		public LatticePart(int[] filter, LiteralGroup autoGroup, LiteralGroup varGroup) {
			super();
			this.filter = filter;
			this.modelGroup = autoGroup;
			this.varGroup = varGroup;
			children = new TreeMap<Integer, Shortcut>();
			pairs = new TreeSet<IntPair>();
			pairsAr = null;
		}

		public Collection<IntPair> getPairs() {
			if(pairsAr == null) {
				return pairs;
			} else {
				return pairsAr;
			}
		}
		
		public void finishPairs() {
			pairsAr = new ArrayList<IntPair>(pairs.size());//;
			for(IntPair i : pairs) {
				pairsAr.add(i);//getCachedPair(i));
			}
//			pairsAr.addAll(pairs);
			pairs = null;
					
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


		public LocalInfo(LatticePart lp) {
			super();
			this.lp = lp;
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
