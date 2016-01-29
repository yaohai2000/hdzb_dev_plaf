package com.bhz.web.servlet;

import java.util.Map;

public class BizHubRequest {
	private String serviceId;
	private String method;
	private String mediaType;
	private String attachContent;
	private Map<String,Object> params;
	public String getServiceId() {
		return serviceId;
	}
	public void setServiceId(String serviceId) {
		this.serviceId = serviceId;
	}
	public Map<String, Object> getParams() {
		return params;
	}
	public void setParams(Map<String, Object> params) {
		this.params = params;
	}
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public String getMediaType() {
		return mediaType;
	}
	public void setMediaType(String mediaType) {
		this.mediaType = mediaType;
	}
	public String getAttachContent() {
		return attachContent;
	}
	public void setAttachContent(String attachContent) {
		this.attachContent = attachContent;
	}
}
