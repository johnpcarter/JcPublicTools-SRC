package com.jc.net;

import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import com.jc.util.HashMapWithTimeToLive;

public class ReliableOutboundHttpConnectionFactory 
{
	private static ReliableOutboundHttpConnectionFactory 	_default;
	
	private Map<String, ReliableOutboundHttpConnection> 	_partialTransfers;
	
	static
	{
		_default = new ReliableOutboundHttpConnectionFactory();
	}
	
	public ReliableOutboundHttpConnectionFactory()
	{
		_partialTransfers = new HashMapWithTimeToLive<String, ReliableOutboundHttpConnection>(100, 0.85f);
	}
	
	public static ReliableOutboundHttpConnection getConnection(String uniqueId, URL endPoint, String login, String password, Map<String, String> headerProperties, int chunkSize, boolean compressContent)
	{
		return _default.getReliableOutboundHttpConnection(uniqueId, endPoint, login, password, headerProperties, chunkSize, compressContent);
	}	
	
	public ReliableOutboundHttpConnection getReliableOutboundHttpConnection(String uniqueId, URL endPoint, String login, String password, Map<String, String> headerProperties, int chunkSize, boolean compressContent)
	{
		ReliableOutboundHttpConnection conn = null;
	
// first check for any existing cached transfer with this id. If so return it, any transfers will continue from where they left off
		
		if ((conn=_partialTransfers.get(uniqueId)) == null)
			conn = new PersistenceReliableOutboundHttpConnection(uniqueId, endPoint, login, password, headerProperties, chunkSize, compressContent);
			
		return conn;
	}
	
	private class PersistenceReliableOutboundHttpConnection extends ReliableOutboundHttpConnection
	{
		public PersistenceReliableOutboundHttpConnection(String uniqueId, URL endPoint, String login, String password, Map<String, String> headerProperties, int chunkSize, boolean compressContent) 
		{
			super(uniqueId, endPoint, login, password, headerProperties, chunkSize, compressContent);
		}
		
		@Override
		public HttpResponse send(InputStream data, String contentType, String charset, boolean requireResponse) throws InvalidConnectionException, ConnectionInterruptedException, InvalidDataInputException
		{
			try {
				return super.send(data, contentType, charset, requireResponse);
			}
			catch(ConnectionInterruptedException e)
			{
// if the transfer was interrupted, flag error as normal but also cache the connection object so that if the initiator chooses to retry
// the transfer will continue from where it failed.
				
				_partialTransfers.put(getUniqueId(), this);
				
				throw e;
			}
		}
	}
}
