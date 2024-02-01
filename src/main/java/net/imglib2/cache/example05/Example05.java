package net.imglib2.cache.example05;

import bdv.cache.SharedQueue;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.volatiles.VolatileViews;
import java.io.IOException;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.cache.img.optional.CacheOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

public class Example05
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


		final RandomAccessible< UnsignedShortType > source = Views.extendBorder( img );
		final double[] sigma1 = new double[] { 5, 5, 5 };
		final double[] sigma2 = new double[] { 4, 4, 4 };
		final Img< UnsignedShortType > gauss1 = factory.create( dimensions, cell -> Gauss3.gauss( sigma1, source, cell ), options().initializeCellsAsDirty( true ) );
		final Img< UnsignedShortType > gauss2 = factory.create( dimensions, cell -> Gauss3.gauss( sigma2, source, cell ), options().initializeCellsAsDirty( true ) );

//		BdvFunctions.show( gauss1, "Gauss 1", BdvOptions.options().addTo( bdv ) );
//		BdvFunctions.show( gauss2, "Gauss 2", BdvOptions.options().addTo( bdv ) );

		final SharedQueue queue = new SharedQueue( 7 );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( gauss1, queue ), "Gauss 1", BdvOptions.options().addTo( bdv ) );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( gauss2, queue ), "Gauss 2", BdvOptions.options().addTo( bdv ) );
	}
}
