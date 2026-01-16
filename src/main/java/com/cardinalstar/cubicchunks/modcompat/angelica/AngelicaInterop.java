package com.cardinalstar.cubicchunks.modcompat.angelica;

public class AngelicaInterop {

    private static IAngelicaDelegate delegate;

    public static boolean hasDelegate() {
        return delegate != null;
    }

    public static IAngelicaDelegate getDelegate() {
        return delegate;
    }

    public static void setDelegate(IAngelicaDelegate delegate) {
        AngelicaInterop.delegate = delegate;
    }
}
