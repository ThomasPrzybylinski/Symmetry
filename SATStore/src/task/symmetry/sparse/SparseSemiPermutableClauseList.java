package task.symmetry.sparse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeSet;

import util.lit.LitSorter;
import util.lit.LitsMap;
import formula.Conjunctions;
import formula.VariableContext;
import formula.simple.CNF;
import formula.simple.ClauseList;
import group.LiteralPermutation;

/*
 *  Ideas to make it more scalable for sym finding:
 *  	Have a "current partition" array. The partial permute functions will only change that array (plus the permuted boolean? Or permutation to 0 means "not permuted")
 *  	We also have a watched literal in each clause (preferably the last element). When it gets permuted, we check the clause
 *  it contains to see if all have been permuted. If yes, we return the fully permuted cause to check up.
 * 
 */

//Mostly unmodifiable
public class SparseSemiPermutableClauseList extends ClauseList {
	private class ClausePos {
		public int[] clause;
		public int clauseIndex;
		public int varIndex;

		public ClausePos(int[] clause,int cIndex, int vIndex) {
			this.clause = clause;
			this.clauseIndex = cIndex;
			this.varIndex = vIndex;
		}
	}

	private class PermuteStruct {
		int from;
		int to;

		public PermuteStruct(int from, int to) {
			this.from = from;
			this.to = to;
		}

		@Override
		public String toString() {
			return from + "->" + to;
		}


	}

	private int numVars;
	int curLevel = 0;
	int[] permutedLevel;
	int[] unPermutedLitsInClause;
	ClausePos[][] clausesWithVar; //var to list of clauses

	Stack<PermuteStruct> perms = new Stack<PermuteStruct>();

	public SparseSemiPermutableClauseList(Conjunctions cnf) {
		super(cnf);
		setupDataStructures();
	}

	public SparseSemiPermutableClauseList(VariableContext context) {
		super(context);
		setupDataStructures();
	}

	//WARNING: USES THE int[] OF CNF, SHOULD NOT BE MODIFIED
	public SparseSemiPermutableClauseList(ClauseList cnf) {
		this(cnf,false);
	}

	public SparseSemiPermutableClauseList(ClauseList cnf, boolean copy) {
		super(cnf.getContext());
		if(copy) {
			cnf = cnf.getCopy();
		}

		for(int[] clause : cnf.getClauses()) {
			clauses.add(clause.clone());
		}
		setupDataStructures();
	}

	private void setupDataStructures() {
		this.numVars = getContext().size();
		clausesWithVar = new ClausePos[getContext().size()+1][];
		permutedLevel = new int[getContext().size()+1];

		Arrays.fill(permutedLevel,-1);

		List<ArrayList<ClausePos>> temp = new ArrayList<ArrayList<ClausePos>>();

		temp.add(new ArrayList<ClausePos>());

		for(int k = 0; k < getContext().size();k++) {
			temp.add(new ArrayList<ClausePos>());
		}


		List<int[]> clauses = getClauses();
		this.sort();

		unPermutedLitsInClause = new int[clauses.size()];

		for(int k = 0; k < clauses.size(); k++) {
			int[] clause = clauses.get(k).clone();
			unPermutedLitsInClause[k] = clause.length;

			for(int i = 0; i < clause.length; i++) {
				int var = Math.abs(clause[i]);
				ClausePos toAdd = new ClausePos(clause,k,i);
				temp.get(var).add(toAdd);
			}
		}

		clausesWithVar[0] = new ClausePos[0];

		for(int k = 1; k < clausesWithVar.length; k++) {
			ArrayList<ClausePos> toCopy = temp.get(k);
			clausesWithVar[k] = toCopy.toArray(new ClausePos[toCopy.size()]);
		}
	}



