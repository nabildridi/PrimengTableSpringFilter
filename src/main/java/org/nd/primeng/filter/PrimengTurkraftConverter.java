package org.nd.primeng.filter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.nd.primeng.search.SearchBuilder;
import org.springframework.util.StreamUtils;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

public class PrimengTurkraftConverter extends HttpServletRequestWrapper {

	private SearchBuilder searchBuilder = new SearchBuilder();
	private Map<String, String[]> paramsMap;

	private byte[] cachedBody;

	public PrimengTurkraftConverter(HttpServletRequest request) throws IOException {
		super(request);
		paramsMap = new HashMap<>(request.getParameterMap());
		getBody(request);
	}

	private void getBody(HttpServletRequest request) throws IOException {

		String input = null;

		try {
			InputStream requestInputStream = request.getInputStream();
			this.cachedBody = StreamUtils.copyToByteArray(requestInputStream);
			input = new String(cachedBody, StandardCharsets.UTF_8);
		} catch (IOException e) {
		}

		// test if body is primeng
		if (input != null && input.contains("first") && input.contains("rows") && input.contains("filters")) {
			convertPrimengJson(input);
		}
	}

	private void convertPrimengJson(String body) throws IOException {

		Map<String, String[]> queryParams = searchBuilder.process(body);
		paramsMap.putAll(queryParams);

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

	@Override
	public ServletInputStream getInputStream() {
		return new CachedBodyServletInputStream(cachedBody);
	}

	@Override
	public BufferedReader getReader() {
		ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cachedBody);
		return new BufferedReader(new InputStreamReader(byteArrayInputStream, StandardCharsets.UTF_8));
	}

	private static class CachedBodyServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream byteArrayInputStream;

		public CachedBodyServletInputStream(byte[] cachedBody) {
			this.byteArrayInputStream = new ByteArrayInputStream(cachedBody);
		}

		@Override
		public boolean isFinished() {
			return byteArrayInputStream.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener listener) {
			// no-op
		}

		@Override
		public int read() throws IOException {
			return byteArrayInputStream.read();
		}
	}

}