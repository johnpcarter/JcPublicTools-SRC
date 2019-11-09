package com.jc.util;

/**
 * Exception class that acts the root for all exceptions triggered from Common
 *
 * @author : John Carter
 * @version : %I%, %G%
 */
public class FormattedException extends Exception
{
    /**
	 * 
	 */
	private static final long serialVersionUID = -2306357407981386611L;

	public final static String      UNKNOWN = "unknown";

    private String                  _owner;
    private int                     _code;

    /**
     * 
     * @param code
     */
    public FormattedException(int code)
    {
    	super();
    	_code = code;
    }
    
    public FormattedException(int code, String moreInfo)
    {
    	super(moreInfo);
    	_code = code;
    }
    
    /**
     * Constructor with nested exception
     *
     * @param e The exception that needs to passed back
     * @param code The error code that represents the context of why the exception was raised
     */
    public FormattedException(int code, Throwable e)
    {
        super(e);
        _code = code;

        if (e.getCause() != null)
            _owner = e.getCause().toString();
        else
            _owner = UNKNOWN;
    }

    /**
     * Constructor
     *
     * @param err localised error message to better aid tracability
     * @param owner Id of the module/component responsible for raising this exception
     * @param code The error code that represents this exception
     */
    public FormattedException(int code, String err, String owner)
    {
        super(err);
        _owner = owner;
        _code = code;
    }

    /**
     * Returns an error code that gives categorises cause of this exception.
     * @return int representing error code
     */
    public int getErrorCode()
    {
        return _code;
    }

    /**
     * owner Id of the module/component responsible that raised exception
     * @return id of the logical component that triggered the error
     */
    public String getOwner()
    {
        return _owner;
    }

	@Override
	public String getMessage()
    {
        return  formatCode(_code);
    }
    
	@Override 
	public String toString()
	{
        return  "[" + formatCode(_code) + "] " + _owner + " - " + super.toString();
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
}
