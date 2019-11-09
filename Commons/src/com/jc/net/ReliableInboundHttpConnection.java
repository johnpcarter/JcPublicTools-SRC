package com.jc.net;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.log4j.Logger;

public class ReliableInboundHttpConnection 
{	
	public final static int				REQUEST_OFFSET = 205;
	public final static int				BUFFER_SIZE = 10240;
	
	private File						_tempDir;
	private boolean						_decompressContent;
	
	public ReliableInboundHttpConnection(String tempDir, boolean decompressContent)
	{
		_decompressContent = decompressContent;
		_tempDir = new File(tempDir);
		
		if (!_tempDir.exists() || !_tempDir.canWrite())
			throw new RuntimeException("Temp directory does not exist or cannot be written to : " + _tempDir.getAbsolutePath());
	}
	
	public HttpRequest read(Socket connection) throws IOException, InvalidConnectionException
	{
		Map<String, String> requestHeaders = new HashMap<String, String>();
		
        InputStream in = connection.getInputStream();

        // Read http headers

        String header = null;
        
        while(!(header=readLineFromInputStream(in)).isEmpty())
        {
        	Logger.getLogger(ReliableInboundHttpConnection.class).debug("Reading header: " + new String(header));
        	
        	int indexOfSeparator = header.indexOf(":");
        	
        	if (indexOfSeparator != -1)
        		requestHeaders.put(header.substring(0, indexOfSeparator), header.substring(indexOfSeparator+2));
        }
        
        // process http body
                
    	Logger.getLogger(ReliableInboundHttpConnection.class).debug("Reading request body");

    	HttpRequestResponse requestReply = null;
        String encoding = null;
        
        if ((encoding=requestHeaders.get("Transfer-Encoding")) != null && encoding.equalsIgnoreCase("chunked"))
        	requestReply = read(new ChunkedInputStream(in), requestHeaders);
        else
        	requestReply = read(new BufferedInputStream(in), requestHeaders);
        
        if (requestReply.getResponseCode() == 200)
        	Logger.getLogger(ReliableInboundHttpConnection.class).debug("Successfully cached request body in file: " + requestReply.getCachedFileName());
        
    	// set http response
    	
    	OutputStream out = connection.getOutputStream();
    	
    	out.write(constructHttpResponseHeader(requestReply).getBytes());
    	
    	return requestReply;
	}
	
	public HttpRequestResponse read(InputStream in, Map<String, String> requestHeaders) throws InvalidConnectionException
	{		
		if (in == null)
			throw new InvalidConnectionException("No input stream given, please configure content-type '" + requestHeaders.get(ReliableOutboundHttpConnection.CONTENT_TYPE_TAG) + "' with stream content handler");
		
		String uniqueId = requestHeaders.get(ReliableOutboundHttpConnection.UNIQUE_ID);
		long offset = 0;
		
		try {
			offset = Long.parseLong(requestHeaders.get(ReliableOutboundHttpConnection.OFFSET));
		} catch(Exception e)
		{
			// do now't
		}
		
		Logger.getLogger(ReliableInboundHttpConnection.class).debug("Processing body with id: " + uniqueId);
		
		if (uniqueId == null)
			throw new InvalidConnectionException("Unique id header missing");

		try 
		{
			ReliableInboundHttpConnectionReader reader = new ReliableInboundHttpConnectionReader(uniqueId, offset);
			File cachedFile = reader.cache(in);
			
			return new HttpRequestReplyImpl(cachedFile, 200);
		} 
		catch (ConnectionResetException e) // flags sender to redo request starting from given offset
		{
			Logger.getLogger(ReliableInboundHttpConnection.class).debug("Sending response to ask for data to start at offset: " + e.getRequiredOffset());
			
			HttpRequestReplyImpl results = new HttpRequestReplyImpl(null, REQUEST_OFFSET);
			results.setResponseHeader(ReliableOutboundHttpConnection.OFFSET, "" + e.getRequiredOffset());
			return results;
		} 
		catch (ConnectionInterruptedException e) // return code is not really relevant as the connection has been lost
		{
			Logger.getLogger(ReliableInboundHttpConnection.class).error("Request failed: " + e.getMessage());
			
			return new HttpRequestReplyImpl(null, 503);
		}
	}
	
