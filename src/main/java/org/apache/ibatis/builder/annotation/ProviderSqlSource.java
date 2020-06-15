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
package org.apache.ibatis.builder.annotation;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.reflection.ParamNameResolver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.defaults.DefaultSqlSession;

/**
 * Mapper方法为@provider类型注解，则通过ProviderSqlSource创建BoundSql
 *
 * @author Clinton Begin
 * @author Kazuki Shimizu
 */
public class ProviderSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlSourceBuilder sqlSourceParser;
  private final Class<?> providerType;
  // @provider类型注解指定的type（类）与method（方法名）对应的方法
  private Method providerMethod;
  // sql执行时绑定的入参名
  private String[] providerMethodArgumentNames;
  // provider方法的参数类型数组
  private Class<?>[] providerMethodParameterTypes;
  private ProviderContext providerContext;
  private Integer providerContextIndex;

  /**
   * @deprecated Please use the {@link #ProviderSqlSource(Configuration, Object, Class, Method)} instead of this.
   */
  @Deprecated
  public ProviderSqlSource(Configuration configuration, Object provider) {
    this(configuration, provider, null, null);
  }

  /**
   * @since 3.4.5
   * @param provider @provider注解实例对象
   * @param mapperType Mapper接口类型
   * @param mapperMethod 当前在解析的Mapper接口方法
   */
  public ProviderSqlSource(Configuration configuration, Object provider, Class<?> mapperType, Method mapperMethod) {
    String providerMethodName;
    try {
      this.configuration = configuration;
      this.sqlSourceParser = new SqlSourceBuilder(configuration);
      // @provider注解的type属性值：provider类型
      this.providerType = (Class<?>) provider.getClass().getMethod("type").invoke(provider);
      // provider类的方法名
      providerMethodName = (String) provider.getClass().getMethod("method").invoke(provider);

      // 以方法名获取方法实例
      for (Method m : this.providerType.getMethods()) {
        if (providerMethodName.equals(m.getName()) && CharSequence.class.isAssignableFrom(m.getReturnType())) {
          // 匹配两个符合条件的方法则抛异常
          if (providerMethod != null) {
            throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
                    + providerMethodName + "' is found multiple in SqlProvider '" + this.providerType.getName()
                    + "'. Sql provider method can not overload.");
          }
          this.providerMethod = m;
          // 获取执行sql时绑定的入参名
          this.providerMethodArgumentNames = new ParamNameResolver(configuration, m).getNames();
          // provider方法的参数类型数组
          this.providerMethodParameterTypes = m.getParameterTypes();
        }
      }
    } catch (BuilderException e) {
      throw e;
    } catch (Exception e) {
      throw new BuilderException("Error creating SqlSource for SqlProvider.  Cause: " + e, e);
    }
    if (this.providerMethod == null) {
      throw new BuilderException("Error creating SqlSource for SqlProvider. Method '"
          + providerMethodName + "' not found in SqlProvider '" + this.providerType.getName() + "'.");
    }
    for (int i = 0; i < this.providerMethodParameterTypes.length; i++) {
      Class<?> parameterType = this.providerMethodParameterTypes[i];
      // 若指定的provider方法参数存在ProviderContext类型，则将mapperType与mapperMethod包装到ProviderContext，待执行provider方法获取sql时传入
      if (parameterType == ProviderContext.class) {
        if (this.providerContext != null) {
          // 不可出现两个或以上ProviderContext参数
          throw new BuilderException("Error creating SqlSource for SqlProvider. ProviderContext found multiple in SqlProvider method ("
              + this.providerType.getName() + "." + providerMethod.getName()
              + "). ProviderContext can not define multiple in SqlProvider method argument.");
        }
        this.providerContext = new ProviderContext(mapperType, mapperMethod);
        // 记录ProviderContext参数位置
        this.providerContextIndex = i;
      }
    }
  }

  /**
   *
   * @param parameterObject Mapper接口方法实际入参 ->
   *                        经过{@link MapperMethod.MethodSignature#convertArgsToSqlCommandParam(java.lang.Object[]))后 ->
   *                        又经过{@link DefaultSqlSession#wrapCollection(java.lang.Object)}
   * @return
   */
  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // 执行provider方法获取sql，并解析创建StaticSqlSource返回
    SqlSource sqlSource = createSqlSource(parameterObject);
    // 执行StaticSqlSource#getBoundSql
    return sqlSource.getBoundSql(parameterObject);
  }

  /**
   *
   * @param parameterObject 实际执行Mapper接口方法时绑定的入参
   * @return StaticSqlSource
   */
  private SqlSource createSqlSource(Object parameterObject) {
    try {
      // 获取绑定参数个数（除去ProviderContext类型）
      int bindParameterCount = providerMethodParameterTypes.length - (providerContext == null ? 0 : 1);
      String sql;
      // 根据provider方法实际入参情况提供入参，进行调用返回静态sql
      if (providerMethodParameterTypes.length == 0) {
        // 执行Provider方法获取sql -- 无参
        sql = invokeProviderMethod();
      } else if (bindParameterCount == 0) {
        // 执行Provider方法获取sql -- 有且只有ProviderContext类型参数（可参考示例：OurSqlBuilder#buildSelectByIdProviderContextOnly）
        sql = invokeProviderMethod(providerContext);
      } else if (bindParameterCount == 1
           && (parameterObject == null || providerMethodParameterTypes[providerContextIndex == null || providerContextIndex == 1 ? 0 : 1].isAssignableFrom(parameterObject.getClass()))) {
        // 除去ProviderContext类型的入参只有一个，且符合实际参数parameterObject类型：
        // 执行Provider方法获取sql -- ProviderContext类型与实际入参，作为Provider方法入参
        sql = invokeProviderMethod(extractProviderMethodArguments(parameterObject));
      } else if (parameterObject instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) parameterObject;
        // 实际入参parameterObject为Map，以provider方法参数名为key从Map中查询对应value组成provider方法入参数组
        // 执行Provider方法获取sql -- ProviderContext类型与多个参数
        sql = invokeProviderMethod(extractProviderMethodArguments(params, providerMethodArgumentNames));
      } else {
        throw new BuilderException("Error invoking SqlProvider method ("
                + providerType.getName() + "." + providerMethod.getName()
                + "). Cannot invoke a method that holds "
                + (bindParameterCount == 1 ? "named argument(@Param)" : "multiple arguments")
                + " using a specifying parameterObject. In this case, please specify a 'java.util.Map' object.");
      }
      Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
      // 替换#{}为?,解析为StaticSqlSource静态sql实体
      return sqlSourceParser.parse(replacePlaceholder(sql), parameterType, new HashMap<>());
    } catch (BuilderException e) {
      throw e;
    } catch (Exception e) {
      throw new BuilderException("Error invoking SqlProvider method ("
          + providerType.getName() + "." + providerMethod.getName()
          + ").  Cause: " + e, e);
    }
  }

  /**
   * 提取Provider方法入参
   * 有ProviderContext类型参数则入参为providerContext与实际入参parameterObject
   * 否则只有parameterObject
   *
   * @param parameterObject 实际执行Mapper接口方法时绑定的入参
   * @return
   */
  private Object[] extractProviderMethodArguments(Object parameterObject) {
    if (providerContext != null) {
      // 存在ProviderContext
      Object[] args = new Object[2];
      args[providerContextIndex == 0 ? 1 : 0] = parameterObject;
      args[providerContextIndex] = providerContext;
      return args;
    } else {
      // 只有parameterObject
      return new Object[] { parameterObject };
    }
  }

  private Object[] extractProviderMethodArguments(Map<String, Object> params, String[] argumentNames) {
    Object[] args = new Object[argumentNames.length];
    for (int i = 0; i < args.length; i++) {
      if (providerContextIndex != null && providerContextIndex == i) {
        args[i] = providerContext;
      } else {
        args[i] = params.get(argumentNames[i]);
      }
    }
    return args;
  }

  private String invokeProviderMethod(Object... args) throws Exception {
    Object targetObject = null;
    if (!Modifier.isStatic(providerMethod.getModifiers())) {
      // 不为static方法，则实例化对象
      targetObject = providerType.newInstance();
    }
    // 执行(静态)方法
    CharSequence sql = (CharSequence) providerMethod.invoke(targetObject, args);
    return sql != null ? sql.toString() : null;
  }

  private String replacePlaceholder(String sql) {
    return PropertyParser.parse(sql, configuration.getVariables());
  }

}
