package com.cathacks2016.proxy.networking;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.cathacks2016.proxy.networking.ssl.SSLClientSession;
import com.cathacks2016.proxy.networking.ssl.SSLSocketChannel;

public class ThreadedServerBucket {

	private ServerSocketChannel server_socket_channel;
	private SSLContext ssl_context;
	private HashMap<String, ServerBucket> buckets = new HashMap<>();
	private int next_thread_id = 0;
	
	public ThreadedServerBucket( ServerSocketChannel server_socket_channel, SSLContext ssl_context ) {
		this.server_socket_channel = server_socket_channel;
		this.ssl_context = ssl_context;
		
		// Create base thread
		ServerBucket initial_bucket = new ServerBucket();
		buckets.put( initial_bucket.getName(), initial_bucket );
		initial_bucket.start();
		
	}
	
	private class ServerBucket extends Thread{
		
		public static final int CAPACITY = 100;
		private final Vector<SSLClientSession> connections = new Vector<>();
		private Selector server_selector;
		private boolean running = true;
		private Map<SelectionKey, SSLClientSession> open_channels = new ConcurrentHashMap<SelectionKey, SSLClientSession>();
		private JSONParser parser = new JSONParser();
		
		public ServerBucket() {
			super("Communication Server Thread ("+(next_thread_id++)+")");

			try {
				server_socket_channel.register(server_selector = Selector.open(), SelectionKey.OP_ACCEPT);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void run(){
			while( running ){

				try {
					/*count = */server_selector.select();
				} catch (IOException e1) {
					System.out.println("Selector has been closed.");
					return;
				}
					
//				if(count > 0)
//					OutputHandler.println("Communication Server: "+count+" selected.");
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
//								OutputHandler.println("Messages left: "+session.out_messages.size());
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
				System.out.println(this.getName()+" thread stopped.");
			} catch (IOException e) {
				e.printStackTrace();
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
			
			if(username == null)
				System.out.println("Unknown user has disconnected.");
			else
				System.out.println(username+" has disconnected from the communication server.");
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
				SSLEngine ssl_engine = ssl_context.createSSLEngine("localhost", accepted_channel.socket().getPort());
				ssl_engine.setUseClientMode(false);
				
				// Create a SSL Socket Channel for the channel & engine
				SSLSocketChannel ssl_socket_channel = new SSLSocketChannel(accepted_channel, ssl_engine);
				
				// Create a new session class for the user
				SSLClientSession session = new SSLClientSession(ssl_socket_channel);
				
				// Register for OP_READ with ssl_socket_channel as attachment
				SelectionKey key = accepted_channel.register(server_selector, SelectionKey.OP_READ, session);

				// Add client to open channels map
				open_channels.put(key, session);
				
				System.out.println("Thread with ID - " + this.getName() + " - accepted a connection.");
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
			
//			OutputHandler.println("Server: writing "+new String(out_message.array(), 0, out_message.position()));
			int count = 0;
			
			while (out_message.position() > 0) {
				count = ssl_socket_channel.write();
				if (count == 0) {
//					OutputHandler.println("Count was 0.");
					break;
				}
//				else {
//					OutputHandler.println("Count was "+count);
//				}
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
					
//					OutputHandler.println("Server: read "+message);			
					
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
		 * Processes the incoming message
		 * @param json
		 */
		private void processNetworkMessage( JSONObject json, SelectionKey key ){
			
			//Switch statement based off of Network Message ID's
			switch(Integer.parseInt((String)json.get("ID")))
			{
				case NetworkEngine.COMM_SERVER_MSG_ID_SEND_MESSAGE:
				{
					sendMessageToPlayers(json, false);
					break;
				}
				case NetworkEngine.COMM_SERVER_MSG_ID_WHOAMI:
				{
					String username = (String)json.get("username");
					((SSLClientSession)key.attachment()).setUsername(username);
					
					// send message to players saying that the player logged in
					JSONObject logged_in = new JSONObject();
					logged_in.put("ID", Integer.toString(server_systems.network_engine.MSG_ID_PLAYER_LOGGED_IN));
					logged_in.put("dest", Integer.toString(server_systems.network_engine.COMM_SERVER_MSG_DEST_GAME_MANAGER));
					logged_in.put("username", username);
					sendMessageToPlayers(logged_in, false);
					break;
				}
			}
		}
		
		/**
		 * Sends a message to currently logged in players
		 * @param (JSON) message
		 * @param (boolean) true if it came from server
		 */
		public void sendMessageToPlayers(JSONObject json) {
			sendMessageToPlayers(json.toString());
		}
		
		/**
		 * Sends a message to currently logged in players
		 * @param (JSON String) message
		 * @param (boolean) true if it came from server
		 */
		public void sendMessageToPlayers(String message) {
			for(Map.Entry<SelectionKey, SSLClientSession> entry : open_channels.entrySet()) {
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
		
		/**
		 * Sets the servers "running" boolean to false effectively
		 * stopping the continuous while loop
		 */
		public void stopCommunicationServer() {
			this.running = false;
		}
		
	}
	
}
