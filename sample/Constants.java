package sample;

public class Constants {
	
	public enum LearningType {
		NO_LEARNING, // No learning, basic robot
		LEARNED_GREEDY, // No learning, will pick greedy move from Lookup Table
		SARSA, // On-policy SARSA
		Q_LEARNING // Off-policy Q-learning
	}
	
	public static final boolean IntermediateRewards = true;
	public static final boolean TerminalRewards = true;

	public static final boolean NON_TERMINAL_STATE = false;
	public static final boolean TERMINAL_STATE = true;

	public static final double ALPHA = 0.5;
	public static final double GAMMA = 0.8;
	public static final double EPSILON = 0.7; // Epsilon 0.3, 0.5, 0.7

	public static final int MOVE_UP = 0;
	public static final int MOVE_DN = 1;
	public static final int MOVE_LEFT = 2;
	public static final int MOVE_RIGHT = 3;
	public static final int MOVE_NUM = 4;
	public static final int MOVE_DISTANCE = 50;

	public static final int FIRE_0 = 0;
	public static final int FIRE_3 = 1;
	public static final int FIRE_NUM = 2;

	public static final int ACTION_DIMENSIONALITY = MOVE_NUM * FIRE_NUM;
	public static final int LEARN_MODE_MAX_Q = 0;
	public static final int LEARN_MODE_EPS_GREEDY = 1;

	public static final int STATE_POS_X_WIDTH = 4;
	public static final int STATE_POS_X_OFFSET = 0;
	public static final int STATE_POS_X_MAX = 800; // Max X size

	public static final int STATE_POS_Y_WIDTH = 4;
	public static final int STATE_POS_Y_OFFSET = 4;
	public static final int STATE_POS_Y_MAX = 600;  // Max Y size

	public static final int STATE_DISTANCE_WIDTH = 4;
	public static final int STATE_DISTANCE_OFFSET = 8;
	public static final int STATE_DISTANCE_MAX = 1000;

	public static final int STATE_ROBOT_HEADING_WIDTH = 4;
	public static final int STATE_ROBOT_HEADING_OFFSET = 12;
	public static final int STATE_ROBOT_HEADING_MAX = 360;

	public static final int ACTION_MOVE_OFFSET = 0;
	public static final int ACTION_MOVE_WIDTH = 2;
	public static final int ACTION_FIRE_OFFSET = 2;
	public static final int ACTION_FIRE_WIDTH = 1;

	public static final int ACTION_FIELD_WIDTH = 3;
	public static final int ACTION_FIELD_OFFSET = 16;
	
	public static final String LUT_FILE = "./lut.dat";
	public static final String WIN_RATES_LOG = "./winrates.csv";

}
