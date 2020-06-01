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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.defaults.RawSqlSource;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * XMLScriptBuilder将解析<select/>、<update/>、<delete/>和<insert/>的字节点们
 * 并封装成sql脚本的实体MixedSqlNode
 *
 * @author Clinton Begin
 */
public class XMLScriptBuilder extends BaseBuilder {

  private final XNode context;
  /**
   * 是否为动态sql：
   * 当<select/>、<update/>、<delete/>或<insert/>节点存在${}或者存在ELEMENT_NODE类型的子节点(类似<if/>等)则为动态sql，
   * 值得注意的是只存在#{}并非动态sql，而是jdbc的?占位
   */
  private boolean isDynamic;
  private final Class<?> parameterType;
  private final Map<String, NodeHandler> nodeHandlerMap = new HashMap<>();

  public XMLScriptBuilder(Configuration configuration, XNode context) {
    this(configuration, context, null);
  }

  public XMLScriptBuilder(Configuration configuration, XNode context, Class<?> parameterType) {
    super(configuration);
    this.context = context;
    this.parameterType = parameterType;
    initNodeHandlerMap();
  }

  /**
   * 初始化所有的NodeHandler
   */
  private void initNodeHandlerMap() {
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }

  public SqlSource parseScriptNode() {
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource;
    if (isDynamic) {
      // 动态sql创建DynamicSqlSource
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      // 静态sql创建RawSqlSource
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  /**
   * 用于解析节点的所有child：
   * 将节点的每个child实例化SqlNode，并将所有SqlNode的集合存放到MixedSqlNode对象
   * 注意：
   * <select>
   *     xxx
   *    <if test=''>
   *      yyy
   *     </if>
   * </select>
   * <select>的子节点为<if>，type为ELEMENT_NODE和xxx,type为TEXT_NODE
   * <if>的子节点为yyy,type为TEXT_NODE
   *
   * 方法执行逻辑：
   * 1.首次调用此方法时,node为<select/>、<update/>、<delete/>或<insert/>节点；
   * 2.当解析上述节点的子节点type为TEXT_NODE时，将该子节点实例化为TextSqlNode或StaticTextSqlNode，添加至contents，return MixedSqlNode；
   * 3.当解析上述节点的子节点type为ELEMENT_NODE时，将通过对应的NodeHandler解析，部分节点会进一步递归此方法，最终生成一个相应的SqlNode实现类添加至contents；
   * 4.最终只有StaticTextSqlNode或者TextSqlNode的#apply方法会拼接sql，其他类型的SqlNode实质上是对其他SqlNode的装饰增强，
   * 经过层层的装饰加强后最终将由StaticTextSqlNode或者TextSqlNode执行拼接sql。
   *
   * @param node 节点
   * @return 实例化为MixedSqlNode，MixedSqlNode为特殊的SqlNode实现类，用于将所有
   */
  protected MixedSqlNode parseDynamicTags(XNode node) {
    // 用于存放处理完成的子节点，每个子节点将映射为SqlNode实例
    List<SqlNode> contents = new ArrayList<>();
    NodeList children = node.getNode().getChildNodes();
    // 循环处理每一个子节点
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      // Node.CDATA_SECTION_NODE指CDATA节点
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        // 节点为纯文本
        String data = child.getStringBody("");
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // 判断是否是动态sql（是否包含${}）
        if (textSqlNode.isDynamic()) {
          contents.add(textSqlNode);
          // 包含${}则标记为动态sql
          isDynamic = true;
        } else {
          contents.add(new StaticTextSqlNode(data));
        }
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628
        // 节点为element（包含不为纯文本的子节点）,例如<if>、<foreach>...
        String nodeName = child.getNode().getNodeName();
        // 每个<if>、<foreach>之类的节点对应一个NodeHandler
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // 调用NodeHandler对应的实现类处理节点并添加到contents
        handler.handleNode(child, contents);
        // 出现非文本子节点则标记为动态sql
        isDynamic = true;
      }
    }
    // 返回所有child实例化SqlNode集合
    return new MixedSqlNode(contents);
  }

  private interface NodeHandler {
    void handleNode(XNode nodeToHandle, List<SqlNode> targetContents);
  }

