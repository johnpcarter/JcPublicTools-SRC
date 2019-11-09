package com.jc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import com.jc.util.AttributeParser.CommitSequenceException;
import com.jc.util.AttributeParser.SequenceGenerator;

public class FileBasedNextSequenceGenerator  implements SequenceGenerator
{
	private int nextSeq;
	private File baseDir;
	
	public FileBasedNextSequenceGenerator(File basedir) throws InvalidDirectoryException
	{
		this.nextSeq = -1;
		this.baseDir = basedir;
		
		if (!baseDir.exists() || !this.baseDir.canWrite())
			throw new InvalidDirectoryException(baseDir.getAbsolutePath());
	}
	
	public synchronized String getNextSequence(String id) 
	{
		if (nextSeq == -1)
		{
			File seqFile = new File(baseDir, id + ".seq");
		
			if (seqFile.exists())
				nextSeq = readSeqFromFile(seqFile) + 1;
			else
				nextSeq = 0;
		}
		else
		{
			nextSeq += 1;
		}
		
		return "" + nextSeq;
	}
	
	public void commitSequence(String id) throws CommitSequenceException
	{		
		try {
			writeSeqToFile(new File(baseDir, id + ".seq"), nextSeq);
		} catch (IOException e) {
			throw new CommitSequenceException(e);
		}
	}
	
	private int readSeqFromFile(File file)
	{
		BufferedReader in = null;
				
		try {
			in = new BufferedReader(new FileReader(file));
			return Integer.parseInt(in.readLine());
			
		} catch (FileNotFoundException e) {
			return -1;
		} catch (NumberFormatException e) {
			return -1;
		} catch (IOException e) {
			return -1;
		}
		finally
		{
			if (in != null)
			{
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private void writeSeqToFile(File file, int seq) throws IOException
	{
		BufferedWriter out = null;
		
		try {
			out = new BufferedWriter(new FileWriter(file));
			out.write("" + seq);
		} 
		finally
		{
			if (out != null)
			{
				try {
					out.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public class InvalidDirectoryException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public InvalidDirectoryException(String path)
		{
			super(path);
		}
	}
}
