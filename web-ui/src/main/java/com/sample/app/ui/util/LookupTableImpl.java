package com.sample.app.ui.util;

import com.github.bordertech.wcomponents.util.LookupTable;
import com.github.bordertech.wcomponents.util.SystemException;
import com.sample.app.model.client.CodeOption;
import com.sample.app.model.services.ClientServices;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.Caching;
import javax.cache.configuration.MutableConfiguration;
import javax.cache.expiry.AccessedExpiryPolicy;
import javax.cache.expiry.Duration;

/**
 * Lookup code tables.
 */
public class LookupTableImpl implements LookupTable {

	private static final String WITH_NULL_OPTION = "WITH-NULL";
	private static final String NO_NULL_OPTION = "NO-NULL";
	private static final String CACHE_NAME = "client-lookuptable";
	private static final ClientServices CLIENT_SERVICES = ClientServicesHelperFactory.getInstance();
	private static final String CACHE_DELIM = "#";

	@Override
	public List<?> getTable(final Object table) {
		TableDetails details = (TableDetails) table;
		List options = getCache().get(details.getKey());
		if (options != null) {
			return options;
		}
		options = loadTable(details.getTable());
		if (!options.isEmpty() && details.isWithNull()) {
			options = new ArrayList(options);
			options.add(0, null);
		}
		getCache().put(details.getKey(), options);
		return options;
	}

	private List<?> loadTable(final String table) {
		try {
			List options = CLIENT_SERVICES.retrieveCodes(table);
			return options;
		} catch (Exception e) {
			throw new SystemException("Could not load table [" + table + "]. " + e.getMessage(), e);
		}
	}

	@Override
	public String getCacheKeyForTable(final Object table) {
		TableDetails details = (TableDetails) table;
		StringBuilder builder = new StringBuilder();
		builder.append(details.getTable());
		builder.append(CACHE_DELIM);
		builder.append(details.withNull ? WITH_NULL_OPTION : NO_NULL_OPTION);
		// Add more cache busting here
		return builder.toString();
	}

	@Override
	public Object getTableForCacheKey(final String key) {
		String parts[] = key.split(CACHE_DELIM);
		String table = parts[0];
		boolean nullFlag = Objects.equals(WITH_NULL_OPTION, parts[1]);
		return new TableDetails(table, nullFlag);
	}

	@Override
	public String getCode(final Object table, final Object entry) {
		if (table instanceof TableDetails && ((TableDetails) table).isWithNull() && entry == null) {
			return ((TableDetails) table).getNullCode();
		}
		if (entry instanceof CodeOption) {
			return ((CodeOption) entry).getCode();
		}
		return null;
	}

	@Override
	public String getDescription(final Object table, final Object entry) {
		if (table == null) {
			return null;
		}
		if (entry == null && table instanceof TableDetails && ((TableDetails) table).isWithNull()) {
			return ((TableDetails) table).getNullDescription();
		}
		if (entry instanceof CodeOption) {
			return ((CodeOption) entry).getDescription();
		}
		return "";
	}

	/**
	 * @return the cache instance
	 */
	private synchronized Cache<String, List> getCache() {
		Cache<String, List> cache = Caching.getCache(CACHE_NAME, String.class, List.class);
		if (cache == null) {
			final CacheManager mgr = Caching.getCachingProvider().getCacheManager();
			MutableConfiguration<String, List> config = new MutableConfiguration<>();
			config.setTypes(String.class, List.class);
			config.setExpiryPolicyFactory(AccessedExpiryPolicy.factoryOf(new Duration(TimeUnit.HOURS, 12)));
			// Velocity template classes are not serializable so use by ref.
			config.setStoreByValue(false);
			cache = mgr.createCache(CACHE_NAME, config);
		}
		return cache;
	}

	/**
	 * Table option details.
	 */
	public static class TableDetails implements Serializable {

		private final boolean withNull;

		private final String table;

		private static final String NULL_CODE = "";

		private static final String NULL_DESCRIPTION = "";

		/**
		 * @param table the table name
		 */
		public TableDetails(final String table) {
			this(table, false);
		}

		/**
		 *
		 * @param table the table name
		 * @param withNull true if include null option
		 */
		public TableDetails(final String table, final boolean withNull) {
			this.table = table;
			this.withNull = withNull;
		}

		/**
		 * @return true if include null
		 */
		public boolean isWithNull() {
			return withNull;
		}

		/**
		 * @return the table name
		 */
		public String getTable() {
			return table;
		}

		/**
		 * @return the table key
		 */
		public String getKey() {
			String flag = isWithNull() ? WITH_NULL_OPTION : NO_NULL_OPTION;
			return table + flag;
		}

		/**
		 * @return the null code option
		 */
		public String getNullCode() {
			return isWithNull() ? NULL_CODE : null;
		}

		/**
		 * @return the null code description
		 */
		public String getNullDescription() {
			return isWithNull() ? NULL_DESCRIPTION : null;
		}
	}

}
