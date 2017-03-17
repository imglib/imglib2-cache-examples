package net.imglib2.cache.example05;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.AccessFlags.DIRTY;
import static net.imglib2.cache.img.AccessFlags.VOLATILE;
import static net.imglib2.cache.img.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import bdv.img.cache.CreateInvalidVolatileCell;
import bdv.img.cache.VolatileCachedCellImg;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
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
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.Pair;
import net.imglib2.util.Util;
import net.imglib2.util.ValuePair;
import net.imglib2.view.Views;

public class Example05
{
	public static class CheckerboardLoader implements CacheLoader< Long, Cell< DirtyShortArray > >
	{
		private final CellGrid grid;

		ConcurrentHashMap< Long, AtomicInteger > loadcounts = new ConcurrentHashMap<>();

		AtomicInteger loadcount = new AtomicInteger( 0 );

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

	public static class GaussLoader implements CacheLoader< Long, Cell< VolatileShortArray > >
	{
		private final CellGrid grid;

		private final RandomAccessible< UnsignedShortType > source;

		private final double sigma;

		ConcurrentHashMap< Long, AtomicInteger > loadcounts = new ConcurrentHashMap<>();

		AtomicInteger loadcount = new AtomicInteger( 0 );

		public GaussLoader( final CellGrid grid, final RandomAccessible< UnsignedShortType > source, final double sigma )
		{
			this.grid = grid;
			this.source = source;
			this.sigma = sigma;
		}

		@Override
		public Cell< VolatileShortArray > get( final Long key ) throws Exception
		{
			final long index = key;

			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );

			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final VolatileShortArray array = new VolatileShortArray( blocksize, true );

			final Img< UnsignedShortType > img = ArrayImgs.unsignedShorts( array.getCurrentStorageArray(), Util.int2long( cellDims ) );
			Gauss3.gauss( sigma, source, Views.translate( img, cellMin ) );

			return new Cell<>( cellDims, cellMin, array );
		}
	}

	static Pair< Img< UnsignedShortType >, Img< VolatileUnsignedShortType > >
		createGauss( final RandomAccessible< UnsignedShortType > source, final double sigma, final CellGrid grid, final BlockingFetchQueues< Callable< ? > > queue )
				throws IOException
	{
		final UnsignedShortType type = new UnsignedShortType();
		final VolatileUnsignedShortType vtype = new VolatileUnsignedShortType();

		final Path blockcache = DiskCellCache.createTempDirectory( "Gauss" + sigma + "-", true );
		final DiskCellCache< VolatileShortArray > diskcache = new DiskCellCache<>(
				blockcache,
				grid,
				new GaussLoader( grid, source, sigma ),
				AccessIo.get( SHORT, VOLATILE ),
				type.getEntitiesPerPixel() );
		final IoSync< Long, Cell< VolatileShortArray > > iosync = new IoSync<>( diskcache );
		final Cache< Long, Cell< VolatileShortArray > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< VolatileShortArray > >( 1000 )
				.withRemover( iosync )
				.withLoader( iosync );
		final Img< UnsignedShortType > gauss = new LazyCellImg<>( grid, new UnsignedShortType(), cache.unchecked()::get );

		final CreateInvalid< Long, Cell< VolatileShortArray > > createInvalid = CreateInvalidVolatileCell.get( grid, type );
		final VolatileCache< Long, Cell< VolatileShortArray > > volatileCache = new WeakRefVolatileCache<>( cache, queue, createInvalid );

		final CacheHints hints = new CacheHints( LoadingStrategy.VOLATILE, 0, false );
		final VolatileCachedCellImg< VolatileUnsignedShortType, ? > vgauss = new VolatileCachedCellImg<>( grid, vtype, hints, volatileCache.unchecked()::get );

		return new ValuePair<>( gauss, vgauss );
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();
		final VolatileUnsignedShortType vtype = new VolatileUnsignedShortType();

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
		final int numFetcherThreads = 4;
		final BlockingFetchQueues< Callable< ? > > queue = new BlockingFetchQueues<>( maxNumLevels );
		new FetcherThreads( queue, numFetcherThreads );

		final Pair< Img< UnsignedShortType >, Img< VolatileUnsignedShortType > > gauss1 = createGauss( Views.extendBorder( img ), 5, grid, queue );
		final Pair< Img< UnsignedShortType >, Img< VolatileUnsignedShortType > > gauss2 = createGauss( Views.extendBorder( img ), 4, grid, queue );

		BdvFunctions.show( gauss1.getB(), "Gauss 1", BdvOptions.options().addTo( bdv ) );
		BdvFunctions.show( gauss2.getB(), "Gauss 2", BdvOptions.options().addTo( bdv ) );
	}
}
