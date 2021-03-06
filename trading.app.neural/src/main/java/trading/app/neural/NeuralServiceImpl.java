package trading.app.neural;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.logging.Logger;

import org.encog.Encog;
import org.encog.engine.network.activation.ActivationElliott;
import org.encog.engine.network.activation.ActivationLinear;
import org.encog.engine.network.activation.ActivationTANH;
import org.encog.mathutil.randomize.ConsistentRandomizer;
import org.encog.ml.data.MLData;
import org.encog.ml.data.MLDataPair;
import org.encog.ml.data.MLDataSet;
import org.encog.ml.data.basic.BasicMLDataPair;
import org.encog.ml.data.basic.BasicMLDataSet;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.training.propagation.resilient.RPROPConst;
import org.encog.neural.networks.training.propagation.resilient.ResilientPropagation;
import org.encog.neural.pattern.FeedForwardPattern;
import org.encog.persist.EncogDirectoryPersistence;
import org.encog.util.Stopwatch;
import org.encog.util.simple.EncogUtility;

import com.google.common.eventbus.EventBus;

import trading.app.neural.NeuralContext;
import trading.app.neural.events.TestIterationCompletedEvent;
//import trading.data.MLBarDataConverter;
//import trading.data.MLBarDataLoader;
//import trading.data.model.BarEntity;
//import trading.data.model.DataPair;
//import trading.data.model.OutputEntity;
import trading.app.neural.events.TrainIterationCompletedEvent;
import trading.app.neural.mlData.Level1DataManager;
import trading.data.model.Level1;

/**
 * Neural network service
 * 
 * @author pdg
 * 
 */
public class NeuralServiceImpl extends NeuralServiceBase {

	/**
	 * Ctor
	 */
	public NeuralServiceImpl() {
	}

	/**
	 * @see NeuralServiceBase#getFirstLayerSize(int)
	 */
	@Override
	public int getFirstLayerSize(int entityListSize) {
		return entityListSize * Level1DataManager.LEVEL1_DATA_SIZE;
	}

	/**
	 * @see NeuralServiceBase#getLastLayerSize()
	 */
	public int getLastLayerSize() {
		return Level1DataManager.OUTPUT_SIZE;
	}

	/**
	 * @see NeuralServiceBase#createNetwork(List)
	 */
	@Override
	public BasicNetwork createNetwork(List<Integer> layers) {
		if (layers.size() < 2) {
			throw new IllegalArgumentException("Wrong network layers count");
		}
		final FeedForwardPattern pattern = new FeedForwardPattern();
		// Input neurons
		int input = layers.get(0);
		pattern.setInputNeurons(input);
		// Hidden neurons
		for (int i = 1; ((i < layers.size() - 1)); i++) {
			int neurons = layers.get(i);
			if (neurons > 0) {
				pattern.addHiddenLayer(layers.get(i));
			}
		}
		// Output neurons
		int output = layers.get(layers.size() - 1);
		pattern.setOutputNeurons(output);

		// // Activation function
		pattern.setActivationFunction(new ActivationTANH());
//		pattern.setActivationFunction(new ActivationLinear());

		// //pattern.setActivationFunction(new ActivationElliott());

		// Create network
		final BasicNetwork network = (BasicNetwork) pattern.generate();
		// Randomize the network
		(new ConsistentRandomizer(-1, 1, 100)).randomize(network);
		neuralContext.setNetwork(network);

		return network;

	}

	/**
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @see NeuralServiceBase#trainNetwork()
	 */
	@Override
	public void trainNetwork() {
		// Load defautl train dataset and train network
		MLDataSet ds = neuralContext.getNeuralDataManager()
				.loadTrainMLDataSet();
		trainNetwork(ds);
	}

	/**
	 * Additional training of network on last data
	 */
	public void trainNetworkAdditional() {
		// Get dataset for additional training and train on it
		MLDataSet ds = neuralContext.getNeuralDataManager()
				.loadAdditionalTrainMLDataSet();
		trainNetwork(ds);
	}

