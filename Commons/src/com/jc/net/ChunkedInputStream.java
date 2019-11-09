package com.jc.net;

import java.io.IOException;
import java.io.InputStream;

public class ChunkedInputStream extends InputStream 
{
	private InputStream		_wrappedInputStream;
	private byte[]			_chunkBuf;
	private int				_chunkIndex;
	
	public ChunkedInputStream(InputStream in)
	{
		_wrappedInputStream = in;
		_chunkBuf = null;
		_chunkIndex = -1;
	}

	@Override
	public int read() throws IOException 
	{
		return 0;
	}
	
	@Override
	public int read(byte[] buf) throws IOException
	{
		return read(buf, 0, buf.length);
	}
	
	@Override
	public int read(byte[] buf, int offset, int len) throws IOException
	{
		if (_chunkBuf == null || _chunkIndex >= _chunkBuf.length)
		{
			int chunkSize = readChunkSizeFromInputStream(_wrappedInputStream);
			_chunkBuf = new byte[chunkSize];
			_chunkIndex = 0;
						
			// read next chunk
			
			if (chunkSize > 0)
			{
				int bytesRead = _wrappedInputStream.read(_chunkBuf);
				
				// read trailing CRLF
				_wrappedInputStream.read(); //CR \r
				_wrappedInputStream.read(); //LF \n
				
				if (bytesRead != _chunkBuf.length)
					throw new IOException("Invalid chunk size, expected: " + _chunkBuf.length + ", but got:" + bytesRead);
			}
			else
			{
				return 0; // reached the end of the stream
			}
		}
		
		int bytesCopied = 0;
		
		while(bytesCopied < len)
		{
			buf[offset + bytesCopied++] = _chunkBuf[_chunkIndex++];
			
			if (_chunkIndex >= _chunkBuf.length)
			{
				bytesCopied += read(buf, offset+bytesCopied, len-bytesCopied);
				break;
			}
		}
		
		return bytesCopied;
	}
	
	private int readChunkSizeFromInputStream(InputStream in) throws IOException
	{
		String rawValue = null;
		
		try
		{
			rawValue = "0x" + readLineFromInputStream(in);
			return Integer.decode(rawValue);
		}
		catch(Exception e)
		{
			throw new IOException("Invalid chunk marker:" + rawValue);
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
}