package com.jc.wm.exception;

import java.util.Locale;

public interface Localizer 
{
	public String getLocalizedMessage(Locale locale, int messageCode, String appId, String originalMessage);
}
