package net.imglib2.cache.exampleclassifier;

import bdv.cache.SharedQueue;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import bdv.util.volatiles.VolatileViews;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;
import weka.classifiers.Classifier;
import weka.core.Instance;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

public class ExampleClassifyingCell
{
	public static class CheckerboardLoader implements CellLoader< UnsignedShortType >
	{
		private final CellGrid grid;

		public CheckerboardLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public void load( final SingleCellArrayImg< UnsignedShortType, ? > cell ) throws Exception
		{
			final int n = grid.numDimensions();
			long sum = 0;
			for ( int d = 0; d < n; ++d )
				sum += cell.min( d ) / grid.cellDimension( d );
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000: 0xffff );

			cell.forEach( t -> t.set( color ) );
		}
	}

	public static class ClassifyingCellLoader< T extends RealType< T > > implements CellLoader< UnsignedShortType >
	{
		private final Classifier classifier;

		private final InstanceView< T > instances;

		public ClassifyingCellLoader(
				final List< RandomAccessibleInterval< T > > features,
				final Classifier classifier,
				final int numClasses )
		{
			this.instances = new InstanceView<>(
					Views.collapseReal( Views.stack( features ) ),
					InstanceView.makeDefaultAttributes( features.size(), numClasses ) );
			this.classifier = classifier;
		}

		@Override
		public void load( final SingleCellArrayImg< UnsignedShortType, ? > cell ) throws Exception
		{
			final Cursor< Instance > instancesCursor = Views.flatIterable( Views.interval( instances, cell ) ).cursor();
			final Cursor< UnsignedShortType > imgCursor = Views.flatIterable( cell ).cursor();
			while ( imgCursor.hasNext() )
				imgCursor.next().set( 1 - ( int ) classifier.classifyInstance( instancesCursor.next() ) );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();

		final DiskCachedCellImgFactory< UnsignedShortType > factory = new DiskCachedCellImgFactory<>( type, options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 ) );

		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
		final Img< UnsignedShortType > img = factory.create( dimensions, loader );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );

		final ThresholdingClassifier classifier = new ThresholdingClassifier( 0.5 );
		final Img< UnsignedShortType > prediction = factory.create( dimensions,
				new ClassifyingCellLoader<>( Arrays.asList( img ), classifier, 2 ),
				options().initializeCellsAsDirty( true ) );

		final SharedQueue queue = new SharedQueue( 7 );
		final BdvSource bdvSourcePrediction = BdvFunctions.show( VolatileViews.wrapAsVolatile( prediction, queue ), "Prediction", BdvOptions.options().addTo( bdv ) );
		bdvSourcePrediction.setDisplayRange( 0, 1 );
	}
}
