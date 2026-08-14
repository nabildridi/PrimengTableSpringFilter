package org.nd.primeng.search;

import java.text.MessageFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.springframework.util.StringUtils;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;

public class SearchBuilder {

	private ObjectMapper mapper = new ObjectMapper();

	public Map<String, String[]> process(String primengRequestJson) {

		Map<String, String[]> queryParams = new HashMap<String, String[]>();

		// to json object
		JsonNode primengRequestNode = toJsonNode(primengRequestJson);

		ParsingResult parsingResult = parse(primengRequestNode);

		// build pagination data
		Map<String, String[]> pageParams = buildPageable(parsingResult);
		queryParams.putAll(pageParams);

		// build sort
		Map<String, String[]> sortParams = buildSortQuery(primengRequestNode);
		queryParams.putAll(sortParams);

		// build global
		if (parsingResult.isGeneralFiltering() && parsingResult.getGlobalFields() != null) {
			Map<String, String[]> globalParams = new HashMap<String, String[]>();
			globalParams = this.buildGlobalFilterQuery(parsingResult);
			queryParams.putAll(globalParams);
			// build filters
		} else if (parsingResult.isColumnsFiltering()) {
			Map<String, String[]> filterParams = new HashMap<String, String[]>();
			filterParams = this.buildFiltersQuery(parsingResult);
			queryParams.putAll(filterParams);
		}

		return queryParams;
	}

	private JsonNode toJsonNode(String primengRequestJson) {
		try {

			JsonNode primengRequestNode = mapper.readTree(primengRequestJson);
			return primengRequestNode;

		} catch (Exception e) {
			return null;
		}
	}

