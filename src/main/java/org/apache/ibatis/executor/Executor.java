/**
 *    Copyright 2009-2015 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 */
public interface Executor {

  /**
   * 定义无ResultHandler常量
   */
  ResultHandler NO_RESULT_HANDLER = null;

  /**
   * 依据Mapper方法：MappedStatement，执行insert、update或delete
   */
  int update(MappedStatement ms, Object parameter) throws SQLException;

  /**
   * 查询（传入缓存与分页BoundSql）
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey cacheKey, BoundSql boundSql) throws SQLException;

  /**
   * 查询
   */
  <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException;

  /**
   * 查询，返回值为 Cursor
   */
  <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException;

  /**
   * 对于批量执行sql，在提交或者关闭事务之前，需调用Statement#executeBatch()批量执行语句
   */
  List<BatchResult> flushStatements() throws SQLException;

  /**
   * 提交事务
   */
  void commit(boolean required) throws SQLException;

  /**
   * 回滚事务
   */
  void rollback(boolean required) throws SQLException;

  /**
   * 依据MappedStatement与参数生成CacheKey
   */
  CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql);

  /**
   * 是否缓存
   */
  boolean isCached(MappedStatement ms, CacheKey key);

  /**
   * 清除本地缓存
   */
  void clearLocalCache();

  /**
   * 延迟加载
   */
  void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType);

  /**
   * 获取事务实体
   */
  Transaction getTransaction();

  /**
   * 关闭事务
   */
  void close(boolean forceRollback);

  /**
   * 是否关闭事务
   */
  boolean isClosed();

  /**
   * 设置Executor的包装对象
   */
  void setExecutorWrapper(Executor executor);

}
