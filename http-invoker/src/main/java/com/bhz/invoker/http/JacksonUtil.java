package com.bhz.invoker.http;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JacksonUtil {

	public static <T> String bean2Json(T obj) throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		String Json = mapper.writeValueAsString(obj);
		return Json;
	}

	public static <T> T json2Bean(String jsonStr, Class<T> objClass) throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = new ObjectMapper();
		return mapper.readValue(jsonStr, objClass);
	}
}
