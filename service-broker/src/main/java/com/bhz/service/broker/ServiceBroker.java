package com.bhz.service.broker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.retry.RetryNTimes;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.UriSpec;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ServiceBroker {
	private static ServiceBroker serviceBroker = null;
	// Service registry node
	private String serviceNode;
	// Service Gateway connection string
	private String gwConStr = "";
	// Service Gateway session timeout (in MilliSeconds)
	private int gwSessionTimoutMs;
	// Service Gateway connection timeout (in MilliSeconds)
	private int gwConnTimeoutMs;
	// Service Gateway retry policy parameter
	// 1. policy name; 2. retry times; 3. base time (in milliseconds)
	private String gwRetryPolicyName;
	private int gwRetryTimes;
	private int gwBaseTimeMs;

	// Service Provider Properties
	// Service publish host & port;
	private String publishHost = "";
	private int publishPort;
	// Services to be published
	private List<Service> serviceList = new ArrayList<Service>();

	// Zookeeper client (Curator)
	static CuratorFramework client = null;

	private ServiceBroker() {
		parseConfig();
		RetryPolicy rp = null;
		if (this.gwRetryPolicyName.equals("exponentialbackoff")) {
			rp = new ExponentialBackoffRetry(this.gwBaseTimeMs, this.gwRetryTimes);
		} else if (this.gwRetryPolicyName.equals("retryN")) {
			rp = new RetryNTimes(this.gwRetryTimes, this.gwBaseTimeMs);
		}
		if(client == null){
			client = CuratorFrameworkFactory.builder().connectString(this.gwConStr).sessionTimeoutMs(this.gwSessionTimoutMs)
					.connectionTimeoutMs(this.gwConnTimeoutMs).retryPolicy(rp).build();
		}
		client.start();
	}

	public static ServiceBroker getInstance() {
		if (serviceBroker == null) {
			serviceBroker = new ServiceBroker();
		}
		return serviceBroker;
	}

	public void close() {
		if(client != null){
			client.close();
		}
		System.out.println("Service Broker down");
	}

	public void exportService(String contextPath) {
		for (Service s : serviceList) {
			try {
				ServiceInstance<Void> serviceInstance = ServiceInstance.<Void> builder()
						.uriSpec(new UriSpec(String.format("{scheme}://{address}:{port}%s%s", contextPath, s.getServiceUrl())))
						.address(this.publishHost).port(this.publishPort).name(s.getRegistryName()).build();

				ServiceDiscoveryBuilder.builder(Void.class).client(client).basePath(this.serviceNode)
						.thisInstance(serviceInstance).build().start();

			} catch (Exception e) {
				Logger.getLogger(ServiceBroker.class).error(e.getMessage());
			}
		}
	}

	private void parseConfig() {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			DocumentBuilder builder = dbf.newDocumentBuilder();
			Document doc = builder.parse(ServiceBroker.class.getClassLoader().getResourceAsStream("conf/service-registrar.xml"));
			NodeList nodeList = doc.getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node root = nodeList.item(i);
				if (root.getNodeName().equals("service-registrar")) {
					NodeList contentNodeList = root.getChildNodes();
					for (int index = 0; index < contentNodeList.getLength(); index++) {
						Node ele = contentNodeList.item(index);
						// Register Center Properties
						if (ele.getNodeName().equals("register-center")) {
							NodeList rcNodeList = ele.getChildNodes();
							for (int a = 0; a < rcNodeList.getLength(); a++) {
								Node content = rcNodeList.item(a);
								if (content.getNodeName().equals("url")) {
									this.gwConStr = content.getTextContent().trim();
								} else if (content.getNodeName().equals("registry-node")) {
									this.serviceNode = content.getTextContent().trim();
								} else if (content.getNodeName().equals("session-timeout-ms")) {
									this.gwSessionTimoutMs = Integer.parseInt(content.getTextContent().trim());
								} else if (content.getNodeName().equals("connection-timeout-ms")) {
									this.gwConnTimeoutMs = Integer.parseInt(content.getTextContent().trim());
								} else if (content.getNodeName().equals("retry-policy")) {
									NamedNodeMap rpAttrs = content.getAttributes();
									for (int idx = 0; idx < rpAttrs.getLength(); idx++) {
										if (rpAttrs.item(idx).getNodeName().equals("policyname")) {
											this.gwRetryPolicyName = rpAttrs.item(idx).getNodeValue().trim();
										} else if (rpAttrs.item(idx).getNodeName().equals("retryTimes")) {
											this.gwRetryTimes = Integer
													.parseInt(rpAttrs.item(idx).getNodeValue().trim());
										} else if (rpAttrs.item(idx).getNodeName().equals("baseTimeMS")) {
											this.gwBaseTimeMs = Integer
													.parseInt(rpAttrs.item(idx).getNodeValue().trim());
										}
									}
								}
							}
						}
						if (ele.getNodeName().equals("services")) {
							NamedNodeMap attrs = ele.getAttributes();
							for (int idx = 0; idx < attrs.getLength(); idx++) {
								if (attrs.item(idx).getNodeName().equals("host")) {
									this.publishHost = attrs.item(idx).getNodeValue().trim();
								} else if (attrs.item(idx).getNodeName().equals("port")) {
									this.publishPort = Integer.parseInt(attrs.item(idx).getNodeValue().trim());
								}
							}
							NodeList serviceNodeList = ele.getChildNodes();
							for (int x = 0; x < serviceNodeList.getLength(); x++) {
								Node sn = serviceNodeList.item(x);
								if (sn.getNodeName().equals("service")) {
									Service s = new Service();
									NamedNodeMap sa = sn.getAttributes();
									for (int y = 0; y < sa.getLength(); y++) {
										if (sa.item(y).getNodeName().equals("registry-name")) {
											s.setRegistryName(sa.item(y).getNodeValue().trim());
										} else if (sa.item(y).getNodeName().equals("url")) {
											s.setServiceUrl(sa.item(y).getNodeValue().trim());
										}
									}
									serviceList.add(s);
								}
							}
						}
					}
				}
			}

		} catch (IOException ex) {
			Logger.getLogger(ServiceBroker.class).error(ex.getMessage());
			;
		} catch (SAXException ex) {
			Logger.getLogger(ServiceBroker.class).error(ex.getMessage());
		} catch (ParserConfigurationException ex) {
			Logger.getLogger(ServiceBroker.class).error(ex.getMessage());
		}
	}

}
