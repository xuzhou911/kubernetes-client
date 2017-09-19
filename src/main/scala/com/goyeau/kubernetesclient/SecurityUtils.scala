/*
 * Copyright 2017 Joan Goyeau (http://goyeau.com)
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
package com.goyeau.kubernetesclient

import java.io._
import java.security.{KeyStore, SecureRandom, Security}
import java.security.cert.{CertificateFactory, X509Certificate}
import java.util.Base64
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}

import akka.http.scaladsl.ConnectionContext
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.{PEMKeyPair, PEMParser}

object SecurityUtils {

  private val TrustStoreSystemProperty = "javax.net.ssl.trustStore"
  private val TrustStorePasswordSystemProperty = "javax.net.ssl.trustStorePassword"
  private val KeyStoreSystemProperty = "javax.net.ssl.keyStore"
  private val KeyStorePasswordSystemProperty = "javax.net.ssl.keyStorePassword"

  def httpsConnectionContext(config: KubeConfig) = {
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(keyManagers(config), trustManagers(config), new SecureRandom)
    ConnectionContext.https(sslContext)
  }

  private def keyManagers(config: KubeConfig) = {
    // Client certificate
    val certDataStream = config.clientCertData.map(data => new ByteArrayInputStream(Base64.getDecoder.decode(data)))
    val certFileStream = config.clientCertFile.map(new FileInputStream(_))

    // Client key
    val keyDataStream = config.clientKeyData.map(data => new ByteArrayInputStream(Base64.getDecoder.decode(data)))
    val keyFileStream = config.clientKeyFile.map(new FileInputStream(_))

    for {
      keyStream <- keyDataStream.orElse(keyFileStream)
      certStream <- certDataStream.orElse(certFileStream)
    } yield {
      Security.addProvider(new BouncyCastleProvider())
      val pemKeyPair = new PEMParser(new InputStreamReader(keyStream)).readObject().asInstanceOf[PEMKeyPair]
      val privateKey = new JcaPEMKeyConverter().setProvider("BC").getPrivateKey(pemKeyPair.getPrivateKeyInfo)

      val certificateFactory = CertificateFactory.getInstance("X509")
      val certificate = certificateFactory.generateCertificate(certStream).asInstanceOf[X509Certificate]

      defaultKeyStore.setKeyEntry(
        certificate.getSubjectX500Principal.getName,
        privateKey,
        config.clientKeyPass.fold(Array.empty[Char])(_.toCharArray),
        Array(certificate)
      )
    }

    val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
    keyManagerFactory.init(defaultKeyStore, Array.empty)
    keyManagerFactory.getKeyManagers
  }

  private lazy val defaultKeyStore = {
    val propertyKeyStoreFile =
      Option(System.getProperty(KeyStoreSystemProperty, "")).filter(_.nonEmpty).map(new File(_))

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(
      propertyKeyStoreFile.map(new FileInputStream(_)).orNull,
      System.getProperty(KeyStorePasswordSystemProperty, "").toCharArray
    )
    keyStore
  }

  private def trustManagers(config: KubeConfig) = {
    val certDataStream = config.caCertData.map(data => new ByteArrayInputStream(Base64.getDecoder.decode(data)))
    val certFileStream = config.caCertFile.map(new FileInputStream(_))

    certDataStream.orElse(certFileStream).foreach { certStream =>
      val certificateFactory = CertificateFactory.getInstance("X509")
      val certificate = certificateFactory.generateCertificate(certStream).asInstanceOf[X509Certificate]
      defaultTrustStore.setCertificateEntry(certificate.getSubjectX500Principal.getName, certificate)
    }

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(defaultTrustStore)
    trustManagerFactory.getTrustManagers
  }

  private lazy val defaultTrustStore = {
    val securityDirectory = s"${System.getProperty("java.home")}/lib/security"

    val propertyTrustStoreFile =
      Option(System.getProperty(TrustStoreSystemProperty, "")).filter(_.nonEmpty).map(new File(_))
    val jssecacertsFile = Option(new File(s"$securityDirectory/jssecacerts")).filter(f => f.exists && f.isFile)
    val cacertsFile = new File(s"$securityDirectory/cacerts")

    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType)
    keyStore.load(
      new FileInputStream(propertyTrustStoreFile.orElse(jssecacertsFile).getOrElse(cacertsFile)),
      System.getProperty(TrustStorePasswordSystemProperty, "changeit").toCharArray
    )
    keyStore
  }
}
