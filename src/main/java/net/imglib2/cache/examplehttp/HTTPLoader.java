package net.imglib2.cache.examplehttp;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.LongFunction;

import org.apache.commons.io.IOUtils;

import net.imglib2.Interval;
import net.imglib2.cache.CacheLoader;
import net.imglib2.util.Intervals;

public class HTTPLoader< A > implements CacheLoader< Interval, A >
{

	public static final String GET = "GET";


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
	public A get( final Interval interval ) throws IOException
	{
		final String address = addressComposer.apply( interval );
		final URL url = new URL( address );
		final HttpURLConnection connection = ( HttpURLConnection ) url.openConnection();
		connection.setRequestMethod( GET );
		final int responseCode = connection.getResponseCode();

		if ( responseCode != 200 )
			throw new RuntimeException( "Request failed with code " + responseCode );


		final long numElements = Intervals.numElements( interval );
		final InputStream stream = connection.getInputStream();
		final byte[] response = IOUtils.toByteArray( stream );
//		System.out.println( response.length + " " + numElements + " " + bytesPerPixel );

		final A access = accessFactory.apply(numElements );
		copyToAccess.accept(response, access );

		return access;

	}

}
