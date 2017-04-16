package net.imglib2.cache.examplehttp;

import static net.imglib2.cache.img.AccessFlags.VOLATILE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Function;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.DisplayMode;
import net.imglib2.Interval;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.cache.Cache;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.img.PrimitiveType;
import net.imglib2.cache.queue.BlockingFetchQueues;
import net.imglib2.cache.queue.FetcherThreads;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.cache.ref.WeakRefVolatileCache;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.CreateInvalid;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.cache.volatiles.VolatileCache;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.img.basictypeaccess.volatiles.VolatileArrayDataAccess;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileFloatArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.type.volatiles.AbstractVolatileNativeRealType;
import net.imglib2.type.volatiles.VolatileFloatType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.ValuePair;
import net.imglib2.view.ExtendedRandomAccessibleInterval;
import net.imglib2.view.Views;

public class ExampleHTTP
{

	static < T extends RealType< T > & NativeType< T >, VT extends AbstractVolatileNativeRealType< T, VT >, A extends VolatileArrayDataAccess< A > >
	Pair< Img< T >, Img< VT > >
	createFunctorLoadedImgs(
			final CellGrid grid,
			final BlockingFetchQueues< Callable< ? > > queue,
			final FunctorLoader< A > loader,
			final T type,
			final VT vtype,
			final PrimitiveType primitiveType )
					throws IOException
	{

		final Path blockcache = DiskCellCache.createTempDirectory( "HTTP-", true );

		final DiskCellCache< A > diskcache = new DiskCellCache<>(
				blockcache,
				grid,
				loader,
				AccessIo.get( primitiveType, VOLATILE ),
				type.getEntitiesPerPixel() );
		final IoSync< Long, Cell< A > > iosync = new IoSync<>( diskcache );
		final Cache< Long, Cell< A > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< A > >( 1000 )
				.withRemover( iosync )
				.withLoader( iosync );
		final LazyCellImg< T, A > http = new LazyCellImg<>( grid, type, cache.unchecked()::get );

		final CreateInvalid< Long, Cell< A > > createInvalid = CreateInvalidVolatileCell.get( grid, type );
		final VolatileCache< Long, Cell< A > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );

		final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, 0, false );
		final VolatileCachedCellImg< VT, A > vhttp = new VolatileCachedCellImg<>( grid, vtype, hints, volatileCache.unchecked()::get );

		return new ValuePair<>( http, vhttp );
	}

	public static void main( final String[] args ) throws IOException
	{

		// http://emdata.janelia.org/api/node/822524777d3048b8bd520043f90c1d28/grayscale/metadata
		final long[] minPoint = { 1728, 1536, 1344 };
//		final long offset = minPoint;
		final long[] offset = Arrays.stream( minPoint ).map( p -> p * 2 ).toArray();
		final int[] cellDimensions = new int[] { 64, 64, 64 };
//		final long[] dimensions = new long[] { 3584, 2944, 6912 }; // complete data set
		final long[] dimensions = new long[] { 1000, 1000, 1000 };
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );

		final int numProc = Runtime.getRuntime().availableProcessors();
		final int maxNumLevels = 1;
		final int numFetcherThreads = numProc - 1;
		final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels );
		new FetcherThreads( queue, numFetcherThreads );

		// GET <api URL>/node/<UUID>/<data
		// name>/isotropic/<dims>/<size>/<offset>[/<format>][?queryopts]
		// http://emdata.janelia.org/api/node/822524777d3048b8bd520043f90c1d28/grayscale/isotropic/0_1_2/512_256/200_200_000/jpg:80
		final String format = String.format( "%s/%s/%s",
				"http://emdata.janelia.org/api/node/822524777d3048b8bd520043f90c1d28/grayscale/isotropic/0_1_2",
				"%d_%d_%d",
				"%d_%d_%d" );
		System.out.println( "format: " + format );
		final Function< Interval, String > addressComposer = interval -> {
			final String address = String.format(
					format,
					interval.max( 0 ) - interval.min( 0 ) + 1,
					interval.max( 1 ) - interval.min( 1 ) + 1,
					interval.max( 2 ) - interval.min( 2 ) + 1,
					offset[ 0 ] + interval.min( 0 ),
					offset[ 1 ] + interval.min( 1 ),
					offset[ 2 ] + interval.min( 2 ) );
//			System.out.println( "Getting address: " + address );
			return address;
		};
		final BiConsumer< byte[], VolatileByteArray > copier = ( bytes, access ) -> System.arraycopy( bytes, 0, access.getCurrentStorageArray(), 0, bytes.length );
		final HTTPLoader< VolatileByteArray > functor = new HTTPLoader<>( addressComposer, ( n ) -> new VolatileByteArray( ( int ) n, true ), copier );
		final FunctorLoader< VolatileByteArray > loader = new FunctorLoader<>( grid, functor );
		final Pair< Img< UnsignedByteType >, Img< VolatileUnsignedByteType > > httpImgs =
				createFunctorLoadedImgs( grid, queue, loader, new UnsignedByteType(), new VolatileUnsignedByteType(), PrimitiveType.BYTE );

		final FunctorLoader.Functor< Interval, VolatileFloatArray > gradientFunctor = interval -> {
			final ExtendedRandomAccessibleInterval< UnsignedByteType, Img< UnsignedByteType > > source = Views.extendBorder( httpImgs.getA() );
			final long numElements = Intervals.numElements( interval );
			final VolatileFloatArray store = new VolatileFloatArray( ( int ) numElements, true );
			final ArrayImg< FloatType, FloatArray > img = ArrayImgs.floats( store.getCurrentStorageArray(), Intervals.dimensionsAsLongArray( interval ) );
			for ( int d = 0; d < 3; ++d ) {

				final VolatileFloatArray storeDim = new VolatileFloatArray( ( int ) numElements, true );
				final ArrayImg< FloatType, FloatArray > imgDim = ArrayImgs.floats( storeDim.getCurrentStorageArray(), Intervals.dimensionsAsLongArray( interval ) );
				final long[] localOffset = Intervals.minAsLongArray( interval );
				PartialDerivative.gradientCentralDifference2(
						Converters.convert( source, new RealFloatConverter<>(), new FloatType() ),
						Views.translate( imgDim, localOffset ), d );
				final FloatType dummy = new FloatType();
				for ( final Pair< FloatType, FloatType > p : Views.interval( Views.pair( imgDim, img ), img ) )
				{
					final float val = p.getA().get();
					dummy.set( val * val );
					p.getB().add( dummy );
				}
			}

			for ( final FloatType pxl : img )
				pxl.setReal( Math.sqrt( pxl.get() ) );

			return store;
		};
		final FunctorLoader< VolatileFloatArray > gradientLoader = new FunctorLoader<>( grid, gradientFunctor );
		final Pair< Img< FloatType >, Img< VolatileFloatType > > gradientImgs =
				createFunctorLoadedImgs( grid, queue, gradientLoader, new FloatType(), new VolatileFloatType(), PrimitiveType.FLOAT );

		final BdvStackSource< VolatileUnsignedByteType > bdv = BdvFunctions.show( httpImgs.getB(), "dvid" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( DisplayMode.SINGLE );
		BdvFunctions.show( gradientImgs.getB(), "gradient", BdvOptions.options().addTo( bdv ) );
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 0 ).setRange( 0.0, 255.0 );
		bdv.getBdvHandle().getSetupAssignments().getMinMaxGroups().get( 1 ).setRange( 0.0, 30.0 );

	}
}
