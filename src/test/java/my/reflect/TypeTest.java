package my.reflect;

import my.dto.TypeTestDto;
import my.dto.TypeTestDto2;
import my.dto.TypeTestDto3;
import org.apache.ibatis.domain.jpetstore.Cart;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * @author cheney
 */
public class TypeTest {

    /**
     * itemList字段的值类型为ParameterizedType，其RawType为List，实际泛型参数为CartItem
     */
    @Test
    public void test() throws NoSuchFieldException {
        Type getCartItemList = Cart.class.getDeclaredField("itemList").getGenericType();
        Type rawType = ((ParameterizedType) getCartItemList).getRawType();
        System.out.println(rawType);
        System.out.println("==================");
        Type[] actualTypeArguments = ((ParameterizedType) getCartItemList).getActualTypeArguments();
        for (Type type : actualTypeArguments) {
            System.out.println(type);
        }
    }

    /**
     * getGenericReturnType -> 返回type(parameterizedType...)
     * getReturnType -> 返回class
     *
     * @throws NoSuchMethodException
     */
    @Test
    public void test2() throws NoSuchMethodException {
        Type genericReturnType = Cart.class.getMethod("getCartItemList").getGenericReturnType();
        Type returnType = Cart.class.getMethod("getCartItemList").getReturnType();
        System.out.println(genericReturnType);
        System.out.println(returnType);
    }

    @Test
    public void test3() throws NoSuchMethodException {
        Method getT = TypeTestDto.class.getMethod("getTList");
        Type genericReturnType = getT.getGenericReturnType();
        System.out.println(genericReturnType);
        System.out.println(genericReturnType instanceof ParameterizedType);

        System.out.println("==================================");

        Method t = TypeTestDto.class.getMethod("getT");
        Type genericReturnType2 = t.getGenericReturnType();
        System.out.println(genericReturnType2);
        System.out.println(genericReturnType2 instanceof TypeVariable);
        TypeVariable typeVariable = (TypeVariable) genericReturnType2;
        System.out.println(typeVariable.getBounds()[0]);
        System.out.println(typeVariable.getGenericDeclaration().getTypeParameters()[0]);

        System.out.println("==================================");

        Method t2 = TypeTestDto2.class.getMethod("getT");
        System.out.println(t2.getDeclaringClass());
    }

    /**
     * 获取方法返回泛型擦除后的类型的方式(这里的擦除是指继承时将泛型参数具象化,例:ArrayList<T> --> Test extend ArrayList<String>,此时泛型参数具象化后为String)
     *
     * @throws NoSuchMethodException
     */
    @Test
    public void test4() throws NoSuchMethodException {
        Method getT = TypeTestDto2.class.getMethod("getT");
        Class<?> superclass = TypeTestDto2.class.getSuperclass();
        Type superType = TypeTestDto2.class.getGenericSuperclass();
        //转为ParameterizedType
        ParameterizedType parameterizedType = (ParameterizedType) superType;
        Type superClassAsClass = parameterizedType.getRawType();

        //证明ParameterizedType的rawType与superClass实际上是同个对象
        System.out.println(superclass == superClassAsClass);

        //获取父类的泛型参数
        TypeVariable<? extends Class<?>> typeParameter = superclass.getTypeParameters()[0];
        //index[0]的类型与getT方法返回的泛型参数一致，所以getT的TypeVariable擦除后的类型
        System.out.println(typeParameter == getT.getGenericReturnType());

        //获取泛型擦除后的类型
        System.out.println(parameterizedType.getActualTypeArguments()[0]);
    }

    /**
     * 获取类上所有泛型擦除后的类型的方式(这里的擦除是指继承时将泛型参数具象化,
     * 例:TypeTestDto3 extend TypeTestDto2<Integer>,TypeTestDto2<R> extend TypeTestDto<String,R>此时泛型参数具象化后为<String,Integer>
     * 从第一个父级开始向父级递归：
     * 递归方法：第一次 -> TypeTestDto2_parameterizedType.getRawType() ->TypeTestDto2_class.getTypeParameters() 获取所有原始泛型 -> R
     *                   TypeTestDto2_parameterizedType.getActualTypeArguments() 获取所有泛型参数 -> Integer
     *
     *                   TypeTestDto2_class.getGenericSuperclass() -> TypeTestDto_parameterizedType.getActualTypeArguments() -> <String,R>
     *                       ->判断R == TypeTestDto2_class原始泛型的R -> 得出R具象化后为Integer类型 -> 得出<String,Integer>
     *
     * 若TypeTestDto还有superClass则继续递归。
     */
    @Test
    public void test5() {
        Type superType_Dto2 = TypeTestDto3.class.getGenericSuperclass();
        ParameterizedType parameterizedType_Dto2 = (ParameterizedType) superType_Dto2;
        Class<?> superClass_Dto2 = (Class<?>) parameterizedType_Dto2.getRawType();
        ParameterizedType parameterizedType_Dto = (ParameterizedType) superClass_Dto2.getGenericSuperclass();
        System.out.println("TypeTestDto2_class -> " + superClass_Dto2.getTypeParameters()[0]);
        System.out.println("parameterizedType_Dto2 -> " + parameterizedType_Dto2.getActualTypeArguments()[0]);
        System.out.print("parameterizedType_Dto -> " + parameterizedType_Dto.getActualTypeArguments()[0]);
        System.out.println("," + parameterizedType_Dto.getActualTypeArguments()[1]);

        System.out.println("===============================");

        boolean eq = superClass_Dto2.getTypeParameters()[0] == parameterizedType_Dto.getActualTypeArguments()[1];
        System.out.println(eq);
        if (eq) {
            System.out.println(superClass_Dto2.getTypeParameters()[0] + " -> " + parameterizedType_Dto2.getActualTypeArguments()[0]);
        }

    }

    @Test
    public void test6() throws NoSuchMethodException {
        Method getT = TypeTestDto2.class.getMethod("getT");
        Type genericReturnType = getT.getGenericReturnType();
        for (Type t : ((TypeVariable) genericReturnType).getBounds()) {
            System.out.println(t);
        }
    }
}
