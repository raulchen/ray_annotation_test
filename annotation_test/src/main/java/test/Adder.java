package test;

import ray.annotation.RayRemote;

@RayRemote
public class Adder {

    public static String addString(String a, String b) {
        return a + b;
    }

    public static int addInt(int a, int b) {
        return a + b;
    }

}
