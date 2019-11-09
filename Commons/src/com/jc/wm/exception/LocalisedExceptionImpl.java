package com.jc.wm.exception;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.jc.wm.util.ServiceUtils;
import com.wm.app.b2b.server.ServiceException;

/**
 * @author John Carter (jc@johncarter.eu)
 *
 */
abstract class LocalisedExceptionImpl implements LocalisedException 
{
	private String 		_rootContextId;
	private String		_contextId;
	private String		_appId;
	private String		_serviceId;
	private String		_integrationType;
	private String		_integrationId;
	private ExceptionLevel	_level;
	private ExceptionType _type;
	
	private int			_errorCode;
	private String		_originalMessage;
	private Throwable	_originalException;
	
	private Throwable	_ownerException;
		
	//private static Map<String, String> 	_baseCodesByWmPackage;
	
	/*public static void setErrorCodesForWmPackage(Map<String, String> packageErrorCodes)
	{
		_baseCodesByWmPackage = packageErrorCodes;
	}*/
	
	protected LocalisedExceptionImpl(int code, Throwable ownerException)
	{
		_errorCode = code;
		setOwnerAndPackage();
		setContextIds();
		_ownerException = ownerException;
		_type = ExceptionType.FRAMEWORK;
		_level = ExceptionLevel.NOTSET;
	}
	
	protected LocalisedExceptionImpl(int code, Throwable ownerException, String message)
	{
		this(code, ownerException);
		
		_originalMessage = message;
	}
	
	protected LocalisedExceptionImpl(int code, Throwable ownerException, Throwable e)
	{
		this(code, ownerException);
		
		_originalException = e;
	}
	
	public ExceptionLevel getExceptionLevel()
	{		
		return _level;
	}
	
	public void setExceptionLevel(ExceptionLevel level)
	{
		_level = level;
	}
	
	public ExceptionType getExceptionType()
	{
		return _type;
	}
	
	public void setExceptionType(ExceptionType type)
	{
		_type = type;
	}
	
	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getAppId()
	 */
	@Override
	public String getAppId() 
	{
		return _appId;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getContextId()
	 */
	@Override
	public String getContextId() 
	{
		return _contextId;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getErrorCode()
	 */
	@Override
	public int getErrorCode() 
	{
		return _errorCode;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getIntegrationId()
	 */
	@Override
	public String getIntegrationId() 
	{
		return _integrationId;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getIntegrationType()
	 */
	@Override
	public String getIntegrationType() 
	{
		return _integrationType;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getLocalisedError(java.util.Locale)
	 */
	public abstract String getLocalizedMessage(Locale locale);

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getOriginalInfo()
	 */
	@Override
	public String getOriginalInfo() 
	{		
		if (_originalException != null)
			_originalMessage = _originalException.toString();
		
		return _originalMessage;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getRootContextId()
	 */
	@Override
	public String getRootContextId() 
	{
		return _rootContextId;
	}

	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#getServiceId()
	 */
	@Override
	public String getServiceId() 
	{
		return _serviceId;
	}

	@Override
	public void setServiceId(String serviceId) 
	{
		_serviceId = serviceId;
	}
	
	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#isError()
	 */
	@Override
	public boolean isError() 
	{
		return (_ownerException instanceof ServiceException);
	}
	
	/* (non-Javadoc)
	 * @see com.jc.wm.exception.LocalisedException#isWarning()
	 */
	@Override
	public boolean isWarning() 
	{
		return (_ownerException instanceof RuntimeException);
	}
	
	@Override
	public String getMessage() 
	{		
		String msg = "[" + formatCode(_errorCode) + "] ";
		
		if (_appId != null)
			msg += " " + _appId;
				
		if (getOriginalInfo() != null)
		{
			if (_appId != null)
				msg += " - " + _originalMessage;
			else
				msg += _originalMessage;
		}
		
		return msg;
	}
	
	@Override
	public String toString() 
	{		
		String err = getMessage();
		
		if (_serviceId != null)
			err += " - " + _serviceId;
		
		return err;
	}
	
	@Override
	public StackTraceElement[] getStackTrace() 
	{
		System.out.println("****** lgetStackTrace " + _originalException);
		
		if (_originalException == null)
			return new StackTraceElement[0];
		
		StackTraceElement[] trace = _originalException.getStackTrace();
		List<StackTraceElement> newTrace = new ArrayList<StackTraceElement>();
		
		int i = 0;
		while (i< trace.length && !trace[i].getClassName().contains("NativeMethodAccessorImpl"))
			newTrace.add(trace[i++]);
		
		return newTrace.toArray(new StackTraceElement[newTrace.size()]);
	}
	
	protected void setOwnerAndPackage()
	{
		try {
    		_appId = ServiceUtils.getPackageForCaller(true);
    		_serviceId = ServiceUtils.getCallingService();
    	} catch (Throwable e) 
    	{
    		_appId = UNKNOWN_APP;
    		_serviceId = UNKNOWN_SERVICE;
    	}	
	}
	
	protected void setContextIds()
	{
		try {
			String[] ids = ServiceUtils.getContextIDsForService();
			
    		_rootContextId = ids[ids.length-1];	// root context id is always last
    		_contextId = ids[0];	// context is always first
    	}
		catch(Throwable e)
    	{
			_rootContextId = CONTEXT_UNKNOWN;
			_contextId = CONTEXT_UNKNOWN;
		}
	}
	
	private String formatCode(int code)
	{
		String out = null;
		
		if (code < 10)
			out  = "000" + code;
		else if (code < 100)
			out = "00" + code;
		else if (code < 1000)
			out = "0" + code;
		else
			out = "" + code;
		
		return out;
	}		
	
	/*private int reformatErrorCode(int errorCode)
	{				
		try
		{			
			int pckErrorCode = 0;

			if (_baseCodesByWmPackage != null)
			{
				String pckCode = _baseCodesByWmPackage.get(ServiceUtils.getPackageForCaller());
				
				if (pckCode != null)
				{
					try {
						pckErrorCode = Integer.parseInt(pckCode);
					} catch(Exception e) {}; // do now't if invalid, just use default
				}
			}
			
			return pckErrorCode + errorCode;
		}
		catch(Exception e)
		{
			return 0; // Used for calls outside of wm
		}
	}*/
}
