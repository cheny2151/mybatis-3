/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.scripting.xmltags;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession;

/**
 * @author Clinton Begin
 */
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  /**
   * sql根节点：
   * xml配置时为XMLScriptBuilder#parseDynamicTags()解析结果:MixedSqlNode
   */
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  /**
   * 主要过程：
   * rootSqlNode：含${}与<if/>等标签的动态sql（也包含#{}）
   * => SqlNode#apply：依据入参parameterObject解析动态内容，${}内容由OGNL表达式解析
   * => SqlSourceBuilder#parse：解析#{}，替换为?占位符并生成ParameterMapping，返回StaticSqlSource
   * => StaticSqlSource#getBoundSql: 生成一个BoundSql
   * => 返回BoundSql
   *
   * 注意：
   * 对于${},是在此处执行TextSqlNode#apply时，通过OGNL表达式解析器解析获取值;
   * 对于#{}，是在执行sql时，通过反射获取对应的属性值。
   *
   * @param parameterObject Mapper接口方法实际入参 ->
   *                        经过{@link MapperMethod.MethodSignature#convertArgsToSqlCommandParam(java.lang.Object[]))后 ->
   *                        又经过{@link DefaultSqlSession#wrapCollection(java.lang.Object)}
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // parameterObject为Mapper接口入参(原始Mapper接口入参经过#convertArgsToSqlCommandParam与#wrapCollection)
    // 创建DynamicContext，用于存放sql拼接
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    /* 从根节点开始调用#apply()，最终将根据绑定值解析动态sql节点拼接成静态sql存放于context#sqlBuilder成员变量，
       既执行完#apply()后，<if/>等动态节点与${}都填充解析完毕，除了sql就只剩#{} */
    rootSqlNode.apply(context);
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // 解析#{}返回StaticSqlSource（context.getBindings()为parameterObject再外加额外参数）
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // 将DynamicContext#bindings属性中属于Map部分的参数设置到BoundSql#additionalParameters中
    context.getBindings().forEach(boundSql::setAdditionalParameter);
    // 返回的boundSql已经具备了sql需要执行的所有碎片（静态sql，参数绑定值）
    return boundSql;
  }

}
