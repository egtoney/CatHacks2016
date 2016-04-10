package SSL;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

public class SSLSocketChannel {
	
	private SocketChannel socket_channel;
	private SSLEngine ssl_engine;
	private ByteBuffer app_send_buffer; 
	private ByteBuffer net_send_buffer; 
	private ByteBuffer app_recv_buffer; 
	private ByteBuffer net_recv_buffer; 
	private SSLEngineResult engine_result = null;
	private int threadNumber = 1;
	
	public SSLSocketChannel(SocketChannel socket_channel, SSLEngine ssl_engine) {
		this.socket_channel = socket_channel;
		this.ssl_engine = ssl_engine;
		
		SSLSession session = ssl_engine.getSession();  // create a new session
		int net_buffer_size = session.getPacketBufferSize();
		int app_buffer_size = session.getApplicationBufferSize();
		this.app_send_buffer = ByteBuffer.allocate(app_buffer_size);
		this.net_send_buffer = ByteBuffer.allocate(net_buffer_size);
		this.app_recv_buffer = ByteBuffer.allocate(app_buffer_size);
		this.net_recv_buffer = ByteBuffer.allocate(net_buffer_size);
	}
	
	/**
	 * Returns the ssl_engine
	 * @return SSLEngine
	 */
	public SSLEngine getEngine() {
		return ssl_engine;
	}

	/**
	 * Returns the SSLSocketChannel's receiving buffer
	 * @return
	 */
	public ByteBuffer getAppRecvBuffer() {
		return app_recv_buffer;
	}

	/**
	 * Returns the SSLSocketChannel's sending buffer
	 * @return
	 */
	public ByteBuffer getAppSendBuffer() {
		return app_send_buffer;
	}
	
