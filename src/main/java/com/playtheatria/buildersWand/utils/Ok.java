package com.playtheatria.buildersWand.utils;

public record Ok<T, E extends Throwable>(T value) implements Result<T, E> {}
