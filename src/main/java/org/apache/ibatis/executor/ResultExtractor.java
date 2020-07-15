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
package org.apache.ibatis.executor;

import java.lang.reflect.Array;
import java.util.List;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.Configuration;

/**
 * @author Andrew Gustafson
 */
public class ResultExtractor {
  private final Configuration configuration;
  private final ObjectFactory objectFactory;

  public ResultExtractor(Configuration configuration, ObjectFactory objectFactory) {
    this.configuration = configuration;
    this.objectFactory = objectFactory;
  }

  /**
   * 尝试提取list为targetType类型：
   * 1.targetType为List：直接返回入参list；
   * 2.targetType为其他Collection子类：通过objectFactory创建Collection；
   * 3.targetType为数组：构建Array并复制值；
   * 4.否则list只能存在一个值，返回该值。
   *
   * @param list       Executor执行结果
   * @param targetType 集合或数组类型
   * @return
   */
  public Object extractObjectFromList(List<Object> list, Class<?> targetType) {
    Object value = null;
    if (targetType != null && targetType.isAssignableFrom(list.getClass())) {
      // targetType为List或List的父类
      value = list;
    } else if (targetType != null && objectFactory.isCollection(targetType)) {
      // targetType为Collection或其他Collection子类
      value = objectFactory.create(targetType);
      MetaObject metaObject = configuration.newMetaObject(value);
      metaObject.addAll(list);
    } else if (targetType != null && targetType.isArray()) {
      // targetType为数组
      Class<?> arrayComponentType = targetType.getComponentType();
      // 反射创建数组
      Object array = Array.newInstance(arrayComponentType, list.size());
      // 为array赋值
      if (arrayComponentType.isPrimitive()) {
        for (int i = 0; i < list.size(); i++) {
          Array.set(array, i, list.get(i));
        }
        value = array;
      } else {
        value = list.toArray((Object[])array);
      }
    } else {
      if (list != null && list.size() > 1) {
        throw new ExecutorException("Statement returned more than one row, where no more than one was expected.");
      } else if (list != null && list.size() == 1) {
        // 只有一个值
        value = list.get(0);
      }
    }
    return value;
  }
}
