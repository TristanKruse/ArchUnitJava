package com.example.service;

import com.example.api.Greeting;

public final class GreetingService {
    public Greeting greet(String name) {
        return new Greeting("Hello, " + name);
    }
}