	private String readLineFromInputStream(InputStream in) throws IOException
	{
		StringBuffer linebuffer = new StringBuffer();
		byte[] buf = new byte[1];
		char nextChar = 0;
		
		while(in.read(buf) != -1)
		{
			nextChar = new String(buf).charAt(0);
			
			if (nextChar != '\n' && nextChar != '\r')
			{
				linebuffer.append(nextChar);
			}
			else
			{
				// ensure we read the new line char that follows
				if (nextChar == '\r')
					in.read();
				
				break;
			}
		}
		
		return linebuffer.toString();
	}
	
	 private String constructHttpResponseHeader(HttpResponse reply) 
	  {
		  String s = "HTTP/1.0 ";
		
		switch (reply.getResponseCode()) 
		{
			case 200:
			    s = s + "200 OK";
			    break;
			case 205:
				s = s + "205 Reset";
				break;
			case 400:
				s = s + "400 Bad Request";
			    break;
			case 403:
			    s = s + "403 Forbidden";
			    break;
			case 404:
			    s = s + "404 Not Found";
			    break;
			case 500:
			    s = s + "500 Internal Server Error";
			    break;
			case 501:
			    s = s + "501 Not Implemented";
			    break;
			case 503:
				s = s + "503 Service Unavailable";
				break;
			default:
				s = s + "" + reply.getResponseCode() + " Other";
		}
			
		s = s + "\r\n"; //other header fields,
		s = s + "Connection: close\r\n"; //we can't handle persistent connections
		s = s + "Server: SimpleHTTPtutorial v0\r\n"; //server name
			
		Map<String, Object> headers = reply.getResponseHeaders();
		
		for (String key : headers.keySet())
		{
			s = s + key + ": " + headers.get(key) + "\n";
		}
		
		s = s + "\r\n"; //this marks the end of the httpheader
		//and the start of the body
		//ok return our newly created header!
		return s;
	}
	 
	public interface HttpRequest
	{
		public File getCachedFileName();
		public InputStream getCachedInputStream() throws IOException;

	}
	
	public interface HttpResponse
	{
		public int getResponseCode();
		public Map<String, Object> getResponseHeaders();
	}
	
	public interface HttpRequestResponse extends HttpRequest, HttpResponse
	{
		
	}
	
	private class HttpRequestReplyImpl implements HttpRequestResponse
	{
		private File 				_cachedFile;
		private int 				_responseCode;
		private Map<String, Object> _responseHeaders;
		
		public HttpRequestReplyImpl(File cachedFile, int responseCode)
		{
			_cachedFile = cachedFile;
			_responseCode = responseCode;
			_responseHeaders = new HashMap<String, Object>();
		}
		
		@Override
		public InputStream getCachedInputStream() throws IOException 
		{
			try 
			{
				return new WrappedInputStream(_cachedFile);
			} 
			catch (FileNotFoundException e) 
			{
				// can't happen but just in case
					
				throw new RuntimeException(e);
			}
		}
			
		@Override
		public File getCachedFileName() 
		{
			return _cachedFile;
		}

		@Override
		public int getResponseCode() 
		{
			return _responseCode;
		}

		public void setResponseHeader(String key, String value)
		{
			if (key != null && value != null)
				_responseHeaders.put(key, value);
		}
		
		@Override
		public Map<String, Object> getResponseHeaders() 
		{
			return _responseHeaders;
		}
	}
	
	private class ReliableInboundHttpConnectionReader
	{
		private String			_uniqueId;
		private File			_cacheFile;
		
		public ReliableInboundHttpConnectionReader(String uniqueId, long offset) throws ConnectionResetException
		{
			_uniqueId = uniqueId;
			_cacheFile = new File(_tempDir, _uniqueId);
			
			if (_cacheFile.exists())
			{				
				if (!_cacheFile.canWrite())
				throw new RuntimeException("Cache file cannot be generated or written to : " + _cacheFile.getAbsolutePath());
				else if (_cacheFile.length() != offset)
					throw new ConnectionResetException(_cacheFile.length());
			}
		}
		
