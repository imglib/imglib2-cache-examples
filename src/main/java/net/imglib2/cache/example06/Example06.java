package net.imglib2.cache.example06;

import static bdv.viewer.DisplayMode.SINGLE;
import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.util.BdvOptions;
import bdv.util.volatiles.SharedQueue;
import bdv.util.volatiles.VolatileViews;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.algorithm.gauss3.Gauss3;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.view.Views;

public class Example06
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
		final ShortType stype = new ShortType();

		final DiskCachedCellImgOptions factoryOptions = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 );
		final DiskCachedCellImgFactory< UnsignedShortType > factory = new DiskCachedCellImgFactory<>( type, factoryOptions );

		final CheckerboardLoader loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );
		final Img< UnsignedShortType > img = factory.create( dimensions, loader );

		final Bdv bdv = BdvFunctions.show( img, "Cached" );
		bdv.getBdvHandle().getViewerPanel().setDisplayMode( SINGLE );

		final RandomAccessible< UnsignedShortType > source = Views.extendBorder( img );
		final double[] sigma1 = new double[] { 5, 5, 5 };
		final double[] sigma2 = new double[] { 4, 4, 4 };
		final Img< UnsignedShortType > gauss1 = factory.create( dimensions, cell -> Gauss3.gauss( sigma1, source, cell, 1 ), options().initializeCellsAsDirty( true ) );
		final Img< UnsignedShortType > gauss2 = factory.create( dimensions, cell -> Gauss3.gauss( sigma2, source, cell, 1 ), options().initializeCellsAsDirty( true ) );

		final DiskCachedCellImgFactory< ShortType > sfactory = new DiskCachedCellImgFactory<>( stype, factoryOptions );
//		final Img< ShortType > diff = sfactory.create( dimensions, cell -> Views.interval( Views.pair( cell, Views.pair( gauss1, gauss2 ) ), cell ).forEach( a -> a.getA().set( ( short ) ( a.getB().getA().get() - a.getB().getB().get() + 65535 / 4 ) ) ) );
		final Img< ShortType > diff = sfactory.create( dimensions, cell -> {
			final Cursor< UnsignedShortType > in1 = Views.flatIterable( Views.interval( gauss1, cell ) ).cursor();
			final Cursor< UnsignedShortType > in2 = Views.flatIterable( Views.interval( gauss2, cell ) ).cursor();
			final Cursor< ShortType > out = Views.flatIterable( cell ).cursor();
			while ( out.hasNext() )
				out.next().set( ( short ) ( in1.next().get() - in2.next().get() + 65535 / 4 ) );
		}, options().initializeCellsAsDirty( true ) );

		final SharedQueue queue = new SharedQueue( 7 );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( gauss1, queue ), "Gauss 1", BdvOptions.options().addTo( bdv ) );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( gauss2, queue ), "Gauss 2", BdvOptions.options().addTo( bdv ) );
		BdvFunctions.show( VolatileViews.wrapAsVolatile( diff, queue ), "Diff", BdvOptions.options().addTo( bdv ) );
	}
}
