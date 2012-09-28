package com.multisitesync;

import java.io.InputStream;
import java.io.StringReader;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Scanner;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.net.URI;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPath;

import org.w3c.dom.Document;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import org.xml.sax.InputSource;

public class SyncServer
{
	private URL authURL;
	private String username;
	private String password;
	private File localDirectory;
	
	private final int AUTH_PORT = 80;
	
	// chunk size for file downloads - grabs this much of the file at a time in a loop
	private final int fileDownloadBufSize = 1024 * 10;
	
	public SyncServer(URL authURL, String username, String password, File localDirectory)
	{
		this.authURL = authURL;
		this.username = username;
		this.password = password;
		this.localDirectory = localDirectory;
	}
	
	private String downloadIndex()
	{
		String result = null;

        DefaultHttpClient httpclient = new DefaultHttpClient();
        try
        {
            httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(authURL.getHost(), AUTH_PORT),
                    new UsernamePasswordCredentials(username, password));

            List<NameValuePair> formparams = new ArrayList<NameValuePair>();
            formparams.add(new BasicNameValuePair("username", username));
            formparams.add(new BasicNameValuePair("password", password));
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(formparams, "UTF-8");
            HttpPost httppost = new HttpPost(authURL.toString());
            httppost.setEntity(entity);

            HttpResponse response = httpclient.execute(httppost);

        	int statusCode = response.getStatusLine().getStatusCode();
        	if (statusCode == 200)
        	{
                HttpEntity entityResp = response.getEntity();
                
	            if (entityResp != null)
	            {
	                InputStream contentStream = entityResp.getContent();
	                Scanner contentScanner = new Scanner(contentStream);
	                result = "";
	                while (contentScanner.hasNextLine() && Base.bKeepRunning)
	                {
	                	String line = contentScanner.nextLine();
	                	result = result + line;
	                }
	                
	                EntityUtils.consume(entityResp);
	            }
        	}
        }
        
        catch (Exception e)
        {
			Base.logException(e);
        }
        
        finally
        {
            httpclient.getConnectionManager().shutdown();
        }
				
		return result;
	}
	
	public class SyncFile
	{
		URL serverURL;
		String serverHash;
		File localFile;
		
		public SyncFile(URL serverURL, String serverHash, File localFile)
		{
			this.serverURL = serverURL;
			this.serverHash = serverHash;
			this.localFile = localFile;
		}
		
		public URL getServerURL()
		{
			return serverURL;
		}
		
		public String getServerHash()
		{
			return serverHash;
		}
		
		public File getLocalFile()
		{
			return localFile;
		}
	}
	
	private List<SyncFile> parseIndex(String index)
	{
		List<SyncFile> result = null;
		
		try
		{
	        InputSource is = new InputSource();
	        is.setCharacterStream(new StringReader(index));
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			
			Document doc = builder.parse(is);
			
			XPathFactory xpFactory = XPathFactory.newInstance();
			XPath xPath = xpFactory.newXPath();
			
			String status = xPath.evaluate("/siteindex/status", doc);
			if (status.compareToIgnoreCase("Ok") == 0)
			{
				result = new ArrayList<SyncFile>();
				
				int numFiles = Integer.parseInt(xPath.evaluate("count(/siteindex/*)",doc));
				numFiles--;

				for (int i = 0; i < numFiles; i++)
				{
					String baseXPath = "/siteindex/file[" + (i+1) + "]/";
					
					String serverPath = xPath.evaluate(baseXPath + "filename", doc);
					String authURLStr = authURL.toString();
					String authURLPath = authURL.getPath();
					String serverURLStr = authURLStr.substring(0, authURLStr.length() - authURLPath.length()) + serverPath;
					URL serverURL = new URL(serverURLStr);

					String serverHash = xPath.evaluate(baseXPath + "hash", doc);
					
					String localFileName = xPath.evaluate(baseXPath + "localfilename", doc);
					File localFile = new File(localDirectory,localFileName);
						
					SyncFile sf = new SyncFile(serverURL,serverHash,localFile);
					result.add(sf);
				}
			}
		}
		
		catch (Exception e)
		{
			Base.logException(e);
		}
		
		return result;
	}
	
	public List<SyncFile> getIndex()
	{
		String indexContents = downloadIndex();
		if (indexContents == null)
			return null;
		
		return parseIndex(indexContents);
	}

	public boolean downloadFile(SyncFile syncFile)
	{
		boolean result = false;
		
        DefaultHttpClient httpclient = new DefaultHttpClient();
        try
        {
            httpclient.getCredentialsProvider().setCredentials(
                    new AuthScope(authURL.getHost(), AUTH_PORT),
                    new UsernamePasswordCredentials(username, password));

            URL serverURL = syncFile.getServerURL();
            URI uri = new URI(serverURL.getProtocol(),serverURL.getHost(),serverURL.getPath(),"");
            
            HttpGet httpget = new HttpGet(uri);

            HttpResponse response = httpclient.execute(httpget);

        	int statusCode = response.getStatusLine().getStatusCode();
        	if (statusCode == 200)
        	{
                HttpEntity entityResp = response.getEntity();
                
	            if ((entityResp != null))
	            {            	            	
	            	// create temp file to download to
	            	File tempFile = File.createTempFile("SyncClient",".tmp");
	            	FileOutputStream fos = new FileOutputStream(tempFile);
	
	            	// download the file chunk by chunk until we have it all
	            	InputStream fis = entityResp.getContent();
	    		    byte[] dataBytes = new byte[fileDownloadBufSize];            	
	    		    int bytesReadThisIter = 0;
	    		    while (Base.bKeepRunning && ((bytesReadThisIter = fis.read(dataBytes)) != -1))
	    		    	fos.write(dataBytes,0,bytesReadThisIter);

	    		    // flush and close the temp file
	    		    fos.flush();
	            	fos.close();

	            	if (Base.bKeepRunning)
	            	{
	            		// verify hash of downloaded temp file
		            	String tmpFileHash = Base.computeFileSHA1Hash(tempFile);
		            	if (tmpFileHash.compareToIgnoreCase(syncFile.getServerHash()) == 0)
		            	{
		            		// ensure target local directory exists
			            	File localFile = syncFile.getLocalFile();
			            	File localDirectory = localFile.getParentFile();
			            	localDirectory.mkdirs();            	            	
			            	
			            	// delete any existing local file if there is one
			            	localFile.delete();
			            	
			            	// move the temp file into place
			            	tempFile.renameTo(localFile);
					            			                
			                result = true;
		            	}
	            	}
	            	
	            	// make sure temp file is removed
	            	tempFile.delete();

	                EntityUtils.consume(entityResp);
	            }
        	}
        	else
        		Base.log("status code == " + statusCode);
        }
        
        catch (Exception e)
        {
			Base.logException(e);
        }
        
        finally
        {
            httpclient.getConnectionManager().shutdown();
        }	
        
        return result;
	}
}