  private class BindHandler implements NodeHandler {
    public BindHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      final String name = nodeToHandle.getStringAttribute("name");
      final String expression = nodeToHandle.getStringAttribute("value");
      // 用于绑定新key，value对
      final VarDeclSqlNode node = new VarDeclSqlNode(name, expression);
      targetContents.add(node);
    }
  }

  private class TrimHandler implements NodeHandler {
    public TrimHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      String prefix = nodeToHandle.getStringAttribute("prefix");
      String prefixOverrides = nodeToHandle.getStringAttribute("prefixOverrides");
      String suffix = nodeToHandle.getStringAttribute("suffix");
      String suffixOverrides = nodeToHandle.getStringAttribute("suffixOverrides");
      TrimSqlNode trim = new TrimSqlNode(configuration, mixedSqlNode, prefix, prefixOverrides, suffix, suffixOverrides);
      targetContents.add(trim);
    }
  }

  private class WhereHandler implements NodeHandler {
    public WhereHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      WhereSqlNode where = new WhereSqlNode(configuration, mixedSqlNode);
      targetContents.add(where);
    }
  }

  private class SetHandler implements NodeHandler {
    public SetHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析set子节点
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 包装为SetSqlNode，提供<set/>节点特有的逻辑
      SetSqlNode set = new SetSqlNode(configuration, mixedSqlNode);
      // 添加到同层级SqlNode尾部
      targetContents.add(set);
    }
  }

  private class ForEachHandler implements NodeHandler {
    public ForEachHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 先解析foreach子节点
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 取出foreach上所有属性
      String collection = nodeToHandle.getStringAttribute("collection");
      String item = nodeToHandle.getStringAttribute("item");
      String index = nodeToHandle.getStringAttribute("index");
      String open = nodeToHandle.getStringAttribute("open");
      String close = nodeToHandle.getStringAttribute("close");
      String separator = nodeToHandle.getStringAttribute("separator");
      // 包装为ForEachSqlNode，实现遍历逻辑
      ForEachSqlNode forEachSqlNode = new ForEachSqlNode(configuration, mixedSqlNode, collection, index, item, open, close, separator);
      // 添加到同层级SqlNode尾部
      targetContents.add(forEachSqlNode);
    }
  }

  private class IfHandler implements NodeHandler {
    public IfHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 递归调用解析动态sql
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      // 获取if上的test属性值
      String test = nodeToHandle.getStringAttribute("test");
      // 将解析结果包装到IfSqlNode中，实现test判断
      IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
      // 添加到同层级SqlNode尾部
      targetContents.add(ifSqlNode);
    }
  }

  private class OtherwiseHandler implements NodeHandler {
    public OtherwiseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      // 解析otherwise,otherwise无其他逻辑直接将解析结果添加到targetContents
      MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
      targetContents.add(mixedSqlNode);
    }
  }

  private class ChooseHandler implements NodeHandler {
    public ChooseHandler() {
      // Prevent Synthetic Access
    }

    @Override
    public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
      List<SqlNode> whenSqlNodes = new ArrayList<>();
      List<SqlNode> otherwiseSqlNodes = new ArrayList<>();
      // 处理if，otherwise节点，分别添加到whenSqlNodes和otherwiseSqlNodes
      handleWhenOtherwiseNodes(nodeToHandle, whenSqlNodes, otherwiseSqlNodes);
      // 获取otherwiseSqlNodes唯一的元素，可为空，不唯一则抛出异常
      SqlNode defaultSqlNode = getDefaultSqlNode(otherwiseSqlNodes);
      // 包装为ChooseSqlNode
      ChooseSqlNode chooseSqlNode = new ChooseSqlNode(whenSqlNodes, defaultSqlNode);
      // 添加到同层级SqlNode尾部
      targetContents.add(chooseSqlNode);
    }

    private void handleWhenOtherwiseNodes(XNode chooseSqlNode, List<SqlNode> ifSqlNodes, List<SqlNode> defaultSqlNodes) {
      List<XNode> children = chooseSqlNode.getChildren();
      for (XNode child : children) {
        String nodeName = child.getNode().getNodeName();
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        // Choose的if子节点处理后添加到ifSqlNodes，otherwise子节点解析后添加到defaultSqlNodes
        if (handler instanceof IfHandler) {
          handler.handleNode(child, ifSqlNodes);
        } else if (handler instanceof OtherwiseHandler) {
          handler.handleNode(child, defaultSqlNodes);
        }
      }
    }

    private SqlNode getDefaultSqlNode(List<SqlNode> defaultSqlNodes) {
      SqlNode defaultSqlNode = null;
      if (defaultSqlNodes.size() == 1) {
        defaultSqlNode = defaultSqlNodes.get(0);
      } else if (defaultSqlNodes.size() > 1) {
        throw new BuilderException("Too many default (otherwise) elements in choose statement.");
      }
      return defaultSqlNode;
    }
  }

}
