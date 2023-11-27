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

import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.net.*;
import java.nio.charset.*;

import org.dom4j.Namespace;
import org.jivesoftware.openfire.*;
import org.jivesoftware.openfire.nio.OfflinePacketDeliverer;
import org.jivesoftware.openfire.session.LocalClientSession;
import org.jivesoftware.openfire.session.LocalSession;
import org.jivesoftware.openfire.session.ClientSession;
import org.jivesoftware.openfire.spi.ConnectionConfiguration;
import org.jivesoftware.openfire.spi.ConnectionManagerImpl;
import org.jivesoftware.openfire.spi.ConnectionType;
import org.jivesoftware.openfire.net.VirtualConnection;
import org.jivesoftware.openfire.auth.UnauthorizedException;
import org.jivesoftware.openfire.auth.AuthToken;
import org.jivesoftware.openfire.auth.AuthFactory;

import org.jivesoftware.util.JiveGlobals;

import org.xmpp.packet.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dom4j.*;
import net.sf.json.*;


public class LLaMAConnection extends VirtualConnection
{
    private static final Logger Log = LoggerFactory.getLogger(LLaMAConnection.class);
	
    private SessionPacketRouter router;	
    private PacketDeliverer backupDeliverer;
    private ConnectionConfiguration configuration;	
    private ConnectionType connectionType;	
	private String username;
	private AuthToken authToken = null;
    private LocalClientSession session;	
    private ExecutorService exec = Executors.newFixedThreadPool(LLaMA.numThreads);	
	
	private final String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
	private final String hostname = XMPPServer.getInstance().getServerInfo().getHostname();

    public String remoteAddr;	
    public String remoteUrl;	

    public LLaMAConnection(String username, String remoteUrl) {
		this.username = username;
		this.remoteUrl = remoteUrl;
		this.remoteAddr = "llama-" + System.currentTimeMillis();
		
		try {
			
			for (ClientSession sess : SessionManager.getInstance().getSessions(username)) 
			{				
				if (((LocalClientSession) sess).getConnection() instanceof LLaMAConnection) {
					sess.close();
				}
			}	
			
			session = SessionManager.getInstance().createClientSession( this, (Locale) null );
			AuthToken authToken = AuthToken.generateUserToken( username );
			session.setAuthToken(authToken, remoteAddr);	
				
			router = new SessionPacketRouter( session );
			route("<presence />");
			
			Log.info("xmpp session created for " + username);			
		} catch (Exception e) {
			Log.error("XMPPConnection  error", e);
		}		
    }
	
	public void handlePrediction(final String prompt, final JID requestor, final Message.Type chatType) {
		exec.execute(new Runnable() {
			public void run() {
				long threadId = Thread.currentThread().getId()% LLaMA.numThreads;
	
				final String alias = JiveGlobals.getProperty("llama.alias", "LLaMA");
				/*
					"system_prompt": {
						"prompt": "Transcript of a never ending dialog, where the User interacts with an Assistant.\nThe Assistant is helpful, kind, honest, good at writing, and never fails to answer the User's requests immediately and with precision.\nUser: Recommend a nice restaurant in the area.\nAssistant: I recommend the restaurant \"The Golden Duck\". It is a 5 star restaurant with a great view of the city. The food is delicious and the service is excellent. The prices are reasonable and the portions are generous. The restaurant is located at 123 Main Street, New York, NY 10001. The phone number is (212) 555-1234. The hours are Monday through Friday from 11:00 am to 10:00 pm. The restaurant is closed on Saturdays and Sundays.\nUser: Who is Richard Feynman?\nAssistant: Richard Feynman was an American physicist who is best known for his work in quantum mechanics and particle physics. He was awarded the Nobel Prize in Physics in 1965 for his contributions to the development of quantum electrodynamics. He was a popular lecturer and author, and he wrote several books, including \"Surely You're Joking, Mr. Feynman!\" and \"What Do You Care What Other People Think?\".\nUser:",
						"anti_prompt": "User:",
						"assistant_name": "Assistant:"
					}
				*/	
				double temperature = 0.5;
				double top_p = 0.9;
				
				try {
					temperature = Double.parseDouble(JiveGlobals.getProperty("llama.temperature", "0.5"));
					top_p = Double.parseDouble(JiveGlobals.getProperty("llama.top.p.sampling", "0.9"));
				} catch (Exception e) {
					Log.error("Unable to set temperature or top_p", e);
				}
				JSONObject systemPrompt = new JSONObject();
				systemPrompt.put("prompt", JiveGlobals.getProperty("llama.system.prompt", LLaMA.getSystemPrompt()));
				systemPrompt.put("anti_prompt", "/stop");				
				systemPrompt.put("assistant_name", alias);					
				
				JSONObject testData = new JSONObject();
				testData.put("system_prompt", systemPrompt);
				testData.put("prompt", "<s>[INST]" + prompt + "[/INST]</s>");				
				testData.put("n_predict", JiveGlobals.getIntProperty("llama.predictions", 256));
				testData.put("stream", true);
				testData.put("cache_prompt", JiveGlobals.getBooleanProperty("llama.cache.prompt", true));				
				testData.put("slot_id", threadId);				
				testData.put("temperature", temperature);
				testData.put("top_k", JiveGlobals.getIntProperty("llama.top.k.sampling", 40));
				testData.put("top_p", top_p);				
				
				getJson("/completion", testData, requestor, chatType);
			}
		});			
	}
	
