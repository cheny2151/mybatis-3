package my.dto;


import java.util.ArrayList;
import java.util.List;

/**
 * @author cheney
 * @date 2019/4/30
 */
public class TypeTestDto<T,R> {

    public void someMethod() {

    }

    public List<T> getTList() {
        return new ArrayList<>();
    }

    public T getT() {
        return (T) new Object();
    }

    public R getR() {
        return (R) new Object();
    }
}
