/*
 * Copyright 1999-2008 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.nimbustools.ctxbroker.security;

import org.globus.gsi.CertUtil;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.X509V3CertificateGenerator;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.X509Extensions;
import org.bouncycastle.asn1.x509.X509Name;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nimbustools.ctxbroker.ContextBrokerException;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.NoSuchProviderException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.InvalidKeyException;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Calendar;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.math.BigInteger;

public class CertificateAuthority {

    private static final Log logger =
               LogFactory.getLog(CertificateAuthority.class.getName());

    private static final String replaceToken = "CN=XXXXX";

    private final KeyPairGenerator kpGen;
    private final X509V3CertificateGenerator certGen;
    private final X509Certificate caX509;
    private final PrivateKey caPrivate;
    private final X509Name caX509Name;
    private final String targetString;
    private final CertificateFactory factory;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * CertificateAuthority constructor
     *
     * @param caCert CA's public cert, X509Certificate
     * @param caPrivateKey (unencrypted) private key object
     * @param globusCADN only used for logging
     * @throws NoSuchProviderException problem initializing keypair generator
     * @throws NoSuchAlgorithmException problem initializing keypair generator
     * @throws CertificateException problem initializing certificate factory
     * @throws IOException file/stream problem
     * @throws ContextBrokerException other problem with CA input
     */
    protected CertificateAuthority(X509Certificate caCert,
                                   PrivateKey caPrivateKey,
                                   String globusCADN)
            throws NoSuchProviderException,
                   NoSuchAlgorithmException,
                   CertificateException,
                   IOException,
                   ContextBrokerException {

        if (caCert == null) {
            throw new IllegalArgumentException("caCert is null");
        }

        if (caPrivateKey == null) {
            throw new IllegalArgumentException("caPrivateKey is null");
        }
        
        this.kpGen = KeyPairGenerator.getInstance("RSA", "BC");
        this.kpGen.initialize(1024, new SecureRandom());

        this.certGen = new X509V3CertificateGenerator();

        this.factory = CertificateFactory.getInstance("X.509","BC");

        this.caX509 = caCert;
        this.caPrivate = caPrivateKey;

        this.caX509Name = new X509Principal(
                                caX509.getIssuerX500Principal().getEncoded());

        this.initializeGenerator();

        X500Principal subjectDN = caCert.getSubjectX500Principal();

        String targetBase = subjectDN.getName(X500Principal.RFC2253);

        String[] parts = targetBase.split(",");
        String target = "";
        int cnCount = 0;
        for (int i = 0; i < parts.length; i++) {
            String newpiece;
            if (parts[i].startsWith("CN") || parts[i].startsWith("cn")) {
                newpiece = replaceToken;
                cnCount += 1;
            } else {
                newpiece = parts[i];
            }
            if (i == 0) {
                target = newpiece;
            } else {
                target = newpiece + "," + target;
            }
        }

        if (cnCount == 0) {
            throw new ContextBrokerException("Unsupported: CA has no " +
                                                 "CN (?)");
        }

        if (cnCount != 1) {
            throw new ContextBrokerException("Unsupported: CA has more " +
                                                 "than one CN");
        }

        this.targetString = target;

        final String msg = "Initialized certificate authority with subject " +
                         "DN (RFC2253) = '" + targetBase + "' " +
                         "and Globus style DN = '" + globusCADN + "'. " +
                         "New DNs will look like this (RFC2253): '" +
                         this.targetString + "'";

        logger.info(msg);
    }

    protected KeyPair createNewKeyPair() {
        return kpGen.generateKeyPair();
    }

    protected X509Certificate signNewCertificate(String cnString,
                                                 PublicKey pubkey,
                                                 Calendar expires)
            throws SignatureException,
                   InvalidKeyException,
                   CertificateException,
                   IOException {

        this.setGenerator(this.getTargetDN(cnString),
                          pubkey,
                          expires.getTime());

        X509Certificate x509 =
                this.certGen.generateX509Certificate(this.caPrivate);

        InputStream in = new ByteArrayInputStream(x509.getEncoded());

        X509Certificate x509Cert =
                (X509Certificate) this.factory.generateCertificate(in);

        X500Principal subjectDN = x509Cert.getSubjectX500Principal();

        String DN = subjectDN.getName(X500Principal.RFC2253);
        String globusDN = CertUtil.toGlobusID(DN, false);

        String msg = "Created new certificate with DN (RFC2253) = '" +
                     DN + "' and Globus style DN = '" + globusDN + "'";

        logger.trace(msg);

        return x509Cert;
    }

    private String getTargetDN(String cnString) {
        return targetString.replaceAll(replaceToken, "CN=" + cnString);
    }

    private void initializeGenerator() {
        this.certGen.reset();

        this.certGen.setSerialNumber(this.caX509.getSerialNumber().add(BigInteger.ONE));
        this.certGen.setSignatureAlgorithm(this.caX509.getSigAlgName());
        this.certGen.setIssuerDN(this.caX509Name);

        this.certGen.addExtension(X509Extensions.BasicConstraints,
                                 true,
                                 new BasicConstraints(false));

        this.certGen.addExtension(X509Extensions.KeyUsage,
                                  true,
                                  new KeyUsage(KeyUsage.digitalSignature |
                                               KeyUsage.keyEncipherment));
    }

    private void setGenerator(String targetDN,
                              PublicKey pubkey,
                              Date expires) {
        this.certGen.setNotBefore(
                new Date(System.currentTimeMillis() - 10000));
        this.certGen.setNotAfter(expires);
        this.certGen.setSubjectDN(new X509Principal(targetDN));
        this.certGen.setPublicKey(pubkey);
    }
    
}
