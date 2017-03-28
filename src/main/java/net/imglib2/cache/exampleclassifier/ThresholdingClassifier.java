package net.imglib2.cache.exampleclassifier;

import weka.classifiers.Classifier;
import weka.core.Capabilities;
import weka.core.Instance;
import weka.core.Instances;

public class ThresholdingClassifier implements Classifier
{
	final double threshold;

	public ThresholdingClassifier( final double threshold )
	{
		super();
		this.threshold = threshold;
	}

	@Override
	public void buildClassifier( final Instances arg0 ) throws Exception
	{
		// nothing to do here
	}

	@Override
	public double classifyInstance( final Instance arg0 ) throws Exception
	{
		return arg0.value( 0 ) > threshold ? 1 : 0;
	}

	@Override
	public double[] distributionForInstance( final Instance arg0 ) throws Exception
	{
		final double[] distribution = new double[ 2 ];
		distribution[ arg0.value( 0 ) > threshold ? 1 : 0 ] = 1.0;
		return distribution;
	}

	@Override
	public Capabilities getCapabilities()
	{
		// don't need this
		return null;
	}

}
