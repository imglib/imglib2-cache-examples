package net.imglib2.cache.examplehttp;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.BdvSource;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.Interval;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gradient.PartialDerivative;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.util.IntervalKeyLoaderAsLongKeyLoader;
import net.imglib2.converter.Converters;
import net.imglib2.converter.RealFloatConverter;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.volatiles.array.DirtyVolatileByteArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Intervals;
import net.imglib2.view.Views;

public class ExampleHTTP
{
	public static void main( final String[] args ) throws IOException
	{
		// http://emdata.janelia.org/api/node/822524777d3048b8bd520043f90c1d28/grayscale/metadata
		final long[] minPoint = { 1728, 1536, 1344 };
//		final long offset = minPoint;
		final long[] offset = Arrays.stream( minPoint ).map( p -> p * 2 ).toArray();
		final int[] cellDimensions = new int[] { 64, 64, 64 };
//		final long[] dimensions = new long[] { 3584, 2944, 6912 }; // complete data set
		final long[] dimensions = new long[] { 300, 300, 300 };
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );


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
			return address;
		};
		final BiConsumer< byte[], DirtyVolatileByteArray > copier = ( bytes, access ) ->
		{
			System.arraycopy( bytes, 0, access.getCurrentStorageArray(), 0, bytes.length );
			access.setDirty();
		};
		final HTTPLoader< DirtyVolatileByteArray > functor = new HTTPLoader<>( addressComposer, ( n ) -> new DirtyVolatileByteArray( ( int ) n, true ), copier );
		final IntervalKeyLoaderAsLongKeyLoader< DirtyVolatileByteArray > loader = new IntervalKeyLoaderAsLongKeyLoader<>( grid, functor );

		final DiskCachedCellImgOptions factoryOptions = options()
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 1000 )
				.cellDimensions( cellDimensions );

		final Img< UnsignedByteType > httpImg = new DiskCachedCellImgFactory< UnsignedByteType >( factoryOptions )
				.createWithCacheLoader( dimensions, new UnsignedByteType(), loader );

		final RandomAccessible< FloatType > source = Converters.convert( Views.extendBorder( httpImg ), new RealFloatConverter<>(), new FloatType() );
		final CellLoader< FloatType > gradientLoader = new CellLoader< FloatType >()
		{
			@Override
			public void load( final SingleCellArrayImg< FloatType, ? > cell ) throws Exception
			{
				final int n = cell.numDimensions();
				for ( int d = 0; d < n; ++d )
				{
					final Img< FloatType > imgDim = ArrayImgs.floats( Intervals.dimensionsAsLongArray( cell ) );
					PartialDerivative.gradientCentralDifference2( Views.offsetInterval( source, cell ), imgDim, d );
					final Cursor< FloatType > c = imgDim.cursor();
					for ( final FloatType t : cell )
					{
						final float val = c.next().get();
						t.set( t.get() + val * val );
					}
				}
				for ( final FloatType t : cell )
					t.set( ( float ) Math.sqrt( t.get() ) );
			}
		};

		final Img< FloatType > gradientImg = new DiskCachedCellImgFactory< FloatType >( factoryOptions )
				.create( dimensions, new FloatType(), gradientLoader,
						options().initializeCellsAsDirty( true ) );

		final BdvSource httpSource = BdvFunctions.show(
				VolatileViews.wrapAsVolatile( httpImg, new SharedQueue( 20 ) ),
				"dvid" );

		final int numProc = Runtime.getRuntime().availableProcessors();
		final BdvSource gradientSource = BdvFunctions.show(
				VolatileViews.wrapAsVolatile( gradientImg, new SharedQueue( numProc - 1 ) ),
				"gradient",
				BdvOptions.options().addTo( httpSource ) );

		final Bdv bdv = httpSource;
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );
		httpSource.setDisplayRange( 0.0, 255.0 );
		gradientSource.setDisplayRange( 0.0, 30.0 );
	}
}
