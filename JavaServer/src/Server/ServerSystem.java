package Server;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
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
import java.util.Map.Entry;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManager;
import javax.swing.filechooser.FileSystemView;

import SSL.SelfTrustManager;
import Server.StringModificationTree.StringNode;


public class ServerSystem {
	
	private static ServerSocketChannel commServerSocketChannel;
	public static final int LOGIN = 0;
	
	public static final int NEW_FILE = 1;
	public static final int INSERT = 2;
	
	public static final int PULL_CHANGES = 3;
	public static final int AVAILABLE_FILES = 4;
	public static final int GET_FILE = 5;
	public static final int DELETE = 6;
	public static final int LOGOUT = 7;
	public static final int KEEP_ALIVE = 8;
	
	private static final String SERVER_INET_ADDRESS = "10.20.216.10";
	private final int SERVER_PORT = 22754;
	
	public static void main(String[] args){
		ServerSystem serverSystem = new ServerSystem();
	}
	
	private HashMap<String, StringModificationTree> openFiles;
	private HashMap<String, String> filePathNames;
	
	private ConcurrentServer commServer; 
	private File fileDirectory;
	
	private SSLServerSocketFactory sslSocketFactory;
	private SSLContext sslContext;
	/*
	 * Security variables
	 */
	private final String SERVER_KEYSTORE_LOCATION = "security/charon_server.jks";
	
	private final String KEYSTORE_PASSWORD = "#Ry@uL?P@r+xydc$9Zy9etuVsHsXx*qaDG!_XL_RL2TxHE6yJ8VFFjCfxa5a3-zw";
	private final String CERTIFICATE_PASSWORD = "Q2H5m=?PvsnQM@Jc9W*bVQNnp3j^XrmCaJWtKHNDyY_PEcU$A-?t228sTP48SM9+";
	
	
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
			
			initConcurrentServer();
			
			Timer t = new Timer();
			t.scheduleAtFixedRate(new TimerTask(){

				@Override
				public void run() {
					for( Entry<String, StringModificationTree> file : openFiles.entrySet() ){
						String new_path = fileDirectory.getAbsolutePath() + "/" + file.getKey();
						if( new_path.contains("\\") ){
							new_path = new_path.replaceAll("\\", "/");
						}
						
						File new_file = new File( new_path );
						
						BufferedWriter out = null;
						try {
							out = new BufferedWriter( new FileWriter( new_file ) );
							
							String buffer = file.getValue().toString();
							
							System.out.println("Writing "+new_file.getAbsolutePath()+" with "+buffer);
							
							out.write( buffer );
							out.flush();
							
						} catch (IOException e) {
							e.printStackTrace();
						} finally {
							if( out != null ){
								try {
									out.close();
								} catch (IOException e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}
					}
				}
				
			}, 1000, 2000);
			
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | UnrecoverableKeyException | KeyManagementException e) {
			System.out.println(e.getMessage());
		}
	}
	
	public String getFile(String filename) {
		return openFiles.get(filename).toString();
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
	
	
	public Set<String> getFileNames() {
		return filePathNames.keySet();
	}
	
	/**
	 * Sets up the communication accept server
	 * @throws IOException
	 */
	public void initConcurrentServer() throws IOException {
		InetSocketAddress inet_socket_address = new InetSocketAddress(SERVER_INET_ADDRESS, SERVER_PORT);
		
		try {
			commServerSocketChannel = ServerSocketChannel.open();
			commServerSocketChannel.configureBlocking(false);
			commServerSocketChannel.socket().bind(inet_socket_address, 50);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		commServer = new ConcurrentServer(this, sslContext, commServerSocketChannel, "Thread 0");
		
		commServer.start();
	}
	
	private HashMap<String, StringModificationTree> loadFiles() { 
		HashMap<String, StringModificationTree> files = new HashMap<>();
		
		String[] fileList = fileDirectory.list();
		long file_loaded_time = System.currentTimeMillis();
		
		for(String f : fileList) {
			String new_path = fileDirectory.getAbsolutePath() + "/" + f;
			if( new_path.contains("\\") ){
				new_path = new_path.replaceAll("\\", "/");
			}
			
			String file_name = f;
			if( file_name.contains(File.separator) ){
				file_name = file_name.substring(file_name.lastIndexOf(File.separator), file_name.length() - 1);
			}
			
			// if hidden file
			if( file_name.startsWith(".") )
				continue;
			
			filePathNames.put(file_name, f);
			
			StringModificationTree fileAsString = new StringModificationTree( file_loaded_time, readFile(new_path, Charset.defaultCharset()) );
			
			if(fileAsString != null)
				files.put(file_name, fileAsString);
		}
		
		return files;
	}
	
	private String readFile(String path, Charset encoding) {
		byte[] encoded;
		
		try {
			encoded = Files.readAllBytes(Paths.get(path));
			
		} catch (IOException e) {
			encoded = null;
			
			//Silently Ignore
		}
		
		System.out.println(path);
		
		return new String(encoded, encoding);
	}

	public void addTextToFile( String file, long timestamp, int location, String text ){
		String new_path = fileDirectory.getAbsolutePath() + "/" + file;
		if( new_path.contains("\\") ){
			new_path = new_path.replaceAll("\\", "/");
		}
		
		if( openFiles.containsKey( file ) ){
			openFiles.get(file).applyTransformation( new StringNode( timestamp, location, text ) );
		}else{
			System.out.println("Tried to modify file "+new_path);
		}
	}
	
	public void removeTextFromFile( String file, long timestamp, int location, int length ){
		String new_path = fileDirectory.getAbsolutePath() + "/" + file;
		if( new_path.contains("\\") ){
			new_path = new_path.replaceAll("\\", "/");
		}
		
		if( openFiles.containsKey( file ) ){
			openFiles.get(file).applyTransformation( new StringNode( timestamp, location, length ) );
		}else{
			System.out.println("Tried to modify file "+new_path);
		}
	}

	public void createNewFile( String file, String file_text ) {
		String new_path = fileDirectory.getAbsolutePath() + "/" + file;
		if( new_path.contains("\\") ){
			new_path = new_path.replaceAll("\\", "/");
		}
		
		if( !openFiles.containsKey( new_path ) ){
			try {
				File new_file = new File( new_path );
				
				if( !new_file.exists() )
					new_file.createNewFile();
				
				System.out.println( new_file.getAbsolutePath() );

				StringModificationTree fileAsString = new StringModificationTree( System.currentTimeMillis(), file_text );
				
				openFiles.put(file, fileAsString);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
