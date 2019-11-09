package com.jc.wm.exception;

import java.util.Locale;

import com.wm.app.b2b.server.ServiceException;

/**
 * Exception class that acts the root for all exceptions triggered from wm packages.
 * All error codes start from the base 1000
 *
 * @author : John Carter (integrate@johncarter.eu)
 * @version : 1.0
 */
public class IntegrationException extends ServiceException implements LocalisedException
{	
	private static final long 		serialVersionUID = 6499984231379503085L;

	private LocalisedException 		_wrappedExceptionImpl;
	
	private static Localizer 		_localizer;
	
    /**
     * Report an exception using a unique code identifier
     * 
     * @param code Represents the type of error raised
     */
    public IntegrationException(int code)
    {
    	_wrappedExceptionImpl = new LocalisedExceptionImpl(code, this)
    	{
			@Override
			public String getLocalizedMessage(Locale locale) {
				return IntegrationException.this.getLocalizedMessage(locale);
			}
    	};    	
    }
    
    /**
     * Constructor with nested exception. The owner is automatically set to the
     * name of the package identified by the calling service.
     *
     * @param code The error code that represents the context of why the exception was raised
     * @param e The exception that needs to passed back
     */
    public IntegrationException(int code, Throwable e)
    {
    	super(e);
    	setStackTrace(e.getStackTrace());
    	
    	_wrappedExceptionImpl = new LocalisedExceptionImpl(code, this, e)
    	{
			@Override
			public String getLocalizedMessage(Locale locale) {
				// TODO Auto-generated method stub
				return null;
			}
    	};
    }

    /**
     * Constructor. The owner is automatically set to the name of the package
     * identified by the calling service.
     *
     * @param code The error code that represents this exception
     * @param message localised error message to better aid tracability
     */
    public IntegrationException(int code, String message)
    {
    	super(message);
        
    	_wrappedExceptionImpl = new LocalisedExceptionImpl(code, this, message)
    	{
			@Override
			public String getLocalizedMessage(Locale locale) {
				return IntegrationException.this.getLocalizedMessage(locale);
			}
    	};
    }
    
    public static void setLocalizer(Localizer localizer)
    {
    	_localizer = localizer;
    }
    
    @Override
    public String getLocalizedMessage(Locale locale) 
    {    	
    	if (_localizer == null)
    		return _wrappedExceptionImpl.getMessage();
    	else
    		return localisedMessage(locale) + " (" + _wrappedExceptionImpl.getErrorCode() + ")";
    }
    
    private String localisedMessage(Locale locale)
    {
    	try {
    		return _localizer.getLocalizedMessage(locale, _wrappedExceptionImpl.getErrorCode(), _wrappedExceptionImpl.getAppId(), _wrappedExceptionImpl.getMessage());
    	}
    	catch(Throwable e) // in the case of error, don't try to localise the message!
    	{
    		e.printStackTrace();
    		return _wrappedExceptionImpl.getMessage();
    	}
    }
    
    @Override
    public String getLocalizedMessage() 
    {
    	// TODO
    	return super.getLocalizedMessage();
    }
    
    @Override
    public StackTraceElement[] getStackTrace() 
    {
    	StackTraceElement[] trace = _wrappedExceptionImpl.getStackTrace();
    	
    	if (trace != null)
    		return trace;
    	else
    		return super.getStackTrace();
    }
    
    @Override
    public Throwable getCause() 
    {
    	// TODO
    	return super.getCause();
    }
    
    @Override
    public String getMessage() 
    {
    	return _wrappedExceptionImpl.getMessage();
    }
    
    @Override
    public String toString() 
    {
    	return _wrappedExceptionImpl.toString();
    }
    
    @Override
    public ExceptionType getExceptionType() 
    {
    	return _wrappedExceptionImpl.getExceptionType();
    }
    
    @Override
    public void setExceptionType(ExceptionType type) 
    {
    	_wrappedExceptionImpl.setExceptionType(type);
    }
    
    @Override
    public ExceptionLevel getExceptionLevel()
	{
    	if (_wrappedExceptionImpl.getExceptionLevel() == ExceptionLevel.NOTSET)
    		return ExceptionLevel.FATAL;
    	else
    		return _wrappedExceptionImpl.getExceptionLevel();
	}

    @Override
	public void setExceptionLevel(ExceptionLevel level)
	{
		if (level != ExceptionLevel.WARNING)		// Integration exceptions cannot flagged as warning only!
			_wrappedExceptionImpl.setExceptionLevel(level);
	}
    
	@Override
	public String getAppId() 
	{
		return _wrappedExceptionImpl.getAppId();
	}

	@Override
	public String getContextId() {
		return _wrappedExceptionImpl.getContextId();
	}

	@Override
	public int getErrorCode() {
		return _wrappedExceptionImpl.getErrorCode();
	}

	@Override
	public String getIntegrationId() {
		return _wrappedExceptionImpl.getIntegrationId();
	}

	@Override
	public String getIntegrationType() {
		return _wrappedExceptionImpl.getIntegrationType();
	}

	@Override
	public String getOriginalInfo() {
		return _wrappedExceptionImpl.getOriginalInfo();
	}

	@Override
	public String getRootContextId() {
		return _wrappedExceptionImpl.getRootContextId();
	}

	@Override
	public String getServiceId() {
		return _wrappedExceptionImpl.getServiceId();
	}

	@Override
	public void setServiceId(String serviceId) 
	{
		_wrappedExceptionImpl.setServiceId(serviceId);
	}
	
	@Override
	public boolean isError() {
		return _wrappedExceptionImpl.isError();
	}

	@Override
	public boolean isWarning() {
		return _wrappedExceptionImpl.isWarning();
	}
}
