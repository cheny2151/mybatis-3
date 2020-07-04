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
package org.apache.ibatis.builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.GenericTokenParser;
import org.apache.ibatis.parsing.TokenHandler;
import org.apache.ibatis.reflection.MetaClass;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;

/**
 * @author Clinton Begin
 */
public class SqlSourceBuilder extends BaseBuilder {

  private static final String PARAMETER_PROPERTIES = "javaType,jdbcType,mode,numericScale,resultMap,typeHandler,jdbcTypeName";

  public SqlSourceBuilder(Configuration configuration) {
    super(configuration);
  }

  /**
   * 解析sql将#{}内容映射为一个ParameterMapping，并将#{}替换为?
   *
   * @param originalSql          静态sql
   * @param parameterType        Mapper接口入参(经过#convertArgsToSqlCommandParam与#wrapCollection)通过#getClass获得的类型
   * @param additionalParameters 额外添加的参数
   */
  public SqlSource parse(String originalSql, Class<?> parameterType, Map<String, Object> additionalParameters) {
    ParameterMappingTokenHandler handler = new ParameterMappingTokenHandler(configuration, parameterType, additionalParameters);
    GenericTokenParser parser = new GenericTokenParser("#{", "}", handler);
    // 解析sql将#{}内容映射为一个ParameterMapping，并将#{}替换为?
    String sql = parser.parse(originalSql);
    // 生成一个存放静态sql的对象
    return new StaticSqlSource(configuration, sql, handler.getParameterMappings());
  }

  /**
   * token解析钩子
   */
  private static class ParameterMappingTokenHandler extends BaseBuilder implements TokenHandler {

    // #{}映射结果
    private List<ParameterMapping> parameterMappings = new ArrayList<>();
    // Mapper接口入参(经过#convertArgsToSqlCommandParam与#wrapCollection)通过#getClass获得的类型
    private Class<?> parameterType;
    // 额外参数additionalParameters的MetaObject
    private MetaObject metaParameters;

    public ParameterMappingTokenHandler(Configuration configuration, Class<?> parameterType, Map<String, Object> additionalParameters) {
      super(configuration);
      this.parameterType = parameterType;
      this.metaParameters = configuration.newMetaObject(additionalParameters);
    }

    public List<ParameterMapping> getParameterMappings() {
      return parameterMappings;
    }

    /**
     * 发现#{}时的执行的回调，替换解析#{}为ParameterMapping并替换为?
     */
    @Override
    public String handleToken(String content) {
      parameterMappings.add(buildParameterMapping(content));
      return "?";
    }

    /**
     * 将#{}的内容映射到ParameterMapping对象中
     * 所以一个#{}对应一个ParameterMapping实例
     *
     * @param content #{}的内容
     * @return
     */
    private ParameterMapping buildParameterMapping(String content) {
      // 将表达式解析成键值对的形式
      Map<String, String> propertiesMap = parseParameterMapping(content);
      // 从解析结果中获取property
      String property = propertiesMap.get("property");
      Class<?> propertyType;
      // 获取属性类型获取优先级：额外参数 => 存在对应的typeHandlerRegistry => 游标CURSOR => null||Map => java实体
      if (metaParameters.hasGetter(property)) { // issue #448 get type from additional params
        // 额外参数中获取类型（metaParameters为额外参数additional）
        propertyType = metaParameters.getGetterType(property);
      } else if (typeHandlerRegistry.hasTypeHandler(parameterType)) {
        // 存在对应的typeHandlerRegistry（基本上为基本类型），直接返回此类型
        propertyType = parameterType;
      } else if (JdbcType.CURSOR.name().equals(propertiesMap.get("jdbcType"))) {
        // 游标
        propertyType = java.sql.ResultSet.class;
      } else if (property == null || Map.class.isAssignableFrom(parameterType)) {
        // null||Map，设置为Object（只知道为Map，无法判断property对应值类型）
        propertyType = Object.class;
      } else {
        // java实体，通过parameterType获取属性类型（MetaClass）
        MetaClass metaClass = MetaClass.forClass(parameterType, configuration.getReflectorFactory());
        if (metaClass.hasGetter(property)) {
          propertyType = metaClass.getGetterType(property);
        } else {
          propertyType = Object.class;
        }
      }
      // 根据propertiesMap（即#{}中表达式的解析结果键值对）实例化成ParameterMapping对象
      // 注意此处构建Builder时，已经通过构造函数将javaType设置为propertyType
      ParameterMapping.Builder builder = new ParameterMapping.Builder(configuration, property, propertyType);
      Class<?> javaType = propertyType;
      String typeHandlerAlias = null;
      for (Map.Entry<String, String> entry : propertiesMap.entrySet()) {
        String name = entry.getKey();
        String value = entry.getValue();
        if ("javaType".equals(name)) {
          // 覆盖propertyType
          javaType = resolveClass(value);
          builder.javaType(javaType);
        } else if ("jdbcType".equals(name)) {
          builder.jdbcType(resolveJdbcType(value));
        } else if ("mode".equals(name)) {
          builder.mode(resolveParameterMode(value));
        } else if ("numericScale".equals(name)) {
          builder.numericScale(Integer.valueOf(value));
        } else if ("resultMap".equals(name)) {
          builder.resultMapId(value);
        } else if ("typeHandler".equals(name)) {
          typeHandlerAlias = value;
        } else if ("jdbcTypeName".equals(name)) {
          builder.jdbcTypeName(value);
        } else if ("property".equals(name)) {
          // Do Nothing
          // 构造Builder的时候已经传入该property
        } else if ("expression".equals(name)) {
          // #{}不支持表达式（${}才支持）
          throw new BuilderException("Expression based parameters are not supported yet");
        } else {
          throw new BuilderException("An invalid property '" + name + "' was found in mapping #{" + content + "}.  Valid properties are " + PARAMETER_PROPERTIES);
        }
      }
      if (typeHandlerAlias != null) {
        // 当#{}中有声明typeHandler属性
        builder.typeHandler(resolveTypeHandler(javaType, typeHandlerAlias));
      }
      // 生成ParameterMapping
      return builder.build();
    }

    private Map<String, String> parseParameterMapping(String content) {
      try {
        return new ParameterExpression(content);
      } catch (BuilderException ex) {
        throw ex;
      } catch (Exception ex) {
        throw new BuilderException("Parsing error was found in mapping #{" + content + "}.  Check syntax #{property|(expression), var1=value1, var2=value2, ...} ", ex);
      }
    }
  }

}
