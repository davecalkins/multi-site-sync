package com.multisitesync;

import java.awt.Image;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.Toolkit;
import java.awt.PopupMenu;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Desktop;
import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import javax.swing.filechooser.FileSystemView;

import com.multisitesync.SyncServer.SyncFile;

public class SyncClient implements ActionListener
{
	private static final String settingsFileName      = "SyncClient-settings.txt";
	private static final long   minUpdateIntervalSecs = 60;
	private static final long   updateIntervalIncMS   = 200;
	private static final String trayIconImageFile     = "SyncClientTrayIcon.png";
	private static final String trayIconBusyImageFile = "SyncClientTrayIconBusy.png";		
	private static final String menuTitle             = "SyncClient";
	private static final String menuTextSettings      = "Settings";
	private static final String menuTextLogFile       = "Log File";
	private static final String menuTextExit          = "Exit";
	
	private TrayIcon trayIcon = null;
	private Image trayIconImg = null;
	private Image trayIconBusyImg = null;
	
	public static void main(String[] args)
	{
		SyncClient client = new SyncClient();
		client.init();
		client.run();
	}
	
	public SyncClient()
	{
	}

	public void init()
	{
		//
		// setup tray icon and menu
		//
		try
		{
			PopupMenu pm = new PopupMenu(menuTitle);

			MenuItem mi;
			
			mi = new MenuItem(menuTextSettings);
			mi.addActionListener(this);
			pm.add(mi);

			mi = new MenuItem(menuTextLogFile);
			mi.addActionListener(this);
			pm.add(mi);
			
			pm.addSeparator();
			
			mi = new MenuItem(menuTextExit);
			mi.addActionListener(this);
			pm.add(mi);
			
			trayIconImg = Toolkit.getDefaultToolkit().getImage(trayIconImageFile);
			trayIconBusyImg = Toolkit.getDefaultToolkit().getImage(trayIconBusyImageFile);
			
			trayIcon = new TrayIcon(trayIconImg,menuTitle,pm);
			
			SystemTray.getSystemTray().add(trayIcon);
		}
		
		catch (Exception e)
		{
			Base.logException(e);
		}
	}
	
	public void actionPerformed(ActionEvent actionEvent)
	{
		//
		// handle tray icon menu selections
		//
		try
		{
			if (actionEvent.getActionCommand().compareToIgnoreCase(menuTextSettings) == 0)
			{
				Base.log("user selected settings from tray icon menu");
				Desktop.getDesktop().edit(new File(settingsFileName));
			}
			else if (actionEvent.getActionCommand().compareToIgnoreCase(menuTextLogFile) == 0)
			{
				Base.log("user selected logfile from tray icon menu");
				Desktop.getDesktop().edit(new File(Base.logFileName));
			}
			else if (actionEvent.getActionCommand().compareToIgnoreCase(menuTextExit) == 0)
			{
				Base.log("user selected exit from tray icon menu");
				SystemTray.getSystemTray().remove(trayIcon);
				Base.bKeepRunning = false;
			}
		}
		
		catch (Exception e)
		{
			Base.logException(e);
		}
	}
	
