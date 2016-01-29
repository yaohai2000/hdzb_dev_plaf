package com.bhz.web.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
//import java.util.HashMap;
import java.util.List;
//import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
//import java.util.stream.Collectors;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.x.discovery.ServiceDiscovery;
import org.apache.curator.x.discovery.ServiceDiscoveryBuilder;
import org.apache.curator.x.discovery.ServiceInstance;
import org.apache.curator.x.discovery.ServiceProvider;
import org.apache.curator.x.discovery.strategies.RandomStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;

@WebServlet(urlPatterns = { "/bizhub" }, asyncSupported = true)
public class BizHubServlet extends HttpServlet {
	private static final long serialVersionUID = 4201677323398855156L;
	private CuratorFramework client;
	private static Logger logger = LogManager.getLogger(BizHubServlet.class);

	@Override
	public void init() throws ServletException {
		logger.entry();
		super.init();
		String connStr = this.getServletContext().getInitParameter("service-registrar-url");
		if (connStr == null || connStr.equals("")) {
			logger.error("No Registration Center URL configuration.");
			throw new ServletException("No Registration Center URL configuration.");
		}
		logger.info("Initialize Service Registrar Parameter.");
		this.client = CuratorFrameworkFactory.builder().connectString(connStr.trim()).sessionTimeoutMs(5000)
				.connectionTimeoutMs(10000).retryPolicy(new ExponentialBackoffRetry(1000, 3)).build();
		client.start();
		logger.info("Service Registrar Parameter set.");
		logger.exit();
	}
	
