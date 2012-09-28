package com.multisitesync;

import java.io.File;
import java.io.PrintWriter;
import java.util.Scanner;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public class INIFile
{
	private Map<String,Map<String,String>> data;
	
	public INIFile()
	{
		data = new HashMap<String,Map<String,String>>();
	}

	public String getString(String sSection, String sName)
	{
		String sValue = "";
		
		synchronized(data)
		{
			if (data.containsKey(sSection))
			{
				Map<String,String> pairs = data.get(sSection);
				if (pairs.containsKey(sName))
				{
					sValue = pairs.get(sName);
				}
			}
		}
		
		return sValue;
	}
	
	public int getInt(String sSection, String sName)
	{
		int iValue = 0;
		
		String sValue = getString(sSection,sName);
		Scanner s = new Scanner(sValue);
		if (s.hasNextInt())
			iValue = s.nextInt();
		
		return iValue;
	}
	
	public double getDouble(String sSection, String sName)
	{
		double dValue = 0;
		
		String sValue = getString(sSection,sName);
		Scanner s = new Scanner(sValue);
		if (s.hasNextDouble())
			dValue = s.nextDouble();
		
		return dValue;
	}

	public List<String> getSectionNames()
	{
		List<String> result = new ArrayList<String>();
		
		synchronized(data)
		{
			for (String key : data.keySet())
			{
				if (key.length() > 0)
					result.add(key);
			}
		}
		
		return result;
	}
	
	public void loadFile(String file)
	{
		Scanner fileScanner = null;
		
		try
		{
			File inputFile = new File(file);

			synchronized(data)
			{
				data.clear();

				// read the file line by line
				String sSection = "";
				fileScanner = new Scanner(inputFile);
				while (fileScanner.hasNextLine())
				{
					String line = fileScanner.nextLine().trim();

					// ignore blank lines
					if (line.length() == 0)
						continue;
					
					// ignore comment lines
					if (line.startsWith(";"))
						continue;
					
					// look for section name
					int ob = line.indexOf('[');
					int cb = line.indexOf(']');
					if ((ob >= 0) && (cb > (ob+1)))
					{
						sSection = line.substring(ob+1,cb).trim();
						continue;
					}

					// look for name/value pair
					int eq = line.indexOf('=');
					if (eq >= 0)
					{
						Scanner lineScanner = new Scanner(line);
						lineScanner.useDelimiter("=");
						String sName = null;
						if (lineScanner.hasNext())
							sName = lineScanner.next().trim();
						String sValue = null;
						if (lineScanner.hasNext())
							sValue = lineScanner.next().trim();
						if ((sName != null) && (sValue != null))
						{
							// update data with this loaded name/value pair in the appropriate section
							Map<String,String> pairs = null;
							if (data.containsKey(sSection))
								pairs = data.get(sSection);
							else
								pairs = new HashMap<String,String>();
							pairs.put(sName, sValue);
							data.put(sSection, pairs);
						}
					}
				}
			}
		}
		
		catch (Exception e)
		{				
			Base.logException(e);
		}
		
		finally
		{
			if (fileScanner != null)
			{
				fileScanner.close();
				fileScanner = null;
			}
		}
	}

	//--------------------------------------------------------------------------
	//--------------------------------------------------------------------------
	//--------------------------------------------------------------------------
	
	public static void main(String[] args)
	{
		Base.log(">>>>>>>>> test: INIFile");
		
		INIFile settingsFile = null;
		final String testFile = "___INIFile-test-settings.ini";
		
		try
		{
			PrintWriter out = new PrintWriter(testFile);
			out.printf("favColor = orange\n");
			out.printf("[ Settings ]\n");
			out.printf("myString=Hello\n");
			out.printf("myInt=32\n");
			out.printf("myPI=3.14159\n");
			out.close();
			
			settingsFile = new INIFile();
			settingsFile.loadFile(testFile);
			
			if (settingsFile.getString("","favColor").compareTo("orange") != 0)
				throw new Exception("ERROR 1");

			if (settingsFile.getString("Settings","myString").compareTo("Hello") != 0)
				throw new Exception("ERROR 2");

			if (settingsFile.getInt("Settings","myInt") != 32)
				throw new Exception("ERROR 3");

			if (settingsFile.getDouble("Settings","myPI") != 3.14159)
				throw new Exception("ERROR 4");

			if (settingsFile.getString("Settings","invalidName").compareTo("") != 0)
				throw new Exception("ERROR 5");

			if (settingsFile.getString("invalidSection","favColor").compareTo("") != 0)
				throw new Exception("ERROR 6");

			if (settingsFile.getString("invalidSection","invalidName").compareTo("") != 0)
				throw new Exception("ERROR 7");
			
			Base.log("<<<<<<<<< test result: SUCCESS");
		}
			
		catch (Exception e)
		{
			Base.logException(e);
		}
		
		finally
		{
			File f = new File(testFile);
			f.delete();
		}
	}
}
