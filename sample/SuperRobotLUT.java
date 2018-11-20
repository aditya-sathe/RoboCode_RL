package sample;

import robocode.*;

import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.Random;

public class SuperRobotLUT extends AdvancedRobot {
	enum LearningPolicy {
		NO_LEARNING, // No learning, basic robot
		LEARNED_GREEDY, // No learning, will pick greedy move from Lookup Table
		SARSA, // On-policy SARSA
		Q_LEARNING // Off-policy Q-learning
	}

	private static final boolean NON_TERMINAL_STATE = false;
	private static final boolean TERMINAL_STATE = true;

	private static final double ALPHA = 0.5;
	private static final double GAMMA = 0.8;
	private static final double EPSILON = 0.1;

	private LearningPolicy currentPolicy = LearningPolicy.Q_LEARNING;
	private boolean mIntermediateRewards = true;
	private boolean mTerminalRewards = true;
	private static boolean mDebug = true;

	private static final int ARENA_SIZEX_PX = 800;
	private static final int ARENA_SIZEY_PX = 600;
	private static final int NULL_32 = 0xFFFFFFFF;

	private static final int ACTION_MOVE_UP = 0;
	private static final int ACTION_MOVE_DN = 1;
	private static final int ACTION_MOVE_LT = 2;
	private static final int ACTION_MOVE_RT = 3;
	private static final int ACTION_MOVE_NUM = 4;
	private static final int ACTION_MOVE_DISTANCE = 50;

	private static final int ACTION_FIRE_0 = 0;
	private static final int ACTION_FIRE_3 = 1;
	private static final int ACTION_FIRE_NUM = 2;

	private static final int ACTION_DIMENSIONALITY = ACTION_MOVE_NUM * ACTION_FIRE_NUM;
	private static final int ACTION_MODE_MAX_Q = 0;
	private static final int ACTION_MODE_EPSILON_GREEDY = 1;

	private static final int STATE_POS_X_WIDTH = 4;
	private static final int STATE_POS_X_OFFSET = 0;
	private static final int STATE_POS_X_MAX = ARENA_SIZEX_PX;

	private static final int STATE_POS_Y_WIDTH = 4;
	private static final int STATE_POS_Y_OFFSET = 4;
	private static final int STATE_POS_Y_MAX = ARENA_SIZEY_PX;

	private static final int STATE_DISTANCE_WIDTH = 4;
	private static final int STATE_DISTANCE_OFFSET = 8;
	private static final int STATE_DISTANCE_MAX = 1000;

	private static final int STATE_ROBOT_HEADING_WIDTH = 4;
	private static final int STATE_ROBOT_HEADING_OFFSET = 12;
	private static final int STATE_ROBOT_HEADING_MAX = 360;

	private static final int ACTION_MOVE_OFFSET = 0;
	private static final int ACTION_MOVE_WIDTH = 2;
	private static final int ACTION_FIRE_OFFSET = 2;
	private static final int ACTION_FIRE_WIDTH = 1;

	private static final int ACTION_FIELD_WIDTH = 3;
	private static final int ACTION_FIELD_OFFSET = 16;

	private static final String LUT_FILE = "./lut.dat";
	private File mLutFile;

	private static final String WIN_RATES_LOG = "./winrates.csv";
	private File mWinRatesFile;

	private int mRobotX;
	private int mRobotY;
	private int mRobotHeading;
	private int mRobotGunHeading;
	private int mRobotGunBearing;
	private int mRobotEnergy;
	private int mEnemyDistance;
	private int mEnemyBearing;
	private int mEnemyEnergy;
	private int mEnemyHeading;
	private int mEnemyBearingFromGun;
	private int mEnemyX;
	private int mEnemyY;

	private int mPreviousStateActionHash;
	private int mCurrentStateActionHash = NULL_32;
	private int mPreviousEnergyDifference;
	private int mCurrentEnergyDifference;
	private double mCurrentReward;

	private static HashMap<Integer, Double> mReinforcementLearningMap = new HashMap<>();

