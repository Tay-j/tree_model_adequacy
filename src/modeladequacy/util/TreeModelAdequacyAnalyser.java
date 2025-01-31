package modeladequacy.util;


import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import treestat2.statistics.TreeHeight;
import treestat2.statistics.TreeSummaryStatistic;
import beastfx.app.tools.Application;
import beastfx.app.util.OutFile;
import beastfx.app.util.TreeFile;
import beast.base.core.Description;
import beast.base.core.Input;
import beast.base.inference.Runnable;
import beast.base.core.Log;
import beast.base.evolution.tree.Tree;
import beast.base.parser.NexusParser;



@Description("Analyse set of trees created through Tree Model Adequacy")
public class TreeModelAdequacyAnalyser extends Runnable {
	// assume the root dir has subdirs called run0, run1, run2,...
	public Input<String> rootDirInput = new Input<>("rootdir", "root directory for storing individual tree files (default /tmp)", "/tmp");
	public Input<Integer> treeCountInput = new Input<>("nrOfTrees", "the number of trees to use, default 100", 100);
	public Input<TreeFile> treeFileInput = new Input<>("treefile", "file with original tree to test adequacy for");
	public Input<Tree> treeInput = new Input<>("tree", "original tree to test adequacy for");
	public Input<List<TreeSummaryStatistic<?>>> statsInput = new Input<>("statistic", "set of statistics that need to be produced", new ArrayList<>());
	public Input<OutFile> outFileInput = new Input<>("out","Output file, where logs are stored"); 
	
	
	
	List<TreeSummaryStatistic<?>> stats;
	
	@Override
	public void initAndValidate() {
		stats = statsInput.get();
		//stats.add(new TreeHeight());
	}

	
	
	@Override
	public void run() throws Exception {
		
		Tree origTree = getOriginalTree();
		
		// init stats
		Map<String, Object> origStats = new LinkedHashMap<>();
		for (TreeSummaryStatistic<?> stat : stats) {
			Map<String,?> map = stat.getStatistics(origTree);
			for (String name : map.keySet()) {
				origStats.put(name, map.get(name));
			}
		}
		
		
		String rootDir = rootDirInput.get();
		int treeCount = treeCountInput.get();
		Map<String, Object>[] treeStats = new LinkedHashMap[treeCount];

		for (int i = 0; i < treeCount; i++) {
			treeStats[i] = new LinkedHashMap<>();
			NexusParser parser = new NexusParser();
			File file = new File(rootDir + "/run" + i + "/output.tree");
			parser.parseFile(file);
			int lastTreeIndex = parser.trees.size();
			Tree tree = parser.trees.get(lastTreeIndex-1);
			int treeq = parser.trees.lastIndexOf(tree);
			for (TreeSummaryStatistic<?> stat : stats) {
				Map<String,?> map = stat.getStatistics(tree);
				for (String name : map.keySet()) {
					treeStats[i].put(name, map.get(name));
				}
			}
		}
		
		// report stats
		reportStats(origStats, treeStats);
		
		// log stats
		PrintStream out = System.out;
		if (outFileInput.get() != null && !outFileInput.get().getName().equals("[[none]]")) {
			out = new PrintStream(outFileInput.get());			
		}
		logStats(out, origStats, treeStats);
		
	}

	

	private void reportStats(Map<String, Object> origStats, Map<String, Object>[] treeStats) {
		List<String> labels = getLabels(origStats);
		int n = treeStats.length;
		
		Log.info("statistic               2.5%        5%          50%         95%         97.5%       observed    p-value");
		for (String label : labels) {
			Object stat = origStats.get(label);
			if (stat instanceof Double) {
				Double origStat = (Double) stat;
				Double [] stats = new Double[n];

				for (int i = 0; i < n; i++) {
					stats[i] = (Double) treeStats[i].get(label);
					
				}
				Arrays.sort(stats);
				double r2_5  = stats[(int)(2.5 * n / 100.0)];
				double r5    = stats[(int)(5 * n / 100.0)];
				double r50   = stats[(int)(50 * n / 100.0)];
				double r95   = stats[(int)(95 * n / 100.0)];
				double r97_5 = stats[(int)(97.5 * n / 100.0)];
				double pval = 0.0;
				for (int s=0; s < stats.length; s++){
					if(origStat > stats[s]){
						pval++;
					}
				}
				
				double pValue = pval / stats.length;
				if (label.length() > 23) {
					label = label.substring(0, 23);
				}				
				label = label + "                        ".substring(label.length());
				Log.info.print(label);
				Log.info.print(String.format("%6.3e",r2_5)  + "   ");
				Log.info.print(String.format("%6.3e",r5)    + "   ");
				Log.info.print(String.format("%6.3e",r50)   + "   ");
				Log.info.print(String.format("%6.3e",r95)   + "   ");
				Log.info.print(String.format("%6.3e",r97_5) + "   ");
				Log.info.print(String.format("%6.3e",origStat) + "   ");
				Log.info.print(String.format("%6.3e",pValue) + "\n");
				
			}			
		}
	}



	private void logStats(PrintStream out, Map<String, Object> origStats, Map<String, Object>[] treeStats) {
		List<String> labels = getLabels(origStats);
		
		out.print("state\t");
		for (String label : labels) {
			out.print(label+"\t");
		}
		out.println();
		
		// first line contains stats for original tree
		out.print("0\t");
		for (String label : labels) {
			Object o = origStats.get(label);
			out.print(o.toString() + "\t");
		}
		out.println();
		
		// print out stats for sampled trees
		for (int i = 0; i < treeStats.length; i++) {
			out.print((i+1) + "\t");
			Map<String, Object> stats = treeStats[i];
			for (String label : labels) {
				Object o = stats.get(label);
				out.print(o.toString() + "\t");
			}
			out.println();			
		}		
	}



	private List<String> getLabels(Map<String, Object> stats) {
		List<String> labels = new ArrayList<>();
		for (String label : stats.keySet()) {
			labels.add(label);
		}
		return labels;
	}



	private Tree getOriginalTree() throws IOException {
		if (treeInput.get() != null) {
			return treeInput.get();
		}
		NexusParser parser = new NexusParser();
		System.out.println("get original tree");
		parser.parseFile(treeFileInput.get());
		Tree tree = parser.trees.get(0);
		return tree;
	}

	public static void main(String[] args) throws Exception {
		new Application(new TreeModelAdequacyAnalyser(), "Tree Model Adeqaucy Analyser", args);
	}
}
