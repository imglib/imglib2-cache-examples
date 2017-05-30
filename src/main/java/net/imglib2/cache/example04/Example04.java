package net.imglib2.cache.example04;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

public class Example04
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
			final short color = ( short ) ( ( sum & 0x01 ) == 0 ? 0x0000 : 0xffff );

			cell.forEach( t -> t.set( color ) );
		}
	}

	public static class GaussLoader implements CellLoader< UnsignedShortType >
	{
		private final RandomAccessible< UnsignedShortType > source;

		public GaussLoader( final RandomAccessible< UnsignedShortType > source )
		{
			this.source = source;
		}

		@Override
		public void load( final SingleCellArrayImg< UnsignedShortType, ? > cell ) throws Exception
		{
			Gauss3.gauss( 5, source, cell );
		}
	}

	public static void main( final String[] args ) throws IOException
	{
		System.setProperty( "apple.laf.useScreenMenuBar", "true" );

		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
		final Img< UnsignedShortType > img = new DiskCachedCellImgFactory< UnsignedShortType >( options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 ) )
						.create( dimensions, new UnsignedShortType(), loader );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );

		final GaussLoader loader2 = new GaussLoader( Views.extendBorder( img ) );
		final Img< UnsignedShortType > img2 = new DiskCachedCellImgFactory< UnsignedShortType >( options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 )
				.dirtyAccesses( false ) )
						.create( dimensions, new UnsignedShortType(), loader2 );

		BdvFunctions.show( img2, "Gauss", BdvOptions.options().addTo( bdv ) );
	}
}