	private final TurnCompleteCondition mTurnComplete = new TurnCompleteCondition(this);
	private final MoveCompleteCondition mMoveComplete = new MoveCompleteCondition(this);
	private final GunTurnCompleteCondition mGunMoveComplete = new GunTurnCompleteCondition(this);

	private static int[] mWinRateArr = new int[1000];

	public void run() {
		int currentStateHash, selectedAction;
		setColors(Color.BLACK, Color.RED, Color.GRAY, Color.YELLOW, Color.GREEN);

		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);

		mLutFile = getDataFile(LUT_FILE);
		mWinRatesFile = getDataFile(WIN_RATES_LOG);

		if (mLutFile.length() == 0) {
			newLutFile(mLutFile);
		}

		if (mReinforcementLearningMap.isEmpty()) {
			loadLut(mLutFile);
		}

		if (currentPolicy == LearningPolicy.SARSA) {
			currentStateHash = generateStateHash();
			selectedAction = getActionHash(ACTION_MODE_EPSILON_GREEDY, currentStateHash);
			takeAction(currentStateHash, selectedAction);
		}

		for (;;) {
			turnRadarRight(20);
		}
	}

	public void onScannedRobot(ScannedRobotEvent event) {
		double angle;

		mRobotX = (int) getX();
		mRobotY = (int) getY();
		mRobotHeading = (int) getHeading();
		mRobotGunHeading = (int) getGunHeading();
		mRobotGunBearing = normalizeAngle(mRobotHeading - mRobotGunHeading);
		mRobotEnergy = (int) getEnergy();

		mEnemyDistance = (int) event.getDistance();
		mEnemyHeading = (int) event.getHeading();
		mEnemyBearing = (int) event.getBearing();
		mEnemyBearingFromGun = normalizeAngle(mRobotGunBearing + mEnemyBearing);
		mEnemyEnergy = (int) event.getEnergy();

		angle = Math.toRadians(getHeading() + event.getBearing() % 360);

		mEnemyX = (int) (getX() + Math.sin(angle) * event.getDistance());
		mEnemyY = (int) (getY() + Math.cos(angle) * event.getDistance());

		learn(NON_TERMINAL_STATE);
	}

	private void learn(boolean terminalState) {
		double qPrevNew;
		int currentStateHash, actionHash;

		currentStateHash = generateStateHash();

		if (mIntermediateRewards) {
			mPreviousEnergyDifference = mCurrentEnergyDifference;
			mCurrentEnergyDifference = mRobotEnergy - mEnemyEnergy;
			mCurrentReward += mCurrentEnergyDifference - mPreviousEnergyDifference;
		}

		switch (currentPolicy) {
		case NO_LEARNING:

			actionHash = getRandomAction();

			takeAction(currentStateHash, actionHash);
			break;
		case LEARNED_GREEDY:

			currentStateHash = generateStateHash();

			actionHash = getActionHash(ACTION_MODE_MAX_Q, currentStateHash);

			takeAction(currentStateHash, actionHash);
			break;

		case SARSA:

			actionHash = getActionHash(ACTION_MODE_EPSILON_GREEDY, currentStateHash);

			qPrevNew = calculateQPrevNew(getQValue(combineStateActionHashes(currentStateHash, actionHash)));

			mReinforcementLearningMap.put(mPreviousStateActionHash, qPrevNew);

			mCurrentReward = 0.0;

			if (terminalState) {

				return;
			}

			takeAction(currentStateHash, actionHash);
			break;

		case Q_LEARNING:

			if (terminalState) {

				qPrevNew = calculateQPrevNew(0.0);

				mReinforcementLearningMap.put(mPreviousStateActionHash, qPrevNew);
				return;
			} else {

				actionHash = getActionHash(ACTION_MODE_EPSILON_GREEDY, currentStateHash);

				takeAction(currentStateHash, actionHash);

				currentStateHash = generateStateHash();

				actionHash = getActionHash(ACTION_MODE_MAX_Q, currentStateHash);

				qPrevNew = calculateQPrevNew(getQValue(combineStateActionHashes(currentStateHash, actionHash)));

				mReinforcementLearningMap.put(mPreviousStateActionHash, qPrevNew);

				mCurrentReward = 0.0;
			}
			break;
		default:
			break;
		}
	}

	private void takeAction(int currentStateHash, int actionHash) {

		mCurrentStateActionHash = updateIntField(currentStateHash, ACTION_FIELD_WIDTH, ACTION_FIELD_OFFSET, actionHash);

		parseActionHash(actionHash);

		mPreviousStateActionHash = mCurrentStateActionHash;
	}

	private double calculateQPrevNew(double qNext) {
		double qPrevNew, qPrevOld;

		qPrevOld = getQValue(mPreviousStateActionHash);
		qPrevNew = qPrevOld + (ALPHA * (mCurrentReward + (GAMMA * qNext) - qPrevOld));

		return qPrevNew;
	}

	private int getActionHash(int mode, int currentStateHash) {
		int moveAction, fireAction;
		int randomPick, actionHash, completeHash, selectedActionHash, selectedCompleteHash;
		int[] qMaxActions;
		int currentQMaxActionNum = 0;
		double currentMax = -999.0;
		double qVal, randomDouble;

		qMaxActions = new int[ACTION_DIMENSIONALITY];

		for (moveAction = 0; moveAction < ACTION_MOVE_NUM; moveAction++) {
			for (fireAction = 0; fireAction < ACTION_FIRE_NUM; fireAction++) {

				actionHash = generateActionHash(moveAction, fireAction);
				completeHash = combineStateActionHashes(currentStateHash, actionHash);

				qVal = getQValue(completeHash);

				if (qVal > currentMax) {
					qMaxActions = new int[ACTION_DIMENSIONALITY];
					currentQMaxActionNum = 1;
					qMaxActions[currentQMaxActionNum - 1] = completeHash;
					currentMax = qVal;
				} else if (qVal == currentMax) {
					currentQMaxActionNum++;
					qMaxActions[currentQMaxActionNum - 1] = completeHash;
				}

			}
		}

		if (currentQMaxActionNum == 1) {
			selectedCompleteHash = qMaxActions[0];
			selectedActionHash = getIntFieldVal(selectedCompleteHash, ACTION_FIELD_WIDTH, ACTION_FIELD_OFFSET);

		} else {
			randomPick = getRandomInt(0, currentQMaxActionNum - 1);
			selectedCompleteHash = qMaxActions[randomPick];
			selectedActionHash = getIntFieldVal(selectedCompleteHash, ACTION_FIELD_WIDTH, ACTION_FIELD_OFFSET);

		}

		switch (mode) {

		case ACTION_MODE_EPSILON_GREEDY:

			randomDouble = getRandomDouble(0.0, 1.0);
			if (randomDouble < EPSILON) {

				selectedActionHash = getRandomAction();

			} else {

			}
			break;

		case ACTION_MODE_MAX_Q:

			break;
		default:

			break;
		}

		return selectedActionHash;
	}

	public void onBulletHit(BulletHitEvent event) {
		if (mIntermediateRewards) {
			mCurrentReward += 30;
		}
	}

	public void onHitByBullet(HitByBulletEvent event) {
		if (mIntermediateRewards) {
			mCurrentReward -= 30;
		}
	}

	public void onBattleEnded(BattleEndedEvent event) {

		saveLut(mLutFile);

		saveStats(mWinRatesFile);
	}

	public void onDeath(DeathEvent event) {

		if (mTerminalRewards) {
			mCurrentReward -= 100;
			learn(TERMINAL_STATE);
		}
	}

	public void onWin(WinEvent event) {

		mWinRateArr[(getRoundNum() - 1) / 100]++;

		if (mTerminalRewards) {
			mCurrentReward += 100;
			learn(TERMINAL_STATE);
		}
	}

	private int normalizeAngle(int angle) {
		int result = angle;

		while (result > 180)
			result -= 360;
		while (result < -180)
			result += 360;

		return result;
	}

	private int combineStateActionHashes(int stateHash, int actionHash) {
		return updateIntField(stateHash, ACTION_FIELD_WIDTH, ACTION_FIELD_OFFSET, actionHash);
	}

	private double getQValue(int stateActionHash) {

		mReinforcementLearningMap.putIfAbsent(stateActionHash, 0.0);

		return mReinforcementLearningMap.get(stateActionHash);
	}

	private int generateStateHash() {
		int stateHash = 0;

		int quantRobotX;
		int quantRobotY;
		int quantDistance;
		int quantRobotHeading;

		quantRobotX = quantizeInt(mRobotX, STATE_POS_X_MAX, 1 << STATE_POS_X_WIDTH);
		quantRobotY = quantizeInt(mRobotY, STATE_POS_Y_MAX, 1 << STATE_POS_Y_WIDTH);
		quantDistance = quantizeInt(mEnemyDistance, STATE_DISTANCE_MAX, 1 << STATE_DISTANCE_WIDTH);
		quantRobotHeading = quantizeInt(mRobotHeading, STATE_ROBOT_HEADING_MAX, 1 << STATE_ROBOT_HEADING_WIDTH);

		stateHash = updateIntField(stateHash, STATE_POS_X_WIDTH, STATE_POS_X_OFFSET, quantRobotX);
		stateHash = updateIntField(stateHash, STATE_POS_Y_WIDTH, STATE_POS_Y_OFFSET, quantRobotY);
		stateHash = updateIntField(stateHash, STATE_DISTANCE_WIDTH, STATE_DISTANCE_OFFSET, quantDistance);
		stateHash = updateIntField(stateHash, STATE_ROBOT_HEADING_WIDTH, STATE_ROBOT_HEADING_OFFSET, quantRobotHeading);

		if ((quantRobotX < 0) || (quantRobotY < 0) || (quantDistance < 0) || (quantRobotHeading < 0))

		{
			throw new ArithmeticException("Quantized value cannot be negative!!!");
		}

		return stateHash;
	}

	private int generateActionHash(int moveAction, int fireAction) {

		int actionHash = 0;

		actionHash = updateIntField(actionHash, ACTION_MOVE_WIDTH, ACTION_MOVE_OFFSET, moveAction);
		actionHash = updateIntField(actionHash, ACTION_FIRE_WIDTH, ACTION_FIRE_OFFSET, fireAction);
		return actionHash;
	}

	private void parseActionHash(int actionHash) {
		int moveDirection, fireType, newHeading, estimatedEnemyBearingFromGun;
		double angle;

		moveDirection = getIntFieldVal(actionHash, ACTION_MOVE_WIDTH, ACTION_MOVE_OFFSET);
		fireType = getIntFieldVal(actionHash, ACTION_FIRE_WIDTH, ACTION_FIRE_OFFSET);

		newHeading = -1 * normalizeAngle((int) getHeading());
		switch (moveDirection) {
		case ACTION_MOVE_UP:
			break;
		case ACTION_MOVE_DN:
			newHeading = normalizeAngle(newHeading + 180);
			break;
		case ACTION_MOVE_LT:
			newHeading = normalizeAngle(newHeading + 270);
			break;
		case ACTION_MOVE_RT:
			newHeading = normalizeAngle(newHeading + 90);
			break;
		default:

			break;
		}
		setTurnRight(newHeading);

		execute();
		waitFor(mTurnComplete);
		setAhead(ACTION_MOVE_DISTANCE);

		execute();
		waitFor(mMoveComplete);

		angle = absoluteBearing((int) getX(), (int) getY(), mEnemyX, mEnemyY);
		estimatedEnemyBearingFromGun = normalizeAngle((int) (angle - getGunHeading()));
		turnGunRight(estimatedEnemyBearingFromGun);

		execute();
		waitFor(mGunMoveComplete);

		switch (fireType) {
		case ACTION_FIRE_0:

			break;
		case ACTION_FIRE_3:

			setFireBullet(3);
			break;
		default:

			break;
		}

		execute();
	}

	private int getRandomAction() {
		int actionHash, randomMove, randomFire;

		randomMove = getRandomInt(0, ACTION_MOVE_NUM - 1);
		randomFire = getRandomInt(0, ACTION_FIRE_NUM - 1);
		actionHash = generateActionHash(randomMove, randomFire);

		return actionHash;
	}

	private double absoluteBearing(int x0, int y0, int x1, int y1) {
		int xo = x1 - x0;
		int yo = y1 - y0;
		double hyp = calculateDistance(x0, y0, x1, y1);
		double asin = Math.toDegrees(Math.asin(xo / hyp));
		double bearing = 0;

		if (xo > 0 && yo > 0) {

			bearing = asin;
		} else if (xo < 0 && yo > 0) {

			bearing = 360 + asin;
		} else if (xo > 0 && yo < 0) {

			bearing = 180 - asin;
		} else if (xo < 0 && yo < 0) {

			bearing = 180 - asin;
		}

		return bearing;
	}

	private double calculateDistance(int x0, int y0, int x1, int y1) {
		double distance;

		distance = Math.sqrt(Math.pow((x1 - x0), 2) + Math.pow((y1 - y0), 2));

		return distance;
	}

	private int updateIntField(int inputInteger, int fieldWidth, int fieldOffset, int value) {
		int returnValue;
		int mask;

		returnValue = inputInteger;
		mask = ~(((1 << fieldWidth) - 1) << fieldOffset);
		returnValue &= mask;

		returnValue |= value << fieldOffset;

		return returnValue;
	}

	private int getIntFieldVal(int inputInteger, int fieldWidth, int fieldOffset) {
		int returnValue;
		int mask;
		returnValue = inputInteger;
		mask = ((1 << fieldWidth) - 1) << fieldOffset;
		returnValue &= mask;
		returnValue >>>= fieldOffset;
		return returnValue;
	}

	private double getRandomDouble(double min, double max) {
		double random, result;
		random = new Random().nextDouble();
		result = min + (random * (max - min));
		return result;
	}

	private int getRandomInt(int min, int max) {
		int result;
		Random random;
		random = new Random();
		result = random.nextInt(max - min + 1) + min;
		return result;
	}

	private int quantizeInt(int value, int realMax, int quantizedMax) {
		int quantizedVal;
		quantizedVal = (int) ((double) value * (double) quantizedMax / (double) realMax);
		return quantizedVal;
	}

	private void newLutFile(File lutFile) {
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

	private void saveLut(File lutFile) {
		try {
			RobocodeFileOutputStream fileOut = new RobocodeFileOutputStream(lutFile);
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(fileOut));
			out.writeObject(mReinforcementLearningMap);
			out.close();
			fileOut.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}

	private void loadLut(File lutFile) {
		try {
			FileInputStream fileIn = new FileInputStream(lutFile);
			ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(fileIn));
			mReinforcementLearningMap = (HashMap<Integer, Double>) in.readObject();
			in.close();
			fileIn.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		} catch (ClassNotFoundException exception) {
			exception.printStackTrace();
		}
	}

	private void saveStats(File statsFile) {
		int i;

		try {
			RobocodeFileOutputStream fileOut = new RobocodeFileOutputStream(statsFile);
			PrintStream out = new PrintStream(new BufferedOutputStream(fileOut));
			out.format("Alpha, %f,\n", ALPHA);
			out.format("Gamma, %f,\n", GAMMA);
			out.format("Epsilon, %f,\n", EPSILON);
			switch (currentPolicy) {
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
			out.format("Intermediate Rewards, %b,\n", mIntermediateRewards);
			out.format("Terminal Rewards, %b,\n", mTerminalRewards);
			out.format("100 Rounds, Wins,\n");
			for (i = 0; i < getRoundNum() / 100; i++) {
				out.format("%d, %d,\n", i + 1, mWinRateArr[i]);
			}

			out.close();
			fileOut.close();
		} catch (IOException exception) {
			exception.printStackTrace();
		}
	}
}
