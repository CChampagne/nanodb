package org.cch.napa.entity;

import org.cch.napa.ConnectionProvider;
import org.cch.napa.JdbcDao;
import org.cch.napa.exceptions.AnnotationException;
import org.cch.napa.exceptions.PersistenceException;
import org.cch.napa.entity.annotations.atk.EntityHandler;
import org.cch.napa.mapper.impl.EntityRecordMapper;

/**
 * @author Christophe Champagne
 *
 */
public interface EntityDaoFactory {
	JdbcDao getJdbcDao();
	JdbcDao getJdbcDao(ConnectionProvider connectionProvider);
	<E> EntityHandler<E> getEntityHandler(Class<E> entityClass) throws AnnotationException;
	<E> EntityDao<E> getEntityDao(Class<E> entityClass) throws PersistenceException;
	<E> EntityDao<E> getEntityDao(Class<E> entityClass, ConnectionProvider connectionProvider) throws PersistenceException;
	SQLTypeMapper getSqlTypeMapper();
	<E> SQLGenerator<E> getSQLGenerator(Class<E> entityClass) throws AnnotationException;
	<E> EntityRecordMapper<E> getEntityRecordMapper(Class<E> entityClass) throws AnnotationException;
	ConnectionProvider getDefaultConnectionProvider();
}
