package task.formula;

import task.formula.random.CNFCreator;
import task.translate.ConsoleDecodeable;
import formula.VariableContext;
import formula.simple.CNF;

public class PigeonHoleCreator implements CNFCreator, ConsoleDecodeable {
	private int pigeons;
	private int holes;
	
	public PigeonHoleCreator(int pigeons, int holdes) {
		super();
		this.pigeons = pigeons;
		this.holes = holdes;
	}

	public static CNF createPigeonHole(int numPigeons, int numHoles) {
		CNF ret = new CNF(new VariableContext());
		
		for(int k = 0; k < numPigeons; k++) {
			int initialVar = (k)*(numHoles)+1;

			int[] pigeonInOneHole = new int[numHoles];
			
			for(int i = 0; i < pigeonInOneHole.length; i++) {
				pigeonInOneHole[i] = initialVar+i;
				
				//A pigeon cannot be in more than 1 hole at once
				for(int l = i+1; l < numHoles; l++) {
					ret.addClause(-(initialVar+i),-(initialVar+l));
				}
			}
			
			ret.addClause(pigeonInOneHole);
			
			//Two pigeons cannot be in the same hole
			for(int j = k+1; j < numPigeons; j++) {
				int initialVar2 = (j)*(numHoles)+1;
				for(int l = 0; l < numHoles; l++) {
					ret.addClause(-(initialVar+l),-(initialVar2+l));
				}
			}
		}
		
		return ret;
	}

	@Override
	public CNF generateCNF(VariableContext context) {
		return createPigeonHole(pigeons,holes);
	}
	
	@Override
	public String consoleDecoding(int[] model) {
		StringBuilder sb = new StringBuilder();
		for(int k = 0; k < holes; k++) {
			sb.append('H').append(k).append(':');
			for(int i = 0; i < pigeons; i++) {
				int var = (i)*(holes)+k;
				if(model[var] > 0) {
					sb.append("P").append(i).append(' ');
				}
			}
			sb.append(ConsoleDecodeable.newline);
		}
		return sb.toString();
	}

}