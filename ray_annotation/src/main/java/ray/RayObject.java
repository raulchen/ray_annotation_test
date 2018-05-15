package ray;

public class RayObject <T> {

    private T value;

    private RayObject(T value){
        this.value = value;
    }

    public T get() {
        return value;
    }

    public static <T> RayObject<T> of(T value){
        return new RayObject<>(value);
    }
}