	public void route(String xml) {
		try {
			router.route(DocumentHelper.parseText(xml).getRootElement());	
		} catch (Exception e) {
			Log.error("xmpp routing failed", e);
		}			
	}
	
	public JID getJid() {
		return session.getAddress();
	}
	
    @Override
    public void closeVirtualConnection() {
		exec.shutdown();
    }

    @Override
    public byte[] getAddress() {
		return remoteAddr.getBytes();
    }

    @Override
    public String getHostAddress() {
		return remoteAddr;
    }

    @Override
    public String getHostName()  {
        return username;
    }

    @Override
    public void systemShutdown() {
		exec.shutdown();
    }

    @Override
    public void deliver(Packet packet) throws UnauthorizedException {	
		// auto accept presence subscriptions
		
        if (packet instanceof Presence) {
			Presence presence = (Presence) packet;			
			
			if (presence.getType() == Presence.Type.subscribe) {
				Presence presence1 = new Presence();
				presence1.setTo(packet.getFrom());	
				presence1.setFrom(username + "@" + domain + "/" + remoteAddr);
				presence1.setType(Presence.Type.subscribed);
				XMPPServer.getInstance().getPresenceRouter().route(presence1);	

				Presence presence2 = new Presence();
				presence2.setTo(packet.getFrom());	
				presence2.setFrom(username + "@" + domain + "/" + remoteAddr);
				presence2.setType(Presence.Type.subscribe);
				XMPPServer.getInstance().getPresenceRouter().route(presence2);					
			}
		}
		else

		// auto accept MUC invitations
		
        if (packet instanceof Message) 
		{		
			Message message = (Message) packet;
			String muc = null;			
			/*
				<message from="florence@desktop-545pc5b/converse.js-22447515" id="2446786b-7063-4e7a-84ad-c5d91bd492dd" to="dele@desktop-545pc5b">
					<x xmlns="jabber:x:conference" jid="lobby@conference.desktop-545pc5b" reason="Please join me at CCC"></x>
				</message>
			*/
			
			Element childElement = message.getChildElement("x", "http://jabber.org/protocol/muc#user");

			if (childElement != null) {
				Element inviteElement = childElement.element("invite");

				if (inviteElement != null) {
					muc = packet.getFrom().toString();
				}
			}
			else {
				childElement = message.getChildElement("x", "jabber:x:conference");

				if (childElement != null) {
					muc = childElement.attribute("jid").getStringValue();
				}				
			}
			
			if (muc != null) {
				Log.debug("Auto-accept MUC invitation " + muc);	

				Presence presence = new Presence();
				presence.setTo(muc + "/" + username);	
				presence.setFrom(username + "@" + domain + "/" + remoteAddr);	
				presence.addChildElement("x", "http://jabber.org/protocol/muc");				
				XMPPServer.getInstance().getPresenceRouter().route(presence);					
			}
			else {						
				Log.debug("Incoming Message " + packet.getFrom() + "\n" + message.getBody());
				String from = packet.getFrom().getNode();
				String msg = message.getBody();
				
				if (!isNull(msg) && !isNull(from) /*&& SessionManager.getInstance().getSessions(from).size() > 0*/) 
				{
					handlePrediction(msg, packet.getFrom(), message.getType());
				}					
			}
		}
		else 
			
		if (packet instanceof IQ)  {
			IQ iq = (IQ) packet;
			Log.debug("Incoming IQ " + packet.getFrom() + " " + iq.getType());			
		}
    }

    @Override
    public void deliverRawText(String text) {
		Log.debug("deliverRawText\n" + text);	
    }

    @Override
    public boolean validate() {
        return true;
    }

    @Override
    public boolean isSecure() {
        return false;
    }

    @Override
    public PacketDeliverer getPacketDeliverer() {
        if (backupDeliverer == null) {
            backupDeliverer = new OfflinePacketDeliverer();
        }
        return backupDeliverer;
    }

    @Override
    public ConnectionConfiguration getConfiguration() {
        if (configuration == null) {
            final ConnectionManagerImpl connectionManager = ((ConnectionManagerImpl) XMPPServer.getInstance().getConnectionManager());
            configuration = connectionManager.getListener( connectionType, true ).generateConnectionConfiguration();
        }
        return configuration;
    }

