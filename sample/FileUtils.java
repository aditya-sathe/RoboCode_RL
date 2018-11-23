package sample;

import static sample.Constants.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;

import robocode.RobocodeFileOutputStream;

public class FileUtils {
	
	public static void newLutFile(File lutFile) {
		try {
			RobocodeFileOutputStream fileOut = new RobocodeFileOutputStream(lutFile);
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(fileOut));
			out.writeObject(new HashMap<Integer, Double>());
			out.close();
			fileOut.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	public static void saveLut(File lutFile, Map<Integer, Double> rlMap) {
		try {
			RobocodeFileOutputStream fileOut = new RobocodeFileOutputStream(lutFile);
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(fileOut));
			out.writeObject(rlMap);
			out.close();
			fileOut.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	public static HashMap<Integer, Double> loadLut(File lutFile) {
		
		FileInputStream fileIn = null;
		ObjectInputStream in = null;
		try {
			fileIn = new FileInputStream(lutFile);
			in = new ObjectInputStream(new BufferedInputStream(fileIn));
			return (HashMap<Integer, Double>) in.readObject();
		} catch (IOException exception) {
			exception.printStackTrace();
		} catch (ClassNotFoundException exception) {
			exception.printStackTrace();
		}finally {
			try {
				in.close();
				fileIn.close();
			} catch (IOException e) {}
		}
		return null;
	}

	public static void saveStats(File statsFile, LearningType currType, int rndNum, int[] winArr ) {
		int i;

		try {
			RobocodeFileOutputStream fileOut = new RobocodeFileOutputStream(statsFile);
			PrintStream out = new PrintStream(new BufferedOutputStream(fileOut));
			out.format("Alpha, %f,\n", ALPHA);
			out.format("Gamma, %f,\n", GAMMA);
			out.format("Epsilon, %f,\n", EPSILON);
			switch (currType) {
			case NO_LEARNING:
				out.format("Learning Policy, NO LEARNING,\n");
				break;
			case LEARNED_GREEDY:
				out.format("Learning Policy, LEARNED GREEDY,\n");
				break;
			case SARSA:
				out.format("Learning Policy, SARSA,\n");
				break;
			case Q_LEARNING:
				out.format("Learning Policy, Q-LEARNING,\n");
				break;
			}
			out.format("Intermediate Rewards, %b,\n", IntermediateRewards);
			out.format("Terminal Rewards, %b,\n", TerminalRewards);
			out.format("100 Rounds, Wins,\n");
			for (i = 0; i < rndNum / 100; i++) {
				out.format("%d, %d,\n", i + 1, winArr[i]);
			}

			out.close();
			fileOut.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}
}