	private ParsingResult parse(JsonNode primengRequestNode) {

		ParsingResult parsingResult = new ParsingResult();

		try {

			// start index
			int first = primengRequestNode.get("first").asInt();
			parsingResult.setStartIndex(first);

			// rows per page langth
			int rows = primengRequestNode.get("rows").asInt();
			parsingResult.setPageLength(rows);

			// global filter fields
			List<String> globalFields = new ArrayList<>();
			JsonNode globalFieldsNode = primengRequestNode.get("globalFilter");
			if (globalFieldsNode != null) {
				if (globalFieldsNode.isArray()) {
					Iterator<JsonNode> fieldsIter = globalFieldsNode.iterator();
					while (fieldsIter.hasNext()) {
						globalFields.add(fieldsIter.next().asText());
					}

				}

				if (globalFieldsNode.isObject()) {

					if (isValueNotNullAndNotEmpty(globalFieldsNode)) {
						globalFields.add(globalFieldsNode.asText());
					}

				}
			}
			parsingResult.setGlobalFields(globalFields);

			// columns filters
			JsonNode filters = primengRequestNode.get("filters");
			Iterator<Map.Entry<String, JsonNode>> iter = filters.properties().iterator();

			while (iter.hasNext()) {
				Map.Entry<String, JsonNode> entry = iter.next();
				String columnName = entry.getKey();
				JsonNode filterNode = entry.getValue();

				List<ColumnFilter> filtersOfField = new ArrayList<ColumnFilter>();

				if (!columnName.equals("global")) {

					if (filterNode.isArray()) {

						Iterator<JsonNode> rulesIter = filterNode.iterator();
						while (rulesIter.hasNext()) {
							JsonNode ruleEntry = rulesIter.next();

							if (isValueNotNullAndNotEmpty(ruleEntry)) {
								ColumnFilter columnFilter = extractFilterData(ruleEntry, columnName);
								filtersOfField.add(columnFilter);
							}
						}

					}

					if (filterNode.isObject()) {

						if (isValueNotNullAndNotEmpty(filterNode)) {
							ColumnFilter columnFilter = extractFilterData(filterNode, columnName);
							filtersOfField.add(columnFilter);
						}

					}

					parsingResult.getColumnsFilters().put(columnName, filtersOfField);

				} else {

					if (isValueNotNullAndNotEmpty(filterNode)) {
						String globalFilter = filterNode.get("value").asText();
						parsingResult.setGeneralFilter(globalFilter);
					}

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

		return parsingResult;
	}

	// Sort
	private Map<String, String[]> buildSortQuery(JsonNode primengRequestNode) {

		Map<String, String[]> params = new HashMap<String, String[]>();

		JsonNode multiSortMetaArray = primengRequestNode.get("multiSortMeta");
		if (multiSortMetaArray != null) {

			List<String> queries = new ArrayList<String>();

			// this is multisort
			for (JsonNode multiSortItem : multiSortMetaArray) {
				String sortField = multiSortItem.get("field").asText();

				int sortOrderInt = multiSortItem.get("order").asInt();
				String sortOrder = null;
				if (sortOrderInt == 1)
					sortOrder = "";
				if (sortOrderInt == -1)
					sortOrder = "-";

				queries.add(sortOrder + sortField);
			}

			params.put("sort", new String[] { StringUtils.collectionToDelimitedString(queries, ",") });

		} else {
			// Single sort

			String sortField = null;
			JsonNode node = primengRequestNode.get("sortField");
			if (node != null) {
				sortField = node.asText();
			}

			Integer sortOrderInt = null;
			node = primengRequestNode.get("sortOrder");
			if (node != null) {
				sortOrderInt = node.asInt();
			}

			if (sortOrderInt != null && sortField != null) {
				String sortOrder = null;
				if (sortOrderInt.intValue() == 1)
					sortOrder = "";
				if (sortOrderInt.intValue() == -1)
					sortOrder = "-";

				params.put("sort", new String[] { sortOrder + sortField });
			}

		}

		return params;
	}

	// page
	private Map<String, String[]> buildPageable(ParsingResult parsingResult) {

		Map<String, String[]> params = new HashMap<String, String[]>();
		params.put("page", new String[] { "" + parsingResult.getPage() });
		params.put("size", new String[] { "" + parsingResult.getPageLength() });
		return params;

	}

	// filters
	private Map<String, String[]> buildFiltersQuery(ParsingResult parsingResult) {

		Map<String, String[]> params = new HashMap<String, String[]>();
		List<String> queries = new ArrayList<String>();

		for (String fieldName : parsingResult.getColumnsFilters().keySet()) {

			List<ColumnFilter> ColumnFiltersList = parsingResult.getColumnsFilters().get(fieldName);

			if (ColumnFiltersList.size() == 1) {
				ColumnFilter cf = ColumnFiltersList.get(0);
				String query = getColumnQuery(cf, fieldName);
				queries.add(query);
			}

			if (ColumnFiltersList.size() > 1) {
				String localOperator = ColumnFiltersList.get(0).getOperator();
				List<String> groupedQueries = new ArrayList<String>();

				for (ColumnFilter cf : ColumnFiltersList) {

					String query = getColumnQuery(cf, fieldName);
					groupedQueries.add(query);
				}

				String localQuery = "(" + StringUtils.collectionToDelimitedString(groupedQueries, " " + localOperator + " ") + ")";
				queries.add(localQuery);

			}

		}

		if (queries.size() > 0) {
			params.put("filter", new String[] { StringUtils.collectionToDelimitedString(queries, " and ") });
		}

		return params;
	}

	private Map<String, String[]> buildGlobalFilterQuery(ParsingResult parsingResult) {

		Map<String, String[]> params = new HashMap<String, String[]>();

		String valueToSearch = parsingResult.getGeneralFilter();

		valueToSearch = escapeSpecialChars(valueToSearch);

		List<String> queries = new ArrayList<String>();

		String template = "{0}~~''%{1}%''";
		for (String fieldName : parsingResult.getGlobalFields()) {

			String query = MessageFormat.format(template, fieldName, valueToSearch);
			queries.add(query);

		}

		if (queries.size() > 0) {
			params.put("filter", new String[] { StringUtils.collectionToDelimitedString(queries, " or ") });
		}

		return params;

	}

	private String escapeSpecialChars(String valueToSearch) {

		String[] specialChars = { "\\", "'" };
		for (String special : specialChars) {
			valueToSearch = valueToSearch.replace(special, "\\".concat(special));
		}

		return valueToSearch;

	}

	private String getDatesQuery(ColumnFilter cf, String queryTemplate, String fieldName) {

		LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.parse(cf.getValueToSearch()), TimeZone.getDefault().toZoneId());

		LocalDateTime start = dateTime.with(LocalTime.of(0, 0, 0, 0));
		LocalDateTime end = dateTime.with(LocalTime.of(23, 59, 59, 999));

		String query = formatThree(queryTemplate, fieldName, start.toString(), end.toString());

		return query;

	}

	private String getFragmentForMatchMode(String matchMode, ColumnType type) {

		String operator = null;

		if (type == ColumnType.TEXT) {
			if (matchMode.equals("contains") || matchMode.equals("default"))
				operator = "{0}~~''%{1}%''";
			if (matchMode.equals("startsWith"))
				operator = "{0}~~''{1}%''";
			if (matchMode.equals("notContains"))
				operator = "not exists({0}~~''%{1}%'')";
			if (matchMode.equals("endsWith"))
				operator = "{0}~~''%{1}''";
			if (matchMode.equals("equals"))
				operator = "{0}:''{1}''";
			if (matchMode.equals("notEquals"))
				operator = "{0}!''{1}''";
			if (matchMode.equals("in"))
				operator = "{0} in {1}";
		}

		if (type == ColumnType.NUMERIC) {
			if (matchMode.equals("equals") || matchMode.equals("default"))
				operator = "{0}:{1}";
			if (matchMode.equals("notEquals"))
				operator = "{0}!{1}";
			if (matchMode.equals("lt"))
				operator = "{0}<{1}";
			if (matchMode.equals("lte"))
				operator = "{0}<:{1}";
			if (matchMode.equals("gt"))
				operator = "{0}>{1}";
			if (matchMode.equals("gte"))
				operator = "{0}>:{1}";
			if (matchMode.equals("in"))
				operator = "{0} in {1}";
		}

		if (type == ColumnType.BOOLEAN) {
			if (matchMode.equals("equals") || matchMode.equals("default"))
				operator = "{0}:{1}";
		}

		if (type == ColumnType.DATE) {
			if (matchMode.equals("dateIs") || matchMode.equals("default"))
				operator = "{0}>:''{1}'' and {0}<:''{2}''";
			if (matchMode.equals("dateIsNot"))
				operator = "{0}<''{1}'' or {0}>''{2}''";
			;
			if (matchMode.equals("dateBefore"))
				operator = "{0}<''{1}''";
			if (matchMode.equals("dateAfter"))
				operator = "{0}>''{1}''";
		}

		return operator;

	}

	private String formatTwo(String template, String fieldName, String value) {
		var result = MessageFormat.format(template, fieldName, value);
		return result;
	}

	private String formatThree(String template, String fieldName, String value1, String value2) {

		var result = MessageFormat.format(template, fieldName, value1, value2);
		return result;
	}

	private ColumnFilter extractFilterData(JsonNode jsonFilter, String fieldName) {

		ColumnFilter columnFilter = new ColumnFilter();

		JsonNode valueNode = jsonFilter.get("value");

		String valueToSearch = null;
		if (valueNode.isArray()) {
			valueToSearch = valueNode.toString();

			if (valueToSearch.equals("[]"))
				valueToSearch = null;
			valueToSearch = valueToSearch.replace('"', '\'');
			columnFilter.setValueToSearch(valueToSearch);
			columnFilter.setMatchMode("in");

		} else {
			valueToSearch = jsonFilter.get("value").asText();

			valueToSearch = escapeSpecialChars(valueToSearch);
			columnFilter.setValueToSearch(valueToSearch);

			JsonNode matchModeNode = jsonFilter.get("matchMode");
			if (matchModeNode != null) {
				columnFilter.setMatchMode(matchModeNode.asText());
			}

		}

		JsonNode operatorNode = jsonFilter.get("operator");
		if (operatorNode != null) {
			columnFilter.setOperator(operatorNode.asText());
		}

		ColumnType type = findType(valueNode);
		columnFilter.setType(type);

		return columnFilter;

	}

	private ColumnType findType(JsonNode valueNode) {

		if (valueNode.isNull()) {
			return null;
		}

		if (valueNode.isArray()) {
			return ColumnType.TEXT;
		}

		if (valueNode.isBoolean()) {
			return ColumnType.BOOLEAN;
		}

		if (valueNode.isNumber()) {
			return ColumnType.NUMERIC;
		}

		if (valueNode.isTextual()) {
			// test if date else it's a string
			String value = valueNode.asText();
			if (isDate(value)) {
				return ColumnType.DATE;
			} else {
				return ColumnType.TEXT;
			}
		}

		return null;

	}

	private String getColumnQuery(ColumnFilter cf, String fieldName) {

		String query = null;
		String queryTemplate = getFragmentForMatchMode(cf.getMatchMode(), cf.getType());

		if (cf.getType() != ColumnType.DATE) {
			query = formatTwo(queryTemplate, fieldName, cf.getValueToSearch());
		} else {
			query = getDatesQuery(cf, queryTemplate, fieldName);

		}

		return query;
	}

	private boolean isValueNotNullAndNotEmpty(JsonNode nodeToTest) {

		boolean result = true;

		if (nodeToTest.get("value").isArray()) {
			ArrayNode array = (ArrayNode) nodeToTest.get("value");
			if (array.isEmpty())
				result = false;
		} else {
			if (nodeToTest.get("value").isNull())
				result = false;

		}

		return result;
	}

	private boolean isDate(String input) {
		try {
			Instant.parse(input);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
