package net.imglib2.cache.exampleclassifier;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.AccessFlags.VOLATILE;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.Cache;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DirtyDiskCellCache;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DirtyShortArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;
import weka.classifiers.Classifier;
import weka.core.Instance;

public class ExampleClassifyingCell
{
	public static class CheckerboardLoader implements CacheLoader< Long, Cell< DirtyShortArray > >
	{
		private final CellGrid grid;

		public CheckerboardLoader( final CellGrid grid )
		{
			this.grid = grid;
		}

		@Override
		public Cell< DirtyShortArray > get( final Long key ) throws Exception
		{
			final long index = key;

			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );
			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final DirtyShortArray array = new DirtyShortArray( blocksize );

			final long[] cellGridPosition = new long[ n ];
			grid.getCellGridPositionFlat( index, cellGridPosition );
			long sum = 0;
			for ( int d = 0; d < n; ++d )
				sum += cellGridPosition[ d ];
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000 : 0xffff );
			Arrays.fill( array.getCurrentStorageArray(), color );

			return new Cell<>( cellDims, cellMin, array );
		}
	}

	public static class ClassifyingCellLoader< T extends RealType< T > > implements CacheLoader< Long, Cell< VolatileShortArray > >
	{
		private final CellGrid grid;

		private final List< RandomAccessible< T > > features;

		private final Classifier classifier;

		private final int numClasses;

		public ClassifyingCellLoader(
				final CellGrid grid,
				final List< RandomAccessible< T > > features,
				final Classifier classifier,
				final int numClasses )
		{
			this.grid = grid;
			this.features = features;
			this.classifier = classifier;
			this.numClasses = numClasses;
		}

		@Override
		public Cell< VolatileShortArray > get( final Long key ) throws Exception
		{
			final long index = key;

			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );
			final long[] cellMax = IntStream.range( 0, n ).mapToLong( d -> cellMin[ d ] + cellDims[ d ] - 1 ).toArray();
			final FinalInterval cellInterval = new FinalInterval( cellMin, cellMax );

			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final VolatileShortArray array = new VolatileShortArray( blocksize, true );

			final Img< UnsignedShortType > img = ArrayImgs.unsignedShorts( array.getCurrentStorageArray(), Util.int2long( cellDims ) );
			final ArrayList< RandomAccessibleInterval< T > > featureBlocks = new ArrayList<>();
			for ( final RandomAccessible< T > f : this.features )
				featureBlocks.add( Views.interval( f, cellInterval ) );

			final InstanceView< T > instances = new InstanceView<>( Views.collapseReal( Views.stack( featureBlocks ) ), InstanceView.makeDefaultAttributes( features.size(), numClasses ) );

			final Cursor< Instance > instancesCursor = Views.interval( instances, cellInterval ).cursor();
			final Cursor< UnsignedShortType > imgCursor = img.cursor();
			while ( imgCursor.hasNext() )
				imgCursor.next().set( 1 - ( int ) classifier.classifyInstance( instancesCursor.next() ) );

			return new Cell<>( cellDims, cellMin, array );
		}
	}

	static < T extends RealType< T > > Pair< Img< UnsignedShortType >, Img< VolatileUnsignedShortType > >
	createClassifier( final List< RandomAccessible< T > > source, final Classifier classifier, final int numClasses, final CellGrid grid, final BlockingFetchQueues< Callable< ? > > queue )
			throws IOException
	{
		final UnsignedShortType type = new UnsignedShortType();
		final VolatileUnsignedShortType vtype = new VolatileUnsignedShortType();

		final Path blockcache = DiskCellCache.createTempDirectory( "Classifier", true );
		final DiskCellCache< VolatileShortArray > diskcache = new DiskCellCache<>(
				blockcache,
				grid,
				new ClassifyingCellLoader<>( grid, source, classifier, numClasses ),
				AccessIo.get( SHORT, VOLATILE ),
				type.getEntitiesPerPixel() );
		final IoSync< Long, Cell< VolatileShortArray > > iosync = new IoSync<>( diskcache );
		final Cache< Long, Cell< VolatileShortArray > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< VolatileShortArray > >( 1000 )
				.withRemover( iosync )
				.withLoader( iosync );
		final Img< UnsignedShortType > prediction = new LazyCellImg<>( grid, type, cache.unchecked()::get );

		final CreateInvalid< Long, Cell< VolatileShortArray > > createInvalid = CreateInvalidVolatileCell.get( grid, type );
		final VolatileCache< Long, Cell< VolatileShortArray > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );

		final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, 0, false );
		final VolatileCachedCellImg< VolatileUnsignedShortType, ? > vprediction = new VolatileCachedCellImg<>( grid, vtype, hints, volatileCache.unchecked()::get );

		return new ValuePair<>( prediction, vprediction );
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();

		final CellGrid grid = new CellGrid( dimensions, cellDimensions );
		final Path blockcache = DiskCellCache.createTempDirectory( "CellImg-", true );
		final DiskCellCache< DirtyShortArray > diskcache = new DirtyDiskCellCache<>(
				blockcache,
				grid,
				new CheckerboardLoader( grid ),
				AccessIo.get( SHORT, DIRTY ),
				type.getEntitiesPerPixel() );
		final IoSync< Long, Cell< DirtyShortArray > > iosync = new IoSync<>( diskcache );
		final UncheckedCache< Long, Cell< DirtyShortArray > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< DirtyShortArray > >( 1000 )
				.withRemover( iosync )
				.withLoader( iosync )
				.unchecked();
		final Img< UnsignedShortType > img = new LazyCellImg<>( grid, new UnsignedShortType(), cache::get );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );




		final int maxNumLevels = 1;
		final int numFetcherThreads = 7;
		final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels );
		new FetcherThreads( queue, numFetcherThreads );

		final ThresholdingClassifier classifier = new ThresholdingClassifier( 0.5 );
		final Pair< Img< UnsignedShortType >, Img< VolatileUnsignedShortType > > prediction = createClassifier( Arrays.asList( img ), classifier, 2, grid, queue );

		BdvFunctions.show( prediction.getB(), "Prediction", BdvOptions.options().addTo( bdv ) );
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 1 ).setRange( 0, 1 );

	}
}
