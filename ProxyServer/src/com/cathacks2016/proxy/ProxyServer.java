package com.cathacks2016.proxy;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;

import com.cathacks2016.proxy.networking.ThreadedServerBucket;
import com.cathacks2016.proxy.networking.ssl.SelfTrustManager;

public class ProxyServer {

	/*
	 * Security variables
	 */
	private static final String SERVER_KEYSTORE_LOCATION = "security/charon_server.jks";
	private static final String KEYSTORE_PASSWORD = "#Ry@uL?P@r+xydc$9Zy9etuVsHsXx*qaDG!_XL_RL2TxHE6yJ8VFFjCfxa5a3-zw";
	private static final String CERTIFICATE_PASSWORD = "Q2H5m=?PvsnQM@Jc9W*bVQNnp3j^XrmCaJWtKHNDyY_PEcU$A-?t228sTP48SM9+";
	
	private static final String PROXY_SERVER_IP = "10.20.236.179";
	private static final int PROXY_SERVER_PORT = 22724;
	
	private static ServerSocketChannel server_socket_channel;
	private SSLContext ssl_context;
	private SSLServerSocketFactory ssl_server_creator;
	
	public ProxyServer(){
		/*
		 * Load in the SSL certificates
		 */
		try {
			KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
			keyStore.load(new FileInputStream(SERVER_KEYSTORE_LOCATION), KEYSTORE_PASSWORD.toCharArray());

			KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
			keyManagerFactory.init(keyStore, CERTIFICATE_PASSWORD.toCharArray());
			
			KeyManager keyManagers[] = keyManagerFactory.getKeyManagers();

			TrustManager trustManagers[] = { new SelfTrustManager(keyStore) };
			
			ssl_context = SSLContext.getInstance("TLS");
			ssl_context.init(keyManagers, trustManagers, new SecureRandom());
			
			ssl_server_creator = ssl_context.getServerSocketFactory();
			
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
			e.printStackTrace();
		}
		
		/*
		 * Initialize server connection threads
		 */
		InetSocketAddress inet_socket_address = new InetSocketAddress(PROXY_SERVER_IP, PROXY_SERVER_PORT);
		
		try {
			server_socket_channel = ServerSocketChannel.open();
			server_socket_channel.configureBlocking(false);
			server_socket_channel.socket().bind(inet_socket_address, 50);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		ThreadedServerBucket socket_buckets = new ThreadedServerBucket( server_socket_channel, ssl_context );
		
		/*
		 * 
		 */
		
	}
	
}
