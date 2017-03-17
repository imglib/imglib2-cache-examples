package net.imglib2.cache.example02;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

import java.io.IOException;

import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import bdv.viewer.ViewerPanel;
import net.imglib2.RandomAccess;
import net.imglib2.RealPositionable;
import net.imglib2.algorithm.neighborhood.HyperSphereShape;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.DiskCachedCellImgOptions.CacheType;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.array.DirtyIntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.position.transform.Round;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.view.Views;

public class Example02b
{
	public static void main( final String[] args ) throws IOException
	{
		final int[] cellDimensions = new int[] { 64, 64, 64 };
		final long[] dimensions = new long[] { 640, 640, 640 };

		final DiskCachedCellImgOptions options = options()
				.cellDimensions( cellDimensions )
				.cacheType( CacheType.BOUNDED )
				.maxCacheSize( 100 );

		final CacheLoader< Long, Cell< DirtyIntArray > > loader = new CheckerboardLoader( new CellGrid( dimensions, cellDimensions ) );

		final Img< UnsignedByteType > img = new DiskCachedCellImgFactory< UnsignedByteType >( options ).create(
				dimensions,
				new UnsignedByteType(),
				loader );

		ImageJFunctions.show( img );

		final Bdv bdv = BdvFunctions.show( img, "Example02" );


		/*
		 * Install behaviour for painting into img with shortcut "D"
		 */

		final Behaviours behaviours = new Behaviours( new InputTriggerConfig() );
		behaviours.install( bdv.getBdvHandle().getTriggerbindings(), "paint" );
		behaviours.behaviour( new DragBehaviour()
		{
			final ViewerPanel viewer = bdv.getBdvHandle().getViewerPanel();
			final RandomAccess< Neighborhood< UnsignedByteType > > sphere = new HyperSphereShape( 10 ).neighborhoodsRandomAccessible( Views.extendZero( img ) ).randomAccess();
			final RealPositionable roundpos = new Round<>( sphere );

			void draw( final int x, final int y )
			{
				viewer.displayToGlobalCoordinates( x, y, roundpos );
				sphere.get().forEach( t -> t.set( 0xFF ) );
				viewer.requestRepaint();
			}

			@Override
			public void init( final int x, final int y )
			{
				draw( x, y );
			}

			@Override
			public void end( final int x, final int y )
			{}

			@Override
			public void drag( final int x, final int y )
			{
				draw( x, y );
			}
		}, "paint", "D" );
	}
}
