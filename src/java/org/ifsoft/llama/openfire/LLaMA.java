/*
 * Copyright (C) 2017 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.ifsoft.llama.openfire;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.security.Security;

import org.jivesoftware.openfire.container.Plugin;
import org.jivesoftware.openfire.container.PluginManager;
import org.jivesoftware.openfire.net.SASLAuthentication;
import org.jivesoftware.openfire.http.HttpBindManager;
import org.jivesoftware.openfire.XMPPServer;
import org.jivesoftware.openfire.sasl.AnonymousSaslServer;
import org.jivesoftware.openfire.muc.*;
import org.jivesoftware.openfire.user.UserManager;
import org.jivesoftware.util.cache.Cache;
import org.jivesoftware.util.cache.CacheFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jivesoftware.util.JiveGlobals;
import org.jivesoftware.util.PropertyEventDispatcher;
import org.jivesoftware.util.PropertyEventListener;
import org.jivesoftware.util.StringUtils;

import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.plus.annotation.ContainerInitializer;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlets.*;
import org.eclipse.jetty.servlet.*;
import org.eclipse.jetty.websocket.servlet.*;
import org.eclipse.jetty.websocket.server.*;
import org.eclipse.jetty.webapp.WebAppContext;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.util.security.*;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.*;

import java.lang.reflect.*;
import java.util.*;

import org.jitsi.util.OSUtils;
import de.mxro.process.*;
import net.sf.json.*;
import org.xmpp.packet.*;

public class LLaMA implements Plugin, PropertyEventListener, ProcessListener, MUCEventListener {
    private static final Logger Log = LoggerFactory.getLogger(LLaMA.class);
    private XProcess llamaThread = null;
    private String llamaExePath = null;
    private String llamaHomePath = null;
    private ExecutorService executor;
    private WebAppContext jspService;
	private LLaMAConnection llamaConnection = null;
	private static final String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
	private static final String hostname = XMPPServer.getInstance().getServerInfo().getHostname();	
	
    public static LLaMA self;	
	public static int numThreads = 8;	
	
    public void destroyPlugin() {
        PropertyEventDispatcher.removeListener(this);
        MUCEventDispatcher.removeListener(this);		

        try {
			if (llamaConnection != null) llamaConnection.close();
            if (executor != null)  executor.shutdown();
            if (llamaThread != null) llamaThread.destory();
            if (jspService != null) HttpBindManager.getInstance().removeJettyHandler(jspService);				

            Log.info("llama terminated");
        }
        catch (Exception e) {
            Log.error("LLaMA destroyPlugin", e);
        }
    }

    public void initializePlugin(final PluginManager manager, final File pluginDirectory) {			
        boolean llamaEnabled = JiveGlobals.getBooleanProperty("llama.enabled", true);
		boolean llamaHosted = JiveGlobals.getBooleanProperty("llama.hosted", true);	

        if (llamaEnabled) {
			Log.info("llama enabled");				
			PropertyEventDispatcher.addListener(this);
			MUCEventDispatcher.addListener(this);
			
			checkNatives(pluginDirectory);
			executor = Executors.newCachedThreadPool();
			startJSP(pluginDirectory);
			
			if (llamaHosted) {
				createLLaMAUser();	
				loginLLaMAUser(true);
				llamaConnection.handlePrediction("what is your name?", null, null);					
			} else {
				setupLLaMA(pluginDirectory);
			}
			self = this;					
			
			Log.info("llama initiated");
		} else {
			Log.info("llama disabled");			
		}
    }

    public static String getPort() {
        return "8080";
    }

    public static String getModelPath() {
        return (JiveGlobals.getHomeDirectory() + File.separator + "llama").replace("\\", "/");
    }

    public static String getHostedUrl() {
        return "https://chatgpt.free-solutions.ch";
    }
	
    public static String getModelUrl() {
        return "https://huggingface.co/TheBloke/Mistral-7B-Instruct-v0.1-GGUF/resolve/main/mistral-7b-instruct-v0.1.Q5_K_M.gguf?download=true";
    }

    public static String getSystemPrompt() {
        return "You are a helpful assistant.";
    }
	
    public static String getTurnPort() {
        return "10014";
    }

    public String getHome() {
        return llamaHomePath;
    }

    public static String getUrl() {
        return "https://" + hostname + ":" + JiveGlobals.getProperty("httpbind.port.secure", "7443");
    }

    public static String getIpAddress() {
        String ourIpAddress = "127.0.0.1";

        try {
            ourIpAddress = InetAddress.getByName(hostname).getHostAddress();
        } catch (Exception e) {

        }

        return ourIpAddress;
    }

    public void onOutputLine(final String line) {
        Log.info("onOutputLine " + line);
		
		if (line.contains("HTTP server listening") && llamaConnection != null) {
			Log.info("Sending test data to LLaMA");
			
			llamaConnection.handlePrediction("what is your name?", null, null);			
		}
    }

    public void onProcessQuit(int code) {
        Log.info("onProcessQuit " + code);
    }

    public void onOutputClosed() {
        Log.error("onOutputClosed");
    }

    public void onErrorLine(final String line) {
        Log.info(line);
    }

    public void onError(final Throwable t) {
        Log.error("Thread error", t);
    }

    private void startJSP(File pluginDirectory) {
        jspService = new WebAppContext(null, pluginDirectory.getPath() + "/classes/jsp",  "/llama");
        jspService.setClassLoader(this.getClass().getClassLoader());
        jspService.getMimeTypes().addMimeMapping("wasm", "application/wasm");

        final List<ContainerInitializer> initializers = new ArrayList<>();
        initializers.add(new ContainerInitializer(new JettyJasperInitializer(), null));
        jspService.setAttribute("org.eclipse.jetty.containerInitializers", initializers);
        jspService.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());

        Log.info("LLaMA jsp service enabled");
        HttpBindManager.getInstance().addJettyHandler(jspService);
    }

	private void setupLLaMA(File pluginDirectory) {
		final String path = JiveGlobals.getProperty("llama.model.path", getModelPath());		
		final String filePath = path + File.separator + "llama.model.gguf";		
	    final File folder = new File(path);			
		
		if (!folder.exists())
		{						
			new Thread()
			{
				@Override public void run()
				{
					try
					{
						folder.mkdir();	
						String modelUrl = JiveGlobals.getProperty("llama.model.url", getModelUrl());
						InputStream inputStream = new URL(modelUrl).openStream();

						try
						{	
							BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
							byte[] bytesIn = new byte[4096];
							int read;
							
							Log.info("llama model - start writing file..." + filePath);							

							while ((read = inputStream.read(bytesIn)) != -1) {
								bos.write(bytesIn, 0, read);
							}
							bos.close();

							File file = new File(filePath);							
							file.setReadable(true, true);
							file.setWritable(true, true);
							file.setExecutable(true, true);		

							Log.info("llama model - finished writing file..." + filePath);
						}
						catch(Exception e) {
							Log.error("Error", e);
						}

						inputStream.close();

						Log.info("llama model downloaded ok");
						startLLaMAProcess(filePath);						
						
					}						
					catch (Exception e)
					{
						Log.error(e.getMessage(), e);
					}						
					
				}

			}.start();
		}
		else {
			Log.warn("llama folder already exist.");
			startLLaMAProcess(filePath);
		}								
	}	
	
    private void startLLaMAProcess(String filename) {
		final String alias = JiveGlobals.getProperty("llama.alias", "LLaMA");
        final boolean llamaEnabled = JiveGlobals.getBooleanProperty("llama.enabled", true);
		final String modelUrl = JiveGlobals.getProperty("llama.model.url", getModelUrl());		

        if (llamaExePath != null && llamaEnabled && !isNull(modelUrl))	{
            createLLaMAUser();
			loginLLaMAUser(false);				
						
			try {
				final String llamaHost = JiveGlobals.getProperty("llama.host", getIpAddress());					
				final String llamaPort = JiveGlobals.getProperty("llama.port", LLaMA.self.getPort());				
				//final String params = "-ngl 1 --host " + llamaHost + " -a " + alias + " -m " + filename + " -c 2048 --path . --port " + llamaPort + " -np " + numThreads;			
				final String params = "--host " + llamaHost + " -a " + alias + " -m " + filename + " -c 2048 --path . --port " + llamaPort + " -np " + numThreads;			
				llamaThread = Spawn.startProcess(llamaExePath + " " + params, new File(llamaHomePath), this);
				
				Thread.sleep(1000);
			
				Log.info("LLaMA enabled " + llamaExePath + " " + params);				
			}
			catch (Exception e)
			{
				Log.error("startLLaMAProcess error", e);
			}		

        } else {
            Log.info("LLaMA disabled");
        }
    }
	
	private void loginLLaMAUser(boolean hosted) {
		Log.info("LLaMA user login");
			
		final String llamaUser = JiveGlobals.getProperty("llama.username", "llama");
		String llamaUrl = JiveGlobals.getProperty("llama.hosted.url", getHostedUrl());
		
		if (!hosted) {
			final String llamaHost = JiveGlobals.getProperty("llama.host", getIpAddress());	
			final String llamaPort = JiveGlobals.getProperty("llama.port", LLaMA.self.getPort());
			
			llamaUrl = "http://" + llamaHost + ":" + llamaPort;			
		}
		
		llamaConnection = new LLaMAConnection(llamaUser, llamaUrl);		
	}
	
    private void checkNatives(File pluginDirectory) {
        try
        {
            llamaHomePath = pluginDirectory.getAbsolutePath() + File.separator + "classes";

            if(OSUtils.IS_LINUX64)
            {
                llamaHomePath = llamaHomePath + File.separator + "linux-64";
                llamaExePath = llamaHomePath + File.separator + "server";
                makeFileExecutable(llamaExePath);
            }
            else if(OSUtils.IS_WINDOWS64)
            {
                llamaHomePath = llamaHomePath + File.separator + "win-64";
                llamaExePath = llamaHomePath + File.separator + "server.exe";
                makeFileExecutable(llamaExePath);				

            } else {
                Log.error("checkNatives unknown OS " + pluginDirectory.getAbsolutePath());
                return;
            }
        }
        catch (Exception e)
        {
            Log.error("checkNatives error", e);
        }
    }

    private void makeFileExecutable(String path) {
        File file = new File(path);
        file.setReadable(true, true);
        file.setWritable(true, true);
        file.setExecutable(true, true);
        Log.info("makeFileExecutable llama executable path " + path);
    }

    private void createLLaMAUser() {
        final UserManager userManager = XMPPServer.getInstance().getUserManager();
        final String llamaUser = JiveGlobals.getProperty("llama.username", "llama");

        if ( !userManager.isRegisteredUser( new JID(llamaUser + "@" + domain), false ) )
        {
            Log.info( "No LLaMA user detected. Generating one." );

            try
            {
                String password = StringUtils.randomString(40);
                JiveGlobals.setProperty("llama.password", password);
                userManager.createUser(llamaUser, password, "LLaMA User (generated)", null);
            }
            catch ( Exception e )
            {
                Log.error( "Unable to provision an llamaUser user.", e );
            }
        }
    }

    private boolean isNull(String value)   {
        return (value == null || "undefined".equals(value)  || "null".equals(value) || "".equals(value.trim()) || "unknown".equals(value) || "none".equals(value));
    }
	
    // -------------------------------------------------------
    //
    //  MUCEventListener
    //
    // -------------------------------------------------------

    public void roomCreated(JID roomJID) {

    }

    public void roomDestroyed(JID roomJID) {

    }

    public void occupantJoined(JID roomJID, JID user, String nickname) {

    }

    public void occupantLeft(JID roomJID, JID user, String nickname) {

    }

	public void occupantNickKicked(JID roomJID, String nickname) {
		
	}
	
    public void nicknameChanged(JID roomJID, JID user, String oldNickname, String newNickname) {

    }

    public void messageReceived(JID roomJID, JID user, String nickname, Message message)  {
		final String from = user.toBareJID();
		final String room = roomJID.getNode();	
		final String body = message.getBody();

		if (body != null) {
			try {
				final String llamaUser = JiveGlobals.getProperty("llama.username", "llama");
				final String llamaAlias = JiveGlobals.getProperty("llama.alias", "LLaMA");
				final MUCRoom mucRoom = XMPPServer.getInstance().getMultiUserChatManager().getMultiUserChatService("conference").getChatRoom(room);		
				boolean isOccupant = false;
				
				for (MUCRole role : mucRoom.getOccupants()) {
					Log.info("matching room occupant " + role.getUserAddress() + " with " + llamaUser );
					
					if (role.getUserAddress().getNode().equals(llamaUser)) {
						isOccupant = true;	
						break;
					}
				}

				if (body.toLowerCase().startsWith(llamaUser.toLowerCase())) {	// message aimed at LLaMA
					
					if (!isOccupant) {
						Presence pres = new Presence();	
						pres.setTo(roomJID + "/" + llamaAlias);	
						pres.setFrom(llamaUser + "@" + domain + "/" + llamaConnection.remoteAddr);
						pres.addChildElement("x", "http://jabber.org/protocol/muc");						
						
						XMPPServer.getInstance().getPresenceRouter().route(pres);
						Thread.sleep(1000);					
					}	

					llamaConnection.handlePrediction(body, roomJID, message.getType());	
				}
			} catch (Exception e) {
				Log.error("unable to handle groupchat message", e);
			}
		}
    }

    public void roomSubjectChanged(JID roomJID, JID user, String newSubject) {

    }

    public void privateMessageRecieved(JID a, JID b, Message message) {

    }
	
    //-------------------------------------------------------
    //
    //  PropertyEventListener
    //
    //-------------------------------------------------------

    public void propertySet(String property, Map params) {

    }

    public void propertyDeleted(String property, Map<String, Object> params) {

    }

    public void xmlPropertySet(String property, Map<String, Object> params) {

    }

    public void xmlPropertyDeleted(String property, Map<String, Object> params) {

    }
		
}