package org.nd.primeng.filter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.nd.primeng.search.SearchBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class TurkraftConverter extends HttpServletRequestWrapper {

	private SearchBuilder searchBuilder = new SearchBuilder();
	private Map<String, String[]> paramsMap;

	public TurkraftConverter(HttpServletRequest request, Map<String, String[]> originalParams) throws IOException {
		super(request);
		paramsMap = originalParams;
		getBody(request);
	}

	private void getBody(HttpServletRequest request) throws IOException {
		StringBuilder stringBuilder = new StringBuilder();
		BufferedReader bufferedReader = null;
		try (InputStream inputStream = request.getInputStream()) {
			bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			char[] charBuffer = new char[128];
			int bytesRead = -1;
			while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
				stringBuilder.append(charBuffer, 0, bytesRead);
			}
		}
		String input = stringBuilder.toString();
		// test if body is primeng
		if (input != null && input.contains("first") && input.contains("rows") && input.contains("filters")) {
			convertPrimengJson(input);
		}
	}

	private String convertPrimengJson(String body) throws IOException {

		Map<String, String[]> queryParams = searchBuilder.process(body);
		paramsMap.putAll(queryParams);
		return body;
	}

	@Override
	public String getParameter(String name) {
		String[] values = paramsMap.get(name);
		if (values == null || values.length == 0) {
			return null;
		}
		return values[0];
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		return Collections.unmodifiableMap(paramsMap);
	}

	@Override
	public Enumeration<String> getParameterNames() {
		return Collections.enumeration(paramsMap.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		return paramsMap.get(name);
	}

}