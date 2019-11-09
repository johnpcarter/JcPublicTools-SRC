package com.jc.net;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.log4j.Logger;

import sun.misc.BASE64Encoder;

public class ReliableOutboundHttpConnection 
{
	public static final String		MY_ID = "wm-applicqtion-custom-ReliableOutboundHttpConnection";
	public static final String		DEFAULT_CONTENT_TYPE = "application/bin";
	
	public static final String		CONTENT_TYPE_TAG = "Content-Type";
	public static final String		UNIQUE_ID = "uniqueId";
	public static final String		OFFSET = "currentOffset";
	
	private URL						_endPoint;
	private String					_login;
	private String 					_password;
	private int 					_chunkSize;
	private boolean					_compressContent;
	
	private Map<String, String>	_headerProperties;
	
	private String	_contentType;
	private String	_charset;
	
	private long	_currentOffset;
	private String	_uniqueId;
	
	public ReliableOutboundHttpConnection(String uniqueId, URL endPoint, String login, String password, Map<String, String> headerProperties, int chunkSize, boolean compressContent)
	{
		_currentOffset = 0;
		_uniqueId = uniqueId;
		_endPoint = endPoint;
		_login = login;
		_password = password;
		_chunkSize = chunkSize;
		_compressContent = compressContent;
		_headerProperties = headerProperties;
	}
	
	protected String getUniqueId()
	{
		return _uniqueId;
	}
	
