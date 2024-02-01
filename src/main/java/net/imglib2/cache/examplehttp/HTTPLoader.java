package net.imglib2.cache.examplehttp;

import java.io.InputStream;
import java.net.URL;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;
import net.imglib2.Interval;
import net.imglib2.util.Intervals;
import org.apache.commons.io.IOUtils;

public class HTTPLoader< A > implements Function< Interval, A >
{

	private final Function< Interval, String > addressComposer;

	private final LongFunction< A > accessFactory;

	private final BiConsumer< byte[], A > copyToAccess;

	public HTTPLoader(
			final Function< Interval, String > addressComposer,
			final LongFunction< A > accessFactory,
			final BiConsumer< byte[], A > copyToAccess )
	{
		super();
		this.addressComposer = addressComposer;
		this.accessFactory = accessFactory;
		this.copyToAccess = copyToAccess;
	}

	@Override
	public A apply( final Interval interval )
	{
		try
		{
			final String address = addressComposer.apply( interval );
			final URL url = new URL( address );
			final InputStream stream = url.openStream();
			final long numElements = Intervals.numElements( interval );
			final byte[] response = IOUtils.toByteArray( stream );

			final A access = accessFactory.apply(numElements );
			copyToAccess.accept(response, access );

			return access;
		}
		catch ( final Exception e )
		{
			throw new RuntimeException( e );
		}

	}

}
