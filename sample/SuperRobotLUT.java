package sample;

import robocode.*;

import java.awt.*;
import java.io.*;
import java.util.HashMap;
import java.util.Random;

import static sample.Constants.*;
import static sample.FileUtils.*;

public class SuperRobotLUT extends AdvancedRobot {

	private LearningType currentLearningType = LearningType.Q_LEARNING;

	private File lutFile;
	private File winRatesFile;

	private int myRobotX;
	private int myRobotY;
	private int myRobotHeading;
	private int myRobotGunHeading;
	private int myRobotGunBearing;
	private int myRobotEnergy;

	private int enemyDist;
	private int enemyBear;
	private int enemyEnergy;
	private int enemyHead;
	private int enemyGunBearing;
	private int enemyX;
	private int enemyY;

	private int prevStateActionHash;
	private int currStateActionHash = 0xFFFFFFFF; // Init to 32 bit
	private int prevEnergyDifference;
	private int currEnergyDifference;
	private double currReward;

	private static HashMap<Integer, Double> mLearningMap = new HashMap<>();

	private final TurnCompleteCondition turnCompl = new TurnCompleteCondition(this);
	private final MoveCompleteCondition moveCompl = new MoveCompleteCondition(this);
	private final GunTurnCompleteCondition gunMoveCompl = new GunTurnCompleteCondition(this);

	private static int[] winRateArr = new int[1000];

	public void run() {
		int currentStateHash, selectedAction;
		setColors(Color.BLACK, Color.RED, Color.GRAY, Color.YELLOW, Color.GREEN);

		setAdjustGunForRobotTurn(true);
		setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);

		lutFile = getDataFile(LUT_FILE);
		winRatesFile = getDataFile(WIN_RATES_LOG);

		if (lutFile.length() == 0) {
			newLutFile(lutFile);
		}

		if (mLearningMap.isEmpty()) {
			mLearningMap = loadLut(lutFile);
		}

		if (currentLearningType == LearningType.SARSA) {
			currentStateHash = generateStateHash();
			selectedAction = getActionHash(Constants.LEARN_MODE_EPS_GREEDY, currentStateHash);
			takeAction(currentStateHash, selectedAction);
		}

