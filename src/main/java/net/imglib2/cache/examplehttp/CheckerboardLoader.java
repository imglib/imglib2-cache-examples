package net.imglib2.cache.examplehttp;

import java.util.Arrays;

import net.imglib2.cache.CacheLoader;
import net.imglib2.img.basictypeaccess.array.DirtyIntArray;
import net.imglib2.img.cell.Cell;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.util.Intervals;

public class CheckerboardLoader implements CacheLoader< Long, Cell< DirtyIntArray > >
{
	private final CellGrid grid;

	public CheckerboardLoader( final CellGrid grid )
	{
		this.grid = grid;
	}

	@Override
	public Cell< DirtyIntArray > get( final Long key ) throws Exception
	{
		final long index = key;

		final int n = grid.numDimensions();
		final long[] cellMin = new long[ n ];
		final int[] cellDims = new int[ n ];
		grid.getCellDimensions( index, cellMin, cellDims );
		final int blocksize = ( int ) Intervals.numElements( cellDims );
		final DirtyIntArray array = new DirtyIntArray( blocksize );

		final long[] cellGridPosition = new long[ n ];
		grid.getCellGridPositionFlat( index, cellGridPosition );
		long sum = 0;
		for ( int d = 0; d < n; ++d )
			sum += cellGridPosition[ d ];
		final int color = sum % 2 == 0 ? 0 : ( 1 << 16 ) - 1;
		Arrays.fill( array.getCurrentStorageArray(), color );

		return new Cell<>( cellDims, cellMin, array );
	}
}