	//returns clauses that have been fully permuted
	public List<int[]> partialPermute(int from, int to) {
		//To make lower calculations easier, we can assume from is positive
		if(from < 0) {
			from = Math.abs(from);
			to = -to;
		}

		if(permutedLevel[from] != -1) throw new IllegalStateException("Cannot permute twice on same variable");

		LinkedList<int[]> fullPermedClauses = new LinkedList<int[]>();

		int fromVar = from;
		ClausePos[] clauses = clausesWithVar[fromVar];

		for(ClausePos cPos : clauses) {
			int[] clause = cPos.clause;
			int cIndex = cPos.clauseIndex;
			int vIndex = cPos.varIndex;
//
			int i = Math.abs(clause[vIndex])/clause[vIndex];
			clause[vIndex] = to*i;

			unPermutedLitsInClause[cIndex]--;

			if(unPermutedLitsInClause[cIndex] == 0) {
				
				fullPermedClauses.add(LitSorter.newSortedClause(clause));
			}
		}
		permutedLevel[fromVar] = curLevel;
		perms.add( new PermuteStruct(from,to));
		return fullPermedClauses;
	}

	//Returns true if permutation seems consitent with a symmetry, false otherwise
	public boolean permuteAndCheck(int from, int to) {
		List<int[]> fullyPermutedClauses = partialPermute(from,to);
		LitsMap<Integer> cur = new LitsMap<Integer>(numVars);

		for(int[] cl : fullyPermutedClauses) {
			Integer freq = getClauseFreq(cl);

			if(freq != null) {

				Integer numLeft = cur.get(cl);

				if(numLeft == null) {
					cur.put(cl,freq-1);
				} else if(numLeft.intValue() >= 1) {
					cur.put(cl,numLeft-1);
				} else {
					return false;
				}

			} else {
				return false;
			}
		}

		Iterator<LitsMap<Integer>.LitNode> rawIter = cur.getRawIterator();

		while(rawIter.hasNext()) {
			LitsMap<Integer>.LitNode node = rawIter.next();
			if(node.getValue() > 0) {
				return false;
			}
		}


		return true;
	}
	
	public Integer getClauseFreq(int[] cl) {
		List<int[]> clauses = getClauses();
		int index = Collections.binarySearch(clauses,cl,compare);
		
		if(index >= 0) {
			int num = 1;
			for(int k = -1; k + index >= 0; k--) {
				if(Arrays.equals(cl,clauses.get(k+index))) {
					num++;
				} else {
					break;
				}
			}
			
			for(int k = 1; k + index < clauses.size(); k++) {
				if(Arrays.equals(cl,clauses.get(k+index))) {
					num++;
				} else {
					break;
				}
			}
			
			return num;
			
		} else {
			return null;
		}
		
	}

	public boolean isPermuted(int lit) {
		int var = Math.abs(lit);
		return permutedLevel[var] != -1;
	}

	public void post() {
		curLevel++;
	}

	public void pop() {
		if(curLevel <= 0) return; //Or throw exception?
		int numPermuted = 0;

		for(int k = 0; k < permutedLevel.length; k++) {
			int lvl = permutedLevel[k];
			if(lvl == curLevel) {
				numPermuted++;
			}
		}

		for(int k = 0; k < numPermuted; k++) {
			undoLastPerm();
		}

		curLevel--;
	}

	public void reset() {
		while(curLevel > 0) {
			pop();
		}
	}

	public void undoLastPerm() {
		PermuteStruct lastPerm = perms.pop();
		int from = lastPerm.to; //we are undoing, so to becomes from
		int to = lastPerm.from;

		if(from < 0) {
			from = Math.abs(from);
			to = -to;
		}

		int undoVar = Math.abs(to);
		ClausePos[] clauses = clausesWithVar[undoVar];

		for(ClausePos cPos : clauses) {
			int[] clause = cPos.clause;
			int cIndex = cPos.clauseIndex;
			int vIndex = cPos.varIndex;

			int i = Math.abs(clause[vIndex])/clause[vIndex];
			clause[vIndex] = to*i;

			unPermutedLitsInClause[cIndex]++;
		}
		permutedLevel[undoVar] = -1;
	}



	@Override
	public CNF permute(int[] permutation) {
		for(int b : permutedLevel) {
			if(b != -1) {
				throw new UnsupportedOperationException("Do not permute a CNF that has been partially permuted");
			}
		}
		return super.permute(permutation);
	}

	@Override
	public void addClause(int... vars) {
		throw new UnsupportedOperationException("SemiPermutableCNFs are meant to be constant at the moment");
		//super.addClause(vars);
	}

	@Override
	public List<int[]> getClauses() {
		return Collections.unmodifiableList(super.getClauses());
	}
	
}