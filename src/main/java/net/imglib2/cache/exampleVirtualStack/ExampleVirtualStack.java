package net.imglib2.cache.exampleVirtualStack;

import bdv.util.BdvFunctions;
import bdv.util.volatiles.VolatileViews;
import ij.IJ;
import ij.ImagePlus;
import net.imglib2.cache.CacheLoader;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.ReadOnlyCachedCellImgFactory;
import net.imglib2.cache.img.ReadOnlyCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.integer.UnsignedShortType;

public class ExampleVirtualStack
{
	public static void main( final String[] args )
	{
		new ij.ImageJ();
		final ImagePlus imp = IJ.openVirtual( "/Users/pietzsch/Desktop/mosaic-red.tif" );

		// assuming we know it is a 3D, 16-bit stack...

		final long[] dimensions = new long[] {
				imp.getStack().getWidth(),
				imp.getStack().getHeight(),
				imp.getStack().getSize()
		};

		// set up cell size such that one cell is one plane
		final int[] cellDimensions = new int[] {
				imp.getStack().getWidth(),
				imp.getStack().getHeight(),
				1
		};

		final CellLoader< UnsignedShortType > loader = new CellLoader< UnsignedShortType >()
		{
			@Override
			public void load( final SingleCellArrayImg< UnsignedShortType, ? > cell ) throws Exception
			{
				final int z = ( int ) cell.min( 2 );
				System.out.println( "load " + z );
				final short[] impdata = ( short[] ) imp.getStack().getProcessor( 1 + z ).getPixels();
				final short[] celldata = ( short[] ) cell.getStorageArray();
				System.arraycopy( impdata, 0, celldata, 0, celldata.length );
			}
		};


		final CacheLoader< Long, Cell< VolatileShortArray > > cloader = new CacheLoader< Long, Cell< VolatileShortArray > >()
		{
			final CellGrid grid = new CellGrid( dimensions, cellDimensions );

			@Override
			public Cell< VolatileShortArray > get( final Long key ) throws Exception
			{
				final long index = key;
				final long[] cellMin = new long[ grid.numDimensions() ];
				final int[] cellDims = new int[ grid.numDimensions() ];
				grid.getCellDimensions( index, cellMin, cellDims );
				final int z = ( int ) cellMin[ 2 ];
				final short[] impdata = ( short[] ) imp.getStack().getProcessor( 1 + z ).getPixels();
				final VolatileShortArray array = new VolatileShortArray( impdata, true );
				return new Cell<>( cellDims, cellMin, array );
			}
		};

//		final Img< UnsignedShortType > img = new ReadOnlyCachedCellImgFactory().createWithCacheLoader( dimensions, new UnsignedShortType(), cloader, options()
//				.cacheType( CacheType.BOUNDED )
//				.maxCacheSize( 100 )
//				.cellDimensions( cellDimensions ) );

		final Img< UnsignedShortType > img = new ReadOnlyCachedCellImgFactory().create(
				dimensions,
				new UnsignedShortType(),
				loader,
				ReadOnlyCachedCellImgOptions.options().cellDimensions( cellDimensions ) );

		BdvFunctions.show( VolatileViews.wrapAsVolatile( img ), "img" );
	}
}
