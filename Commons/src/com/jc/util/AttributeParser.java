/**
 * Generates file names using TN attributes as variables
 */
package com.jc.util;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import com.jc.util.FileBasedNextSequenceGenerator.InvalidDirectoryException;

public class AttributeParser
{
	private static final long serialVersionUID = 0xc77c7d4cL;

	public static final String DEFAULT_ID_KEY = "transferType";
	 
	public static final String SEQUENCE = "seq";
	public static final String RND = "random";
	public static final String TODAYSDATE_BEGIN = "today(";
	public static final String TODAYSDATE_END = ")";
	 
	 
	private boolean					seqDidChange;
	private transient Random 		transferIdGenerator;
	private SequenceGenerator 		seqGenerator;

    public AttributeParser(File configDir) throws InvalidDirectoryException
    {
    	this (new FileBasedNextSequenceGenerator(configDir));
    }
    
    public AttributeParser(SequenceGenerator seqGenerator)
    {
        this.transferIdGenerator = new Random((new Date()).getTime());
        this.seqGenerator = seqGenerator;
        this.seqDidChange = false;
    }

    public String parseAndReplaceVariables(String template, Map<String, String> vars) throws InvalidFormatException
    {
        StringBuffer buf = new StringBuffer(template);

    	while(buf.indexOf("{") != -1)
    		translateNextVariable(vars, buf);
    	
    	return buf.toString();
    }
    
    public String[] outputOrderedArrayAndReplaceVariables(String template, Map<java.lang.String, java.lang.String> vars) throws InvalidFormatException
    {
    	String out = this.parseAndReplaceVariables(template, vars);
    	
    	List<String> orderedList = new ArrayList<String>();
    	
    	for (StringTokenizer tk = new StringTokenizer(out, " "); tk.hasMoreElements();)
    		orderedList.add(tk.nextToken());
    	
    	return orderedList.toArray(new String[orderedList.size()]);
    }
    
    public void translateNextVariable(Map<String, String> vars, StringBuffer template) throws InvalidFormatException
    {
    	int indexOfOpenCurlyBrace = -1;
        
        if ((indexOfOpenCurlyBrace=template.indexOf("{")) != -1)
        {
        	int indexOfEndCurlyBrace = template.indexOf("}");
        	
        	if (indexOfEndCurlyBrace == -1 || indexOfEndCurlyBrace-1 <= indexOfOpenCurlyBrace)
        		throw new InvalidFormatException("Invalid variable replace at pos :" + indexOfOpenCurlyBrace + " in string :" + template.toString());
        	
        	String id = template.substring(indexOfOpenCurlyBrace+1, indexOfEndCurlyBrace);
        	
        	replaceRange(template, indexOfOpenCurlyBrace, indexOfEndCurlyBrace+1, id, vars);
        }             
    }

    private void replaceRange(StringBuffer template, int start, int end, String id, Map<String, String> vars) throws InvalidFormatException
    {
    	if (id == null)
    		template.replace(start, end, "--invalid argument--");
    	else if (id.equals(SEQUENCE))
    		template.replace(start, end, getNextSequence(vars.get(DEFAULT_ID_KEY)));
    	else if (id.startsWith(TODAYSDATE_BEGIN))
    		fixDateTime(template, start, end, new Date());
    	else if (id.equals(RND))
    		template.replace(start, end, "" + transferIdGenerator.nextInt());
    	else if (vars != null && vars.get(id) != null)
            template.replace(start, end, vars.get(id));
    	else
    		template.replace(start, end, id);
    }

    private void fixDateTime(StringBuffer template, int start, int end, Date date) throws InvalidFormatException
    {
    	int formatStart = template.indexOf("(");
    	int formatEnd = template.indexOf(TODAYSDATE_END);
    	        
        if (formatStart != -1 && formatEnd > formatStart)
        {
            String pattern = template.substring(formatStart+1, formatEnd-1);
            DateFormat format = new SimpleDateFormat(pattern);
            
            template.replace(start, end, format.format(date));
        }
        else
        {
        	throw new InvalidFormatException("Invalid date format, please use today(java-pattern), where java pattern can be any recognized java date format pattern");
        }
    }
    
    
    private String getNextSequence(String id)
    {
    	seqDidChange = true;
    	
    	return seqGenerator.getNextSequence(id);	
    }
    
    public void commitSequenceChanges(String id) throws CommitSequenceException
    {    	
    	if (seqDidChange)
    		seqGenerator.commitSequence(id);
    }
    
    public static class InvalidFormatException extends Exception
    {
		private static final long serialVersionUID = 1L;

		public InvalidFormatException(String err) 
    	{
    		super(err);
		}
    }
    
    /**
     * Interface to allow persistence sequences to be generated for each given identifier
     *
     */
    public interface SequenceGenerator
    {
    	public String getNextSequence(String id);
    	public void commitSequence(String id) throws CommitSequenceException;
    }
    
    public static class CommitSequenceException extends Exception
    {
		private static final long serialVersionUID = 1L;

		public CommitSequenceException(Exception e)
    	{
    		super(e);
    	}
    }
}
