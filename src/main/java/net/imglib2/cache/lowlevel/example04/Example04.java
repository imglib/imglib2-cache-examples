package net.imglib2.cache.lowlevel.example04;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.img.basictypeaccess.AccessFlags.DIRTY;
import static net.imglib2.type.PrimitiveType.SHORT;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.IoSync;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.img.AccessIo;
import net.imglib2.cache.img.DirtyDiskCellCache;
import net.imglib2.cache.img.DiskCellCache;
import net.imglib2.cache.ref.GuardedStrongRefLoaderRemoverCache;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.AccessFlags;
import net.imglib2.img.basictypeaccess.array.DirtyShortArray;
import net.imglib2.img.basictypeaccess.array.ShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.cell.LazyCellImg;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.util.Fraction;
import net.imglib2.util.Intervals;
import net.imglib2.util.Util;
import net.imglib2.view.Views;

public class Example04
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

	public static class GaussLoader implements CacheLoader< Long, Cell< ShortArray > >
	{
		private final CellGrid grid;

		private final RandomAccessible< UnsignedShortType > source;

		public GaussLoader( final CellGrid grid, final RandomAccessible< UnsignedShortType > source )
		{
			this.grid = grid;
			this.source = source;
		}

		@Override
		public Cell< ShortArray > get( final Long key ) throws Exception
		{
			final long index = key;

			final int n = grid.numDimensions();
			final long[] cellMin = new long[ n ];
			final int[] cellDims = new int[ n ];
			grid.getCellDimensions( index, cellMin, cellDims );

			final int blocksize = ( int ) Intervals.numElements( cellDims );
			final ShortArray array = new ShortArray( blocksize );

			final Img< UnsignedShortType > img = ArrayImgs.unsignedShorts( array.getCurrentStorageArray(), Util.int2long( cellDims ) );
			Gauss3.gauss( 5, source, Views.translate( img, cellMin ) );

			return new Cell<>( cellDims, cellMin, array );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final UnsignedShortType type = new UnsignedShortType();
		final CellGrid grid = new CellGrid( dimensions, cellDimensions );
		final Path blockcache = DiskCellCache.createTempDirectory( "CellImg", true );
		final Fraction entitiesPerPixel = type.getEntitiesPerPixel();
		final DiskCellCache< DirtyShortArray > diskcache = new DirtyDiskCellCache<>(
				blockcache,
				grid,
				new CheckerboardLoader( grid ),
				AccessIo.get( SHORT, AccessFlags.setOf( DIRTY ) ),
				entitiesPerPixel );
		final IoSync< Long, Cell< DirtyShortArray > > iosync = new IoSync<>( diskcache );
		final UncheckedCache< Long, Cell< DirtyShortArray > > cache = new GuardedStrongRefLoaderRemoverCache< Long, Cell< DirtyShortArray > >( 100 )
				.withRemover( iosync )
				.withLoader( iosync )
				.unchecked();
		final Img< UnsignedShortType > img = new LazyCellImg<>( grid, new UnsignedShortType(), cache::get );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );

		final Path blockcache2 = DiskCellCache.createTempDirectory( "CellImg", true );
		final DiskCellCache< ShortArray > diskcache2 = new DiskCellCache<>(
				blockcache2,
				grid,
				new GaussLoader( grid, Views.extendBorder( img ) ),
				AccessIo.get( SHORT, AccessFlags.setOf() ),
				entitiesPerPixel );
		final IoSync< Long, Cell< ShortArray > > iosync2 = new IoSync<>( diskcache2 );
		final UncheckedCache< Long, Cell< ShortArray > > cache2 = new GuardedStrongRefLoaderRemoverCache< Long, Cell< ShortArray > >( 100 )
				.withRemover( iosync2 )
				.withLoader( iosync2 )
				.unchecked();
		final Img< UnsignedShortType > img2 = new LazyCellImg<>( grid, new UnsignedShortType(), cache2::get );

		BdvFunctions.show( img2, "Gauss", BdvOptions.options().addTo( bdv ) );
	}
}
