package com.jc.wm.exception;

import java.util.Locale;

public interface LocalisedException 
{
	public static final String		UNKNOWN_SERVICE = "UNKNOWN_SERVICE";
	public static final String		UNKNOWN_APP = "UNKNOWN_APP";
	public static final String		CONTEXT_UNKNOWN = "UNKNOWN_CONTEXT";
	
	public int getErrorCode();
	
	public boolean isWarning();

	public boolean isError();	

	public ExceptionLevel getExceptionLevel();

	public void setExceptionLevel(ExceptionLevel level);
		
	public ExceptionType getExceptionType();

	public void setExceptionType(ExceptionType type);
	
	public String getIntegrationType();
	
	public String getIntegrationId();
	
	public String getOriginalInfo();
	
	public String getAppId();
	
	public String getServiceId();
	
	public void setServiceId(String serviceId);
	
	public String getRootContextId();
	
	public String getContextId();
	
	public String getMessage();
	
	public String getLocalizedMessage(Locale locale);
	
	public StackTraceElement[] getStackTrace();
	
	public enum ExceptionLevel {
		NOTSET, WARNING, SEVERE, FATAL
	}
	
	public enum ExceptionType {
		FRAMEWORK, EXTERNAL, APPLICATION, ESB
	}	
}