	public void run()
	{
		Base.log("SyncClient started");		

		// continue until run flag is cleared
		while (Base.bKeepRunning)
		{
			try
			{
				// make sure settings file exists
				File settingsFile = new File(settingsFileName);
				if (!settingsFile.exists())
					Base.log("ERROR, settings file does not exist, " + settingsFileName);
				
				// load settings file (done each time so that changes get used without restarting the app)
				INIFile settings = new INIFile();
				settings.loadFile(settingsFileName);

				// get user names (section names from file) and make sure there are > 0
				List<String> userNames = settings.getSectionNames();
				if (userNames.size() == 0)
					Base.log("ERROR, no users loaded from settings file, " + settingsFileName);

				// for each user in the settings file
				for (int userNameIdx = 0; (userNameIdx < userNames.size()) && Base.bKeepRunning; userNameIdx++)
				{
					String userName = userNames.get(userNameIdx);					
					String password = settings.getString(userName, "password");
					String folderName = settings.getString(userName, "folderName");

					// determine local directory (which should be on the desktop having the name specified
					// in the settings file).  the check for "Desktop" is needed because on Mac the Java
					// home directory gives us the user's home directory not the desktop folder, but
					// the Desktop folder is a subdirectory of that.
					File homeDirectory = FileSystemView.getFileSystemView().getHomeDirectory();
					if (homeDirectory.toString().indexOf("Desktop") == -1)
						homeDirectory = new File(homeDirectory,"Desktop");
					File localDirectory = new File(homeDirectory,folderName);
					localDirectory.mkdirs();

					// contact the server and retrieve the file index for this user
					SyncServer server;
					String authURL = settings.getString("", "authURL");
					server = new SyncServer(new URL(authURL), userName, password, localDirectory);					
					List<SyncFile> serverFiles = server.getIndex();					
					
					// did we get an index?
					if (serverFiles != null)
					{
						// is the index non-empty?
						if (serverFiles.size() > 0)
						{
							// look for local files to delete - any local files beneath the top local directory
							// which are not on the server index need to be deleted
							List<File> localFiles = new ArrayList<File>();
							Base.enumFilesInDirectory(localDirectory, localFiles);
							for (File localFile : localFiles)
							{
								boolean fileOnServer = false;
								for (SyncFile sf : serverFiles)
								{
									if (localFile.toString().compareToIgnoreCase(sf.getLocalFile().toString()) == 0)
									{
										fileOnServer = true;
										break;
									}
								}
								
								if (!fileOnServer)
								{
									// found an extra file, delete it
									Base.log("DELETE EXTRA FILE, user: " + userName + ", local: " + localFile.toString());
									localFile.delete();
								}
							}

							// remove empty directories from local
							Base.removeEmptyDirectories(localDirectory);

							// for all files in the server index
							for (int sfIdx = 0; (sfIdx < serverFiles.size()) && Base.bKeepRunning; sfIdx++)
							{
								SyncFile sf = serverFiles.get(sfIdx);
								
								boolean downloadFile = false;
	
								String updateDesc = "";
								File localFile = sf.getLocalFile();
								
								// local file does not exist, need to download a new file
								if (!localFile.exists())
								{
									updateDesc = "DOWNLOAD NEW FILE";
									downloadFile = true;
								}
								else
								{
									// local file does exist, check the hash
									String localFileHash = Base.computeFileSHA1Hash(localFile);
									
									// hash doesn't match, need to download modified file
									if (localFileHash.compareToIgnoreCase(sf.getServerHash()) != 0)
									{
										updateDesc = "DOWNLOAD MOD FILE";
										downloadFile = true;
									}
								}

								// download this file?
								if (downloadFile)
								{
									try
									{
										// show busy tray icon
										trayIcon.setImage(trayIconBusyImg);
										
										// download the file
										Base.log(updateDesc + ", user: " + userName + ", server: " + sf.getServerURL().getPath() + ", local: " + sf.getLocalFile().toString()); 
										if (server.downloadFile(sf))
											Base.log(updateDesc + ", completed");
										else
											Base.log(updateDesc + ", ERROR, unable to download file");
									}
									
									finally
									{
										// restore normal tray icon
										trayIcon.setImage(trayIconImg);										
									}
								}
							}
						}
						else
							Base.log("ERROR, empty index for user: " + userName);
					}
					else
						Base.log("ERROR, unable to download index for user: " + userName);
				}

				// get the desired update interval from the settings file and enforce a minimum
				// update interval
				long updateIntervalSecs = settings.getInt("", "updateIntervalSeconds");
				if (updateIntervalSecs < minUpdateIntervalSecs)
					updateIntervalSecs = minUpdateIntervalSecs;
				long updateIntervalMS = updateIntervalSecs * 1000;

				// wait for the next update interval - keep checking the run flag in case the user chose to exit the app
				long timeWaitedMS = 0;
				while ((timeWaitedMS < updateIntervalMS) && Base.bKeepRunning)
				{
					Thread.sleep(updateIntervalIncMS);
					timeWaitedMS += updateIntervalIncMS;
				}
			}
			
			catch (Exception e)
			{
				Base.logException(e);
			}
		}
		
		Base.log("SyncClient exiting");
		System.exit(0);
	}
}
