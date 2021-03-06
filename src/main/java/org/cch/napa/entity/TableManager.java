package org.cch.napa.entity;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.cch.napa.ConnectionProvider;
import org.cch.napa.JdbcDao;
import org.cch.napa.exceptions.AnnotationException;
import org.cch.napa.exceptions.PersistenceException;
import org.cch.napa.entity.annotations.DBField;
import org.cch.napa.entity.annotations.atk.EntityField;
import org.cch.napa.entity.annotations.atk.EntityHandler;
import org.cch.napa.entity.annotations.atk.EntityIndex;
import org.cch.napa.exceptions.SQLException;

/**
 * Class in charge of creating, deleting modifying tables
 * @author Christophe Champagne
 * 
 */
public class TableManager {
	private JdbcDao dao;
	private SQLTableCreationScriptsGenerator generator;
	private ConnectionProvider connectionProvider;
	private EntityDaoFactory factory;
	private static final String JDBC_TABLE_TYPE_TABLE = "TABLE";// JDBC table
																// type of a
																// table

	public TableManager(EntityDaoFactory factory) {
		this(factory.getDefaultConnectionProvider(), factory);
	}

	public TableManager(ConnectionProvider connectionProvider, EntityDaoFactory factory) {
		this.connectionProvider = connectionProvider;
		this.dao = factory.getJdbcDao(connectionProvider);
		this.generator = new SQLTableCreationScriptsGenerator(factory);
		this.factory = factory;
	}

	public <E> void dropTable(Class<E> entityClass) throws PersistenceException {
		String drop = generator.generateDropTable(entityClass);
		executeQuery(drop);
	}

	public <E> void checkReadTable(Class<E> entityClass) throws AnnotationException, SQLException {
		String query = generator.generateCanRead(entityClass);
		executeCanRead(query);
	}

	public void checkReadTable(String table) throws AnnotationException, SQLException {
		String query = generator.generateCanRead(table);
		executeCanRead(query);
	}

	public <E> boolean tableExists(Class<E> entityClass) throws AnnotationException, SQLException {
		String table = factory.getEntityHandler(entityClass).getTableName().toUpperCase();
		return getTableNames().contains(table);
	}

	/**
	 * 
	 * @param table
	 *            the name of the table
	 * @return true if the table exists
	 * @throws SQLException A wrapper of an exception thrown by JDBC layer
	 */
	public boolean tableExists(String table) throws SQLException {
		return getTableNames().contains(table.toUpperCase());
	}

	/**
	 * Returns a set of table names in upper case.
	 * 
	 * @return a set of table names in upper case.
	 * @throws SQLException A wrapper of an exception thrown by JDBC layer
	 */
	public Set<String> getTableNames() throws SQLException {
		Set<String> tables = new TreeSet<String>();
		try {
			Connection connection = connectionProvider.getConnection();
			DatabaseMetaData metaData = connection.getMetaData();
			ResultSet rs = metaData.getTables(null, null, "%", null);
			while (rs.next()) {
				String type = rs.getString("TABLE_TYPE");
				if (JDBC_TABLE_TYPE_TABLE.equalsIgnoreCase(type)) {
					String name = rs.getString("TABLE_NAME");
					tables.add(name.toUpperCase());
				}
			}
		} catch (java.sql.SQLException e) {
			// The table does not exist
			throw new org.cch.napa.exceptions.SQLException("Cannot get table names", e);
		}
		return tables;
	}

