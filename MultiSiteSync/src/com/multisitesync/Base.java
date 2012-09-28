package com.multisitesync;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.security.MessageDigest; 
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class Base
{
	public static final String logFileName = "SyncClient-log.txt";

	// run flag used globally to trigger a shutdown of the app.  any code looping
	// through time consuming tasks should be checking this and immediately abort
	// if this becomes false
	public static boolean bKeepRunning = true;

	//
	// log a message to stdout and to the log file
	//
	public static void log(String s)
	{
		try
		{
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd @ hh:mm:ss");
			Date now = new Date();
			String nowStr = sdf.format(now);

			String out = String.format("%1$s - %2$s%n", nowStr, s);
			
			System.out.print(out);
			
			FileWriter fw = new FileWriter(logFileName,true);
			fw.write(out);
			fw.flush();
			fw.close();
		}
		
		catch (Exception e)
		{
			System.out.println(">>> " + e.toString());
			e.printStackTrace();
		}
	}

	//
	// log exception - uses the log method but dumps out stack trace info as well 
	//
	public static void logException(Exception e)
	{
		String s = String.format("EXCEPTION:%n%n>>> %1$s%n", e.toString());

		StackTraceElement[] stack = e.getStackTrace();
		for (StackTraceElement ele : stack)
		{
			s += String.format("   %1$s%n", ele.toString());
		}
		
		log(s);
	}

	//
	// compute the SHA1 hash of the specified file
	//
	private static final int SHA1BufSize = 1024;	
	public static String computeFileSHA1Hash(File file)
	{
		String result = "";
		try
		{
			if (file.exists())
			{
			    MessageDigest md = MessageDigest.getInstance("SHA1");
			    FileInputStream fis = new FileInputStream(file);
			    byte[] dataBytes = new byte[SHA1BufSize];
			 
			    int nread = 0; 
			 
			    while ((nread = fis.read(dataBytes)) != -1)
			    	md.update(dataBytes, 0, nread);
			 
			    fis.close();
			    
			    byte[] mdbytes = md.digest();
			 
			    StringBuffer sb = new StringBuffer("");
			    for (int i = 0; i < mdbytes.length; i++)
			    	sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
	
			    result = sb.toString();
			}
		}
		
		catch (Exception e)
		{
			logException(e);
		}
		
		return result;
	}
	
	//
	// get recursive list of all files in and beneath the specified directory
	//
	public static void enumFilesInDirectory(File parentDir, List<File> files)
	{
		File[] fileArr = parentDir.listFiles();
		if (fileArr == null)
			return;
		for (File f : fileArr)
		{
			if (!f.isDirectory())
				files.add(f);
			else
				enumFilesInDirectory(f,files);
		}
	}

	//
	// remove all empty directories in a depth-first recursive manner beneath
	// the specified top level directory
	//
	public static void removeEmptyDirectories(File parentDir)
	{
		if (!parentDir.isDirectory())
			return;
		File[] fileArr = parentDir.listFiles();
		for (File f : fileArr)
		{
			if (!f.isDirectory())
				continue;
			removeEmptyDirectories(f);
		}
		fileArr = parentDir.listFiles();
		if (fileArr.length == 0)
		{
			// found an empty directory, delete it
			Base.log("DELETE EMPTY DIRECTORY, " + parentDir.toString());
			parentDir.delete();
		}
	}
}

