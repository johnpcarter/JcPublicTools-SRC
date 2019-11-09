package com.jc.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import com.wm.app.b2b.server.ServerAPI;
import com.wm.app.b2b.server.ServiceException;

public class FileCopier 
{
	private File srcFile;
	
	public FileCopier(String srcDirectory, String fileName) throws ServiceException
	{
		if (fileName == null)
			throw new ServiceException("Please provide a valid fileName to copy");
		
		if (srcDirectory != null)
			srcFile = new File(srcDirectory, fileName);
		else
			srcFile = new File(fileName);
		
		if (!srcFile.exists() || !srcFile.canRead())
			throw new ServiceException("File does not exists : " + srcFile.toString());
	}
	
	public File move(String destinationDirectory, String altName,  boolean deleteDestinationFileIfExists) throws ServiceException
	{
		File tgtFile = validateDestinationStrings(srcFile, destinationDirectory, altName, deleteDestinationFileIfExists);
		
// if nothing changed return src as tgt
		
		if (srcFile.getParentFile().equals(tgtFile.getParentFile()) && altName == null)
			return srcFile;
		
		if (srcFile.canWrite())
		{
			 if (srcFile.renameTo(tgtFile))
				 return tgtFile;
			 else
				 return null;
		}
		else
		{
			throw new ServiceException("Source File cannot be moved, insufficient priviledges:" + srcFile.toString());
		}
	}
	
	public File copy(String destinationDirectory, String altName,  boolean deleteDestinationFileIfExists) throws ServiceException
	{
		File tgtFile = validateDestinationStrings(srcFile, destinationDirectory, altName, deleteDestinationFileIfExists);
		
// if nothing changed return src as tgt
				
		if (srcFile.getParentFile().equals(tgtFile.getParentFile()) && altName == null)
			return srcFile;
				
		try {
			if(!tgtFile.exists())
				tgtFile.createNewFile();
			} catch (IOException e) {
		 		throw new ServiceException("Copy failed due to : " + e.getMessage());
			}
	 
	 	FileChannel source = null;
	 	FileChannel destination = null;
	 
		try 
		{
	  		source = new FileInputStream(srcFile).getChannel();
	  		destination = new FileOutputStream(tgtFile).getChannel();
	  		destination.transferFrom(source, 0, source.size());
	  		
	  		return tgtFile;
	 	}
	 	catch(IOException e)
	 	{
	 		throw new ServiceException("Copy failed due to : " + e.getMessage());
	 	}
	 	finally 
		{
	 		try {
	 			if(source != null) 
	 				source.close();
	 		} catch(Exception e) {ServerAPI.logError(e);};
	 		
	 		try {
	 			if(destination != null) 
	 				destination.close();
	 		} catch(Exception e) {ServerAPI.logError(e);};
	  	}
	}
	
	public File rename(String newName, boolean deleteDestinationFileIfExists) throws ServiceException
	{
		File newFile = new File(srcFile.getParent(), newName);
		
		if (srcFile.canWrite())
		{
			if (newFile.exists())
			{
				if (deleteDestinationFileIfExists && newFile.canWrite())
				{
					if (System.getProperty("os.name").startsWith("Windows"))	// windows cannot rename file if dest file already exists, so we have to delete it in advance
					{
						if (!newFile.delete())
							throw new ServiceException("Cannot rename file - a file already exists and the system would not let me over-write it :" + newFile.toString());
					}
				}
				else
				{
					if (!deleteDestinationFileIfExists)
						throw new ServiceException("Cannot rename file - a file already exists with that name and you specified not to replace it! :" + newFile.toString());
					else
						throw new ServiceException("Cannot rename file - destination file already exists and I don't have the right priviledges to delete it :" + newFile.toString());
				}
			}
			
			if (srcFile.renameTo(newFile))
				return newFile;
			else
				throw new ServiceException("Cannot rename file - refused by system");
		}
		else
		{
			throw new ServiceException("Cannot rename file - insuffiencient priviledges :" + srcFile.toString());

		}
	}
	
	private File validateDestinationStrings(File srcFile, String destinationDirectory, String fileName, boolean deleteDestinationFileIfExists) throws ServiceException
	{
		if (destinationDirectory == null)
			throw new ServiceException("Please provide a valid destination directory");

		if (isWindows()) // add drive letter to destination if not included
		{
			String defaultDrive = new File(".").getAbsolutePath();
			
			System.out.println("Default drive is " + defaultDrive.substring(0, 2));
			
			if (!destinationDirectory.contains(":"))
				destinationDirectory = defaultDrive.substring(0, 2) + destinationDirectory;
			
			System.out.println("Dest dir is now " + destinationDirectory);

		}
		
// validate destination directory
		
		File destDir = new File(destinationDirectory);
				
		if (srcFile.equals(new File(destDir, fileName == null ? srcFile.getName() : fileName))) // don't delete target if it is the same as the source
			return srcFile;
		
		if (!destDir.exists())
			destDir.mkdir();
		
		if (!destDir.exists())
			throw new ServiceException("Destination directory does not exist : " + destDir.toString());
		else if (!destDir.canWrite())
			throw new ServiceException("Destination directory cannot be written to: " + destDir.toString());
		
// check if the target file exists and ensure that it can be over-written
		
		File tgtFile = null;

		if (fileName != null)
			tgtFile = new File(destDir, new File(fileName).getName());
		else
			tgtFile = new File(destDir, srcFile.getName());
		
		if (tgtFile.exists())
		{
			if (tgtFile.canWrite() && deleteDestinationFileIfExists)
			{
				if (!tgtFile.delete())
					throw new ServiceException("Destination File already exists and I couldn't delete it: " + tgtFile.toString());
			}
			else
			{
				throw new ServiceException("Destination File already exists and cannot be overwritten: " + tgtFile.toString());
			}
		}

		return tgtFile;
	}
	
	private boolean isWindows()
	{
		return (System.getProperty("os.name").startsWith("Windows"));
	}
}