	private void executeCanRead(String query) throws SQLException {
		PreparedStatement statement = null;
		try {
			Connection connection = connectionProvider.getConnection();
			statement = connection.prepareStatement(query);
			statement.executeQuery();
		} catch (java.sql.SQLException e) {
			// The table does not exist
			throw new SQLException("The query " + query
					+ " doesn't work meaning that the table cannot be read", e);
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (java.sql.SQLException e) {
					new SQLException("Cannot close statement in 'executeCanRead'", e);
				}
			}
		}
	}

	public <E> void createTable(Class<E> entityClass) throws PersistenceException {
		String create = generator.generateCreateTable(entityClass);
		executeQuery(create);
		EntityHandler<E> entityHandler = factory.getEntityHandler(entityClass);
		for(EntityIndex index : entityHandler.getIndexes()){
			create = generator.generateIndex(index);
			executeQuery(create);
		}
	}

	public <E> boolean createTableIfNotExisting(Class<E> entityClass) throws PersistenceException {
		if (!tableExists(entityClass)) {
			createTable(entityClass);
			return true;
		}
		return false;
	}

	public <E> void addField(Class<E> entityClass, String field) throws PersistenceException {
		String addField = generator.generateAddField(entityClass, field);
		executeQuery(addField);
	} 

	public <E> void addMissingFields(Class<E> entityClass) throws PersistenceException {
		EntityHandler<E> handler = factory.getEntityHandler(entityClass);
		Map<String, FieldMetaData>fieldsInDB = getTableMetaDataFromDB(handler.getTableName());
		for(EntityField field : handler.getEntityFields()){
			if(!fieldsInDB.containsKey(field.getDBFieldName().toUpperCase())){
				addField(entityClass, field.getDBFieldName());
			}
		}
	} 

	private void executeQuery(String query) throws PersistenceException {
		PreparedStatement statement = null;
		try {
			statement = dao.prepareStatement(query);
			statement.execute();
			if (!statement.getConnection().getAutoCommit()) {
				statement.getConnection().commit();
			}
		} catch (java.sql.SQLException e) {
			// The table does not exist
			throw new SQLException(e);
		} finally {
			if (statement != null) {
				try {
					if (!statement.getConnection().getAutoCommit()) {
						try {
							statement.getConnection().rollback();
						} catch (Exception ex) {
							throw new SQLException("Could not rollback drop table", ex);
						}
					}
					statement.close();
				} catch (Exception e) {
					throw new SQLException("Could not close statement", e);
				}
			}
		}
	}
	//TODO get complete metadata in the future
	public Map<String, FieldMetaData> getTableMetaDataFromDB(String table) throws SQLException {
		PreparedStatement statement = null;
		String query = "select * from " + table + " where 1=0";
		ResultSetMetaData rsMetaData = null;
		Map<String, FieldMetaData> metaData = new TreeMap<String, FieldMetaData>();
		try {
			Connection connection = connectionProvider.getConnection();
			statement = connection.prepareStatement(query);
			ResultSet resultSet = statement.executeQuery();
			rsMetaData = resultSet.getMetaData();
			for(int i =1; i<=rsMetaData.getColumnCount(); i++){
				DBFieldMetaData field = new DBFieldMetaData();
				field.setFieldName(rsMetaData.getColumnName(i));
				field.setNullable(rsMetaData.isNullable(i)!=ResultSetMetaData.columnNoNulls);
				//field.setDefaultValue("TODO");
				field.setPrecision(rsMetaData.getPrecision(i));
				field.setReadOnly(rsMetaData.isReadOnly(i));
				field.setSize(rsMetaData.getScale(i));//TODO:seems to be incorrect
				field.setSqlType(rsMetaData.getColumnType(i));
				metaData.put(field.getFieldName().toUpperCase(), field);
			}
		} catch (java.sql.SQLException e) {
			// The table does not exist
			throw new SQLException("The query " + query	+ " doesn't work meaning that the table cannot be read", e);
		} finally {
			if (statement != null) {
				try {
					statement.close();
				} catch (java.sql.SQLException e) {
					throw new SQLException("Cannot close statement in 'executeCanRead'", e);
				}
			}
		}
		return metaData;
	}
	/**
	 * 
	 * @param table The name of the table to check
	 * @param field The name of the field to check
	 * @return true if the field exists on the table
	 * @throws AnnotationException 
	 */
	public boolean fieldExistsInTable(String table, String field) throws SQLException{
		//TODO optimize this
		Map<String, FieldMetaData> metaDatas = getTableMetaDataFromDB(table);
		return metaDatas.containsKey(field.toUpperCase());
	}
	private class DBFieldMetaData implements FieldMetaData {
		//Attributes
		private String fieldName;
		private boolean nullable = true;
		private boolean readOnly;
		private int sqlType = Types.NULL;
		private int size = DBField.DEFAULT;
		private int precision = DBField.DEFAULT;
		private String defaultValue;
		/**
		 * @return the fieldName
		 */
		public String getFieldName() {
			return fieldName;
		}
		/**
		 * @param fieldName the fieldName to set
		 */
		public void setFieldName(String fieldName) {
			this.fieldName = fieldName;
		}
		/**
		 * @return the nullable
		 */
		public boolean isNullable() {
			return nullable;
		}
		/**
		 * @param nullable the nullable to set
		 */
		public void setNullable(boolean nullable) {
			this.nullable = nullable;
		}
		/**
		 * @return the readOnly
		 */
		public boolean isReadOnly() {
			return readOnly;
		}
		/**
		 * @param readOnly the readOnly to set
		 */
		public void setReadOnly(boolean readOnly) {
			this.readOnly = readOnly;
		}
		/**
		 * @return the sqlType
		 */
		public int getSqlType() {
			return sqlType;
		}
		/**
		 * @param sqlType the sqlType to set
		 */
		public void setSqlType(int sqlType) {
			this.sqlType = sqlType;
		}
		/**
		 * @return the size
		 */
		public int getSize() {
			return size;
		}
		/**
		 * @param size the size to set
		 */
		public void setSize(int size) {
			this.size = size;
		}
		/**
		 * @return the precision
		 */
		public int getPrecision() {
			return precision;
		}
		/**
		 * @param precision the precision to set
		 */
		public void setPrecision(int precision) {
			this.precision = precision;
		}
		/**
		 * @return the defaultValue
		 */
		public String getDefaultValue() {
			return defaultValue;
		}

		/**
		 * @param defaultValue the defaultValue to set
		 */
		public void setDefaultValue(String defaultValue) {
			this.defaultValue = defaultValue;
		}

	}
}
