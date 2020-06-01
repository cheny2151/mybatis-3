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

import java.util.HashMap;

/**
 * Inline parameter expression parser. Supported grammar (simplified):
 *
 * <pre>
 * inline-parameter = (propertyName | expression) oldJdbcType attributes
 * propertyName = /expression language's property navigation path/
 * expression = '(' /expression language's expression/ ')'
 * oldJdbcType = ':' /any valid jdbc type/
 * attributes = (',' attribute)*
 * attribute = name '=' value
 * </pre>
 *
 * 用于解析#{}中的表达式
 * 表达式示例：
 * a、表达式expression:#{(id.toString()):VARCHAR,arg1=val1}
 * expression=id
 * jdbcType=VARCHAR
 * arg1=val1
 * b、属性property:#{id,jdbcType=INTEGER,arg1=val1}
 * property=id
 * jdbcType=VARCHAR
 * arg1=val1
 *
 * @author Frank D. Martinez [mnesarco]
 */
public class ParameterExpression extends HashMap<String, String> {

  private static final long serialVersionUID = -2417552199605158680L;

  public ParameterExpression(String expression) {
    parse(expression);
  }

  private void parse(String expression) {
    // 返回第一个有意义的字符位置
    int p = skipWS(expression, 0);
    // 第一个有意义的字符为(,则解析为expression，否则为property
    if (expression.charAt(p) == '(') {
      expression(expression, p + 1);
    } else {
      property(expression, p);
    }
  }

  private void expression(String expression, int left) {
    // '('未匹配')'的个数
    int match = 1;
    int right = left + 1;
    while (match > 0) {
      if (expression.charAt(right) == ')') {
        match--;
      } else if (expression.charAt(right) == '(') {
        match++;
      }
      right++;
    }
    // 将第一个'('，与最后一个')'中间的字符串存起来
    put("expression", expression.substring(left, right - 1));
    // 从最后一个')'之后开始解析javaType与option
    jdbcTypeOpt(expression, right);
  }

  private void property(String expression, int left) {
    if (left < expression.length()) {
      // 返回','或者':'所在的位置
      int right = skipUntil(expression, left, ",:");
      // 截取','|':'之前的作为property并存放
      put("property", trimmedStr(expression, left, right));
      // 开始解析javaType与option
      jdbcTypeOpt(expression, right);
    }
  }

  /**
   * 返回第一个有意义的字符位置
   *
   * @param expression #{}中的表达式
   * @param p 起始位置
   * @return
   */
  private int skipWS(String expression, int p) {
    for (int i = p; i < expression.length(); i++) {
      // 0x20为16进制ASCII码：空格符
      if (expression.charAt(i) > 0x20) {
        return i;
      }
    }
    return expression.length();
  }

  /**
   * 返回endChars所在的位置
   */
  private int skipUntil(String expression, int p, final String endChars) {
    for (int i = p; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (endChars.indexOf(c) > -1) {
        return i;
      }
    }
    return expression.length();
  }

  private void jdbcTypeOpt(String expression, int p) {
    // 返回p开始，返回第一个有意义的字符位置
    p = skipWS(expression, p);
    if (p < expression.length()) {
      if (expression.charAt(p) == ':') {
        // 解析javaType
        jdbcType(expression, p + 1);
      } else if (expression.charAt(p) == ',') {
        // 解析option
        option(expression, p + 1);
      } else {
        throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
      }
    }
  }

  private void jdbcType(String expression, int p) {
    // 返回p开始，返回第一个有意义的字符位置
    int left = skipWS(expression, p);
    int right = skipUntil(expression, left, ",");
    if (right > left) {
      // 截取jdbcType并存放起来
      put("jdbcType", trimmedStr(expression, left, right));
    } else {
      throw new BuilderException("Parsing error in {" + expression + "} in position " + p);
    }
    // 开始解析option
    option(expression, right + 1);
  }

  private void option(String expression, int p) {
    // 返回p开始，第一个有意义的字符位置
    int left = skipWS(expression, p);
    // 若left未到表达式结尾
    if (left < expression.length()) {
      int right = skipUntil(expression, left, "=");
      // 获取'='之前的字符串
      String name = trimmedStr(expression, left, right);
      left = right + 1;
      right = skipUntil(expression, left, ",");
      // 获取'='之后','之前的字符串
      String value = trimmedStr(expression, left, right);
      // 存放
      put(name, value);
      // 递归
      option(expression, right + 1);
    }
  }

  /**
   * 截取str字符串start到end（不包含end）,截取过程会跳过无用字符
   */
  private String trimmedStr(String str, int start, int end) {
    while (str.charAt(start) <= 0x20) {
      start++;
    }
    while (str.charAt(end - 1) <= 0x20) {
      end--;
    }
    return start >= end ? "" : str.substring(start, end);
  }

}