		while (true) {
			turnRadarRight(30);
		}
	}

	public void onScannedRobot(ScannedRobotEvent event) {
		double angle;

		myRobotX = (int) getX();
		myRobotY = (int) getY();
		myRobotHeading = (int) getHeading();
		myRobotGunHeading = (int) getGunHeading();
		myRobotGunBearing = normalizeAngle(myRobotHeading - myRobotGunHeading);
		myRobotEnergy = (int) getEnergy();

		enemyDist = (int) event.getDistance();
		enemyHead = (int) event.getHeading();
		enemyBear = (int) event.getBearing();
		enemyGunBearing = normalizeAngle(myRobotGunBearing + enemyBear);
		enemyEnergy = (int) event.getEnergy();

		angle = Math.toRadians(getHeading() + event.getBearing() % 360);

		enemyX = (int) (getX() + Math.sin(angle) * event.getDistance());
		enemyY = (int) (getY() + Math.cos(angle) * event.getDistance());

		train(NON_TERMINAL_STATE);
	}

	private void train(boolean terminalState) {
		double qPrevNew;
		int currentStateHash, actionHash;

		currentStateHash = generateStateHash();

		if (IntermediateRewards) {
			prevEnergyDifference = currEnergyDifference;
			currEnergyDifference = myRobotEnergy - enemyEnergy;
			currReward += currEnergyDifference - prevEnergyDifference;
		}

		switch (currentLearningType) {
		case NO_LEARNING:
			actionHash = getRandomAction();
			takeAction(currentStateHash, actionHash);
			break;
		case LEARNED_GREEDY:
			currentStateHash = generateStateHash();
			actionHash = getActionHash(LEARN_MODE_MAX_Q, currentStateHash);
			takeAction(currentStateHash, actionHash);
			break;
		case SARSA:
			actionHash = getActionHash(LEARN_MODE_EPS_GREEDY, currentStateHash);
			qPrevNew = recalculateQPrev(getQValue(combineStateActionHashes(currentStateHash, actionHash)));
			mLearningMap.put(prevStateActionHash, qPrevNew);
			currReward = 0.0;

			if (terminalState) {
				return;
			}
			takeAction(currentStateHash, actionHash);
			break;

		case Q_LEARNING:
			if (terminalState) {

				qPrevNew = recalculateQPrev(0.0);

				mLearningMap.put(prevStateActionHash, qPrevNew);
				return;
			} else {

				actionHash = getActionHash(LEARN_MODE_EPS_GREEDY, currentStateHash);
				takeAction(currentStateHash, actionHash);
				currentStateHash = generateStateHash();
				actionHash = getActionHash(LEARN_MODE_MAX_Q, currentStateHash);
				qPrevNew = recalculateQPrev(getQValue(combineStateActionHashes(currentStateHash, actionHash)));
				mLearningMap.put(prevStateActionHash, qPrevNew);
				currReward = 0.0;
			}
			break;
		default:
			break;
		}
	}

	private void takeAction(int currentStateHash, int actionHash) {
		currStateActionHash = updateIntField(currentStateHash, ACTION_FIELD_WIDTH, ACTION_FIELD_OFFSET, actionHash);
		parseActionHash(actionHash);
		prevStateActionHash = currStateActionHash;
	}

	private double recalculateQPrev(double qNext) {
		double qPrevNew, qPrevOld;

		qPrevOld = getQValue(prevStateActionHash);
		qPrevNew = qPrevOld + (ALPHA * (currReward + (GAMMA * qNext) - qPrevOld));

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

		for (moveAction = 0; moveAction < MOVE_NUM; moveAction++) {
			for (fireAction = 0; fireAction < FIRE_NUM; fireAction++) {

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
		case LEARN_MODE_EPS_GREEDY:

			randomDouble = getRandomDouble(0.0, 1.0);
			if (randomDouble < EPSILON) {
				selectedActionHash = getRandomAction();
			}
			break;
		case LEARN_MODE_MAX_Q:
			break;
		default:
			break;
		}

		return selectedActionHash;
	}

	public void onBulletHit(BulletHitEvent event) {
		if (IntermediateRewards) {
			currReward += 30;
		}
	}

	public void onHitByBullet(HitByBulletEvent event) {
		if (IntermediateRewards) {
			currReward -= 30;
		}
	}

	public void onBattleEnded(BattleEndedEvent event) {
		saveLut(lutFile, mLearningMap);
		saveStats(winRatesFile, currentLearningType, getRoundNum(), winRateArr);
	}

	public void onDeath(DeathEvent event) {
		if (TerminalRewards) {
			currReward -= 100;
			train(TERMINAL_STATE);
		}
	}

	public void onWin(WinEvent event) {
		winRateArr[(getRoundNum() - 1) / 100]++;
		if (TerminalRewards) {
			currReward += 100;
			train(TERMINAL_STATE);
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
		mLearningMap.putIfAbsent(stateActionHash, 0.0);
		return mLearningMap.get(stateActionHash);
	}

	private int generateStateHash() {
		int stateHash = 0;

		int quantRobotX;
		int quantRobotY;
		int quantDistance;
		int quantRobotHeading;

		quantRobotX = quantizeInt(myRobotX, STATE_POS_X_MAX, 1 << STATE_POS_X_WIDTH);
		quantRobotY = quantizeInt(myRobotY, STATE_POS_Y_MAX, 1 << STATE_POS_Y_WIDTH);
		quantDistance = quantizeInt(enemyDist, STATE_DISTANCE_MAX, 1 << STATE_DISTANCE_WIDTH);
		quantRobotHeading = quantizeInt(myRobotHeading, STATE_ROBOT_HEADING_MAX, 1 << STATE_ROBOT_HEADING_WIDTH);

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
		case MOVE_UP:
			break;
		case MOVE_DN:
			newHeading = normalizeAngle(newHeading + 180);
			break;
		case MOVE_LEFT:
			newHeading = normalizeAngle(newHeading + 270);
			break;
		case MOVE_RIGHT:
			newHeading = normalizeAngle(newHeading + 90);
			break;
		default:

			break;
		}
		setTurnRight(newHeading);

		execute();
		waitFor(turnCompl);
		setAhead(MOVE_DISTANCE);

		execute();
		waitFor(moveCompl);

		angle = absoluteBearing((int) getX(), (int) getY(), enemyX, enemyY);
		estimatedEnemyBearingFromGun = normalizeAngle((int) (angle - getGunHeading()));
		turnGunRight(estimatedEnemyBearingFromGun);

		execute();
		waitFor(gunMoveCompl);

		switch (fireType) {
		case FIRE_0:
			break;
		case FIRE_3:
			setFireBullet(3);
			break;
		default:
			break;
		}
		execute();
	}

	private int getRandomAction() {
		int actionHash, randomMove, randomFire;

		randomMove = getRandomInt(0, MOVE_NUM - 1);
		randomFire = getRandomInt(0, FIRE_NUM - 1);
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

}