	/**
	 * Starts a handshake with the client
	 */
	public void startHandshake()  {
		try {
			ssl_engine.beginHandshake();
			
			while(true) {
				if(!processHandshakeStatus()) {
					if(ssl_engine.getHandshakeStatus().equals(HandshakeStatus.FINISHED) || ssl_engine.getHandshakeStatus().equals(HandshakeStatus.NOT_HANDSHAKING))
						break;
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Read from the channel via the SSLEngine into the application receive buffer.
	 * Called in blocking mode when input is expected, or in non-blocking mode when the channel is readable.
	 * @return number of bytes read
	 * @throws IOException - likely because the socket_channel has been closed
	 */
	public int read() throws IOException
	{
		int	cipher_text_count = 0;
		int	plain_text_count = app_recv_buffer.position();
		
		do {
			flush();
			
			if (ssl_engine.isInboundDone())
				return plain_text_count > 0 ? plain_text_count : -1;
				
			// None, some, or all of the data required may already have been
			// unwrapped/decrypted, or EOF may intervene at any of these points.
			int	count = socket_channel.read(net_recv_buffer);
			
			net_recv_buffer.flip();
			engine_result = ssl_engine.unwrap(net_recv_buffer, app_recv_buffer);
			net_recv_buffer.compact();
			
			switch (engine_result.getStatus()) {
				case BUFFER_UNDERFLOW:
					assert(socket_channel.isOpen());
					cipher_text_count = socket_channel.read(net_recv_buffer);
					
					if (cipher_text_count == 0)
						return plain_text_count;
					
					if (cipher_text_count == -1)
						ssl_engine.closeInbound();	// may throw if incoming close_notify was not received, this is good.
					
					break;
				case BUFFER_OVERFLOW:
					// throw new BufferOverflowException();
					// There is no room in appRecvBuffer to decrypt the data in netRecvBuffer.
					// The application must empty appRecvBuffer every time it gets > 0 from this method.
					// In this case all we can do is return zero to the application.
					// We are certainly not handshaking or at EOS so we can exit straight out of this loop and method.
					return 0;
				case CLOSED:
					// RFC 2246 #7.2.1 requires us to stop accepting input.
					socket_channel.socket().shutdownInput();
					// RFC 2246 #7.2.1 requires us to respond with an outgoing close_notify.
					// This is deferred to processHandshake();
					break;
				case OK:
					plain_text_count = app_recv_buffer.position();
					break;
			}
			
			while (processHandshakeStatus())
				;
			
			plain_text_count = app_recv_buffer.position();

		} while (plain_text_count == 0);
		
		if (ssl_engine.isInboundDone())
			plain_text_count = -1;
		
		return plain_text_count;
	}
	
	/**
	 * Write from the application send buffer to the channel via the SSLEngine.
	 * Called in either blocking or non-blocking mode when application output is ready to send.
	 * @return the number of bytes written
	 */
	public int write() throws IOException {
		int	count = app_send_buffer.position();
		int	bytes_consumed = 0;
		
		if (flush() > 0 && count > 0) 	// If there is stuff left over to write and we still can't write it all, proceed no further.
			return 0;
		
		while (count > 0) {
			
			app_send_buffer.flip();
			engine_result = ssl_engine.wrap(app_send_buffer, net_send_buffer);
			app_send_buffer.compact();
			
			switch (engine_result.getStatus()) {
				case BUFFER_UNDERFLOW:
					throw new BufferUnderflowException(/*"source buffer: "+engineResult.getStatus()*/);
				case BUFFER_OVERFLOW:
					// net_send_buffer is full: flush it and try again
					int	write_count = flush();

					if (write_count == 0)
						return 0;
					continue;
				case CLOSED:
					// I would have thought the SSLEngine would do this itself,
					// so this is probably unreachable.
					throw new SSLException("SSLEngine: invalid state for write - " +engine_result.getStatus());
				case OK:
					bytes_consumed = engine_result.bytesConsumed();
					count -= bytes_consumed;
					break;
			}
			
			while (processHandshakeStatus()) {}
			
			flush();
		}
		
		// return count of bytes written.
		return bytes_consumed;
	}
	
	/**
	 * flush the output stream writing whatever is left to write
	 * @return number of bytes written
	 * @throws IOException
	 */
	public int flush() throws IOException {
		int	count = 0;
		
		net_send_buffer.flip();
		
		try {
			count = socket_channel.write(net_send_buffer);
		} catch(IOException e) {
//			e.printStackTrace();
//			OutputHandler.println("In flush(): Socket unexpectedly closed.");
			return count;
		}
		
		net_send_buffer.compact();
		
		return count;
	}
	
	/**
	 * Closes the the ssl_engine and socket_channel
	 * @throws IOException - Likely due to the socket_channel already being closed
	 */
	public void	close() throws IOException {
		// try a read
		if (!ssl_engine.isInboundDone() && !socket_channel.isBlocking())
			read();
		
		while (net_send_buffer.position() > 0) {
			int	count = flush();
			if (count == 0) {
				// Houston, we have a problem. We can't flush the remaining 
				// outbound data on close.
				break;
			} 
		}
		
		ssl_engine.closeOutbound();
		
		while (processHandshakeStatus()) {}
		
		if (net_send_buffer.position() > 0 && flush() == 0) {
			// Houston, we have a problem. We can't flush the remaining
			// outbound data on close.
			System.out.println("Can't flush remaining "+net_send_buffer.position()+" bytes");
		}

		// RFC 2246 #7.2.1 requires us to respond to an incoming close_notify with an outgoing close_notify,
		// but it doesn't require us to wait for it if we sent it first.
		if (!ssl_engine.isInboundDone())
			ssl_engine.closeInbound();
		
		socket_channel.close();
	}
	
	/**
	 * Processes the handshaking status
	 * @return true iff handshaking can continue
	 * @throws IOException
	 */
	private boolean	processHandshakeStatus() throws IOException {
		int	count;
		
		// process handshake status
		switch (ssl_engine.getHandshakeStatus()) {
			case NOT_HANDSHAKING:	// not presently handshaking => SSLSession is available
			case FINISHED:			// just finished handshaking, SSLSession is available
				return false;
			case NEED_TASK:
				runDelegatedTasks();
				// TODO need to do something to engineResult to stop it looping here forever
				return true; // keep going
			case NEED_WRAP:
				// output needed
				app_send_buffer.flip();
				engine_result = ssl_engine.wrap(app_send_buffer,net_send_buffer);
				app_send_buffer.compact();
				
				return (count = flush()) > 0;

			case NEED_UNWRAP:
				// Sometimes we get > 1 handshake messages at a time ...
				net_recv_buffer.flip();
				engine_result = ssl_engine.unwrap(net_recv_buffer, app_recv_buffer);
				net_recv_buffer.compact();
				
				if (engine_result.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) {
					if (ssl_engine.isInboundDone())
						count = -1;
					else {
						assert(socket_channel.isOpen());
						count = socket_channel.read(net_recv_buffer);
					}
					
					return count > 0;
				}
				
				if (engine_result.getStatus() == SSLEngineResult.Status.BUFFER_OVERFLOW) {
					return false;	// read data is ready but no room in appRecvBuffer
				}
				
				return true;
			default:	// unreachable, just for compiler
				return false;
		}
	}
	
	/**
	 * Process the engine result
	 * @throws IOException
	 */
	private void processEngineResult() throws IOException {
		while (processStatus() && processHandshakeStatus())
			continue;
	}
	
	/**
	 * Processes the engine result status and engine result handshake status
	 * @return
	 * @throws IOException
	 */
	private boolean	processStatus() throws IOException {
		int	count;
		
		// processs I/O
		switch (engine_result.getStatus()) {
			case OK:		// OK: packet was sent or received
				return true;
			case CLOSED:	// Orderly SSL termination from either end
				return engine_result.getHandshakeStatus() != SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
			case BUFFER_OVERFLOW:
				// output needed
				switch (engine_result.getHandshakeStatus()) {
					case NEED_WRAP:
						// If we are wrapping we are doing output to the channel,
						// and we can continue if we managed to write it all.
						flush();
						return net_send_buffer.position() == 0;
					case NEED_UNWRAP:
						// If we are unwrapping we are doing input from the channel
						// but the overflow means there is no room in the appRecvBuffer,
						// so the application has to empty it.
						// fall through
						return false;
					default:
						return false;
				}
			case BUFFER_UNDERFLOW:
				// input needed, existing data too short to unwrap
	
				// Underflow can only mean there is no data in the net_recv_buffer,
				// so try a read. We can continue if we managed to read something,
				// otherwise the application has to wait (select on OP_READ).
				// First flush any pending output.
				flush();
				
				// now read
				count = socket_channel.read(net_recv_buffer);
				
				// If we didn't read anything we want to exit processEngineStatus()
				return count > 0;
			default:	// unreachable, just for compiler
				return false;
		}
	}

	/**
	 * create task to be ran
	 */
	protected void runDelegatedTasks() {
		Thread delegatedTaskThread = new Thread ("SSLEngine.TaskThread-"+(threadNumber++)) {
			public void run() {
//				OutputHandler.println("Need task, running thread #"+threadNumber);
				// run delegated tasks
				Runnable task;
				while ((task = ssl_engine.getDelegatedTask()) != null) {
					task.run();
				}
				
			}
		};
		
		delegatedTaskThread.run();
	}
}
