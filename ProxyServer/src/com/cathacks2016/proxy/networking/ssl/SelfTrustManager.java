package com.cathacks2016.proxy.networking.ssl;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


public class SelfTrustManager implements X509TrustManager {

	private X509TrustManager standardTrustManager;
	
	/*
	 * Creates a new SelftTrustManager instance given a keystore
	 */
	public SelfTrustManager(KeyStore keyStore) throws NoSuchAlgorithmException, KeyStoreException {
		super();

		TrustManagerFactory factory = TrustManagerFactory.getInstance("SunX509");
		factory.init(keyStore);
		TrustManager[] trustmanagers = factory.getTrustManagers();
		if(trustmanagers.length == 0)
			throw new NoSuchAlgorithmException("SunX509 trust manager not supported");
	
		this.standardTrustManager = (X509TrustManager) trustmanagers[0];
	}

	/**
	 * Throws exception if client certificate can't be trusted
	 */
	@Override
	public void checkClientTrusted(X509Certificate[] certs, String authType) throws CertificateException {
		this.standardTrustManager.checkClientTrusted(certs, authType);
	}

	/**
	 * Throws exception if server certificate can't be trusted
	 */
	@Override
	public void checkServerTrusted(X509Certificate[] certs, String authType) throws CertificateException {
//		if (certs != null) 
//		{
//            OutputHandler.println("Server certificate chain:");
//            for (int i = 0; i < certs.length; i++) 
//            	OutputHandler.println("X509Certificate[" + i + "]=" + certs[i]);
//      }
		
        if ((certs != null) && (certs.length == 1))
        	certs[0].checkValidity();
        else 
            this.standardTrustManager.checkServerTrusted(certs,authType);
		
	}

	/**
	 * Returns a list of certificates that are accepted by the trust manager's keystore
	 */
	@Override
	public X509Certificate[] getAcceptedIssuers() {
		return this.standardTrustManager.getAcceptedIssuers();
	}

}