    @Override
    public boolean isCompressed() {
        return false;
    }

	
    //-------------------------------------------------------
    //
    //  Utility methods
    //
    //-------------------------------------------------------	

	private void getJson(String urlToRead, JSONObject data, JID requestor, Message.Type chatType)  {
		URL url;
		HttpURLConnection conn;
		BufferedReader rd;
		String line;
		StringBuilder result = new StringBuilder();

		String llamaHost = JiveGlobals.getProperty("llama.host", hostname);			
		String username = JiveGlobals.getProperty("llama.username", "llama");
		String password = JiveGlobals.getProperty("llama.password", "llama");		
		String auth = username + ":" + password;
		String authHeaderValue = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
		String uri = remoteUrl + urlToRead;
		
		Log.info("getJson from LLaMA " + requestor + " " + uri + "\n" + data);
		
		try {
			url = new URL(uri);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestProperty("Authorization", authHeaderValue);			

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");  
			conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8");
			conn.setRequestProperty("Connection", "Keep-Alive");
			conn.setRequestProperty("Charset", "UTF-8");
			conn.setRequestProperty("Accept", "text/event-stream");
			
			conn.getOutputStream().write(data.toString().getBytes(StandardCharsets.UTF_8));

			rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));

			if (requestor != null) replyState("active", requestor, chatType);
			long seq = 0;
			
			while ((line = rd.readLine()) != null) {
				Log.debug("getJson - stream\n" + line);				
				
				if (line.startsWith("data:")) {
					JSONObject lineJson = new JSONObject("{" + line + "}").getJSONObject("data");
					
					if (!lineJson.getBoolean("stop")) {	// we got a text stream
						String content = lineJson.getString("content");
						
						if (content.equals("\n")) {
							String msg = result.toString();
							Log.info("getJson - chat\n" + msg);	
							result = new StringBuilder();	
							
							if (requestor != null && !isNull(msg)) {
								replyChat(msg, requestor, chatType);
							}
							
						} else {
							result.append(content);
							
							if (requestor != null) {
								replyState("composing", requestor, chatType);
								replyRtt(content, requestor, chatType, seq);
								seq++;	
							}
						}
					} else {	// end of text stream
						String msg = result.toString();
						Log.info("getJson - chat\n" + msg);
						
						if (requestor != null) {
							replyChat(msg, requestor, chatType);						
						}
					}
				} else {
						
				}
			}
			rd.close();	
			if (requestor != null) replyState("inactive", requestor, chatType);				

		} catch (Exception e) {
			Log.error("getJson", e);
		}
	}

	private void replyState(String state, JID requestor, Message.Type chatType) {
		Log.debug("replyState from LLaMA " + requestor + " " + state);	

		Message newMessage = new Message();
		newMessage.setFrom(username + "@" + domain + "/" + remoteAddr);
		newMessage.setTo(requestor);		
		newMessage.setType(chatType);
        newMessage.addExtension(new PacketExtension(state, "http://jabber.org/protocol/chatstates"));		
		
		XMPPServer.getInstance().getRoutingTable().routePacket(requestor, newMessage, true);				
	}
	
	private void replyRtt(String msg, JID requestor, Message.Type chatType, long seq) {
		Log.debug("replyRtt from LLaMA " + requestor + " " + msg);	
		
		Message newMessage = new Message();
		newMessage.setFrom(username + "@" + domain + "/" + remoteAddr);
		newMessage.setTo(requestor);		
		newMessage.setType(chatType);

		Element rtt = DocumentHelper.createElement(QName.get("rtt", "urn:xmpp:rtt:0"));
		rtt.addAttribute("seq", String.valueOf(seq));
		if (seq == 0) rtt.addAttribute("event", "new");
		
		Element t = rtt.addElement("t");
		t.setText(msg);
        newMessage.addExtension(new PacketExtension(rtt));		
		
		XMPPServer.getInstance().getRoutingTable().routePacket(requestor, newMessage, true);	
	}	
	
	private void replyChat(String msg, JID requestor, Message.Type chatType) {
		Log.debug("replyChat from LLaMA " + requestor + "\n" + msg);	

		Message newMessage = new Message();
		newMessage.setFrom(username + "@" + domain + "/" + remoteAddr);
		newMessage.setTo(requestor);		
		newMessage.setType(chatType);
		newMessage.setBody(msg);
        newMessage.addExtension(new PacketExtension("active", "http://jabber.org/protocol/chatstates"));		
		
		XMPPServer.getInstance().getRoutingTable().routePacket(requestor, newMessage, true);				
	}
	
    private boolean isNull(String value)   {
        return (value == null || "undefined".equals(value)  || "null".equals(value) || "".equals(value.trim()) || "unknown".equals(value) || "none".equals(value));
    }	
}