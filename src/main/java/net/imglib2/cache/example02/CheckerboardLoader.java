package net.imglib2.cache.example02;

import net.imglib2.cache.img.CellLoader;
import net.imglib2.img.Img;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.type.numeric.ARGBType;

public class CheckerboardLoader implements CellLoader< ARGBType >
{
	private final CellGrid grid;

	public CheckerboardLoader( final CellGrid grid )
	{
		this.grid = grid;
	}

	@Override
	public void load( final Img< ARGBType > cell ) throws Exception
	{
		final int n = grid.numDimensions();
		long sum = 0;
		for ( int d = 0; d < n; ++d )
			sum += cell.min( d ) / grid.cellDimension( d );
		final int color = ( sum % 2 == 0 ) ? 0xff000000 : 0xff008800;
		cell.forEach( t -> t.set( color ) );
	}
}