		public File cache(InputStream in) throws ConnectionInterruptedException
		{			
			// read content and write to cache file in blocks
						
			OutputStream cacheOut = null;
			
			// DEBUG CODE START
			
			int crashAfterCount = -1;
			int loops = 0;
			/*if (!_cacheFile.exists())
			{
				crashAfterCount = (int) (Math.random() * 10);
				Logger.getLogger(ReliableInboundHttpConnection.class).debug("Will similate connection loss after: " + crashAfterCount);
			}*/
			
			// DEBUG END
			
			try {
				cacheOut = new FileOutputStream(_cacheFile, _cacheFile.exists());
			} 
			catch (FileNotFoundException e) 
			{
				// can't happen, but throw as unmarked exception just in case
				throw new RuntimeException(e);
			}
			
			byte[] buf = new byte[BUFFER_SIZE];
			int bytesRead = -1;
			
			Logger.getLogger(ReliableInboundHttpConnection.class).debug("Caching input in file: " + _cacheFile);
						
			try 
			{
				while((bytesRead=in.read(buf)) > 0)
				{
					// DEBUG START
					
					if (crashAfterCount > 0 && loops++ >= crashAfterCount)
						throw new ConnectionInterruptedException(new Exception("Simulated network loss"));
					
					// DEBUG END
					
					cacheOut.write(buf, 0, bytesRead);
				}
			}
			catch (IOException e)
			{
				e.printStackTrace();
				throw new ConnectionInterruptedException(e);
			}
			finally
			{
				Logger.getLogger(ReliableInboundHttpConnection.class).debug("Finished reading input");
				
				try {
					cacheOut.close();
				} catch (IOException e) {
					// shouldn't happen
				}
			}
			
			// if we get here, means that we successfully cached the input stream locally, so return an input stream to it
			
			return _cacheFile;
		}
	}
	
	public class InvalidConnectionException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public InvalidConnectionException(String e)
		{
			super(e);
		}
	}
	
	public class ConnectionInterruptedException extends Exception
	{
		private static final long serialVersionUID = 1L;
		
		public ConnectionInterruptedException(Exception e)
		{
			super(e);
		}
	}
	
	public class ConnectionResetException extends Exception
	{
		private static final long serialVersionUID = 1L;
		private long _offset = -1;
		
		public ConnectionResetException(long offset)
		{
			_offset = offset;
		}
		
		public long getRequiredOffset()
		{
			return _offset;
		}
	}
	
	private class WrappedInputStream extends InputStream
	{
		private File			_fileToRead;
		private InputStream		_wrappedInputStream;
		
		public WrappedInputStream(File fileToRead) throws IOException
		{
			_fileToRead = fileToRead;
			
			if (_decompressContent)
				_wrappedInputStream = new GZIPInputStream(new BufferedInputStream(new FileInputStream(_fileToRead)));
			else
				_wrappedInputStream = new BufferedInputStream(new FileInputStream(_fileToRead));
		}
		
		@Override
		public int available() throws IOException 
		{
			return _wrappedInputStream.available();
		}
		
		@Override
		public synchronized void mark(int readlimit) 
		{
			_wrappedInputStream.mark(readlimit);
		}
		
		@Override
		public boolean markSupported() 
		{
			return _wrappedInputStream.markSupported();
		}
		
		@Override
		public int read() throws IOException 
		{
			int c = _wrappedInputStream.read();
			
			if (c == -1)
				close();
			
			return c;		
		}
		
		@Override
		public int read(byte[] b) throws IOException 
		{
			return this.read(b, 0, b.length);
		}
		
		@Override
		public int read(byte[] b, int off, int len) throws IOException 
		{
			int numRead = _wrappedInputStream.read(b, off, len);
			
			if (numRead == -1)
				close();
			
			return numRead;
		}
		
		@Override
		public synchronized void reset() throws IOException 
		{
			_wrappedInputStream.reset();
		}
		
		@Override
		public long skip(long n) throws IOException 
		{
			return _wrappedInputStream.skip(n);
		}
		
		@Override
		public void close() throws IOException 
		{
			_wrappedInputStream.close();
			_fileToRead.delete();
		}
	}
}
