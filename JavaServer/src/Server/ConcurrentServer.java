package Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import SSL.SSLClientSession;
import SSL.SSLSocketChannel;

public class ConcurrentServer extends Thread implements Runnable{

	private SSLContext ssl_context;
	private ServerSocketChannel server_socket_channel;
	private Selector server_selector;
	private SSLEngine ssl_engine;
	private Map<SelectionKey, SSLClientSession> open_channels = new ConcurrentHashMap<SelectionKey, SSLClientSession>();
	private JSONParser parser = new JSONParser();
	private boolean running = true;
	private String threadID = null;
	
	private ServerSystem serverSystem;
	
 	public ConcurrentServer( ServerSystem serverSystem, SSLContext ssl_context, ServerSocketChannel server_socket_channel, String threadID) {
		this.serverSystem = serverSystem;
 		this.ssl_context = ssl_context;
		this.threadID = threadID;
		this.server_socket_channel = server_socket_channel;
		
		try {
			server_socket_channel.register(server_selector = Selector.open(), SelectionKey.OP_ACCEPT);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * If a SelectionKey isAcceptable() then call this method
	 * to handle the exception
	 * @throws IOException
	 */
	private void handleAccept(SelectionKey sk) throws IOException {
		SocketChannel accepted_channel = server_socket_channel.accept();
		if (accepted_channel != null) {
			accepted_channel.configureBlocking(false);
			
			//Create an SSL engine for this connection
			ssl_engine = ssl_context.createSSLEngine("localhost", accepted_channel.socket().getPort());
			ssl_engine.setUseClientMode(false);
			
			// Create a SSL Socket Channel for the channel & engine
			SSLSocketChannel ssl_socket_channel = new SSLSocketChannel(accepted_channel, ssl_engine);
			
			// Create a new session class for the user
			SSLClientSession session = new SSLClientSession(ssl_socket_channel);
			
			// Register for OP_READ with ssl_socket_channel as attachment
			SelectionKey key = accepted_channel.register(server_selector, SelectionKey.OP_READ, session);

			// Add client to open channels map
			open_channels.put(key, session);
			
			System.out.println("Thread with ID - " + this.threadID + " - accepted a connection.");
		}
	}
	
	/**
	 * Processes what was read from the selection key's socket channel
	 * @param sk
	 * @throws IOException
	 */
	private void handleRead(SelectionKey key) throws IOException {
		SSLSocketChannel ssl_socket_channel = ((SSLClientSession)key.attachment()).getSSLSocketChannel();
		ByteBuffer request = ssl_socket_channel.getAppRecvBuffer();
		int count = ssl_socket_channel.read();
		
		if (count < 0) {
			
			removeKeyAndSession(key);
			
		} else if (request.position() > 0) {
			
			try {
				String message = new String(request.array(),0,request.position());
				
//				OutputHandler.println("Server: read "+message);			
				
				// Parse the JSON message
				JSONObject json = (JSONObject) parser.parse(message);
				
				// Process the message
				processNetworkMessage(json, key);
				
				request.clear();
			} catch (ParseException e) {
				System.out.println("Invalid message format.");
			}
		}
	}
	
	/**
	 * Handles the writing operations
	 * @param sk
	 * @throws IOException
	 */
	private void handleWrite(SelectionKey key, String message, SSLClientSession  session) throws IOException {
		SSLSocketChannel ssl_socket_channel = session.getSSLSocketChannel();
		
		ByteBuffer out_message = ssl_socket_channel.getAppSendBuffer();
		
		if(message != null) 
			out_message.put(message.getBytes());
		
//		OutputHandler.println("Server: writing "+new String(out_message.array(), 0, out_message.position()));
		int count = 0;
		
		while (out_message.position() > 0) {
			count = ssl_socket_channel.write();
			if (count == 0) {
//				OutputHandler.println("Count was 0.");
				break;
			}
//			else {
//				OutputHandler.println("Count was "+count);
//			}
		}
		
		if (out_message.position() > 0) {
			// short write:
			// Register for OP_WRITE and come back here when ready
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
			
			session.rewrite = true;
		} else {
			if(session.out_messages.isEmpty()) { 
				// Write succeeded, don�t need OP_WRITE any more
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
			} 
			
			session.rewrite = false;
		}
	}
	
	/**
	 * Processes the incoming message
	 * @param json
	 */
	private void processNetworkMessage( JSONObject obj, SelectionKey key ) {
		
		String username = (String) obj.get("username");
		
		switch(Integer.parseInt((String)obj.get("ID")))
		{
			case ServerSystem.LOGIN:
			{
				((SSLClientSession)key.attachment()).setUsername(username);
			}
		
			case ServerSystem.NEW_FILE:
			{
				String filename = (String) obj.get("filename");
				
				
			}
			
			case ServerSystem.INSERT:
			{
				String filename = (String) obj.get("filename");
				
				serverSystem.updateFile(filename, (String) obj.get("string"), Integer.parseInt((String)obj.get("pos")), true);
				sendMessageToAllUsers(username, obj);
			}
			
			case ServerSystem.DELETE:
			{
				String filename = (String) obj.get("filename");
				
				serverSystem.updateFile(filename, (String) obj.get("string"), Integer.parseInt((String)obj.get("pos")), false);
				sendMessageToAllUsers(username, obj);
			}
			
			case ServerSystem.PULL_CHANGES:
			{
				String filename = (String) obj.get("filename");
				
				
			}
			
			case ServerSystem.GET_FILE:
			{
				String filename = (String) obj.get("filename");
				obj.put("file", serverSystem.getFile(filename));
				sendMessageToUser(obj, key, (SSLClientSession)key.attachment());
			}
		}
	}
	
	/**
	 * Removes the key and the session from open_channels,
	 * disconnects the socket channel within the client session, 
	 * and cancels (unregisters) the key. 
	 * @param key
	 */
	private void removeKeyAndSession(SelectionKey key) {
		SSLClientSession session = ((SSLClientSession)key.attachment());
		String username = session.getUsername();
		
		open_channels.remove(key); // remove the open channel
		session.disconnect(); // shutdown the ssl socket channel
		key.cancel(); //cancel the key
		
		// send message to players saying that the user logged out
		JSONObject json = new JSONObject();
		json.put("ID", Integer.toString(serverSystem.LOGOUT));
		json.put("username", username);
		sendMessageToAllUsers(username, json);
		
		if(username == null)
			System.out.println("Unknown user has disconnected.");
		else
			System.out.println(username+" has disconnected from the communication server.");
	}
	
	@Override
	public void run() {
//		int count;
		
		System.out.println("Communication Server: engaged!");
		
		while(running) { 
			
			try {
				/*count = */server_selector.select();
			} catch (IOException e1) {
				System.out.println("Selector has been closed.");
				return;
			}
				
//			if(count > 0)
//				OutputHandler.println("Communication Server: "+count+" selected.");
			/*
			 * Iterate through the server_selector selected keys
			 * to see if any channels are IO ready
			 */
			for(SelectionKey key: server_selector.selectedKeys()) {
				if(!key.isValid()) continue;
				
				try {
					
					if(key.isValid() && key.isAcceptable()) {
						handleAccept(key);
					}
					
					if(key.isValid() && key.isReadable()) {
						handleRead(key);
					}
					
					if(key.isValid() && key.isWritable()) {
						
						SSLClientSession session = (SSLClientSession)key.attachment();
						if(session.rewrite)
							handleWrite(key, null, session);
						else {
							handleWrite(key, session.out_messages.remove(), session);
//							OutputHandler.println("Messages left: "+session.out_messages.size());
						}
					} 
					
				} catch (IOException exc) {
					removeKeyAndSession(key);
				}
			}
			
			server_selector.selectedKeys().clear();
		}
		
		try {
			server_selector.close();
			System.out.println("Communication server stopped.");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Sends a message to currently logged in users
	 * @param (JSON) message
	 * @param (boolean) true if it came from server
	 */
	public void sendMessageToAllUsers(String username, JSONObject json) {
		sendMessageToAllUsers(username, json.toString());
	}
	
	/**
	 * Sends a message to currently logged in players
	 * @param (JSON String) message
	 * @param (boolean) true if it came from server
	 */
	public void sendMessageToAllUsers(String username, String message) {
		
		for(Map.Entry<SelectionKey, SSLClientSession> entry : open_channels.entrySet()) {
			
			if(!entry.getValue().getUsername().equals(username)) {
				try {
					SelectionKey user_key = entry.getKey();
					entry.getValue().out_messages.add(message);
					user_key.interestOps(user_key.interestOps() | SelectionKey.OP_WRITE);
					server_selector.wakeup();
				} catch (CancelledKeyException e) {
					open_channels.remove(entry.getKey());
				}
			}
		}
	}
	
	/**
	 * Sends a message to currently logged in user
	 * @param (JSON) message
	 * @param (boolean) true if it came from server
	 */
	public void sendMessageToUser(JSONObject json, SelectionKey key, SSLClientSession session) {
		sendMessageToUser(json.toString(), key, session);
	}
	
	/**
	 * Sends a message to currently logged in user
	 * @param (JSON String) message
	 * @param (boolean) true if it came from server
	 */
	public void sendMessageToUser(String message, SelectionKey key, SSLClientSession session) {
		try {
			session.out_messages.add(message);
			key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
			server_selector.wakeup();
		} catch (CancelledKeyException e) {
			open_channels.remove(key);
		}
	}
	
	/**
	 * Sets the servers "running" boolean to false effectively
	 * stopping the continuous while loop
	 */
	public void stopCommunicationServer() {
		this.running = false;
	}
}