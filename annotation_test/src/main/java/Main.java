import ray.RayObject;
import test.AdderRemote;

import java.util.Arrays;
import java.util.stream.Stream;

public class Main {
    public static void main(String[] args) {
        Stream.of(
                AdderRemote.addInt(1, 2),
                AdderRemote.addInt(1, RayObject.of(2)),
                AdderRemote.addString(RayObject.of("hello"), "world"),
                AdderRemote.addString(RayObject.of("hello"), RayObject.of("world"))
        ).map(RayObject::get).forEach(System.out::println);

    }
}
