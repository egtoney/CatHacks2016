package SSL;

import java.util.concurrent.ConcurrentLinkedQueue;

public class SSLClientSession {
	
	private SSLSocketChannel ssl_socket_channel;
	private String username = null;
	public ConcurrentLinkedQueue<String> out_messages = new ConcurrentLinkedQueue<String>();
	public boolean rewrite = false;
	
	public SSLClientSession(SSLSocketChannel ssl_socket_channel) {
		this.ssl_socket_channel = ssl_socket_channel;
	}
	
	/**
	 * Closes the socket channel
	 */
	public void disconnect() {
		try {
			if(ssl_socket_channel == null)
				return;
			
			ssl_socket_channel.close();
			
		} catch (Throwable t) {} //ignore closing errors. We know...
	}
	
	/**
	 * Returns the ClientSessions ssl socket channel
	 * @return SSLSocketChannel
	 */
	public SSLSocketChannel getSSLSocketChannel() {
		return ssl_socket_channel;
	}
	
	/**
	 * Sets the username for the client session
	 * @param (String) username
	 */
	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * Gets the username from the client session
	 * @return (String) username
	 */
	public String getUsername() {
		return username;
	}
}
