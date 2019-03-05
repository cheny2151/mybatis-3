package my.reflect;

import org.apache.ibatis.domain.jpetstore.Cart;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

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

}