	/**
	 * Train on specific dataset
	 */
	void trainNetwork(MLDataSet dataSet) {
		neuralContext.getTrainingContext().setLastError(0);
		// neuralContext.getTrainingContext().setSamplesCount(dataSet.size());
		BasicNetwork network = neuralContext.getNetwork();

		// Backpropagation training
		// ResilientPropagation train = new ResilientPropagation(network, ds, 0,
		// RPROPConst.DEFAULT_MAX_STEP);
		ResilientPropagation train = new ResilientPropagation(network, dataSet);

		// Backpropagation train = new Backpropagation(network, ds);
		train.setThreadCount(10);
		neuralContext.getTrainingContext().setTrain(train);

		Logger.getLogger(NeuralServiceImpl.class.getName()).info(
				"Start training");

		// Create watches
		Stopwatch trainWatch = new Stopwatch();
		trainWatch.reset();
		trainWatch.start();

		Stopwatch epochWatch = new Stopwatch();
		epochWatch.reset();
		epochWatch.start();
		double lastError = 0;
		double sameErrorCount = 0;
		final int maxErrorCount = 100; // If error does not change maxErrorCount
										// loops, training completed
		for (int epoch = 1; epoch <= neuralContext.getTrainingContext()
				.getMaxEpochCount() && sameErrorCount <= maxErrorCount; epoch++) {
			epochWatch.reset();
			// Do training iteration
			train.iteration();
			// Calculate error
			double error = train.getError();
			// Increase error coujnt
			if (error == lastError) {
				sameErrorCount++;
			} else {
				sameErrorCount = 0;
				lastError = error;
			}
			// Update neural context
			neuralContext.getTrainingContext().setLastEpoch(epoch);
			neuralContext.getTrainingContext().setLastEpochMilliseconds(
					epochWatch.getElapsedMilliseconds());
			neuralContext.getTrainingContext().setTrainMilliseconds(
					trainWatch.getElapsedMilliseconds());
			neuralContext.getTrainingContext().setLastError(error);

			// Raise event
			TrainIterationCompletedEvent event = new TrainIterationCompletedEvent(
					epoch, neuralContext.getTrainingContext()
							.getLastEpochMilliseconds(), neuralContext
							.getTrainingContext().getTrainMilliseconds(),
					neuralContext.getTrainingContext().getLastError());
			eventBus.post(event);

			// Log
			Logger.getLogger(NeuralServiceImpl.class.getName()).info(
					String.format("Epoch %d. Time %d sec, error %s", epoch,
							epochWatch.getElapsedMilliseconds() / 1000,
							Double.toString(error)));
		}
		trainWatch.stop();
		epochWatch.stop();
		Logger.getLogger(NeuralServiceImpl.class.getName()).info(
				String.format("Training time  %d minutes",
						trainWatch.getElapsedMilliseconds() / 1000 / 60,
						Double.toString(train.getError())));
		train.finishTraining();
	}
	
	/**
	 * Test and learn every iteration
	* @see NeuralServiceBase#testNetwork()
	 */
	@Override
	public void testNetwork() {
		BasicNetwork network = neuralContext.getNetwork();
		List<Level1> data = neuralContext.getNeuralDataManager().loadTestData();
	
		// ??? ToDo: rework, predict
		int startIndex = neuralContext.getLevel1WindowSize()+neuralContext.getPredictionSize();
		int step = 1;
		// Go through all prediction window
		for (int i = startIndex, iteration = 1; i < startIndex + neuralContext.getTrainingContext().getPredictionSamples()* step; i += step, iteration++) {
			// Get input - ideal data pair
			MLData input = neuralContext.getNeuralDataManager().getInputData(data, i);
			// Predict
			MLData output = network.compute(input);
			// No learning if no previous predictions happened
//			if(i < neuralContext.getLevel1WindowSize() + neuralContext.getPredictionSize()){
//				continue;
//			}
			// Learning based on previous prediction and real data comparison	
			MLData ideal = neuralContext.getNeuralDataManager().getOutputData(data, i);
			MLDataPair pair = new BasicMLDataPair(input, ideal);
			MLDataSet dataSet= new BasicMLDataSet(Arrays.asList(new MLDataPair[]{pair}));
			// Train
			trainNetwork(dataSet);
			double error = network.calculateError(dataSet);
			
			// Fire event
			// Last level1 in input window. Prediction window starts next item
			Level1 level1 = data.get(i);
			TestIterationCompletedEvent event = new TestIterationCompletedEvent(
					level1, 
					iteration,
					output.getData(0), // predicted low
					output.getData(1), // predicted high
					ideal.getData(0), // real low
					ideal.getData(1), // real high
					error); 
			eventBus.post(event);
		}
	}		
}