	@Override
	public void destroy() {
		if(client != null){
			logger.info("Service Registrar Stop...");
			client.close();
			logger.exit();
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		long st = System.currentTimeMillis();
		logger.entry();
		logger.info("Active HTTP GET");
		resp.setContentType("text/html;charset=utf-8");
		resp.setHeader("Access-Control-Allow-Origin", "*");
		final PrintWriter out = resp.getWriter();
		final String serviceId = req.getParameter("serviceid");
		if (serviceId == null || serviceId.equals("")) {
			out.print("<p><font color='red'><b>Invoke Invalid</b></font><p>");
			out.flush();
			logger.error("No Service [" + serviceId + "] found!");
			logger.exit();
			return;
		}
		
		logger.info("Invoke [" + serviceId + "] service.");
		List<String> urlList;
		try {
			urlList = client.getChildren().forPath("/services/" + serviceId);
			if (urlList == null || urlList.isEmpty()) {
				out.print("<p><b><font color='red'>!!! There is no service name [" + serviceId
						+ "] registered on this BizHub !!!</font></b></p>");
				out.flush();
				logger.error("There is no service name [" + serviceId + "] registered on this BizHub");
				logger.exit();
				return;
			}
		} catch (Exception e1) {
			out.println(e1.getMessage());
			out.flush();
			logger.error(e1.getMessage());
			logger.exit();
			return;
		}
		ServiceDiscovery<Void> serviceDiscovery = ServiceDiscoveryBuilder.builder(Void.class).basePath("services").client(this.client).build();
		try {
			serviceDiscovery.start();
			ServiceProvider<Void> sp = serviceDiscovery.serviceProviderBuilder().providerStrategy(new RandomStrategy<Void>()).serviceName(serviceId).build();
			sp.start();

			ServiceInstance<Void> ins = sp.getInstance();
			if (ins == null) {
				out.println("<p><font color='red'><b>No Service found!</b></font><p>");
				out.flush();
				logger.error("No Service [" + serviceId + "] found!");
				logger.exit();
				CloseableUtils.closeQuietly(sp);
				CloseableUtils.closeQuietly(serviceDiscovery);
				return;
			}
			final String addr = ins.buildUriSpec();
			AsyncContext ac = req.startAsync();
			ExecutorService es = Executors.newFixedThreadPool(1);
			es.execute(new Runnable() {

				@Override
				public void run() {
					CloseableHttpClient httpclient = HttpClients.createDefault();
					// System.out.println(addr);
					HttpGet httpGet = new HttpGet(addr);
					logger.info("The service provided by " + addr);
					ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

						@Override
						public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
							int status = response.getStatusLine().getStatusCode();
							if (status >= 200 && status < 300) {
								HttpEntity entity = response.getEntity();
								return entity != null ? EntityUtils.toString(entity) : null;
							} else {
								logger.error("Unexpected response status: " + status);
								logger.exit();
								CloseableUtils.closeQuietly(sp);
								CloseableUtils.closeQuietly(serviceDiscovery);
								throw new ClientProtocolException("Unexpected response status: " + status);
							}
						}

					};
					try {
						String responseBody = httpclient.execute(httpGet, responseHandler);
						// System.out.println(responseBody);
						out.println(responseBody);
						logger.info("Invoke service [" + serviceId + "] on {" + addr + "} complete. Consume: "
								+ (System.currentTimeMillis() - st) + "ms.");

					} catch (ClientProtocolException e) {
						logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
					} catch (IOException e) {
						logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
					}finally{
						try {
							httpclient.close();
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					out.flush();
					CloseableUtils.closeQuietly(sp);
					CloseableUtils.closeQuietly(serviceDiscovery);
					ac.complete();
					es.shutdown();
					logger.exit();
				}
			});

		} catch (Exception e) {
			logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
			logger.exit();
		}
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		long st = System.currentTimeMillis();
		logger.entry();
		logger.info("Active HTTP POST");
		resp.setContentType("text/html;charset=utf-8");
		resp.setHeader("Access-Control-Allow-Origin", "*");
		final PrintWriter out = resp.getWriter();
		req.setCharacterEncoding("utf-8");
		// String httpBodyJson =
		// req.getReader().lines().collect(Collectors.joining(System.lineSeparator()));
		/*
		 * Http Request body format:
		 * {"sid":"xxx","method":{"GET"|"POST"},"mediatype":{"json"|
		 * "xml"},"attachcontent":"real request content"}
		 */
		final BizHubRequest bhr = new BizHubRequest();
		// ObjectMapper mapper = new ObjectMapper();
		// JsonNode root = mapper.readTree(httpBodyJson);
		// String serviceId = root.path("sid").asText().trim();
//		String serviceId = req.getParameter("sid");
		String serviceId = req.getHeader("sid");
		if (serviceId == null || serviceId.trim().equals("")) {
			logger.error("No Service assigned");
			logger.exit();
			throw new ServletException("No Service assigned");
		}
		bhr.setServiceId(serviceId.trim());
		// String invokeMethod =
		// root.path("method").asText().toUpperCase().trim();
//		String invokeMethod = req.getParameter("method");
		String invokeMethod = req.getHeader("method");
		if (invokeMethod == null || invokeMethod.trim().equals("")) {
			invokeMethod = "GET";
		}
		// String mediaType =
		// root.path("mediatype").asText().toLowerCase().trim();
//		String mediaType = req.getParameter("mediatype");
		String mediaType = req.getHeader("mediatype");
		if (mediaType == null || mediaType.trim().equals("")) {
			mediaType = "application/x-www-form-urlencoded";
		}else if(mediaType.toLowerCase().equals("json")){
			mediaType = "application/json";
		}else if(mediaType.toLowerCase().equals("xml")){
			mediaType = "application/xml";
		}
		// String attcheContent = root.path("attachcontent").asText().trim();
		String attcheContent = req.getParameter("attachcontent");
		if (attcheContent != null && !attcheContent.equals("")) {
			bhr.setAttachContent(attcheContent.trim());
		} else {
			bhr.setAttachContent(null);
		}
		bhr.setMethod(invokeMethod.trim());
		bhr.setMediaType(mediaType.trim());

		// Map<String,Object> paramMap = new HashMap<String,Object>();
		// JsonNode paramNode = root.path("params");
		// if(paramNode.isMissingNode()){
		//
		// }else{
		// String paramJson = paramNode.toString();
		// paramMap = mapper.readValue(paramJson, new
		// TypeReference<Map<String,Object>>(){});
		// bhr.setParams(paramMap);
		// }

		List<String> urlList;
		try {
			urlList = client.getChildren().forPath("/services/" + bhr.getServiceId());
			if (urlList == null || urlList.isEmpty()) {
				out.print("<p><b><font color='red'>!!! There is no service name [" + bhr.getServiceId()
						+ "] registered on this BizHub !!!</font></b></p>");
				out.flush();
				logger.error("There is no service name [" + bhr.getServiceId() + "] registered on this BizHub");
				logger.exit();
				return;
			}
		} catch (Exception e1) {
			out.println(e1.getMessage());
			out.flush();
			logger.error(e1.getMessage());
			logger.exit();
			return;
		}
		final ServiceDiscovery<Void> serviceDiscovery = ServiceDiscoveryBuilder.builder(Void.class).basePath("services").client(this.client).build();
		try {
			serviceDiscovery.start();
			final ServiceProvider<Void> sp = serviceDiscovery.serviceProviderBuilder().providerStrategy(new RandomStrategy<Void>()).serviceName(serviceId).build();
			sp.start();

			ServiceInstance<Void> ins = sp.getInstance();
			if (ins == null) {
				out.println("<p><font color='red'><b>No Service found!</b></font><p>");
				out.flush();
				logger.error("No Service [" + bhr.getServiceId() + "] found.");
				logger.exit();
				CloseableUtils.closeQuietly(sp);
				CloseableUtils.closeQuietly(serviceDiscovery);
				return;
			}
			final String addr = ins.buildUriSpec();
			AsyncContext ac = req.startAsync();
			ExecutorService es = Executors.newFixedThreadPool(1);
			es.execute(new Runnable() {

				@Override
				public void run() {
					CloseableHttpClient httpclient = HttpClients.createDefault();
					if (bhr.getMethod().equals("GET")) {
						logger.info("Invoke service [" + bhr.getServiceId() + "] on {" + addr + "}");
						HttpGet httpGet = new HttpGet(addr);
						ResponseHandler<String> responseHandler = new ResponseHandler<String>() {

							@Override
							public String handleResponse(HttpResponse response)
									throws ClientProtocolException, IOException {
								int status = response.getStatusLine().getStatusCode();
								if (status >= 200 && status < 300) {
									HttpEntity entity = response.getEntity();
									return entity != null ? EntityUtils.toString(entity, "utf-8") : null;
								} else {
									logger.error("Unexpected response status: " + status);
									logger.exit();
									throw new ClientProtocolException("Unexpected response status: " + status);
								}
							}

						};
						try {
							String responseBody = httpclient.execute(httpGet, responseHandler);
							out.println(responseBody);
							out.flush();
							CloseableUtils.closeQuietly(sp);
							CloseableUtils.closeQuietly(serviceDiscovery);
							logger.info("Invoke service [" + bhr.getServiceId() + "] on {" + addr + "} complete. Consume: " + (System.currentTimeMillis() - st) + "ms");
							logger.exit();
							ac.complete();
							es.shutdown();
						} catch (ClientProtocolException e) {
							logger.error(e.getMessage());
							logger.exit();
						} catch (IOException e) {
							logger.error(e.getMessage());
							logger.exit();
						} finally{
							try {
								httpclient.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					} else if (bhr.getMethod().equals("POST")) {
						logger.info("Invoke service [" + bhr.getServiceId() + "] on {" + addr + "}");
						HttpPost httpPost = new HttpPost(addr);
						String httpBody = null;
						if (bhr.getAttachContent() != null) {
							httpBody = bhr.getAttachContent();
						}
						if(bhr.getMediaType().equals("application/x-www-form-urlencoded")){
							List<NameValuePair> paramList = new ArrayList<NameValuePair>();
							String[] p = httpBody.split("&");
							for(String s:p){
								if(s.indexOf("=")!=-1){
									String[] c = s.split("=");
									if(c.length==2){
										paramList.add(new BasicNameValuePair(c[0], c[1]));
									}else{
										paramList.add(new BasicNameValuePair(c[0], ""));
									}
								}else{
									paramList.add(new BasicNameValuePair(s, ""));
								}
							}
							try {
								HttpEntity entity = new UrlEncodedFormEntity(paramList, "UTF-8");
								httpPost.setEntity(entity);
								String responseBody = httpclient.execute(httpPost, new ResponseHandler<String>() {

									@Override
									public String handleResponse(HttpResponse response) throws ClientProtocolException, IOException {
										int status = response.getStatusLine().getStatusCode();
										if (status >= 200 && status < 300) {
											HttpEntity entity = response.getEntity();
											return entity != null ? EntityUtils.toString(entity, "utf-8") : null;
										} else {
											logger.error("Unexpected response status: " + status);
											logger.exit();
											throw new ClientProtocolException("Unexpected response status: " + status);
										}
									}
									
								});
								out.println(responseBody);
								out.flush();
								CloseableUtils.closeQuietly(sp);
								CloseableUtils.closeQuietly(serviceDiscovery);
								logger.info("Invoke service [" + bhr.getServiceId() + "] on {" + addr + "} complete. Consume: " + (System.currentTimeMillis() - st) + "ms");
								ac.complete();
								es.shutdown();
							} catch (UnsupportedEncodingException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							} catch (ClientProtocolException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							} catch (IOException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							}finally{
								try {
									httpclient.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
							
						}else{
							try {
								StringEntity entity = new StringEntity(httpBody);
								httpPost.setEntity(entity);
								httpPost.setHeader("Accept", bhr.getMediaType());
								httpPost.setHeader("Content-type", bhr.getMediaType());
								String responseBody = httpclient.execute(httpPost, new ResponseHandler<String>() {
	
									@Override
									public String handleResponse(HttpResponse response)
											throws ClientProtocolException, IOException {
										int status = response.getStatusLine().getStatusCode();
										if (status >= 200 && status < 300) {
											HttpEntity entity = response.getEntity();
											return entity != null ? EntityUtils.toString(entity, "utf-8") : null;
										} else {
											logger.error("Unexpected response status: " + status);
											logger.exit();
											throw new ClientProtocolException("Unexpected response status: " + status);
										}
									}
	
								});
								out.println(responseBody);
								out.flush();
								CloseableUtils.closeQuietly(sp);
								CloseableUtils.closeQuietly(serviceDiscovery);
								logger.info("Invoke service [" + bhr.getServiceId() + "] on {" + addr + "} complete. Consume: " + (System.currentTimeMillis() - st) + "ms");
								ac.complete();
								es.shutdown();
							} catch (UnsupportedEncodingException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							} catch (ClientProtocolException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							} catch (IOException e) {
								logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
							}finally{
								try {
									httpclient.close();
								} catch (IOException e) {
									e.printStackTrace();
								}
							}
						}
						logger.exit();
					}
				}
			});
		} catch (Exception e) {
			logger.error("Invoke Service [" + serviceId + "] ERROR! Cause { " + e.getMessage() + "} ");
			logger.exit();
		}

	}

}
