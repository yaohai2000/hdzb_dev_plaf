package com.bhz.invoker.http;

public enum ContentType {
	FORM(0),
	JSON(1),
	XML(2);
	public final int value;
	private ContentType(int value){
		this.value = value;
	}
}