	public HttpResponse sendFile(File dataFile, int retries, long retryInterval, float retryFactor, boolean requiresResponse) throws InvalidConnectionException, ConnectionInterruptedException, InvalidDataInputException, FileNotFoundException
	{
		ConnectionInterruptedException lastError = null;
		InputStream data = null;
		HttpResponse response = null;
		int attempts = 0;
		
		while ((response == null || response.getResponseCode() != 200) && attempts <= retries)
		{
			try 
			{
				data = new BufferedInputStream(new FileInputStream(dataFile));
				response = send(data, DEFAULT_CONTENT_TYPE, Charset.defaultCharset().name(), requiresResponse);
	
				// server has requested that we restart from the specified value
				
				if (response.getResponseCode() == ReliableInboundHttpConnection.REQUEST_OFFSET)
				{
					_currentOffset = Long.parseLong(response.getResponseHeaders().get(ReliableOutboundHttpConnection.OFFSET));				
					Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Server requested restart from offset : " + _currentOffset);
				}
				else if (response.getResponseCode() != 200)
				{
					attempts += 1;
				}
			}
			catch(ConnectionInterruptedException e) // triggered if we can't connect to remote server
			{
				lastError = e;
				attempts += 1;
				
				try {
					Thread.sleep(retryInterval);
				} catch (InterruptedException e1) {
					throw new RuntimeException(e1);
				}
			
				if (retryFactor > 0)
					retryInterval = (long) (retryFactor * retryInterval);
			}
			finally
			{
				if (data != null)
					try {
						data.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
			}
		} // end while
		
		if (response != null)
			return response;
		else
			throw lastError;
	}
	
	public HttpResponse send(InputStream data, boolean requiresResponse) throws InvalidConnectionException, ConnectionInterruptedException, InvalidDataInputException
	{
		return send(data, null, null, requiresResponse);
	}
	
	public HttpResponse send(InputStream data, String contentType, String charset, boolean requireResponse) throws InvalidConnectionException, ConnectionInterruptedException, InvalidDataInputException
	{
		_contentType = contentType;
		_charset = charset;
		
		return sendImpl(data, requireResponse);
	}
	
	private HttpResponse sendImpl(InputStream data, boolean requireResponse) throws InvalidConnectionException, ConnectionInterruptedException, InvalidDataInputException
	{
		HttpURLConnection conn = null;

		// request
		Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Connecting to : " + _endPoint.toExternalForm());

		try {
			conn = (HttpURLConnection) _endPoint.openConnection();
		} catch (IOException e) {
			throw new  ConnectionInterruptedException(e);
		}
		
		conn.setDoOutput(true);
		
		if (requireResponse)
			conn.setDoInput(true);
		
		OutputStream out = null;
		InputStream in = null;
		
		try 
		{			
			conn.setRequestMethod("POST");
			conn.setChunkedStreamingMode(ReliableInboundHttpConnection.BUFFER_SIZE);	// bug in java 6 means we can't go higher than 10kb
			
			setHttpHeader(conn);

			out = new BufferedOutputStream(conn.getOutputStream());

			Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Sending data");
			
			sendData(out, data);
			
			Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Finished sending");
			
			out.close();
			
		// prepare response if completed successfully
			
			Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Getting response code");
			
			return new HttpResponseImpl(conn, requireResponse);
		}
		catch (ConnectionInterruptedException e) 
		{			
			try {
				return new HttpResponseImpl(conn, requireResponse);
			} 
			catch (IOException e1) // couldn't read any response either, so throw original exception
			{
				throw e;
			}
		} catch (IOException e) {
			throw new ConnectionInterruptedException(e);
		}
		finally
		{
			if (out != null)
				try {
					out.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
			if (in != null)
				try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
			Logger.getLogger(ReliableOutboundHttpConnection.class).debug("Disconnecting");
			
			conn.disconnect();
		}
	}
	
	private void setHttpHeader(HttpURLConnection conn) throws InvalidConnectionException
	{					
		conn.setRequestProperty("User-Agent", MY_ID);
		conn.setRequestProperty("Accept", "application/bin");
		conn.setRequestProperty(UNIQUE_ID, _uniqueId);
		conn.setRequestProperty(OFFSET, "" + _currentOffset);
		
		if (_headerProperties != null)
		{
			for (String key : _headerProperties.keySet())
			{
				if (!key.equalsIgnoreCase("contentType") && _headerProperties.get(key) != null)
					conn.setRequestProperty(key, _headerProperties.get(key));
			}
		}
		
		if (_login != null)
		{
			String authStr = _login + ":" + _password;
			String encodedLogin = new BASE64Encoder().encodeBuffer(authStr.getBytes());	//TODO: replace with Apache commons
			encodedLogin = encodedLogin.replaceAll("\n", ""); // Base64Encoder adds a new line if we go over 76 characters, which is not allowed in properties
			conn.setRequestProperty("Authorization", "Basic " + encodedLogin);
		}
		
		if (_charset != null)
			conn.setRequestProperty("Accept-Charset", _charset);
		
		if (_contentType == null)
			_contentType = "text/plain";
		
		if (_charset != null)
			conn.setRequestProperty(CONTENT_TYPE_TAG, _contentType + "; charset=" + _charset);
		else
			conn.setRequestProperty(CONTENT_TYPE_TAG, _contentType);			
	}
	
	private boolean sendData(OutputStream httpOut, InputStream data) throws ConnectionInterruptedException, InvalidDataInputException
	{
		OutputStream out = null;
		byte[] buf = new byte[1024];
		int bytesRead = -1;
		
		try {
			if (_currentOffset > 0 && !_compressContent)
				data.skip(_currentOffset);
		} catch (IOException e) {
			throw new InvalidDataInputException(e);
		}
		
		try {
			if (_compressContent)
				out = new GZIPOutputStream(_currentOffset == 0 ? httpOut : new OffsetOutputStream(httpOut, _currentOffset));
			else
				out = httpOut;
		} 
		catch (IOException e) {
			throw new ConnectionInterruptedException(e);
		}
		
		try
		{
			while((bytesRead=data.read(buf)) > 0)
			{
				try {
					out.write(buf, 0, bytesRead);
				} 
				catch (IOException e) 
				{
					Logger.getLogger(ReliableOutboundHttpConnection.class).error(e.getMessage());
	
	// transmission was interrupted
					throw new ConnectionInterruptedException(e);
				}			
			}
		}
		catch(IOException e)
		{
			throw new InvalidDataInputException(e);
		}
		
		try
		{
			if (_compressContent)
				((GZIPOutputStream) out).finish();
		
			out.flush();
		} 
		catch (IOException e) 
		{
			Logger.getLogger(ReliableOutboundHttpConnection.class).error(e.getMessage());

//transmission was interrupted
			throw new ConnectionInterruptedException(e, -1);
		}
		
		return true;
	}
	
	public class HttpResponseImpl implements HttpResponse
	{
		private String					_httpType;
		private int 					_responseCode;
		private Map<String, String> 	_responseHeaders;
		private String 					_responseMessage;
		private byte[]					_body;
		
		public HttpResponseImpl(int responseCode, HttpURLConnection conn) throws IOException
		{
			_responseCode = responseCode;
			_responseHeaders = new HashMap<String, String>();
			
			// retrieve headers
			
			for (int i=0; ; i++) 
			{
		        String headerName = conn.getHeaderFieldKey(i);
		        String headerValue = conn.getHeaderField(i);

		        if (headerName == null && headerValue == null) 		            // No more headers
		            break;
		        
		        if (headerName == null) 
		        	_httpType = headerValue;
		        else
		        	_responseHeaders.put(headerName, headerValue);
			}
		}
		
		public HttpResponseImpl(HttpURLConnection conn, boolean requiresResponse) throws IOException
		{
			this(conn.getResponseCode(), conn);
			
		    // read response body into a byte array if expecting a formal response

			if (requiresResponse && conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
			{
				InputStream in = conn.getInputStream();
				
				if (in != null)
				{   		        
					_body = new byte[_chunkSize];
		       
					if (_compressContent)
						new GZIPInputStream(in).read(_body);
					else
						in.read(_body);
				}
			}
		}
		
		@Override
		public String getHttpVersion()
		{
			return _httpType;
		}
		
		@Override
		public int getResponseCode()
		{
			return _responseCode;
		}

		@Override
		public String getResponseMessage() 
		{
			return _responseMessage;
		}

		public Map<String, String> getResponseHeaders()
		{
			return _responseHeaders;
		}
		
		@Override
		public byte[] getResponseBody()
		{
			return _body;
		}
		
	}
	
	public interface HttpResponse
	{
		public int getResponseCode();
		String getHttpVersion();
		public Map<String, String> getResponseHeaders();
		public String getResponseMessage();
		public byte[] getResponseBody();
	}
	
	public class InvalidDataInputException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public InvalidDataInputException(Exception e)
		{
			super(e);
		}
	}
	
	public class InvalidConnectionException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public InvalidConnectionException(Exception e)
		{
			super(e);
		}
	}
	
	public class ConnectionInterruptedException extends Exception
	{
		private static final long serialVersionUID = 1L;

		private long 	_offset;
		private boolean _isServerReset;
		
		public ConnectionInterruptedException(Exception e)
		{
			super(e);
			_isServerReset = false;
			_offset = 0;
		}
		
		public ConnectionInterruptedException(long offset)
		{
			_offset = offset;
			_isServerReset = true;
		}
		
		public ConnectionInterruptedException(Exception e, long offset)
		{
			super(e);
			_offset = offset;
			_isServerReset = false;
		}
		
		public long getOffset()
		{
			return _offset;
		}
		
		public boolean isServerReset()
		{
			return _isServerReset;
		}
	}
	
	private class OffsetOutputStream extends FilterOutputStream
	{
		private long 	_requiredOffset;
		private long 	_offsetCount;
		private boolean _reachedOffset;
		
		public OffsetOutputStream(OutputStream out, long offset)
		{
			super(out);
			
			_requiredOffset = offset;
			_offsetCount = 0;
			_reachedOffset = false;
		}
		
		@Override
		public void write(int b) throws IOException 
		{
			if (_reachedOffset)
				super.write(b);
			else if (_offsetCount+1 >= _requiredOffset)
				_reachedOffset = true;
			else
				_offsetCount += 1;
		}
		
		@Override
		public void write(byte[] b) throws IOException
		{
			this.write(b, 0, b.length);
		}
		
		@Override
		public void write(byte[] b, int off, int len) throws IOException 
		{
			if (_reachedOffset)
			{
			// now reached offset so write to underlying stream
				
				super.write(b, off, len);
			}
			else 
			{
			// check if we have now reached the offset
				
				if (_offsetCount + len >= _requiredOffset)
				{
					_reachedOffset = true;
					
					int discardFromBuffer = (int) (_requiredOffset - _offsetCount);
					
					super.write(b, off + discardFromBuffer, len - discardFromBuffer);
				}
				else // not there yet, but keep count
				{
					_offsetCount += len;
				}
			}
		}
	}
}
