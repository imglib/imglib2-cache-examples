package net.imglib2.cache.exampleclassifier;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.util.Pair;
import net.imglib2.view.Views;
import net.imglib2.view.composite.Composite;
import weka.classifiers.Classifier;
import weka.core.Instance;

public class Predict
{

	public static < T extends RealType< T > > void predict( final InstanceView< ?, ? > instances, final Classifier classifier, final RandomAccessibleInterval< ? extends Composite< T > > map ) throws Exception
	{

		for ( final Pair< Instance, ? extends Composite< T > > p : Views.interval( Views.pair( instances, map ), map ) )
		{
			final double[] probs = classifier.distributionForInstance( p.getA() );
			final Composite< ? extends RealType< ? > > target = p.getB();
			for ( int i = 0; i < probs.length; ++i )
				target.get( i ).setReal( probs[i] );
		}

	}

}
