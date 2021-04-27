package puzzlesolver;

import java.util.*;
import java.util.regex.*;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.*;
import java.nio.file.*;
import java.text.NumberFormat;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class PuzzleSolver {

	public static final String[] noteNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
	public static final String[] intervalNames = {"INVALID", "INVALID", "INVALID", "Minor 3rd", "Major 3rd", "Perfect 4th", "INVALID", "Perfect 5th", "Minor 6th", "Major 6th", "INVALID", "INVALID"};
	public static final int[] intervals = {3, 4, 5, 7, 8, 9};
	public static final boolean[] isInterval = {false, false, false, true, true, true, false, true, true, true, false, false};
	public static final int maxCombos = 479001600;
	public static NumberFormat numberFormat = NumberFormat.getInstance();
	
	public static void main(String[] args) {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch(Exception e) {} //It doesn't matter that much, so we can safely ignore it
		
		//Create our progress bar window
		JFrame frame = new JFrame("Interval Finder Progress");
		JProgressBar progressBar = new JProgressBar(0, maxCombos);
		progressBar.setStringPainted(true);
		progressBar.setString("Use the file chooser to create an output file in .csv format.");
		frame.add(progressBar);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setPreferredSize(new Dimension(Toolkit.getDefaultToolkit().getScreenSize().width/2, 75));
		frame.pack();
		frame.setResizable(true);
		frame.setLocationRelativeTo(null);
		frame.setVisible(true);
		
		//Create a new file chooser, starting at the user's home directory
		JFileChooser fileDialog = new JFileChooser(System.getProperty("user.home"));
		fileDialog.setDialogTitle("Create an output file...");
		fileDialog.setFileFilter(new FileNameExtensionFilter("Comma Separated Value Files (.csv)", "csv"));
		int fileChooserValue = fileDialog.showSaveDialog(frame);
		if(fileChooserValue != JFileChooser.APPROVE_OPTION) System.exit(0);
		
		String path = fileDialog.getSelectedFile().getAbsolutePath();
		//Use regex to test if the user's selected path already has a file extension, and if not, add a .csv extension
		if(!path.matches("[^\\.]*\\.[^\\.]*")) path += ".csv";
		
		int[][] solutions = solve(progressBar);
		
		OutputStream outputStream;
		try {//Create a simple File to see if we can open the specified output file
			File outputFile = new File(path);
			//If the output file doesn't exist, create it
			if(!outputFile.exists()) outputFile.createNewFile();
			//If we can't write to the output file, raise an exception
			if(!outputFile.canWrite()) throw new Exception("Cannot write to file");
			//Create an output stream for writing to our output file. TRUNCATE_EXISTING means we erase any existing contents, and CREATE means we create a new file if none exists.
			outputStream = Files.newOutputStream(FileSystems.getDefault().getPath(outputFile.getAbsolutePath()), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
			writeSolutions(outputStream, solutions, progressBar, path);
			//And finally close our output stream to prevent a memory leak.
			outputStream.close();
		} catch(Exception e) {
			System.err.println("IOException trying to read file: "+args[0]);
			e.printStackTrace();
			System.exit(-1);
		}
	}
	
	//This code writes all found solutions to a .csv file for importing to excel or sheets
	public static void writeSolutions(OutputStream output, int[][] solutions, JProgressBar progressBar, String path) throws IOException {
		//Update the progress bar
		String formattedSolutionCount = numberFormat.format(solutions.length);
		progressBar.setValue(0);
		progressBar.setMaximum(solutions.length);
		progressBar.setString("Saving: 0/"+formattedSolutionCount);
		//Prepare by getting bytes of all note and interval names
		byte[][] noteBytes = new byte[12][];
		byte[][] intervalBytes = new byte[6][];
		for(int i = 0; i < 12; i++) noteBytes[i] = (noteNames[i]+",").getBytes();
		for(int i = 0; i < 6; i++) intervalBytes[i] = (intervalNames[i]+",").getBytes();
		
		//The .csv file header
		output.write("Note 1, Note 2, Note 3, Note 4, Note 5, Note 6, Note 7, Note 8, Note 9, Note 10, Note 11, Note 12, Interval 1, Interval 2, Interval 3, Interval 4, Interval 5, Interval 6, Note Name 1, Note Name 2, Note Name 3, Note Name 4, Note Name 5, Note Name 6, Note Name 7, Note Name 8, Note Name 9, Note Name 10, Note Name 11, Note Name 12, Interval Name 1, Interval Name 2, Interval Name 3, Interval Name 4, Interval Name 5, Interval Name 6,\n".getBytes());
		for(int i = 0; i < solutions.length; i++) {
			//Write note IDs
			for(int k = 0; k < 12; k++) {
				String str = solutions[i][k] + ",";
				output.write(str.getBytes());
			}
			//Write interval IDs
			for(int k = 0; k < 6; k++) {
				String str = (solutions[i][k*2+1] - solutions[i][k*2])+",";
				output.write(str.getBytes());
			}
			//Write note names
			for(int k = 0; k < 12; k++) {
				output.write(noteBytes[solutions[i][k]]);
			}
			//Write interval names
			for(int k = 0; k < 6; k++) {
				output.write(intervalBytes[(solutions[i][k*2+1] - solutions[i][k*2])]);
			}
			output.write('\n');
			
			//Update the progress bar
			progressBar.setValue(i);
			progressBar.setString("Saving: "+numberFormat.format(i+1)+"/"+formattedSolutionCount);
		}
		
		//Update the progress bar
		progressBar.setString("Done! Check "+path+" for a .csv file with solutions. You can now close this window.");
	}
	
	//This is the code that actually solves the puzzle, and outputs a list of solutions
	public static int[][] solve(JProgressBar progressBar) {
		String formattedMaxCombos = numberFormat.format(maxCombos);
		ArrayList<int[]> solutions = new ArrayList<int[]>();
		
		int[] swaplist = {11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
		int solutionCount = 0;
		long startTime = System.currentTimeMillis();
		//We declare these outside of the loop so that they don't get re-initialized over and over, which is expensive, however we have to reset them manually
		boolean[] picked = new boolean[12];
		boolean[] intervalsUsed = new boolean[11];
		int[] currentNotes = new int[12]; //We don't have to reset this one because it gets overwritten every iteration
		
		for(int i = 0; i < maxCombos; i++) {
			//Update the progress bar
			progressBar.setValue(i);
			progressBar.setString("Finding solutions: "+numberFormat.format(i)+"/"+formattedMaxCombos+" ("+solutionCount+" solutions)");
			
			
			//Reset our picked and intervalsUsed arrays
			picked[0] = false;
			picked[1] = false;
			picked[2] = false;
			picked[3] = false;
			picked[4] = false;
			picked[5] = false;
			picked[6] = false;
			picked[7] = false;
			picked[8] = false;
			picked[9] = false;
			picked[10] = false;
			picked[11] = false;
			intervalsUsed[0] = false;
			intervalsUsed[1] = false;
			intervalsUsed[2] = false;
			intervalsUsed[3] = false;
			intervalsUsed[4] = false;
			intervalsUsed[5] = false;
			intervalsUsed[6] = false;
			intervalsUsed[7] = false;
			intervalsUsed[8] = false;
			intervalsUsed[9] = false;
			intervalsUsed[10] = false;
			incrementSwaplist(swaplist);
			
			//This generates a unique combination of all 12 notes using our current swaplist
			//Given incrementing values for our swaplist, this will iterate all 479,001,600 combinations of 12 notes
			picked[swaplist[0]] = true;
			currentNotes[0] = swaplist[0];
			for(int k = 1; k < swaplist.length; k++) {
				currentNotes[k] = 0;
				for(int j = swaplist[k]; (j > 0) | (picked[currentNotes[k]]); j--) { //We don't want to stop on notes we already picked if j = 0, hence the multiple boolean statements
					if(picked[currentNotes[k]]) j++; //This makes us skip over notes we already picked
					currentNotes[k]++;
				}
				picked[currentNotes[k]] = true;
			}
			
			//This loop checks every pair of notes to see what interval it uses, and makes sure no interval is used more than once
			boolean hasFailedInterval = false;
			for(int k = 0; k < currentNotes.length/2; k++) {
				//The distance between the two notes in our pair is our interval
				int interval = currentNotes[k*2+1]-currentNotes[k*2];
				//We don't count negative intervals because this balloons our output file size with hundreds of thousands of duplicate solutions
				if(interval < 0 || !isInterval[interval] || intervalsUsed[interval]) {
					hasFailedInterval = true;
					break;
				}
				intervalsUsed[interval] = true;
			}
			if(hasFailedInterval) continue;
			
			//This loop checks if we've used every interval we need to
			for(int k = 0; k < intervals.length; k++) {
				if(!intervalsUsed[intervals[k]]) {
					hasFailedInterval = true;
					break;
				}
			}
			if(hasFailedInterval) continue;
			
			//If we've made it to this point, our current combination is a valid solution, so we just have to add it to the list
			solutionCount++;
			solutions.add(currentNotes.clone());
			System.out.println(Arrays.toString(currentNotes));
		}
		
		//Update the progress bar with the maximum value
		progressBar.setValue(maxCombos);
		progressBar.setString("Finding solutions: "+formattedMaxCombos+"/"+formattedMaxCombos+" ("+solutionCount+" solutions)");
		
		int[][] solutionArray = new int[solutionCount][];
		solutions.toArray(solutionArray);
		return solutionArray;
	}
	
	/* How a swap list works:
	 * 
	 * Our swap list determines what order we pick notes in.
	 * For instance, a swap list of [0, 0, 0] means we pick the first note every time, to get the notes [0, 1, 2]
	 * A swap list of [2, 1, 0] means we pick the last note every time, to get the notes [2, 1, 0]
	 * The maximum value of any element in the swap list is (length-index-1)
	 */
	public static void incrementSwaplist(int[] swaplist) {
		for(int i = 0; i < swaplist.length; i++) {
			swaplist[i]++;
			//If our current value exceeds the maximum for its index, we reset it and move on to the next value
			if(swaplist[i] >= swaplist.length-i) {
				swaplist[i] = 0;
				continue;
			}
			//If our current value does not overflow, we do NOT want to continue incrementing other values
			break;
		}
	}
}
