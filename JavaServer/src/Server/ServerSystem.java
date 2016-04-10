package Server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Set;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.swing.filechooser.FileSystemView;

import SSL.SelfTrustManager;


public class ServerSystem {
	
	private HashMap<String, StringBuilder> openFiles;
	private HashMap<String, String> filePathNames;
	
	private static ServerSocketChannel commServerSocketChannel;
	private CommunicationServer commServer;
	
	public static final int LOGIN = 0;
	public static final int NEW_FILE = 1;
	public static final int INSERT = 2;
	public static final int PULL_CHANGES = 3;
	public static final int AVAILABLE_FILES = 4;
	public static final int GET_FILE = 5;
	public static final int DELETE = 6;
	public static final int LOGOUT = 7;

	private File fileDirectory;
	
	private SSLServerSocketFactory sslSocketFactory; 
	private SSLContext sslContext;
	
	/*
	 * Security variables
	 */
	private final String SERVER_KEYSTORE_LOCATION = "security/charon_server.jks";
	private final String KEYSTORE_PASSWORD = "#Ry@uL?P@r+xydc$9Zy9etuVsHsXx*qaDG!_XL_RL2TxHE6yJ8VFFjCfxa5a3-zw";
	private final String CERTIFICATE_PASSWORD = "Q2H5m=?PvsnQM@Jc9W*bVQNnp3j^XrmCaJWtKHNDyY_PEcU$A-?t228sTP48SM9+";
	
	private final int COMM_SERVER_PORT = 22754;
	private static final String COMM_SERVER_INET_ADDRESS = "10.20.221.152";
	
	public ServerSystem() {
		filePathNames = new HashMap<String, String>();
		fileDirectory = getFileDirectory();
		openFiles = loadFiles();
		
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
			
			sslContext = SSLContext.getInstance("TLS");
			sslContext.init(keyManagers, trustManagers, new SecureRandom());
			
			sslSocketFactory = sslContext.getServerSocketFactory();
			
			initCommServer();
			
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
			System.out.println(e.getMessage());
		}
	}
	
	/**
	 * Sets up the communication accept server
	 * @throws IOException
	 */
	public void initCommServer() throws IOException {
		InetSocketAddress inet_socket_address = new InetSocketAddress(COMM_SERVER_INET_ADDRESS, COMM_SERVER_PORT);
		
		try {
			commServerSocketChannel = ServerSocketChannel.open();
			commServerSocketChannel.configureBlocking(false);
			commServerSocketChannel.socket().bind(inet_socket_address, 50);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		commServer = new CommunicationServer(this, sslContext, commServerSocketChannel, "Thread 0");	
	}
	
	public void updateFile(String filename, String s, int pos, boolean add) {
		if(add)
			openFiles.put(filename, openFiles.get(filename).insert(pos, s));
		else
			openFiles.put(filename, openFiles.get(filename).delete(pos, s.length()));
	}
	
	public Set<String> getFileNames() {
		return filePathNames.keySet();
	}
	
	
	public String getFile(String filename) {
		return openFiles.get(filename).toString();
	}
	
	private HashMap<String, StringBuilder> loadFiles() { 
		HashMap<String, StringBuilder> files = new HashMap<String, StringBuilder>();
		
		String[] fileList = fileDirectory.list();
		
		for(String f : fileList) {
			String name = f.substring(f.lastIndexOf(File.separator), f.length() - 1);
			filePathNames.put(name, f);
			
			String fileAsString = readFile(f, Charset.defaultCharset());
			
			if(fileAsString != null)
				files.put(name, new StringBuilder(fileAsString));
		}
		
		return files;
	}
	
	private File getFileDirectory() {
		String os_name = System.getProperty("os.name").toLowerCase();
		String directory;
		
		if( os_name.contains("windows") ){
			directory = FileSystemView.getFileSystemView().getDefaultDirectory().getPath() + File.separator + "ConcurrentSublimeEditor";
		} else {
			directory = System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ConcurrentSublimeEditor";
		}
		
		File fileDirec = new File(directory);
		
		if(!fileDirec.isDirectory())
			fileDirec.mkdir();
		
		return fileDirec;
	}
	
	private String readFile(String path, Charset encoding) {
		byte[] encoded;
		
		try {
			encoded = Files.readAllBytes(Paths.get(path));
			
		} catch (IOException e) {
			encoded = null;
			
			//Silently Ignore
		}
		
		return new String(encoded, encoding);
	}

	
	public static void main(String[] args){
		ServerSystem serverSystem = new ServerSystem();
		
		
	}
}
